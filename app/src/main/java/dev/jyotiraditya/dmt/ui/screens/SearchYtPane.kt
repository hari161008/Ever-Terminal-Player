package dev.jyotiraditya.dmt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.innertube.Innertube
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.ui.DmtAction
import dev.jyotiraditya.dmt.ui.DmtState
import dev.jyotiraditya.dmt.ui.components.Caption
import dev.jyotiraditya.dmt.ui.components.SearchRow
import dev.jyotiraditya.dmt.ui.components.tuiClickable
import dev.jyotiraditya.dmt.ui.player.SheetHeader
import dev.jyotiraditya.dmt.ui.player.TuiSheet
import dev.jyotiraditya.dmt.ui.theme.LocalAccent
import dev.jyotiraditya.dmt.ui.theme.TuiBg
import dev.jyotiraditya.dmt.ui.theme.TuiDim
import dev.jyotiraditya.dmt.ui.theme.TuiFaint
import dev.jyotiraditya.dmt.ui.theme.TuiFg
import dev.jyotiraditya.dmt.ui.theme.TuiLine
import dev.jyotiraditya.dmt.yt.YT_ID_PREFIX
import dev.jyotiraditya.dmt.yt.YtAudioFormat
import dev.jyotiraditya.dmt.yt.artistLine
import dev.jyotiraditya.dmt.yt.title

@Composable
fun SearchYtPane(state: DmtState, dispatch: (DmtAction) -> Unit) {
    val showingPinned = state.ytQuery.isBlank()
    val list = if (showingPinned) state.ytPinned else state.ytResults
    val pinnedKeys = remember(state.ytPinned) { state.ytPinned.map { it.key }.toSet() }
    var formatPickerSong by remember { mutableStateOf<Innertube.SongItem?>(null) }

    Column {
        SearchRow(
            query = state.ytQuery,
            hint = stringResource(R.string.search_yt_hint),
            shown = state.ytResults.size,
            onQuery = { dispatch(DmtAction.YtQuery(it)) }
        )

        when {
            showingPinned && list.isEmpty() -> Caption(stringResource(R.string.search_yt_prompt))
            !showingPinned && state.ytLoading -> Caption(stringResource(R.string.searching))
            !showingPinned && state.ytError != null -> Caption(state.ytError)
            !showingPinned && list.isEmpty() -> Caption(stringResource(R.string.no_match))
            showingPinned -> Caption(stringResource(R.string.yt_pinned_caption))
        }

        LazyColumn {
            itemsIndexed(list, key = { _, song -> song.key }) { index, song ->
                val id = YT_ID_PREFIX + song.key
                YtSongRow(
                    index = index,
                    song = song,
                    current = id == state.nowPlayingId,
                    pinned = song.key in pinnedKeys,
                    resolving = id == state.ytResolvingId,
                    saving = id == state.ytSavingId,
                    onClick = { dispatch(DmtAction.PlayYtTrack(song)) },
                    onTogglePin = { dispatch(DmtAction.TogglePinYtTrack(song)) },
                    onSave = { formatPickerSong = song },
                )
            }
        }
    }

    formatPickerSong?.let { song ->
        FormatPickerSheet(
            song = song,
            onDismiss = { formatPickerSong = null },
            onPick = { format ->
                dispatch(DmtAction.SaveYtTrack(song, format))
                formatPickerSong = null
            }
        )
    }
}

@Composable
private fun FormatPickerSheet(
    song: Innertube.SongItem,
    onDismiss: () -> Unit,
    onPick: (YtAudioFormat) -> Unit,
) {
    TuiSheet(onDismiss = onDismiss) {
        SheetHeader(title = stringResource(R.string.yt_pick_format), meta = song.title())
        YtAudioFormat.entries.forEach { format ->
            Text(
                text = format.label,
                style = MaterialTheme.typography.bodyMedium,
                color = TuiFg,
                modifier = Modifier
                    .tuiClickable { onPick(format) }
                    .padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun YtSongRow(
    index: Int,
    song: Innertube.SongItem,
    current: Boolean,
    pinned: Boolean,
    resolving: Boolean,
    saving: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onSave: () -> Unit,
) {
    val accent = LocalAccent.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(if (current) TuiFg else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .tuiClickable(onClick)
        ) {
            Text(
                text = if (current) "${song.title()}_" else song.title(),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (current) TuiBg else TuiFg,
                maxLines = 1,
            )
            val meta = if (resolving) {
                stringResource(R.string.resolving)
            } else {
                "${song.artistLine()} · ${song.durationText.orEmpty()}".lowercase()
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = if (current) TuiBg else TuiFaint,
                maxLines = 1,
            )
        }

        Text(
            text = if (pinned) "♥" else "♡",
            style = MaterialTheme.typography.titleMedium,
            color = if (pinned) accent else TuiDim,
            modifier = Modifier
                .tuiClickable(onTogglePin)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Text(
            text = if (saving) stringResource(R.string.yt_saving) else stringResource(R.string.yt_save),
            style = MaterialTheme.typography.labelSmall,
            color = TuiDim,
            modifier = Modifier
                .border(1.dp, TuiLine)
                .tuiClickable(onSave)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}
