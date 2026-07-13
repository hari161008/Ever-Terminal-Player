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
 * Shown once, on the very first launch. "Continue" just dismisses it (it
 * never comes back); "Join" opens the Telegram group and dismisses too —
 * neither path blocks getting into the app.
 */
@Composable
fun TelegramPromptDialog(onContinue: () -> Unit, onJoin: () -> Unit) {
    Dialog(
        onDismissRequest = onContinue,
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
                    text = " " + stringResource(R.string.telegram_prompt_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = TuiFg
                )
            }
            Text(
                text = stringResource(R.string.telegram_prompt_body),
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
                    label = "[ ${stringResource(R.string.telegram_prompt_continue)} ]",
                    onClick = onContinue,
                )
                Spacer(modifier = Modifier.width(10.dp))
                TuiKey(
                    label = "[ ${stringResource(R.string.telegram_prompt_join)} ]",
                    bright = true,
                    onClick = onJoin,
                )
            }
        }
    }
}
