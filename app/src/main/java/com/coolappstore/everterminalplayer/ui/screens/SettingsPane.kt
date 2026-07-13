package com.coolappstore.everterminalplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import com.coolappstore.everterminalplayer.R
import com.coolappstore.everterminalplayer.ui.DmtAction
import com.coolappstore.everterminalplayer.ui.DmtState
import com.coolappstore.everterminalplayer.ui.DmtView
import com.coolappstore.everterminalplayer.ui.components.Caption
import com.coolappstore.everterminalplayer.ui.components.TuiColorPickerPopup
import com.coolappstore.everterminalplayer.ui.components.TuiKey
import com.coolappstore.everterminalplayer.ui.components.tuiClickable
import com.coolappstore.everterminalplayer.ui.theme.AccentPalette
import com.coolappstore.everterminalplayer.ui.theme.LocalAccent
import com.coolappstore.everterminalplayer.ui.theme.TuiAccent
import com.coolappstore.everterminalplayer.ui.theme.TuiDim
import com.coolappstore.everterminalplayer.ui.theme.TuiFaint
import com.coolappstore.everterminalplayer.ui.theme.TuiFg
import com.coolappstore.everterminalplayer.ui.theme.TuiLine
import com.coolappstore.everterminalplayer.ui.theme.nearestAccentIndex

