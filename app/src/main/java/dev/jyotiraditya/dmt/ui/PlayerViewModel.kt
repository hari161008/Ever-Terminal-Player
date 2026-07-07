package dev.jyotiraditya.dmt.ui

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import com.github.innertube.Innertube
import dev.jyotiraditya.dmt.data.Album
import dev.jyotiraditya.dmt.data.KEY_ACCENT
import dev.jyotiraditya.dmt.data.KEY_COLS
import dev.jyotiraditya.dmt.data.KEY_LAST_INDEX
import dev.jyotiraditya.dmt.data.KEY_LAST_POS
import dev.jyotiraditya.dmt.data.KEY_LAST_QUEUE
import dev.jyotiraditya.dmt.data.KEY_LAST_VIEW
import dev.jyotiraditya.dmt.data.KEY_LAST_ALBUM
import dev.jyotiraditya.dmt.data.KEY_LAST_FOLDER
import dev.jyotiraditya.dmt.data.KEY_YT_PINNED
import dev.jyotiraditya.dmt.data.KEY_RAW
import dev.jyotiraditya.dmt.data.KEY_FULLSCREEN
import dev.jyotiraditya.dmt.data.KEY_SPEED
import dev.jyotiraditya.dmt.data.KEY_STAT_COUNTS
import dev.jyotiraditya.dmt.data.KEY_STAT_TOTAL
import dev.jyotiraditya.dmt.data.KEY_SPECS
import dev.jyotiraditya.dmt.data.KEY_WAVE
import dev.jyotiraditya.dmt.data.MediaLibrary
import dev.jyotiraditya.dmt.data.DmtStats
import dev.jyotiraditya.dmt.data.Folder
import dev.jyotiraditya.dmt.data.dmtStore
import dev.jyotiraditya.dmt.data.Spec
import dev.jyotiraditya.dmt.data.Track
import dev.jyotiraditya.dmt.data.toAlbums
import dev.jyotiraditya.dmt.data.toCounts
import dev.jyotiraditya.dmt.data.toFolders
import dev.jyotiraditya.dmt.data.lyrics.Lyrics
import dev.jyotiraditya.dmt.data.lyrics.LyricsExtractor
import dev.jyotiraditya.dmt.data.lyrics.LyricsParser
import dev.jyotiraditya.dmt.player.asKHz
import dev.jyotiraditya.dmt.player.asMB
import dev.jyotiraditya.dmt.player.codecLabel
import dev.jyotiraditya.dmt.player.cycleRepeat
import dev.jyotiraditya.dmt.player.mediaController
import dev.jyotiraditya.dmt.player.queueLabels
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.playback.PlaybackService
import dev.jyotiraditya.dmt.player.await
import dev.jyotiraditya.dmt.player.toMediaItem
import dev.jyotiraditya.dmt.player.togglePlayPause
import dev.jyotiraditya.dmt.yt.YT_ID_PREFIX
import dev.jyotiraditya.dmt.yt.YtLyrics
import dev.jyotiraditya.dmt.yt.YtPinStore
import dev.jyotiraditya.dmt.yt.YtRepository
import dev.jyotiraditya.dmt.yt.artistLine
import dev.jyotiraditya.dmt.yt.title
import dev.jyotiraditya.dmt.yt.toMediaItem as toYtMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DmtView { LIBRARY, ALBUMS, FILES, SEARCH_YT, SETTINGS, STATS }

data class DmtSettings(
    val wave: Boolean = true,
    val cols: Int = 64,
    val listSpecs: Boolean = true,
    val accent: Int = 0,
    val rawArt: Boolean = false,
    val fullScreen: Boolean = true,
)

