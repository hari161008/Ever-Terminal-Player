package com.coolappstore.everterminalplayer.ui

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
import com.coolappstore.everterminalplayer.data.Album
import com.coolappstore.everterminalplayer.data.KEY_ACCENT
import com.coolappstore.everterminalplayer.data.KEY_ACCENT_COLOR
import com.coolappstore.everterminalplayer.data.KEY_COLS
import com.coolappstore.everterminalplayer.data.KEY_LAST_INDEX
import com.coolappstore.everterminalplayer.data.KEY_LAST_POS
import com.coolappstore.everterminalplayer.data.KEY_LAST_QUEUE
import com.coolappstore.everterminalplayer.data.KEY_LAST_VIEW
import com.coolappstore.everterminalplayer.data.KEY_LAST_ALBUM
import com.coolappstore.everterminalplayer.data.KEY_LAST_FOLDER
import com.coolappstore.everterminalplayer.data.KEY_YT_PINNED
import com.coolappstore.everterminalplayer.data.KEY_LIGHT_MODE
import com.coolappstore.everterminalplayer.data.KEY_SKIP_SILENCE
import com.coolappstore.everterminalplayer.data.KEY_KEEP_SCREEN_ON
import com.coolappstore.everterminalplayer.data.KEY_TELEGRAM_PROMPT_SHOWN
import com.coolappstore.everterminalplayer.data.KEY_RAW
import com.coolappstore.everterminalplayer.data.KEY_FULLSCREEN
import com.coolappstore.everterminalplayer.data.KEY_SPEED
import com.coolappstore.everterminalplayer.data.KEY_STAT_COUNTS
import com.coolappstore.everterminalplayer.data.KEY_STAT_TOTAL
import com.coolappstore.everterminalplayer.data.KEY_SPECS
import com.coolappstore.everterminalplayer.data.KEY_WAVE
import com.coolappstore.everterminalplayer.data.MediaLibrary
import com.coolappstore.everterminalplayer.data.DmtStats
import com.coolappstore.everterminalplayer.data.Folder
import com.coolappstore.everterminalplayer.data.dmtStore
import com.coolappstore.everterminalplayer.data.Spec
import com.coolappstore.everterminalplayer.data.Track
import com.coolappstore.everterminalplayer.data.UpdateChecker
import com.coolappstore.everterminalplayer.data.toAlbums
import com.coolappstore.everterminalplayer.data.toCounts
import com.coolappstore.everterminalplayer.data.toFolders
import com.coolappstore.everterminalplayer.data.lyrics.Lyrics
import com.coolappstore.everterminalplayer.data.lyrics.LyricsExtractor
import com.coolappstore.everterminalplayer.data.lyrics.LyricsParser
import com.coolappstore.everterminalplayer.player.asKHz
import com.coolappstore.everterminalplayer.player.asMB
import com.coolappstore.everterminalplayer.player.codecLabel
import com.coolappstore.everterminalplayer.player.cycleRepeat
import com.coolappstore.everterminalplayer.player.mediaController
import com.coolappstore.everterminalplayer.player.queueLabels
import com.coolappstore.everterminalplayer.R
import com.coolappstore.everterminalplayer.playback.PlaybackService
import com.coolappstore.everterminalplayer.player.await
import com.coolappstore.everterminalplayer.player.toMediaItem
import com.coolappstore.everterminalplayer.player.togglePlayPause
import com.coolappstore.everterminalplayer.yt.YT_ID_PREFIX
import com.coolappstore.everterminalplayer.yt.YtAudioFormat
import com.coolappstore.everterminalplayer.yt.YtAudioTranscoder
import com.coolappstore.everterminalplayer.yt.YtLyrics
import com.coolappstore.everterminalplayer.yt.YtPinStore
import com.coolappstore.everterminalplayer.yt.YtRepository
import com.coolappstore.everterminalplayer.yt.artistLine
import com.coolappstore.everterminalplayer.yt.title
import com.coolappstore.everterminalplayer.yt.toMediaItem as toYtMediaItem
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
    val accentColor: Int = 0,
    val rawArt: Boolean = false,
    val fullScreen: Boolean = true,
    val lightMode: Boolean = false,
    val skipSilence: Boolean = false,
    val keepScreenOn: Boolean = false,
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
    val ytVideoMode: Boolean = false,
    val ytVideoKey: YtVideoKey? = null,
    val showTelegramPrompt: Boolean = false,
    val updateChecking: Boolean = false,
    val updateAvailable: Boolean = false,
    val updateVersionName: String = "",
    val updateDownloading: Boolean = false,
    val updateDownloadProgress: Float = 0f,
)

