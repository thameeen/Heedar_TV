package com.heedar.tv.data

import com.heedar.tv.data.remote.UrlNormalizer

/** Alias so test files read naturally while still using the production normalizer. */
internal object UrlNormalizerFixture {
    fun normalize(raw: String?): String? = UrlNormalizer.normalize(raw)
}
