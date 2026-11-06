package com.heedar.tv.ui

import androidx.annotation.StringRes
import com.heedar.tv.R

/**
 * Top-level destinations. Order here is the tab order on every form factor.
 */
enum class ContentSection(@StringRes val titleRes: Int) {
    LIVE(R.string.nav_live),
    MOVIES(R.string.nav_movies),
    SERIES(R.string.nav_series),
    NEWS(R.string.nav_news),
    SPORTS(R.string.nav_sports),
    SEARCH(R.string.nav_search),
    FAVORITES(R.string.nav_favorites),
    SETTINGS(R.string.nav_settings);

    companion object {
        fun fromName(name: String?): ContentSection? =
            entries.firstOrNull { it.name == name }
    }
}