@Composable
fun SettingsPane(state: DmtState, dispatch: (DmtAction) -> Unit) {
    val settings = state.settings
    val on = stringResource(R.string.on)
    val off = stringResource(R.string.off)

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Caption(stringResource(R.string.updates))
        SettingRow(
            label = stringResource(R.string.set_check_updates),
            value = when {
                state.updateChecking -> stringResource(R.string.update_checking)
                state.updateDownloading -> stringResource(
                    R.string.update_downloading_short,
                    (state.updateDownloadProgress * 100).toInt()
                )
                else -> stringResource(R.string.run)
            }
        ) {
            if (!state.updateChecking && !state.updateDownloading) {
                dispatch(DmtAction.CheckForUpdates)
            }
        }

        Caption(stringResource(R.string.config))

        SettingRow(
            label = stringResource(R.string.set_wave),
            value = if (settings.wave) on else off
        ) {
            dispatch(DmtAction.Config(settings.copy(wave = !settings.wave)))
        }
        SettingRow(
            label = stringResource(R.string.set_detail),
            value = stringResource(R.string.set_detail_value, settings.cols)
        ) {
            val next = when (settings.cols) {
                48 -> 64
                64 -> 80
                else -> 48
            }
            dispatch(DmtAction.Config(settings.copy(cols = next)))
        }
        SettingRow(
            label = stringResource(R.string.set_raw),
            value = if (settings.rawArt) on else off
        ) {
            dispatch(DmtAction.Config(settings.copy(rawArt = !settings.rawArt)))
        }
        SettingRow(
            label = stringResource(R.string.set_fullscreen),
            value = if (settings.fullScreen) on else off
        ) {
            dispatch(DmtAction.Config(settings.copy(fullScreen = !settings.fullScreen)))
        }
        SettingRow(
            label = stringResource(R.string.set_specs),
            value = if (settings.listSpecs) on else off
        ) {
            dispatch(DmtAction.Config(settings.copy(listSpecs = !settings.listSpecs)))
        }
        var accentAnchor by remember { mutableStateOf<IntRect?>(null) }
        var showAccentPicker by remember { mutableStateOf(false) }
        val currentAccentColor = if (settings.accentColor != 0) {
            Color(settings.accentColor)
        } else {
            AccentPalette[settings.accent % AccentPalette.size].second
        }

        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .onGloballyPositioned { accentAnchor = it.boundsInWindow().roundToIntRect() }
            ) {
                Text(
                    text = stringResource(R.string.set_accent),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TuiFg
                )
                TuiKey(
                    label = "[ %s%06X ]".format('#', currentAccentColor.toArgb() and 0xFFFFFF),
                    onClick = { showAccentPicker = true }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TuiLine)
            )
        }
        if (showAccentPicker) {
            accentAnchor?.let { anchor ->
                TuiColorPickerPopup(
                    anchorBounds = anchor,
                    label = stringResource(R.string.set_accent),
                    initialColor = currentAccentColor,
                    defaultColor = TuiAccent,
                    onColorChange = { color ->
                        dispatch(
                            DmtAction.Config(
                                settings.copy(
                                    accent = nearestAccentIndex(color),
                                    accentColor = color.toArgb(),
                                )
                            )
                        )
                    },
                    onDismiss = { showAccentPicker = false },
                )
            }
        }
        SettingRow(
            label = stringResource(R.string.set_light_mode),
            value = if (settings.lightMode) on else off
        ) {
            dispatch(DmtAction.Config(settings.copy(lightMode = !settings.lightMode)))
        }
        Caption(stringResource(R.string.audio))
        SettingRow(
            label = stringResource(R.string.set_skip_silence),
            value = if (settings.skipSilence) on else off
        ) {
            dispatch(DmtAction.Config(settings.copy(skipSilence = !settings.skipSilence)))
        }
        SettingRow(
            label = stringResource(R.string.set_keep_screen_on),
            value = if (settings.keepScreenOn) on else off
        ) {
            dispatch(DmtAction.Config(settings.copy(keepScreenOn = !settings.keepScreenOn)))
        }
        Caption(stringResource(R.string.tools))
        SettingRow(
            label = stringResource(R.string.set_eq),
            value = stringResource(R.string.set_eq_open)
        ) {
            dispatch(DmtAction.OpenEqualizer)
        }
        SettingRow(
            label = stringResource(R.string.stats),
            value = stringResource(R.string.stat_view)
        ) {
            dispatch(DmtAction.Show(DmtView.STATS))
        }
        SettingRow(
            label = stringResource(R.string.set_rescan),
            value = stringResource(R.string.run)
        ) {
            dispatch(DmtAction.Rescan)
        }
        SettingRow(
            label = stringResource(R.string.set_clear_cache),
            value = stringResource(R.string.run)
        ) {
            dispatch(DmtAction.ClearCache)
        }

        Caption(stringResource(R.string.about))
        Text(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.bodyMedium,
            color = TuiFg
        )
        Text(
            text = stringResource(R.string.about_body),
            style = MaterialTheme.typography.labelSmall,
            color = TuiDim,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = stringResource(R.string.version),
            style = MaterialTheme.typography.labelSmall,
            color = TuiFaint,
            modifier = Modifier.padding(top = 6.dp)
        )

        val uriHandler = LocalUriHandler.current
        LinkRow(label = stringResource(R.string.credit), url = stringResource(R.string.credit_url), uriHandler = uriHandler, topPadding = 16.dp)
        LinkRow(label = stringResource(R.string.source_code), url = stringResource(R.string.source_code_url), uriHandler = uriHandler, topPadding = 14.dp)
        LinkRow(label = stringResource(R.string.telegram_support), url = stringResource(R.string.telegram_support_url), uriHandler = uriHandler, topPadding = 14.dp, bottomPadding = 28.dp)
    }
}

@Composable
private fun LinkRow(
    label: String,
    url: String,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 0.dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = topPadding, bottom = bottomPadding)
            .tuiClickable { runCatching { uriHandler.openUri(url) } }
    ) {
        Text(
            text = "▪ ",
            style = MaterialTheme.typography.labelMedium,
            color = LocalAccent.current
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TuiDim
        )
        Text(
            text = " ↗",
            style = MaterialTheme.typography.labelMedium,
            color = LocalAccent.current
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TuiFg
            )
            TuiKey(
                label = "[ $value ]",
                onClick = onClick
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TuiLine)
        )
    }
}
