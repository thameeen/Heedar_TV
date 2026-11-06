package com.heedar.tv.ui.common

import android.content.Context
import com.heedar.tv.R

/**
 * Column count for a media grid, derived from the real card width so phones,
 * tablets and TVs all fill their width without hard-coded breakpoints.
 */
fun Context.gridSpanCount(): Int {
    val cardWithGap = resources.getDimensionPixelSize(R.dimen.card_width) +
        resources.getDimensionPixelSize(R.dimen.card_grid_spacing)
    val available = resources.displayMetrics.widthPixels -
        resources.getDimensionPixelSize(R.dimen.screen_padding) * 2
    return (available / cardWithGap).coerceIn(MIN_SPAN, MAX_SPAN)
}

private const val MIN_SPAN = 2
private const val MAX_SPAN = 6
