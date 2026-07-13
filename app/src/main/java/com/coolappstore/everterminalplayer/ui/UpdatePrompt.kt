package com.coolappstore.everterminalplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.coolappstore.everterminalplayer.R
import com.coolappstore.everterminalplayer.ui.components.TuiKey
import com.coolappstore.everterminalplayer.ui.theme.LocalAccent
import com.coolappstore.everterminalplayer.ui.theme.TuiDim
import com.coolappstore.everterminalplayer.ui.theme.TuiFg
import com.coolappstore.everterminalplayer.ui.theme.TuiLine
import com.coolappstore.everterminalplayer.ui.theme.TuiSurface

/**
 * Asks permission before downloading a newer build straight from the
 * project's GitHub releases page. Declining just dismisses it; accepting
 * kicks off the download, which then hands itself to the system package
 * installer once it's done — no browser, no Downloads folder.
 */
@Composable
fun UpdateAvailableDialog(versionName: String, onCancel: () -> Unit, onDownload: () -> Unit) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(28.dp)
                .border(1.dp, TuiLine)
                .background(TuiSurface)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(LocalAccent.current)
                )
                Text(
                    text = " " + stringResource(R.string.update_available_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = TuiFg
                )
            }
            Text(
                text = stringResource(R.string.update_available_body, versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = TuiDim,
                modifier = Modifier.padding(top = 10.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TuiKey(
                    label = "[ ${stringResource(R.string.update_cancel)} ]",
                    onClick = onCancel,
                )
                Spacer(modifier = Modifier.width(10.dp))
                TuiKey(
                    label = "[ ${stringResource(R.string.update_download)} ]",
                    bright = true,
                    onClick = onDownload,
                )
            }
        }
    }
}
