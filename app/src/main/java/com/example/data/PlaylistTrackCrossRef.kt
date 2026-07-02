package com.example.data

import androidx.room.Entity

@Entity(tableName = "playlist_track_cross_ref", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrackCrossRef(
    val playlistId: Int,
    val trackId: String
)
