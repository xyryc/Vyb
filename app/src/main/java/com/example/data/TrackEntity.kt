package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val audioUrl: String,
    val coverUrl: String,
    val isLiked: Boolean = false,
    val genre: String = "Unknown",
    val playCount: Int = 0,
    val folderName: String = "",
    val importDate: Long = 0L
)