/** A one-shot signal telling the active [com.coolappstore.everterminalplayer.yt.YtVideoPreview]
 * to simulate a keypress on the YouTube page — the [nonce] only exists so a
 * repeat of the same key still triggers a fresh LaunchedEffect. */
data class YtVideoKey(val key: String, val nonce: Long)

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
    data class YtVideoProgress(val positionMs: Long, val durationMs: Long) : DmtAction
    data class Expand(val value: Boolean) : DmtAction
    data class RemoveAt(val index: Int) : DmtAction
    data object CycleSleep : DmtAction
    data object CycleSpeed : DmtAction
    data object OpenEqualizer : DmtAction
    data class Config(val settings: DmtSettings) : DmtAction
    data class YtQuery(val value: String) : DmtAction
    data class PlayYtTrack(val song: Innertube.SongItem) : DmtAction
    data class TogglePinYtTrack(val song: Innertube.SongItem) : DmtAction
    data class SaveYtTrack(val song: Innertube.SongItem, val format: YtAudioFormat) : DmtAction
    data class SaveYtTrackTo(val uri: Uri) : DmtAction
    data object ToggleYtVideoMode : DmtAction
    data object ClearCache : DmtAction
    data object DismissTelegramPrompt : DmtAction
    data object CheckForUpdates : DmtAction
    data object ConfirmUpdateDownload : DmtAction
    data object DismissUpdatePrompt : DmtAction
}

