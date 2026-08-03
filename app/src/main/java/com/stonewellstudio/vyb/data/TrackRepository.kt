package com.stonewellstudio.vyb.data

import kotlinx.coroutines.flow.Flow

class TrackRepository(private val trackDao: TrackDao) {
    val allTracks: Flow<List<TrackEntity>> = trackDao.getAllTracks()
    val likedTracks: Flow<List<TrackEntity>> = trackDao.getLikedTracks()
    val playlists: Flow<List<PlaylistEntity>> = trackDao.getAllPlaylists()

    suspend fun insertTracks(tracks: List<TrackEntity>) {
        trackDao.insertTracks(tracks)
    }

    suspend fun deleteDemoTracks() {
        trackDao.deleteDemoTracks()
    }

    suspend fun updateTrack(track: TrackEntity) {
        trackDao.updateTrack(track)
    }

    suspend fun getTrackById(id: String): TrackEntity? {
        return trackDao.getTrackById(id)
    }

    fun searchTracks(query: String): Flow<List<TrackEntity>> {
        return trackDao.searchTracks(query)
    }

    suspend fun insertPlaylist(playlist: PlaylistEntity): Long {
        return trackDao.insertPlaylist(playlist)
    }

    suspend fun deletePlaylist(playlistId: Int) {
        trackDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Int, trackId: String) {
        trackDao.insertPlaylistTrack(PlaylistTrackCrossRef(playlistId, trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Int, trackId: String) {
        trackDao.deletePlaylistTrack(playlistId, trackId)
    }

    fun getTracksForPlaylist(playlistId: Int): Flow<List<TrackEntity>> {
        return trackDao.getTracksForPlaylist(playlistId)
    }
}