data class DmtState(
    val hasPermission: Boolean = false,
    val scanning: Boolean = true,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val query: String = "",
    val filtered: List<Track> = emptyList(),
    val filteredAlbums: List<Album> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val filteredFolders: List<Folder> = emptyList(),
    val view: DmtView = DmtView.LIBRARY,
    val openAlbum: String? = null,
    val openFolder: String? = null,
    val nowPlayingId: String? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<String> = emptyList(),
    val queueIndex: Int = 0,
    val album: String = "",
    val cover: Bitmap? = null,
    val artRaw: Bitmap? = null,
    val lyrics: Lyrics? = null,
    val expanded: Boolean = false,
    val sleepMinutes: Int = 0,
    val sleepLeftMs: Long = 0L,
    val speed: Float = 1f,
    val settings: DmtSettings = DmtSettings(),
    val stats: DmtStats = DmtStats(),
    val tech: List<Spec> = emptyList(),
    val error: String? = null,
    val notice: String? = null,
    val ytQuery: String = "",
    val ytResults: List<Innertube.SongItem> = emptyList(),
    val ytLoading: Boolean = false,
    val ytError: String? = null,
    val ytResolvingId: String? = null,
    val ytPinned: List<Innertube.SongItem> = emptyList(),
    val ytSavingId: String? = null,
)

sealed interface DmtAction {
    data class Permission(val granted: Boolean) : DmtAction
    data object Rescan : DmtAction
    data class Query(val value: String) : DmtAction
    data class Show(val view: DmtView) : DmtAction
    data class OpenAlbum(val name: String?) : DmtAction
    data class OpenFolder(val path: String?) : DmtAction
    data class PlayAt(val list: List<Track>, val index: Int) : DmtAction
    data class Enqueue(val list: List<Track>, val label: String) : DmtAction
    data class Jump(val index: Int) : DmtAction
    data object TogglePlay : DmtAction
    data object Next : DmtAction
    data object Prev : DmtAction
    data object ToggleShuffle : DmtAction
    data object CycleRepeat : DmtAction
    data class Seek(val fraction: Float) : DmtAction
    data class Expand(val value: Boolean) : DmtAction
    data class RemoveAt(val index: Int) : DmtAction
    data object CycleSleep : DmtAction
    data object CycleSpeed : DmtAction
    data object OpenEqualizer : DmtAction
    data class Config(val settings: DmtSettings) : DmtAction
    data class YtQuery(val value: String) : DmtAction
    data class PlayYtTrack(val song: Innertube.SongItem) : DmtAction
    data class TogglePinYtTrack(val song: Innertube.SongItem) : DmtAction
    data class SaveYtTrack(val song: Innertube.SongItem) : DmtAction
    data class SaveYtTrackTo(val uri: Uri) : DmtAction
}

