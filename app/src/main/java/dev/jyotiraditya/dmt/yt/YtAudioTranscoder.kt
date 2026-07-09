package dev.jyotiraditya.dmt.yt

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/** Output formats offered from the Search YT "save" action. Produced with
 * Android's built-in MediaCodec/MediaMuxer only — no external encoder
 * libraries. MP3 relies on the device having a built-in MP3 encoder codec;
 * if a device lacks one, that single conversion fails gracefully (the other
 * three formats are unaffected). */
enum class YtAudioFormat(val label: String, val extension: String) {
    AAC("aac (.m4a)", "m4a"),
    MP3("mp3 (compressed)", "mp3"),
    WAV("wav (lossless pcm)", "wav"),
    FLAC("flac (lossless)", "flac"),
}

object YtAudioTranscoder {

    /** Downloads [sourceUrl] to a temp file, converts it to [format], and
     * writes the result into [outputStream]. Returns true on success. */
    fun convert(
        context: Context,
        sourceUrl: String,
        format: YtAudioFormat,
        userAgent: String,
        outputStream: OutputStream,
    ): Boolean {
        val tempIn = File.createTempFile("dmt_yt_src", ".tmp", context.cacheDir)
        return try {
            downloadTo(sourceUrl, userAgent, tempIn)
            when (format) {
                YtAudioFormat.AAC -> remux(tempIn, outputStream)
                YtAudioFormat.MP3 -> decodeToMp3(tempIn, outputStream)
                YtAudioFormat.WAV -> decodeToWav(tempIn, outputStream)
                YtAudioFormat.FLAC -> decodeToFlac(tempIn, outputStream)
            }
        } catch (e: Exception) {
            false
        } finally {
            tempIn.delete()
        }
    }

