package com.coolappstore.everterminalplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark palette (default)
val DarkBg = Color(0xFF121314)
val DarkSurface = Color(0xFF191A1C)
val DarkFg = Color(0xFFD8D6D0)
val DarkBright = Color(0xFFEDEBE4)
val DarkDim = Color(0xFF83827D)
val DarkFaint = Color(0xFF4C4C49)
val DarkLine = Color(0xFF2A2B2D)
val DarkRed = Color(0xFFC96B6B)

// Light palette
val LightBg = Color(0xFFF4F2ED)
val LightSurface = Color(0xFFFFFFFF)
val LightFg = Color(0xFF2B2A27)
val LightBright = Color(0xFF141311)
val LightDim = Color(0xFF6E6C66)
val LightFaint = Color(0xFFAEABA2)
val LightLine = Color(0xFFDAD7CE)
val LightRed = Color(0xFFA83C3C)

// Theme-independent
val TuiAccent = Color(0xFFE8642C)
val MonoAccent = Color(0xFFD8D6D0)

val TuiBg: Color
    @Composable get() = if (LocalIsLight.current) LightBg else DarkBg

val TuiSurface: Color
    @Composable get() = if (LocalIsLight.current) LightSurface else DarkSurface

val TuiFg: Color
    @Composable get() = if (LocalIsLight.current) LightFg else DarkFg

val TuiBright: Color
    @Composable get() = if (LocalIsLight.current) LightBright else DarkBright

val TuiDim: Color
    @Composable get() = if (LocalIsLight.current) LightDim else DarkDim

val TuiFaint: Color
    @Composable get() = if (LocalIsLight.current) LightFaint else DarkFaint

val TuiLine: Color
    @Composable get() = if (LocalIsLight.current) LightLine else DarkLine

val TuiRed: Color
    @Composable get() = if (LocalIsLight.current) LightRed else DarkRed
