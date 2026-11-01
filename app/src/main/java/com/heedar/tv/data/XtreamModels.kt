package com.heedar.tv.data

import com.google.gson.annotations.SerializedName

// نموذج بيانات القناة الحية
data class LiveChannel(
    @SerializedName("stream_id") val streamId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("category_id") val categoryId: String?
)

// نموذج بيانات الفيلم
data class VodMovie(
    @SerializedName("stream_id") val streamId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("stream_icon") val streamIcon: String?,
    @SerializedName("rating") val rating: String?
)

// نموذج بيانات المسلسل
data class SeriesItem(
    @SerializedName("series_id") val seriesId: Int,
    @SerializedName("name") val name: String,
    @SerializedName("cover") val cover: String?,
    @SerializedName("rating") val rating: String?
)