    private fun downloadTo(url: String, userAgent: String, dest: File) {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.setRequestProperty("User-Agent", userAgent)
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.inputStream.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? =
        (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        }

    /** Straight remux of the source's own audio track into a valid .m4a
     * container — no re-encoding, so this is fast and lossless relative to
     * the source stream. */
    private fun remux(source: File, outputStream: OutputStream): Boolean {
        val tempOut = File.createTempFile("dmt_yt_out", ".m4a", source.parentFile)
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(source.absolutePath)
            val trackIndex = findAudioTrack(extractor) ?: return false
            val format = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)

            val muxer = MediaMuxer(tempOut.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrack = muxer.addTrack(format)
            muxer.start()

            val buffer = ByteBuffer.allocate(1 shl 20)
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                extractor.advance()
            }
            muxer.stop()
            muxer.release()
            extractor.release()

            tempOut.inputStream().use { it.copyTo(outputStream) }
            true
        } catch (e: Exception) {
            false
        } finally {
            tempOut.delete()
        }
    }

    private fun decodeToWav(source: File, outputStream: OutputStream): Boolean {
        val pcmTemp = File.createTempFile("dmt_yt_pcm", ".raw", source.parentFile)
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(source.absolutePath)
            val trackIndex = findAudioTrack(extractor) ?: return false
            val inputFormat = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            var totalBytes = 0L
            FileOutputStream(pcmTemp).use { pcmOut ->
                totalBytes = drainDecoder(decoder, extractor) { chunk -> pcmOut.write(chunk) }
            }
            decoder.stop()
            decoder.release()
            extractor.release()

            writeWavHeader(outputStream, totalBytes, sampleRate, channelCount, 16)
            pcmTemp.inputStream().use { it.copyTo(outputStream) }
            true
        } catch (e: Exception) {
            false
        } finally {
            pcmTemp.delete()
        }
    }

    /** Feeds [extractor]'s samples through [decoder] and hands each decoded
     * PCM chunk to [onChunk]. Returns the total number of PCM bytes produced. */
    private fun drainDecoder(
        decoder: MediaCodec,
        extractor: MediaExtractor,
        onChunk: (ByteArray) -> Unit,
    ): Long {
        var total = 0L
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inIndex = decoder.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inIndex)
                    val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outIndex >= 0) {
                val outputBuffer = decoder.getOutputBuffer(outIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    outputBuffer.get(chunk)
                    onChunk(chunk)
                    total += chunk.size
                }
                decoder.releaseOutputBuffer(outIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEOS = true
                }
            }
        }
        return total
    }

    private fun writeWavHeader(
        out: OutputStream,
        pcmDataSize: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmDataSize + 36
        val header = ByteArray(44)

        fun writeString(offset: Int, value: String) {
            value.forEachIndexed { i, c -> header[offset + i] = c.code.toByte() }
        }
        fun writeIntLE(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = (value shr 8 and 0xff).toByte()
            header[offset + 2] = (value shr 16 and 0xff).toByte()
            header[offset + 3] = (value shr 24 and 0xff).toByte()
        }
        fun writeShortLE(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = (value shr 8 and 0xff).toByte()
        }

        writeString(0, "RIFF")
        writeIntLE(4, totalDataLen.toInt())
        writeString(8, "WAVE")
        writeString(12, "fmt ")
        writeIntLE(16, 16)
        writeShortLE(20, 1)
        writeShortLE(22, channels)
        writeIntLE(24, sampleRate)
        writeIntLE(28, byteRate)
        writeShortLE(32, blockAlign)
        writeShortLE(34, bitsPerSample)
        writeString(36, "data")
        writeIntLE(40, pcmDataSize.toInt())

        out.write(header)
    }

    /** Decodes to PCM, then re-encodes with the device's software MP3
     * encoder. MP3 is an elemental frame stream, so encoded frames can be
     * written straight to the output with no container needed — same as
     * FLAC below. Not every device ships an MP3 encoder, so this fails
     * gracefully (returns false) rather than crashing if it's missing. */
    private fun decodeToMp3(source: File, outputStream: OutputStream): Boolean {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(source.absolutePath)
            val trackIndex = findAudioTrack(extractor) ?: return false
            val inputFormat = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val sourceBitrate = if (inputFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_BIT_RATE)
            } else {
                192_000
            }

            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val encoderFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_MPEG, sampleRate, channelCount
            )
            encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, sourceBitrate.coerceIn(96_000, 320_000))
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_MPEG)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            transcodeThroughEncoder(decoder, encoder, extractor, outputStream)

            decoder.stop()
            decoder.release()
            encoder.stop()
            encoder.release()
            extractor.release()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Decodes to PCM, then re-encodes with the device's software FLAC
     * encoder. Not every device is guaranteed to have one, so this fails
     * gracefully (returns false) rather than crashing if it's missing. */
    private fun decodeToFlac(source: File, outputStream: OutputStream): Boolean {
        return try {
            val extractor = MediaExtractor()
            extractor.setDataSource(source.absolutePath)
            val trackIndex = findAudioTrack(extractor) ?: return false
            val inputFormat = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val encoderFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_FLAC, sampleRate, channelCount
            )
            encoderFormat.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5)
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_FLAC)
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            transcodeThroughEncoder(decoder, encoder, extractor, outputStream)

            decoder.stop()
            decoder.release()
            encoder.stop()
            encoder.release()
            extractor.release()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun transcodeThroughEncoder(
        decoder: MediaCodec,
        encoder: MediaCodec,
        extractor: MediaExtractor,
        outputStream: OutputStream,
    ) {
        val decInfo = MediaCodec.BufferInfo()
        val encInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawDecOutputEOS = false
        var sawEncOutputEOS = false

        while (!sawEncOutputEOS) {
            if (!sawInputEOS) {
                val inIndex = decoder.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inIndex)
                    val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            if (!sawDecOutputEOS) {
                val outIndex = decoder.dequeueOutputBuffer(decInfo, 10_000)
                if (outIndex >= 0) {
                    val decOutBuffer = decoder.getOutputBuffer(outIndex)
                    val isEOS = decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

                    if (decOutBuffer != null && decInfo.size > 0) {
                        val encInIndex = encoder.dequeueInputBuffer(10_000)
                        if (encInIndex >= 0) {
                            val encInBuffer = encoder.getInputBuffer(encInIndex)
                            decOutBuffer.position(decInfo.offset)
                            decOutBuffer.limit(decInfo.offset + decInfo.size)
                            encInBuffer?.put(decOutBuffer)
                            encoder.queueInputBuffer(encInIndex, 0, decInfo.size, decInfo.presentationTimeUs, 0)
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)

                    if (isEOS) {
                        sawDecOutputEOS = true
                        val encInIndex = encoder.dequeueInputBuffer(10_000)
                        if (encInIndex >= 0) {
                            encoder.queueInputBuffer(encInIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                }
            }

            val encOutIndex = encoder.dequeueOutputBuffer(encInfo, 10_000)
            if (encOutIndex >= 0) {
                val encOutBuffer = encoder.getOutputBuffer(encOutIndex)
                if (encOutBuffer != null && encInfo.size > 0) {
                    val chunk = ByteArray(encInfo.size)
                    encOutBuffer.position(encInfo.offset)
                    encOutBuffer.limit(encInfo.offset + encInfo.size)
                    encOutBuffer.get(chunk)
                    outputStream.write(chunk)
                }
                encoder.releaseOutputBuffer(encOutIndex, false)
                if (encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawEncOutputEOS = true
                }
            }
        }
    }
}