private const val QUEUE_CAP = 500
private const val QUEUE_LOOKBACK = 100
private const val REMOTE_ART_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(
        DmtState(
            hasPermission = ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    )
    val state = _state.asStateFlow()

    private var controller: MediaController? = null
    private var noticeJob: Job? = null
    private var ytSearchJob: Job? = null
    private var sleepEndAt: Long? = null
    private var sessionRestored = false
    private var pendingSaveSong: Innertube.SongItem? = null
    private val _saveRequests = Channel<String>(Channel.BUFFERED)
    val saveRequests: Flow<String> = _saveRequests.receiveAsFlow()

    init {
        viewModelScope.launch {
            val prefs = getApplication<Application>().dmtStore.data.first()
            val settings = DmtSettings(
                wave = prefs[KEY_WAVE] ?: true,
                cols = prefs[KEY_COLS] ?: 64,
                listSpecs = prefs[KEY_SPECS] ?: true,
                accent = prefs[KEY_ACCENT] ?: 0,
                rawArt = prefs[KEY_RAW] ?: false,
                fullScreen = prefs[KEY_FULLSCREEN] ?: true,
            )
            _state.update { it.copy(settings = settings) }
            applyIcon(settings.accent)

            val lastView = DmtView.entries.find { it.name == prefs[KEY_LAST_VIEW] } ?: DmtView.LIBRARY
            _state.update {
                it.copy(
                    view = lastView,
                    openAlbum = prefs[KEY_LAST_ALBUM],
                    openFolder = prefs[KEY_LAST_FOLDER],
                    ytPinned = YtPinStore.decode(prefs[KEY_YT_PINNED]),
                )
            }
        }
        viewModelScope.launch {
            getApplication<Application>().dmtStore.data.collect { prefs ->
                val stats = DmtStats(
                    totalMs = prefs[KEY_STAT_TOTAL] ?: 0L,
                    counts = (prefs[KEY_STAT_COUNTS] ?: "").toCounts(),
                )
                _state.update { if (it.stats == stats) it else it.copy(stats = stats) }
            }
        }
        if (_state.value.hasPermission) scan()
        connect()
    }

    private fun windowQueue(list: List<Track>, index: Int): Pair<List<Track>, Int> {
        if (list.size <= QUEUE_CAP) return list to index
        val start = (index - QUEUE_LOOKBACK)
            .coerceAtLeast(0)
            .coerceAtMost(list.size - QUEUE_CAP)
        return list.subList(start, start + QUEUE_CAP).toList() to (index - start)
    }

    private fun filter(tracks: List<Track>, query: String): List<Track> =
        if (query.isBlank()) tracks else tracks.filter {
            it.title.contains(query, true) ||
                it.artist.contains(query, true) ||
                it.album.contains(query, true)
        }

    private fun filterAlbums(albums: List<Album>, query: String): List<Album> =
        if (query.isBlank()) albums else albums.filter {
            it.name.contains(query, true) || it.artist.contains(query, true)
        }

    private fun filterFolders(folders: List<Folder>, query: String): List<Folder> =
        if (query.isBlank()) folders else folders.filter { it.name.contains(query, true) }

    fun dispatch(action: DmtAction) {
        val c = controller
        when (action) {
            is DmtAction.Permission -> {
                _state.update { it.copy(hasPermission = action.granted) }
                if (action.granted) scan()
            }

            DmtAction.Rescan -> scan()
            is DmtAction.Query -> _state.update {
                it.copy(
                    query = action.value,
                    filtered = filter(it.tracks, action.value),
                    filteredAlbums = filterAlbums(it.albums, action.value),
                    filteredFolders = filterFolders(it.folders, action.value),
                )
            }
            is DmtAction.Show -> {
                _state.update { it.copy(view = action.view) }
                viewModelScope.launch {
                    getApplication<Application>().dmtStore.edit { it[KEY_LAST_VIEW] = action.view.name }
                }
            }
            is DmtAction.OpenAlbum -> {
                _state.update { it.copy(openAlbum = action.name) }
                viewModelScope.launch {
                    getApplication<Application>().dmtStore.edit {
                        if (action.name == null) it.remove(KEY_LAST_ALBUM) else it[KEY_LAST_ALBUM] = action.name
                    }
                }
            }
            is DmtAction.OpenFolder -> {
                _state.update { it.copy(openFolder = action.path) }
                viewModelScope.launch {
                    getApplication<Application>().dmtStore.edit {
                        if (action.path == null) it.remove(KEY_LAST_FOLDER) else it[KEY_LAST_FOLDER] = action.path
                    }
                }
            }

            is DmtAction.PlayAt -> c?.run {
                _state.update { it.copy(error = null) }
                val (queue, startIndex) = windowQueue(action.list, action.index)
                setMediaItems(
                    queue.map { it.toMediaItem() },
                    startIndex,
                    0L
                )
                prepare()
                play()
            }

            is DmtAction.Enqueue -> c?.run {
                addMediaItems(action.list.take(QUEUE_CAP).map { it.toMediaItem() })
                prepare()
                notify("queued: ${action.label}")
            }

            is DmtAction.Jump -> c?.run {
                seekTo(action.index, 0L)
                prepare()
                play()
            }

            DmtAction.TogglePlay -> c?.togglePlayPause()
            DmtAction.Next -> c?.seekToNext()
            DmtAction.Prev -> c?.seekToPrevious()
            DmtAction.ToggleShuffle -> c?.run { shuffleModeEnabled = !shuffleModeEnabled }
            DmtAction.CycleRepeat -> c?.cycleRepeat()

            is DmtAction.Seek -> c?.run {
                val duration = _state.value.durationMs
                if (duration > 0) {
                    val target = (action.fraction * duration).toLong()
                    seekTo(target)
                    _state.update { it.copy(positionMs = target) }
                }
            }

            is DmtAction.Expand -> _state.update { it.copy(expanded = action.value) }

            is DmtAction.RemoveAt -> c?.run {
                if (action.index in 0 until mediaItemCount) removeMediaItem(action.index)
            }

            DmtAction.CycleSleep -> cycleSleep()
            DmtAction.CycleSpeed -> cycleSpeed()
            DmtAction.OpenEqualizer -> openEqualizer()

            is DmtAction.Config -> {
                val old = _state.value.settings
                _state.update { it.copy(settings = action.settings) }
                viewModelScope.launch {
                    getApplication<Application>().dmtStore.edit {
                        it[KEY_WAVE] = action.settings.wave
                        it[KEY_COLS] = action.settings.cols
                        it[KEY_SPECS] = action.settings.listSpecs
                        it[KEY_ACCENT] = action.settings.accent
                        it[KEY_RAW] = action.settings.rawArt
                        it[KEY_FULLSCREEN] = action.settings.fullScreen
                    }
                }
                if (old.cols != action.settings.cols) loadCover(c?.currentMediaItem)
                if (old.accent != action.settings.accent) applyIcon(action.settings.accent)
            }

            is DmtAction.YtQuery -> ytQuery(action.value)
            is DmtAction.PlayYtTrack -> playYtTrack(action.song)
            is DmtAction.TogglePinYtTrack -> togglePinYtTrack(action.song)
            is DmtAction.SaveYtTrack -> requestSaveYtTrack(action.song)
            is DmtAction.SaveYtTrackTo -> saveYtTrackTo(action.uri)
        }
    }

    private fun ytQuery(value: String) {
        _state.update { it.copy(ytQuery = value, ytError = null) }
        ytSearchJob?.cancel()
        if (value.isBlank()) {
            _state.update { it.copy(ytResults = emptyList(), ytLoading = false) }
            return
        }
        ytSearchJob = viewModelScope.launch {
            delay(400)
            _state.update { it.copy(ytLoading = true) }
            val result = withContext(Dispatchers.IO) { YtRepository.search(value) }
            if (_state.value.ytQuery != value) return@launch
            result.fold(
                onSuccess = { songs ->
                    _state.update {
                        it.copy(
                            ytLoading = false,
                            ytResults = songs,
                            ytError = if (songs.isEmpty()) {
                                getApplication<Application>().getString(R.string.no_match)
                            } else {
                                null
                            },
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            ytLoading = false,
                            ytResults = emptyList(),
                            ytError = getApplication<Application>().getString(R.string.yt_search_failed),
                        )
                    }
                }
            )
        }
    }

    private fun playYtTrack(song: Innertube.SongItem) {
        val videoId = song.key
        if (videoId.isBlank()) return
        val mediaId = YT_ID_PREFIX + videoId
        _state.update { it.copy(ytResolvingId = mediaId, error = null) }
        viewModelScope.launch {
            val url = withContext(Dispatchers.IO) { YtRepository.resolveStreamUrl(videoId) }
            _state.update { if (it.ytResolvingId == mediaId) it.copy(ytResolvingId = null) else it }
            val c = controller
            if (url == null || c == null) {
                notify(getApplication<Application>().getString(R.string.yt_resolve_failed))
                return@launch
            }
            c.setMediaItems(listOf(song.toYtMediaItem(url)), 0, 0L)
            c.prepare()
            c.play()
        }
    }

    private fun togglePinYtTrack(song: Innertube.SongItem) {
        val current = _state.value.ytPinned
        val pinned = current.any { it.key == song.key }
        val updated = if (pinned) {
            current.filterNot { it.key == song.key }
        } else {
            listOf(song) + current
        }
        _state.update { it.copy(ytPinned = updated) }
        viewModelScope.launch {
            getApplication<Application>().dmtStore.edit { it[KEY_YT_PINNED] = YtPinStore.encode(updated) }
        }
    }

    private fun requestSaveYtTrack(song: Innertube.SongItem) {
        pendingSaveSong = song
        val safeName = "${song.title()} - ${song.artistLine()}"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "track" } + ".mp3"
        viewModelScope.launch { _saveRequests.send(safeName) }
    }

    private fun saveYtTrackTo(uri: Uri) {
        val song = pendingSaveSong
        pendingSaveSong = null
        if (song == null) {
            notify(getApplication<Application>().getString(R.string.yt_save_failed))
            return
        }
        val videoId = song.key
        val mediaId = YT_ID_PREFIX + videoId
        _state.update { it.copy(ytSavingId = mediaId) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val url = YtRepository.resolveStreamUrl(videoId) ?: return@runCatching false
                    val connection =
                        java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("User-Agent", REMOTE_ART_USER_AGENT)
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    val wrote = connection.inputStream.use { input ->
                        getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                            input.copyTo(output)
                            true
                        } ?: false
                    }
                    wrote
                }.getOrDefault(false)
            }
            _state.update { if (it.ytSavingId == mediaId) it.copy(ytSavingId = null) else it }
            notify(
                getApplication<Application>().getString(
                    if (ok) R.string.yt_save_success else R.string.yt_save_failed
                )
            )
        }
    }

    private fun connect() = viewModelScope.launch {
        val c = runCatching { getApplication<Application>().mediaController() }.getOrNull()
            ?: return@launch
        controller = c
        c.addListener(listener)
        syncFrom(c)
        restoreSleep(c)
        restoreSpeed(c)
        loadCover(c.currentMediaItem)
        loadTech(c.currentMediaItem)
        loadLyrics(c.currentMediaItem)
        restoreSession()
        while (isActive) {
            val position = c.currentPosition.coerceAtLeast(0L)
            val duration = c.duration.takeIf { d -> d != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
            val index = c.currentMediaItemIndex
            val sleepLeft = sleepEndAt?.let { end ->
                (end - System.currentTimeMillis()).coerceAtLeast(0L)
            } ?: 0L
            val sleepExpired = sleepEndAt != null && sleepLeft == 0L
            if (sleepExpired) sleepEndAt = null
            _state.update {
                if (it.positionMs == position && it.durationMs == duration &&
                    it.queueIndex == index && it.sleepLeftMs == sleepLeft && !sleepExpired
                ) {
                    it
                } else {
                    it.copy(
                        positionMs = position,
                        durationMs = duration,
                        queueIndex = index,
                        sleepLeftMs = sleepLeft,
                        sleepMinutes = if (sleepExpired) 0 else it.sleepMinutes,
                    )
                }
            }
            delay(if (c.isPlaying) 500 else 1500)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _state.update { it.copy(nowPlayingId = mediaItem?.mediaId, error = null) }
            loadCover(mediaItem)
            loadTech(mediaItem)
            loadLyrics(mediaItem)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            _state.update {
                it.copy(
                    title = mediaMetadata.title?.toString() ?: "unknown",
                    artist = mediaMetadata.artist?.toString() ?: "unknown artist",
                    album = mediaMetadata.albumTitle?.toString().orEmpty(),
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.update { it.copy(shuffle = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.update { it.copy(repeat = repeatMode) }
        }

        override fun onPlaybackParametersChanged(
            playbackParameters: androidx.media3.common.PlaybackParameters,
        ) {
            _state.update { it.copy(speed = playbackParameters.speed) }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            controller?.let { c -> _state.update { it.copy(queue = c.queueLabels()) } }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(error = "playback error: ${error.errorCodeName.lowercase()}")
            }
        }
    }

    private fun syncFrom(c: MediaController) {
        _state.update {
            it.copy(
                nowPlayingId = c.currentMediaItem?.mediaId,
                title = c.mediaMetadata.title?.toString() ?: "unknown",
                artist = c.mediaMetadata.artist?.toString() ?: "unknown artist",
                isPlaying = c.isPlaying,
                shuffle = c.shuffleModeEnabled,
                repeat = c.repeatMode,
                album = c.mediaMetadata.albumTitle?.toString().orEmpty(),
                speed = c.playbackParameters.speed,
                queue = c.queueLabels(),
            )
        }
    }

    private fun scan() = viewModelScope.launch {
        _state.update { it.copy(scanning = true) }
        val query = _state.value.query
        val (tracks, albums, folders) = withContext(Dispatchers.IO) {
            val scanned = MediaLibrary.scan(getApplication())
            Triple(
                scanned,
                scanned.toAlbums(),
                scanned.toFolders(),
            )
        }
        val (filteredTracks, filteredAlbums, filteredFolders) = withContext(Dispatchers.Default) {
            Triple(
                filter(tracks, query),
                filterAlbums(albums, query),
                filterFolders(folders, query),
            )
        }
        _state.update {
            it.copy(
                scanning = false,
                tracks = tracks,
                albums = albums,
                folders = folders,
                filtered = filteredTracks,
                filteredAlbums = filteredAlbums,
                filteredFolders = filteredFolders,
            )
        }
        restoreSession()
    }

    private fun restoreSession() {
        if (sessionRestored) return
        val c = controller ?: return
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        if (c.mediaItemCount > 0) {
            sessionRestored = true
            return
        }
        sessionRestored = true
        viewModelScope.launch {
            val prefs = getApplication<Application>().dmtStore.data.first()
            val savedIds = (prefs[KEY_LAST_QUEUE] ?: "")
                .split(',')
                .mapNotNull { it.toLongOrNull() }
            if (savedIds.isEmpty()) return@launch

            val byId = tracks.associateBy { it.id }
            val existing = savedIds.mapNotNull { byId[it] }
            if (existing.isEmpty()) return@launch

            val savedCurrentId = savedIds.getOrNull(prefs[KEY_LAST_INDEX] ?: 0)
            var index = existing.indexOfFirst { it.id == savedCurrentId }
            var position = prefs[KEY_LAST_POS] ?: 0L
            if (index < 0) {
                index = 0
                position = 0L
            }
            val (queue, startIndex) = windowQueue(existing, index)
            c.setMediaItems(
                queue.map { it.toMediaItem() },
                startIndex,
                position
            )
            c.prepare()
        }
    }

    private fun loadLyrics(mediaItem: MediaItem?) {
        val forId = mediaItem?.mediaId
        val isYt = forId?.startsWith(YT_ID_PREFIX) == true
        viewModelScope.launch {
            val lyrics = if (isYt) {
                val videoId = forId.removePrefix(YT_ID_PREFIX)
                val title = mediaItem?.mediaMetadata?.title?.toString().orEmpty()
                val artist = mediaItem?.mediaMetadata?.artist?.toString().orEmpty()
                withContext(Dispatchers.IO) {
                    YtLyrics.fetch(videoId, title, artist)?.let(LyricsParser::parse)
                }
            } else {
                val track = _state.value.tracks.find { it.id.toString() == forId }
                track?.let {
                    withContext(Dispatchers.IO) {
                        LyricsExtractor.extract(it.path, it.mime)?.let(LyricsParser::parse)
                    }
                }
            }
            _state.update {
                if (it.nowPlayingId != forId) it else it.copy(lyrics = lyrics)
            }
        }
    }

    private fun loadCover(mediaItem: MediaItem?) {
        val uri: Uri? = mediaItem?.localConfiguration?.uri
        val artworkUri: Uri? = mediaItem?.mediaMetadata?.artworkUri
        val forId = mediaItem?.mediaId
        val isYt = forId?.startsWith(YT_ID_PREFIX) == true
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    if (isYt) {
                        artworkUri?.let { loadRemoteBitmap(it) }
                    } else {
                        uri?.let {
                            getApplication<Application>().contentResolver
                                .loadThumbnail(it, Size(512, 512), null)
                        }
                    }
                }.getOrNull()
            }
            val cover = withContext(Dispatchers.IO) {
                raw?.let { art ->
                    runCatching { art.toAsciiBitmap(_state.value.settings.cols) }.getOrNull()
                } ?: mediaItem?.let {
                    generateAsciiPlaceholder(
                        seed = forId?.toLongOrNull() ?: forId.hashCode().toLong(),
                        cols = _state.value.settings.cols,
                    )
                }
            }
            _state.update {
                if (it.nowPlayingId != forId) it else it.copy(cover = cover, artRaw = raw)
            }
        }
    }

    private fun loadRemoteBitmap(uri: Uri): Bitmap? = runCatching {
        val connection = java.net.URL(uri.toString()).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", REMOTE_ART_USER_AGENT)
        connection.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
    }.getOrNull()

    private fun loadTech(mediaItem: MediaItem?) {
        val uri = mediaItem?.localConfiguration?.uri
        val id = mediaItem?.mediaId
        if (id?.startsWith(YT_ID_PREFIX) == true) {
            _state.update {
                if (it.nowPlayingId != id) it else it.copy(tech = listOf(Spec("SRC", "YT", hot = true)))
            }
            return
        }
        viewModelScope.launch {
            val tech = uri?.let {
                withContext(Dispatchers.IO) {
                    buildTech(it, _state.value.tracks.find { t -> t.id.toString() == id })
                }
            }.orEmpty()
            _state.update {
                if (it.nowPlayingId != id) it else it.copy(tech = tech)
            }
        }
    }

    private fun buildTech(uri: Uri, track: Track?): List<Spec> {
        val app = getApplication<Application>()
        var mime = track?.mime.orEmpty()
        var bitrate = track?.bitrate ?: 0
        var sampleRate: Int? = null
        var channels: Int? = null
        var bits: Int? = null
        runCatching {
            val extractor = MediaExtractor()
            extractor.setDataSource(app, uri, null)
            val format = extractor.getTrackFormat(0)
            format.getString(MediaFormat.KEY_MIME)?.let {
                if (mime.isEmpty() || mime == "audio/?") mime = it
            }
            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            }
            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
            if (bitrate <= 0 && format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
            }
            extractor.release()
        }
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(app, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                    ?.toIntOrNull()?.takeIf { it > 0 }?.let { bits = it }
                if (sampleRate == null) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                        ?.toIntOrNull()?.let { sampleRate = it }
                }
                if (bitrate <= 0) {
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toIntOrNull()?.let { bitrate = it }
                }
            }
        }
        return buildList {
            if (mime.isNotEmpty()) {
                add(
                    Spec(
                        label = "FMT",
                        value = mime.codecLabel(),
                    )
                )
            }
            bits?.let {
                add(
                    Spec(
                        label = "BIT",
                        value = "$it",
                    )
                )
            }
            sampleRate?.let {
                add(
                    Spec(
                        label = "RATE",
                        value = it.asKHz(),
                    )
                )
            }
            channels?.let {
                add(
                    Spec(
                        label = "CH",
                        value = if (it == 2) "ST" else "$it",
                    )
                )
            }
            if (bitrate > 0) {
                add(
                    Spec(
                        label = "KBPS",
                        value = "${bitrate / 1000}",
                        hot = true,
                    )
                )
            }
            track?.size?.takeIf { it > 0 }?.let {
                add(
                    Spec(
                        label = "SIZE",
                        value = it.asMB(),
                    )
                )
            }
        }
    }

    private fun openEqualizer() {
        val c = controller ?: return
        val app = getApplication<Application>()
        viewModelScope.launch {
            val sessionId = runCatching {
                c.sendCustomCommand(PlaybackService.CMD_AUDIO_SESSION, Bundle.EMPTY)
                    .await()
                    .extras
                    .getInt(PlaybackService.KEY_AUDIO_SESSION)
            }.getOrDefault(0)
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, app.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(app.packageManager) != null) {
                app.startActivity(intent)
            } else {
                notify(app.getString(R.string.no_eq))
            }
        }
    }

    private fun cycleSpeed() {
        val c = controller ?: return
        val steps = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        val currentIndex = steps.indexOfFirst { kotlin.math.abs(it - _state.value.speed) < 0.01f }
        val next = steps[(currentIndex + 1).mod(steps.size)]
        c.setPlaybackSpeed(next)
        viewModelScope.launch {
            getApplication<Application>().dmtStore.edit { it[KEY_SPEED] = next }
        }
    }

    private fun cycleSleep() {
        val c = controller ?: return
        val next = when (_state.value.sleepMinutes) {
            0 -> 15
            15 -> 30
            30 -> 60
            else -> 0
        }
        val endAt = if (next == 0) 0L else System.currentTimeMillis() + next * 60_000L
        c.sendCustomCommand(
            PlaybackService.CMD_SLEEP_SET,
            Bundle().apply { putLong(PlaybackService.KEY_END_AT, endAt) }
        )
        sleepEndAt = endAt.takeIf { it > 0L }
        _state.update {
            it.copy(
                sleepMinutes = next,
                sleepLeftMs = if (next == 0) 0L else next * 60_000L,
            )
        }
    }

    private suspend fun restoreSpeed(c: MediaController) {
        val saved = getApplication<Application>().dmtStore.data.first()[KEY_SPEED] ?: 1f
        if (kotlin.math.abs(c.playbackParameters.speed - saved) > 0.01f) {
            c.setPlaybackSpeed(saved)
        }
    }

    private suspend fun restoreSleep(c: MediaController) {
        runCatching {
            val result = c.sendCustomCommand(PlaybackService.CMD_SLEEP_GET, Bundle.EMPTY).await()
            val endAt = result.extras.getLong(PlaybackService.KEY_END_AT)
            if (endAt > System.currentTimeMillis()) {
                sleepEndAt = endAt
                val left = endAt - System.currentTimeMillis()
                val step = when {
                    left <= 15 * 60_000L -> 15
                    left <= 30 * 60_000L -> 30
                    else -> 60
                }
                _state.update { it.copy(sleepMinutes = step, sleepLeftMs = left) }
            }
        }
    }

    private fun applyIcon(accent: Int) {
        val app = getApplication<Application>()
        val aliases = listOf("LauncherOrange", "LauncherMoss", "LauncherSteel", "LauncherMono")
        val target = accent % aliases.size
        aliases.forEachIndexed { index, alias ->
            val component = ComponentName(app, "dev.jyotiraditya.dmt.$alias")
            val desired = if (index == target) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val current = app.packageManager.getComponentEnabledSetting(component)
            val effective = if (current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                if (index == 0) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            } else {
                current
            }
            if (effective != desired) {
                app.packageManager.setComponentEnabledSetting(
                    component,
                    desired,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    private fun notify(message: String) {
        noticeJob?.cancel()
        _state.update { it.copy(notice = message) }
        noticeJob = viewModelScope.launch {
            delay(2000)
            _state.update { it.copy(notice = null) }
        }
    }

    override fun onCleared() {
        controller?.release()
        controller = null
    }
}
