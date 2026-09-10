package com.unshoo.pixelmusic.data.database.youtube

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.unshoo.pixelmusic.data.model.youtube.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalSongDataSource {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun create(song: Song)

    @Query(
        """
    SELECT * 
    FROM songs 
    WHERE audioFilePath IS NOT NULL 
      AND audioFilePath != ''
      ORDER BY  
        songs.title COLLATE NOCASE ASC,
        songs.artist COLLATE NOCASE ASC
"""
    )
    suspend fun getDownloadedSongs(): List<Song>

    @Query("SELECT COUNT(*) FROM songs WHERE audioFilePath IS NOT NULL AND audioFilePath != ''")
    suspend fun getDownloadedSongCount(): Int

    @Query(
        """
        SELECT * FROM songs
        WHERE audioFilePath IS NOT NULL AND audioFilePath != ''
        ORDER BY songs.title COLLATE NOCASE ASC, songs.artist COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getDownloadedSongsPage(limit: Int, offset: Int): List<Song>

    @Query(
        """
    SELECT * 
    FROM songs 
    WHERE audioFilePath IS NOT NULL 
      AND audioFilePath != ''
      ORDER BY  
        songs.title COLLATE NOCASE ASC,
        songs.artist COLLATE NOCASE ASC
"""
    )
    fun observeDownloadedSongs(): Flow<List<Song>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createAll(songs: List<Song>)

    @Query("SELECT * FROM songs WHERE youtubeId = :songId")
    suspend fun getSong(songId: String): Song?

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    suspend fun setStreamUrl(songId: String, streamUrl: String) {
        val existing = getSong(songId) ?: Song(
            youtubeId = songId,
            streamUrl = streamUrl
        )

        val updated = existing.copy(streamUrl = streamUrl)
        create(updated)
    }

    @Query("DELETE FROM songs WHERE youtubeId IN (:songIds)")
    suspend fun deleteByIds(songIds: List<String>)

    @Delete
    suspend fun delete(song: Song)

    @Query("SELECT youtubeId FROM songs WHERE youtubeId IN (:ids)")
    suspend fun getExistingSongIdsRaw(ids: List<String>): List<String>

    suspend fun getExistingSongIds(ids: List<String>): List<String> {
        if (ids.isEmpty()) return emptyList()
        return ids.distinct().chunked(800).flatMap { chunk -> getExistingSongIdsRaw(chunk) }
    }

    @Query("UPDATE songs SET isPermanentlyDownloaded = 1, downloadTimestamp = :timestamp WHERE youtubeId = :songId")
    suspend fun markAsPermanentlyDownloaded(songId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET genre = :genre WHERE youtubeId = :songId")
    suspend fun updateGenre(songId: String, genre: String)

    @Query("UPDATE songs SET audioFilePath = :audioPath WHERE youtubeId = :songId")
    suspend fun updateAudioPath(songId: String, audioPath: String)

    @Query("SELECT * FROM songs WHERE genre IS NULL OR genre = ''")
    suspend fun getSongsWithoutGenre(): List<Song>
}
