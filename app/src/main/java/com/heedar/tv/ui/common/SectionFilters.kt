package com.heedar.tv.ui.common

/**
 * Xtream has no dedicated news or sports endpoints: those sections exist only as
 * live categories on the panel. These filters map the user-facing sections onto
 * real server categories by name, and never invent content: if the panel has no
 * matching category the section honestly shows an empty state.
 */
object SectionFilters {

    val NEWS_KEYWORDS = listOf(
        "news",
        "أخبار",
        "اخبار",
        "إخبارية",
        "اخباريه",
        "إخباري"
    )

    val SPORTS_KEYWORDS = listOf(
        "sport",
        "sports",
        "رياضة",
        "رياضه",
        "رياضي"
    )

    fun matchesCategory(name: String, keywords: List<String>): Boolean {
        if (keywords.isEmpty()) return true
        return keywords.any { keyword -> name.contains(keyword, ignoreCase = true) }
    }
}
