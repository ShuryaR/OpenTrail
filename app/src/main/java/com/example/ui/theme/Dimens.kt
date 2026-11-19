package com.example.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How tightly the lists are laid out. Auto picks Compact on short screens or with large text. */
enum class LayoutDensity(val label: String, val description: String) {
    AUTO("Automatic", "Compact on small screens and when text size is large."),
    COMFORTABLE("Comfortable", "More space around each item."),
    COMPACT("Compact", "More calls and leads on screen at once.");

    companion object {
        fun fromName(name: String?): LayoutDensity = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

data class Dimens(
    val cardPad: Dp,
    val cardGap: Dp,
    val listGap: Dp,
    val compact: Boolean
)

private val ComfortableDimens = Dimens(cardPad = 14.dp, cardGap = 4.dp, listGap = 10.dp, compact = false)
private val CompactDimens = Dimens(cardPad = 10.dp, cardGap = 2.dp, listGap = 6.dp, compact = true)

val LocalDimens = compositionLocalOf { ComfortableDimens }

/** Under this height a phone cannot show many rows with comfortable spacing. */
private const val SHORT_SCREEN_DP = 760f

/** Text at or above this scale already makes every row taller, so tighten the padding to compensate. */
private const val LARGE_TEXT_SCALE = 1.15f

fun resolveDimens(density: LayoutDensity, screenHeightDp: Float, fontScale: Float): Dimens =
    when (density) {
        LayoutDensity.COMFORTABLE -> ComfortableDimens
        LayoutDensity.COMPACT -> CompactDimens
        LayoutDensity.AUTO ->
            if (screenHeightDp < SHORT_SCREEN_DP || fontScale >= LARGE_TEXT_SCALE) CompactDimens else ComfortableDimens
    }
