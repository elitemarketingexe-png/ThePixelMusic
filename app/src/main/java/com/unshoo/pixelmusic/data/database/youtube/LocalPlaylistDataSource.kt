package com.unshoo.pixelmusic.data.database.youtube

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.unshoo.pixelmusic.data.model.youtube.Playlist
import com.unshoo.pixelmusic.data.model.youtube.PlaylistInfo
import com.unshoo.pixelmusic.data.model.youtube.PlaylistSongCrossRef
import com.unshoo.pixelmusic.data.model.youtube.Song
import kotlinx.coroutines.flow.Flow

data class PlaylistSongCountRow(
    val playlistId: String,
    @ColumnInfo(name = "songCount") val songCount: Int
)

@Dao
interface LocalPlaylistDataSource {
    @Transaction
    @Query("SELECT * FROM playlists")
    suspend fun getAll(): List<Playlist>

    @Transaction
    @Query("SELECT * FROM playlists")
    fun observeAll(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists")
    fun observeAllPlaylistInfo(): Flow<List<PlaylistInfo>>

    @Query("SELECT playlistId, COUNT(songId) AS songCount FROM PlaylistSongCrossRef GROUP BY playlistId")
    fun observePlaylistSongCounts(): Flow<List<PlaylistSongCountRow>>

    @Query("SELECT * FROM PlaylistSongCrossRef ORDER BY playlistId, position ASC")
    fun observeAllPlaylistSongCrossRefs(): Flow<List<PlaylistSongCrossRef>>

    @Query("SELECT * FROM PlaylistSongCrossRef WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getCrossRefsForPlaylist(playlistId: String): List<PlaylistSongCrossRef>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: String): Playlist?

    @Query(
        """
    SELECT DISTINCT songId
    FROM PlaylistSongCrossRef
    WHERE songId IN (:songIds)
"""
    )
    suspend fun getSongIdsWithPlaylist(songIds: List<String>): List<String>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylistById(playlistId: String): Flow<Playlist?>

    @Query("SELECT EXISTS(SELECT 1 FROM PlaylistSongCrossRef WHERE playlistId = :playlistId AND songId = :songId)")
    suspend fun isSongInPlaylist(playlistId: String, songId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM PlaylistSongCrossRef WHERE playlistId = :playlistId AND songId = :songId)")
    fun observeIsSongInPlaylist(playlistId: String, songId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM PlaylistSongCrossRef WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deleteCrossRef(playlistId: String, songId: String)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlistInfo: PlaylistInfo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<PlaylistSongCrossRef>)

    @Transaction
    suspend fun insertPlaylistWithSongs(
        playlist: Playlist,
    ) {
        if (playlist.info.id == "_downloaded_") {
            insertPlaylist(playlist.info)
            val songs = playlist.songs
            insertSongs(songs)
            val refs = songs.mapIndexed { index, song -> PlaylistSongCrossRef(playlist.info.id, song.youtubeId, index) }
            insertCrossRefs(refs)
        } else {
            deleteCrossRefsByPlaylistId(playlist.info.id)
            insertPlaylist(playlist.info)
            val songs = playlist.songs
            insertSongs(songs)
            val refs = songs.mapIndexed { index, song -> PlaylistSongCrossRef(playlist.info.id, song.youtubeId, index) }
            insertCrossRefs(refs)
        }
    }

    @Transaction
    suspend fun insertPlaylistWithSongsPreserving(
        playlist: Playlist,
        songDataSource: LocalSongDataSource,
    ) {
        val updatedInfo = playlist.info.copy(
            lastSyncSongCount = playlist.songs.size,
            lastSyncTimestamp = System.currentTimeMillis()
        )
        insertPlaylist(updatedInfo)

        val allSongIds = playlist.songs.map { it.youtubeId }
        val existingIds = songDataSource.getExistingSongIds(allSongIds).toSet()
        val newSongs = playlist.songs.filter { it.youtubeId !in existingIds }

        if (newSongs.isNotEmpty()) {
            newSongs.chunked(YOUTUBE_DB_BATCH_SIZE).forEach { chunk ->
                insertSongs(chunk)
            }
        }

        deleteCrossRefsByPlaylistId(playlist.info.id)
        playlist.songs
            .asSequence()
            .mapIndexed { index, song -> PlaylistSongCrossRef(playlist.info.id, song.youtubeId, index) }
            .chunked(YOUTUBE_DB_BATCH_SIZE)
            .forEach { chunk -> insertCrossRefs(chunk) }
    }

    @Query("DELETE FROM playlists")
    suspend fun deleteAll()

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: String)

    @Query("DELETE FROM PlaylistSongCrossRef WHERE playlistId = :playlistId")
    suspend fun deleteCrossRefsByPlaylistId(playlistId: String)

    companion object {
        private const val YOUTUBE_DB_BATCH_SIZE = 500
    }

    @Transaction
    suspend fun deleteFullPlaylist(playlistId: String) {
        deleteCrossRefsByPlaylistId(playlistId)
        deletePlaylistById(playlistId)
    }
}