private const val QUEUE_CAP = 500
private const val QUEUE_LOOKBACK = 100
private const val KEY_PLAY = "Play"
private const val KEY_PAUSE = "Pause"
private const val KEY_ARROW_RIGHT = "ArrowRight"
private const val KEY_ARROW_LEFT = "ArrowLeft"
const val KEY_SEEK_PREFIX = "Seek:"
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

    // Video mode plays its audio through the YtVideoPreview WebView itself,
    // but that WebView only exists while the expanded player is on screen
    // AND the app is in the foreground (collapsing the player tears the
    // WebView down, and backgrounding the app gets its video paused by the
    // OS regardless of composition). Rather than let sound stop in either
    // case, playback is handed over to dmt's own (till-then muted) audio
    // pipeline whenever the WebView can't be trusted to keep playing, and
    // handed back — resynced to wherever the fallback ended up — once the
    // WebView is genuinely on screen and foregrounded again.
    private var appInForeground = true
    private var videoAudioHandedToController = false
    private var pendingIconAccent: Int? = null

    private fun usingWebViewAudio(): Boolean =
        _state.value.ytVideoMode && !videoAudioHandedToController
    private var pendingSaveSong: Innertube.SongItem? = null
    private var pendingSaveFormat: YtAudioFormat = YtAudioFormat.AAC
    private val _saveRequests = Channel<String>(Channel.BUFFERED)
    val saveRequests: Flow<String> = _saveRequests.receiveAsFlow()
    private val _installRequests = Channel<Unit>(Channel.BUFFERED)
    val installRequests: Flow<Unit> = _installRequests.receiveAsFlow()

    init {
        viewModelScope.launch {
            val prefs = getApplication<Application>().dmtStore.data.first()
            val settings = DmtSettings(
                wave = prefs[KEY_WAVE] ?: true,
                cols = prefs[KEY_COLS] ?: 64,
                listSpecs = prefs[KEY_SPECS] ?: true,
                accent = prefs[KEY_ACCENT] ?: 0,
                accentColor = prefs[KEY_ACCENT_COLOR] ?: 0,
                rawArt = prefs[KEY_RAW] ?: false,
                fullScreen = prefs[KEY_FULLSCREEN] ?: true,
                lightMode = prefs[KEY_LIGHT_MODE] ?: false,
                skipSilence = prefs[KEY_SKIP_SILENCE] ?: false,
                keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: false,
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
                    showTelegramPrompt = prefs[KEY_TELEGRAM_PROMPT_SHOWN] != true,
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

            DmtAction.TogglePlay -> {
                val nextPlaying = !_state.value.isPlaying
                c?.togglePlayPause()
                if (usingWebViewAudio()) sendYtVideoKey(if (nextPlaying) KEY_PLAY else KEY_PAUSE)
            }
            DmtAction.Next -> {
                if (usingWebViewAudio()) sendYtVideoKey(KEY_ARROW_RIGHT) else c?.seekToNext()
            }
            DmtAction.Prev -> {
                if (usingWebViewAudio()) sendYtVideoKey(KEY_ARROW_LEFT) else c?.seekToPrevious()
            }
            DmtAction.ToggleShuffle -> c?.run { shuffleModeEnabled = !shuffleModeEnabled }
            DmtAction.CycleRepeat -> c?.cycleRepeat()

            is DmtAction.Seek -> {
                if (usingWebViewAudio()) {
                    val duration = _state.value.durationMs
                    if (duration > 0) {
                        val target = (action.fraction * duration).toLong()
                        _state.update { it.copy(positionMs = target) }
                    }
                    sendYtVideoKey("$KEY_SEEK_PREFIX${action.fraction}")
                } else {
                    c?.run {
                        val duration = _state.value.durationMs
                        if (duration > 0) {
                            val target = (action.fraction * duration).toLong()
                            seekTo(target)
                            _state.update { it.copy(positionMs = target) }
                        }
                    }
                }
            }

            is DmtAction.YtVideoProgress -> {
                if (usingWebViewAudio()) {
                    _state.update {
                        it.copy(positionMs = action.positionMs, durationMs = action.durationMs)
                    }
                }
            }

            is DmtAction.Expand -> {
                _state.update { it.copy(expanded = action.value) }
                updateVideoAudioRouting()
            }

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
                        it[KEY_ACCENT_COLOR] = action.settings.accentColor
                        it[KEY_RAW] = action.settings.rawArt
                        it[KEY_FULLSCREEN] = action.settings.fullScreen
                        it[KEY_LIGHT_MODE] = action.settings.lightMode
                        it[KEY_SKIP_SILENCE] = action.settings.skipSilence
                        it[KEY_KEEP_SCREEN_ON] = action.settings.keepScreenOn
                    }
                }
                if (old.cols != action.settings.cols) loadCover(c?.currentMediaItem)
                // Swapping the launcher-icon alias (setComponentEnabledSetting)
                // is what was causing the app to get killed and restarted the
                // instant an accent was tapped — some launchers/OSes force a
                // process kill to refresh the icon even with DONT_KILL_APP.
                // The on-screen color already updates live via LocalAccent
                // recomposition below, so the icon swap itself is deferred
                // until the app is backgrounded (see setAppForeground), where
                // a kill is invisible to the person using the app.
                pendingIconAccent = action.settings.accent
                if (old.skipSilence != action.settings.skipSilence) {
                    controller?.sendCustomCommand(
                        PlaybackService.CMD_SKIP_SILENCE,
                        Bundle().apply { putBoolean(PlaybackService.KEY_SKIP_SILENCE, action.settings.skipSilence) }
                    )
                }
            }

            is DmtAction.YtQuery -> ytQuery(action.value)
            is DmtAction.PlayYtTrack -> playYtTrack(action.song)
            is DmtAction.TogglePinYtTrack -> togglePinYtTrack(action.song)
            is DmtAction.SaveYtTrack -> requestSaveYtTrack(action.song, action.format)
            is DmtAction.SaveYtTrackTo -> saveYtTrackTo(action.uri)
            DmtAction.ToggleYtVideoMode -> toggleYtVideoMode()
            DmtAction.ClearCache -> clearCache()
            DmtAction.DismissTelegramPrompt -> {
                _state.update { it.copy(showTelegramPrompt = false) }
                viewModelScope.launch {
                    getApplication<Application>().dmtStore.edit {
                        it[KEY_TELEGRAM_PROMPT_SHOWN] = true
                    }
                }
            }
            DmtAction.CheckForUpdates -> checkForUpdates()
            DmtAction.ConfirmUpdateDownload -> confirmUpdateDownload()
            DmtAction.DismissUpdatePrompt -> _state.update {
                it.copy(updateAvailable = false, updateVersionName = "")
            }
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

    private fun requestSaveYtTrack(song: Innertube.SongItem, format: YtAudioFormat) {
        pendingSaveSong = song
        pendingSaveFormat = format
        val safeName = "${song.title()} - ${song.artistLine()}"
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "track" } + "." + format.extension
        viewModelScope.launch { _saveRequests.send(safeName) }
    }

    private fun saveYtTrackTo(uri: Uri) {
        val song = pendingSaveSong
        val format = pendingSaveFormat
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
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                        YtAudioTranscoder.convert(
                            context = getApplication<Application>(),
                            sourceUrl = url,
                            format = format,
                            userAgent = REMOTE_ART_USER_AGENT,
                            outputStream = output,
                        )
                    } ?: false
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

    private fun sendYtVideoKey(key: String) {
        _state.update { it.copy(ytVideoKey = YtVideoKey(key, System.nanoTime())) }
    }

    private fun toggleYtVideoMode() {
        val id = _state.value.nowPlayingId ?: return
        if (!id.startsWith(YT_ID_PREFIX)) return
        val entering = !_state.value.ytVideoMode
        _state.update { it.copy(ytVideoMode = entering) }
        videoAudioHandedToController = false
        if (entering) {
            // Defer to updateVideoAudioRouting: it only mutes dmt's own
            // audio pipeline (in favor of the WebView's own sound) if the
            // WebView is actually visible and foregrounded right now;
            // otherwise it keeps dmt's own pipeline as the audible source.
            updateVideoAudioRouting()
        } else {
            controller?.volume = 1f
        }
    }

    /** Called any time the WebView's ability to actually be heard may have
     * changed — entering/leaving video mode, expanding/collapsing the
     * player, or the app moving to/from the background — so the audible
     * source (WebView vs. dmt's own pipeline) always matches what's really
     * able to play right now, and playback never goes silent. */
    private fun updateVideoAudioRouting() {
        val s = _state.value
        if (!s.ytVideoMode) return
        val c = controller ?: return
        val webViewVisible = s.expanded && appInForeground

        if (!webViewVisible && !videoAudioHandedToController) {
            videoAudioHandedToController = true
            val pos = s.positionMs
            if (pos > 0) c.seekTo(pos)
            c.volume = 1f
            if (s.isPlaying) c.play() else c.pause()
        } else if (webViewVisible && videoAudioHandedToController) {
            videoAudioHandedToController = false
            c.volume = 0f
            val duration = s.durationMs
            if (duration > 0) {
                val fraction = c.currentPosition.coerceAtLeast(0L).toFloat() / duration
                sendYtVideoKey("$KEY_SEEK_PREFIX${fraction}")
            }
            sendYtVideoKey(KEY_PLAY)
        }
    }

    /** Reported by [MainActivity] as the app itself (not just this one
     * Activity instance) moves to/from the background, e.g. when the user
     * minimises it or switches to another app. */
    fun setAppForeground(foreground: Boolean) {
        if (appInForeground == foreground) return
        appInForeground = foreground
        updateVideoAudioRouting()
        if (!foreground) {
            pendingIconAccent?.let { accent ->
                pendingIconAccent = null
                applyIcon(accent)
            }
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
        restoreSkipSilence(c)
        loadCover(c.currentMediaItem)
        loadTech(c.currentMediaItem)
        loadLyrics(c.currentMediaItem)
        restoreSession()
        while (isActive) {
            val videoMode = usingWebViewAudio()
            val position = if (videoMode) _state.value.positionMs else c.currentPosition.coerceAtLeast(0L)
            val duration = if (videoMode) {
                _state.value.durationMs
            } else {
                c.duration.takeIf { d -> d != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
            }
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
            controller?.volume = 1f
            videoAudioHandedToController = false
            _state.update { it.copy(nowPlayingId = mediaItem?.mediaId, error = null, ytVideoMode = false) }
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

    private suspend fun restoreSkipSilence(c: MediaController) {
        val saved = getApplication<Application>().dmtStore.data.first()[KEY_SKIP_SILENCE] ?: false
        c.sendCustomCommand(
            PlaybackService.CMD_SKIP_SILENCE,
            Bundle().apply { putBoolean(PlaybackService.KEY_SKIP_SILENCE, saved) }
        )
    }

    private fun clearCache() {
        controller?.sendCustomCommand(PlaybackService.CMD_CLEAR_CACHE, Bundle.EMPTY)
        notify(getApplication<Application>().getString(R.string.cache_cleared))
    }

    private var pendingReleaseVersionCode: Int = 0

    private fun checkForUpdates() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val installedCode = UpdateChecker.installedVersionCode(app)
            val pendingCode = UpdateChecker.pendingVersionCode(app)
            if (UpdateChecker.hasPendingApk(app) && pendingCode != null) {
                if (pendingCode > installedCode) {
                    // Already downloaded and still not installed — don't
                    // fetch or download anything again, just relaunch the
                    // installer on the file that's already sitting there.
                    _installRequests.send(Unit)
                    return@launch
                } else {
                    // The pending update has since been installed (or is
                    // stale) — clear it out and check fresh below.
                    UpdateChecker.clearPending(app)
                }
            }

            _state.update { it.copy(updateChecking = true) }
            val result = UpdateChecker.fetchLatestRelease()
            _state.update { it.copy(updateChecking = false) }
            result.onSuccess { release ->
                if (release.versionCode > installedCode) {
                    pendingReleaseVersionCode = release.versionCode
                    _state.update {
                        it.copy(
                            updateAvailable = true,
                            updateVersionName = release.versionName,
                        )
                    }
                    pendingReleaseUrl = release.downloadUrl
                } else {
                    notify(app.getString(R.string.update_up_to_date))
                }
            }.onFailure {
                notify(app.getString(R.string.update_error))
            }
        }
    }

    private var pendingReleaseUrl: String? = null

    private fun confirmUpdateDownload() {
        val app = getApplication<Application>()
        val url = pendingReleaseUrl ?: return
        val versionName = _state.value.updateVersionName
        val versionCode = pendingReleaseVersionCode
        _state.update {
            it.copy(updateAvailable = false, updateDownloading = true, updateDownloadProgress = 0f)
        }
        viewModelScope.launch {
            val release = UpdateChecker.ReleaseInfo(versionName, versionCode, url)
            val result = UpdateChecker.downloadApk(app, release) { progress ->
                _state.update { it.copy(updateDownloadProgress = progress) }
            }
            _state.update { it.copy(updateDownloading = false) }
            result.onSuccess {
                _installRequests.send(Unit)
            }.onFailure {
                notify(app.getString(R.string.update_error))
            }
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
            val component = ComponentName(app, "${app.packageName}.$alias")
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
