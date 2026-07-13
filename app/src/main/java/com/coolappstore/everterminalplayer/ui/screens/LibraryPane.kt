package com.coolappstore.everterminalplayer.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.coolappstore.everterminalplayer.R
import com.coolappstore.everterminalplayer.player.asTime
import com.coolappstore.everterminalplayer.ui.DmtAction
import com.coolappstore.everterminalplayer.ui.DmtState
import com.coolappstore.everterminalplayer.ui.components.Caption
import com.coolappstore.everterminalplayer.ui.components.ListRow
import com.coolappstore.everterminalplayer.ui.components.SearchRow
import com.coolappstore.everterminalplayer.ui.components.TuiKey

@Composable
fun LibraryPane(state: DmtState, dispatch: (DmtAction) -> Unit) {
    if (state.tracks.isEmpty()) {
        Caption(stringResource(R.string.no_audio))
        TuiKey(stringResource(R.string.rescan)) { dispatch(DmtAction.Rescan) }
        return
    }

    Column {
        SearchRow(
            query = state.query,
            hint = stringResource(R.string.search_tracks_hint, state.tracks.size),
            shown = state.filtered.size,
            onQuery = { dispatch(DmtAction.Query(it)) }
        )
        if (state.filtered.isEmpty()) {
            Caption(stringResource(R.string.no_match))
        }
        LazyColumn {
            itemsIndexed(state.filtered, key = { _, track -> track.id }) { index, track ->
                ListRow(
                    index = index,
                    line1 = track.title,
                    line2 = "${track.artist} · ${track.durationMs.asTime()}".lowercase(),
                    current = track.id.toString() == state.nowPlayingId,
                    onClick = { dispatch(DmtAction.PlayAt(state.filtered, index)) },
                    onLongClick = { dispatch(DmtAction.Enqueue(listOf(track), track.title)) }
                )
            }
        }
    }
}
