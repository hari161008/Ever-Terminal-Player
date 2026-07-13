package com.coolappstore.everterminalplayer.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the project's GitHub releases page for a newer build than the one
 * currently installed, and — with the person's permission — downloads the
 * apk straight into the app's own storage and hands it to the system
 * package installer directly, without ever touching the Downloads folder.
 *
 * The downloaded apk (plus a small sidecar noting which version it is) is
 * kept on disk until the installed version catches up to it. That way, if
 * the person backs out of the install prompt, checking again doesn't
 * re-download anything — it just relaunches the installer on the file
 * that's already there.
 */
object UpdateChecker {

    private const val REPO = "hari161008/Ever-Terminal-Player"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val APK_NAME = "update.apk"
    private const val VERSION_NAME = "update.versioncode"

    data class ReleaseInfo(
        val versionName: String,
        val versionCode: Int,
        val downloadUrl: String,
    )

    private fun updateDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "updates").apply { mkdirs() }

    private fun apkFile(context: Context): File = File(updateDir(context), APK_NAME)
    private fun versionFile(context: Context): File = File(updateDir(context), VERSION_NAME)

    fun installedVersionCode(context: Context): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info)
    }.getOrDefault(0L)

    fun versionCodeFor(versionName: String): Int {
        val cleaned = versionName.trim().removePrefix("v").removePrefix("V")
        val parts = cleaned.split(".").mapNotNull { it.toIntOrNull() }
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        return ((major * 10_000) + (minor * 100) + patch).coerceAtLeast(1)
    }

    fun hasPendingApk(context: Context): Boolean = apkFile(context).exists()

    fun pendingVersionCode(context: Context): Int? =
        versionFile(context).takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()

    fun clearPending(context: Context) {
        runCatching { apkFile(context).delete() }
        runCatching { versionFile(context).delete() }
    }

    private fun markPending(context: Context, versionCode: Int) {
        runCatching { versionFile(context).writeText(versionCode.toString()) }
    }

    /** Content uri for the already-downloaded apk, for handing to the
     * package installer via FileProvider. */
    fun installUri(context: Context): Uri {
        val file = apkFile(context)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    suspend fun fetchLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "EverTerminalPlayer-UpdateChecker")
            }
            val body = try {
                if (connection.responseCode !in 200..299) {
                    error("release check failed: HTTP ${connection.responseCode}")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }

            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            require(tag.isNotBlank()) { "no version tag in latest release" }

            var apkUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }
            val url = apkUrl ?: error("no apk file attached to the latest release")

            ReleaseInfo(versionName = tag, versionCode = versionCodeFor(tag), downloadUrl = url)
        }
    }

    suspend fun downloadApk(
        context: Context,
        release: ReleaseInfo,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dest = apkFile(context)
            val tmp = File(dest.parentFile, "$APK_NAME.tmp")
            var connection = (URL(release.downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "EverTerminalPlayer-UpdateChecker")
            }
            connection.connect()

            // Some hosts (GitHub's asset CDN included) occasionally hand back
            // a redirect that HttpURLConnection won't follow across hosts on
            // its own — follow it by hand, one hop, just in case.
            var redirects = 0
            while (connection.responseCode in intArrayOf(301, 302, 303, 307, 308) && redirects < 5) {
                val next = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                connection = (URL(next).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    setRequestProperty("User-Agent", "EverTerminalPlayer-UpdateChecker")
                }
                connection.connect()
                redirects++
            }

            if (connection.responseCode !in 200..299) {
                val code = connection.responseCode
                connection.disconnect()
                error("download failed: HTTP $code")
            }

            val total = connection.contentLengthLong
            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            connection.disconnect()

            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            markPending(context, release.versionCode)
            dest
        }
    }
}
