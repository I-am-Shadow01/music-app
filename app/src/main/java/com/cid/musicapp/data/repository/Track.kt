package com.cid.musicapp.data.repository

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int?,
    val thumbnailUrl: String?
)
