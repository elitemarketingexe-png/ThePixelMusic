package com.unshoo.pixelmusic.data.preferences

import android.content.Context
import com.unshoo.pixelmusic.data.database.LocalPlaylistDao
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.youtube.AppDatabase
import com.unshoo.pixelmusic.data.database.toEntity
import com.unshoo.pixelmusic.data.database.toPlaylist
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.data.remote.youtube.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue
import timber.log.Timber

@Singleton
class PlaylistPreferencesRepository @Inject constructor(
    private val localPlaylistDao: LocalPlaylistDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicDao: MusicDao,
    @ApplicationContext private val context: Context
) {
    private val coverPrefs = context.getSharedPreferences("playlist_cover_customizations", Context.MODE_PRIVATE)
    private val pinPrefs = context.getSharedPreferences("playlist_pins", Context.MODE_PRIVATE)
    private val playlistTimestampsPrefs = context.getSharedPreferences("playlist_timestamps", Context.MODE_PRIVATE)
    private val migrationMutex = Mutex()
    @Volatile
    private var migrationChecked = false

    private val _pinnedPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val pinnedPlaylistIds = _pinnedPlaylistIds.asStateFlow()

    init {
        val pinned = pinPrefs.getStringSet("pinned_ids", emptySet()) ?: emptySet()
        _pinnedPlaylistIds.value = pinned
    }

    suspend fun togglePinPlaylist(playlistId: String) {
        val current = _pinnedPlaylistIds.value
        val next = if (current.contains(playlistId)) current - playlistId else current + playlistId
        pinPrefs.edit().putStringSet("pinned_ids", next).apply()
        _pinnedPlaylistIds.value = next
    }

    fun getOrCreatePlaylistTimestamps(
        pId: String,
        syncTimestamp: Long,
        title: String,
        songIds: List<String>
    ): Pair<Long, Long> {
        val createdKey = "${pId}_createdAt"
        val modifiedKey = "${pId}_lastModified"
        val hashKey = "${pId}_stateHash"

        var createdAt = playlistTimestampsPrefs.getLong(createdKey, 0L)
        var lastModified = playlistTimestampsPrefs.getLong(modifiedKey, 0L)
        val oldHash = playlistTimestampsPrefs.getInt(hashKey, 0)

        val currentHash = (title + "_" + songIds.joinToString(",")).hashCode()
        val now = if (syncTimestamp > 0L) syncTimestamp else System.currentTimeMillis()

        if (createdAt == 0L) {
            createdAt = now
            playlistTimestampsPrefs.edit().putLong(createdKey, createdAt).apply()
        }

        if (lastModified == 0L) {
            lastModified = createdAt
            playlistTimestampsPrefs.edit()
                .putLong(modifiedKey, lastModified)
                .putInt(hashKey, currentHash)
                .apply()
        } else if (oldHash != currentHash) {
            lastModified = System.currentTimeMillis()
            playlistTimestampsPrefs.edit()
                .putLong(modifiedKey, lastModified)
                .putInt(hashKey, currentHash)
                .apply()
        }

        return Pair(createdAt, lastModified)
    }

    val userPlaylistsFlow: Flow<List<Playlist>> = combine(
        localPlaylistDao.observePlaylistsWithSongs()
            .onStart { ensureMigratedIfNeeded() }
            .map { rows ->
                rows.map { row ->
                    val pl = row.playlist.toPlaylist(
                        songIds = row.songs.sortedBy { it.sortOrder }.map { it.songId }
                    )
                    if (pl.source == "YOUTUBE") pl.copy(id = pl.id.removePrefix("VL")) else pl
                }.filter { com.unshoo.pixelmusic.data.remote.youtube.YouTubeItemFilter.isMusicPlaylist(it.name, it.id) }
            },
        combine(
            AppDatabase.getInstance(context).playlistRepository().observeAllPlaylistInfo(),
            AppDatabase.getInstance(context).playlistRepository().observeAllPlaylistSongCrossRefs()
        ) { infos, refs -> infos to refs },
        AppDatabase.getInstance(context).playlistRepository().observePlaylistSongCounts(),
        AppDatabase.getInstance(context).songRepository().observeDownloadedSongs(),
        _pinnedPlaylistIds
    ) { localPlaylists, (ytPlaylistInfos, allCrossRefs), ytSongCountRows, downloadedSongs, pinnedIds ->
        val crossRefsByPlaylistId = allCrossRefs.groupBy { it.playlistId }
        val ytSongCounts = ytSongCountRows.associate { it.playlistId to it.songCount }
        val mappedYtPlaylists = ytPlaylistInfos
            .filter { com.unshoo.pixelmusic.data.remote.youtube.YouTubeItemFilter.isMusicPlaylist(it.title, it.id) }
            .map { ytPlaylistInfo ->
            val pId = ytPlaylistInfo.id.removePrefix("VL")
            val defaultCoverImage = ytPlaylistInfo.coverPath ?: ytPlaylistInfo.coverHref
            val savedCoverRaw = coverPrefs.getString("${pId}_coverImageUri", null) ?: coverPrefs.getString("${ytPlaylistInfo.id}_coverImageUri", null)
            val savedCoverImageUri = when (savedCoverRaw) {
                "__REMOVED__" -> null
                null -> defaultCoverImage
                else -> savedCoverRaw
            }
            val coverColor = when {
                coverPrefs.contains("${pId}_coverColorArgb") -> coverPrefs.getInt("${pId}_coverColorArgb", 0)
                coverPrefs.contains("${ytPlaylistInfo.id}_coverColorArgb") -> coverPrefs.getInt("${ytPlaylistInfo.id}_coverColorArgb", 0)
                else -> null
            }
            val coverIcon = coverPrefs.getString("${pId}_coverIconName", null) ?: coverPrefs.getString("${ytPlaylistInfo.id}_coverIconName", null)
            val coverShape = coverPrefs.getString("${pId}_coverShapeType", null) ?: coverPrefs.getString("${ytPlaylistInfo.id}_coverShapeType", null)
            val detail1 = when {
                coverPrefs.contains("${pId}_coverShapeDetail1") -> coverPrefs.getFloat("${pId}_coverShapeDetail1", 0f)
                coverPrefs.contains("${ytPlaylistInfo.id}_coverShapeDetail1") -> coverPrefs.getFloat("${ytPlaylistInfo.id}_coverShapeDetail1", 0f)
                else -> null
            }
            val detail2 = when {
                coverPrefs.contains("${pId}_coverShapeDetail2") -> coverPrefs.getFloat("${pId}_coverShapeDetail2", 0f)
                coverPrefs.contains("${ytPlaylistInfo.id}_coverShapeDetail2") -> coverPrefs.getFloat("${ytPlaylistInfo.id}_coverShapeDetail2", 0f)
                else -> null
            }
            val detail3 = when {
                coverPrefs.contains("${pId}_coverShapeDetail3") -> coverPrefs.getFloat("${pId}_coverShapeDetail3", 0f)
                coverPrefs.contains("${ytPlaylistInfo.id}_coverShapeDetail3") -> coverPrefs.getFloat("${ytPlaylistInfo.id}_coverShapeDetail3", 0f)
                else -> null
            }
            val detail4 = when {
                coverPrefs.contains("${pId}_coverShapeDetail4") -> coverPrefs.getFloat("${pId}_coverShapeDetail4", 0f)
                coverPrefs.contains("${ytPlaylistInfo.id}_coverShapeDetail4") -> coverPrefs.getFloat("${ytPlaylistInfo.id}_coverShapeDetail4", 0f)
                else -> null
            }

            val playlistTitle = if (ytPlaylistInfo.id == "_downloaded_") "Downloaded Songs" else ytPlaylistInfo.title
            val actualDownloaded = if (ytPlaylistInfo.id == "_downloaded_") downloadedSongs.filter { it.downloaded } else emptyList()
            val storedSongCount = if (ytPlaylistInfo.id == "_downloaded_") actualDownloaded.size else (ytSongCounts[pId] ?: ytSongCounts[ytPlaylistInfo.id] ?: ytPlaylistInfo.lastSyncSongCount)
            val playlistSongIds = if (ytPlaylistInfo.id == "_downloaded_") {
                actualDownloaded.map { "youtube_${it.youtubeId}" }
            } else {
                val refs = crossRefsByPlaylistId[ytPlaylistInfo.id]
                    ?: crossRefsByPlaylistId["VL$pId"]
                    ?: crossRefsByPlaylistId[pId]
                    ?: emptyList()
                refs.sortedBy { it.position }.map { "youtube_${it.songId}" }
            }
            val (cTime, mTime) = getOrCreatePlaylistTimestamps(pId, ytPlaylistInfo.lastSyncTimestamp, playlistTitle, playlistSongIds)

            Playlist(
                id = pId,
                name = playlistTitle,
                songIds = playlistSongIds,
                createdAt = cTime,
                lastModified = mTime,
                isAiGenerated = false,
                isQueueGenerated = false,
                coverImageUri = savedCoverImageUri ?: defaultCoverImage,
                coverColorArgb = coverColor,
                coverIconName = coverIcon,
                coverShapeType = coverShape,
                coverShapeDetail1 = detail1,
                coverShapeDetail2 = detail2,
                coverShapeDetail3 = detail3,
                coverShapeDetail4 = detail4,
                source = "YOUTUBE",
                displaySongCount = storedSongCount.takeIf { it > 0 }
            )
        }
        val ytIds = mappedYtPlaylists.map { it.id }.toSet()
        val localWithPins = localPlaylists
            .filterNot { it.source == "YOUTUBE" && it.id in ytIds }
            .map { it.copy(isPinned = pinnedIds.contains(it.id)) }
        val ytWithPins = mappedYtPlaylists.map { it.copy(isPinned = pinnedIds.contains(it.id)) }
        (localWithPins + ytWithPins).distinctBy { it.id }
    }

    val playlistSongOrderModesFlow: Flow<Map<String, String>> =
        userPreferencesRepository.playlistSongOrderModesFlow
    val playlistsSortOptionFlow: Flow<String> = userPreferencesRepository.playlistsSortOptionFlow
    val showTelegramCloudPlaylistsFlow: Flow<Boolean> =
        userPreferencesRepository.showTelegramCloudPlaylistsFlow
    val telegramTopicDisplayModeFlow: Flow<TelegramTopicDisplayMode> =
        userPreferencesRepository.telegramTopicDisplayModeFlow
    suspend fun setTelegramTopicDisplayMode(mode: TelegramTopicDisplayMode) =
        userPreferencesRepository.setTelegramTopicDisplayMode(mode)

    suspend fun createPlaylist(
        name: String,
        songIds: List<String> = emptyList(),
        isAiGenerated: Boolean = false,
        isQueueGenerated: Boolean = false,
        coverImageUri: String? = null,
        coverColorArgb: Int? = null,
        coverIconName: String? = null,
        coverShapeType: String? = null,
        coverShapeDetail1: Float? = null,
        coverShapeDetail2: Float? = null,
        coverShapeDetail3: Float? = null,
        coverShapeDetail4: Float? = null,
        customId: String? = null,
        source: String = "LOCAL"
    ): Playlist {
        ensureMigratedIfNeeded()
        val now = System.currentTimeMillis()
        val normalizedId = if (source == "YOUTUBE" && customId != null) customId.removePrefix("VL") else customId
        val newPlaylist = Playlist(
            id = normalizedId ?: UUID.randomUUID().toString(),
            name = name,
            songIds = songIds,
            createdAt = now,
            lastModified = now,
            isAiGenerated = isAiGenerated,
            isQueueGenerated = isQueueGenerated,
            coverImageUri = coverImageUri,
            coverColorArgb = coverColorArgb,
            coverIconName = coverIconName,
            coverShapeType = coverShapeType,
            coverShapeDetail1 = coverShapeDetail1,
            coverShapeDetail2 = coverShapeDetail2,
            coverShapeDetail3 = coverShapeDetail3,
            coverShapeDetail4 = coverShapeDetail4,
            source = source,
        )
        localPlaylistDao.upsertPlaylist(newPlaylist.toEntity())
        localPlaylistDao.replacePlaylistSongs(newPlaylist.id, newPlaylist.songIds)
        return newPlaylist
    }

    suspend fun deletePlaylist(playlistId: String) {
        ensureMigratedIfNeeded()
        val normalizedId = playlistId.removePrefix("VL")
        val ytDb = AppDatabase.getInstance(context)
        val ytPlaylist = ytDb.playlistRepository().getPlaylistById(normalizedId) ?: ytDb.playlistRepository().getPlaylistById(playlistId)
        if (ytPlaylist != null) {
            DownloadRepository(context).deletePlaylist(ytPlaylist)
        }
        ytDb.playlistRepository().deleteFullPlaylist(normalizedId)
        ytDb.playlistRepository().deleteFullPlaylist(playlistId)

        localPlaylistDao.deletePlaylist(normalizedId)
        localPlaylistDao.deletePlaylist(playlistId)
        localPlaylistDao.clearPlaylistSongs(normalizedId)
        localPlaylistDao.clearPlaylistSongs(playlistId)
        clearPlaylistSongOrderMode(normalizedId)
        clearPlaylistSongOrderMode(playlistId)
        coverPrefs.edit().apply {
            remove("${normalizedId}_coverImageUri")
            remove("${playlistId}_coverImageUri")
            remove("${normalizedId}_coverColorArgb")
            remove("${playlistId}_coverColorArgb")
            remove("${normalizedId}_coverIconName")
            remove("${playlistId}_coverIconName")
            remove("${normalizedId}_coverShapeType")
            remove("${playlistId}_coverShapeType")
            remove("${normalizedId}_coverShapeDetail1")
            remove("${playlistId}_coverShapeDetail1")
            remove("${normalizedId}_coverShapeDetail2")
            remove("${playlistId}_coverShapeDetail2")
            remove("${normalizedId}_coverShapeDetail3")
            remove("${playlistId}_coverShapeDetail3")
            remove("${normalizedId}_coverShapeDetail4")
            remove("${playlistId}_coverShapeDetail4")
        }.apply()
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        ensureMigratedIfNeeded()
        val ytPlaylist = AppDatabase.getInstance(context).playlistRepository().getPlaylistById(playlistId)
        if (ytPlaylist != null) {
            val updatedInfo = ytPlaylist.info.copy(title = newName)
            AppDatabase.getInstance(context).playlistRepository().insertPlaylist(updatedInfo)
        } else {
            val existing = userPlaylistsFlow.first().find { it.id == playlistId } ?: return
            val updated = existing.copy(
                name = newName,
                lastModified = System.currentTimeMillis()
            )
            localPlaylistDao.upsertPlaylist(updated.toEntity())
        }
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        ensureMigratedIfNeeded()
        val normalizedId = playlist.id.removePrefix("VL")
        if (playlist.source == "YOUTUBE") {
            val playlistRepository = AppDatabase.getInstance(context).playlistRepository()
            val ytPlaylist = playlistRepository.getPlaylistById(normalizedId) ?: playlistRepository.getPlaylistById(playlist.id)
            if (ytPlaylist != null) {
                val updatedInfo = ytPlaylist.info.copy(title = playlist.name)
                playlistRepository.insertPlaylist(updatedInfo)
                playlistRepository.deleteCrossRefsByPlaylistId(ytPlaylist.info.id)
                val refs = playlist.songIds.mapIndexed { index, songIdStr ->
                    val rawYtId = songIdStr.removePrefix("youtube_")
                    com.unshoo.pixelmusic.data.model.youtube.PlaylistSongCrossRef(ytPlaylist.info.id, rawYtId, index)
                }
                playlistRepository.insertCrossRefs(refs)
            }
            
            val existingLocal = localPlaylistDao.getPlaylistById(normalizedId) ?: localPlaylistDao.getPlaylistById(playlist.id)
            if (existingLocal != null) {
                localPlaylistDao.upsertPlaylist(existingLocal.copy(name = playlist.name))
                localPlaylistDao.replacePlaylistSongs(existingLocal.id, playlist.songIds)
            }

            coverPrefs.edit().apply {
                if (playlist.coverImageUri != null) {
                    putString("${normalizedId}_coverImageUri", playlist.coverImageUri)
                    putString("${playlist.id}_coverImageUri", playlist.coverImageUri)
                } else {
                    putString("${normalizedId}_coverImageUri", "__REMOVED__")
                    putString("${playlist.id}_coverImageUri", "__REMOVED__")
                }
                if (playlist.coverColorArgb != null) {
                    putInt("${normalizedId}_coverColorArgb", playlist.coverColorArgb)
                    putInt("${playlist.id}_coverColorArgb", playlist.coverColorArgb)
                } else {
                    remove("${normalizedId}_coverColorArgb")
                    remove("${playlist.id}_coverColorArgb")
                }
                putString("${normalizedId}_coverIconName", playlist.coverIconName)
                putString("${playlist.id}_coverIconName", playlist.coverIconName)
                putString("${normalizedId}_coverShapeType", playlist.coverShapeType)
                putString("${playlist.id}_coverShapeType", playlist.coverShapeType)
                if (playlist.coverShapeDetail1 != null) {
                    putFloat("${normalizedId}_coverShapeDetail1", playlist.coverShapeDetail1)
                    putFloat("${playlist.id}_coverShapeDetail1", playlist.coverShapeDetail1)
                } else {
                    remove("${normalizedId}_coverShapeDetail1")
                    remove("${playlist.id}_coverShapeDetail1")
                }
                if (playlist.coverShapeDetail2 != null) {
                    putFloat("${normalizedId}_coverShapeDetail2", playlist.coverShapeDetail2)
                    putFloat("${playlist.id}_coverShapeDetail2", playlist.coverShapeDetail2)
                } else {
                    remove("${normalizedId}_coverShapeDetail2")
                    remove("${playlist.id}_coverShapeDetail2")
                }
                if (playlist.coverShapeDetail3 != null) {
                    putFloat("${normalizedId}_coverShapeDetail3", playlist.coverShapeDetail3)
                    putFloat("${playlist.id}_coverShapeDetail3", playlist.coverShapeDetail3)
                } else {
                    remove("${normalizedId}_coverShapeDetail3")
                    remove("${playlist.id}_coverShapeDetail3")
                }
                if (playlist.coverShapeDetail4 != null) {
                    putFloat("${normalizedId}_coverShapeDetail4", playlist.coverShapeDetail4)
                    putFloat("${playlist.id}_coverShapeDetail4", playlist.coverShapeDetail4)
                } else {
                    remove("${normalizedId}_coverShapeDetail4")
                    remove("${playlist.id}_coverShapeDetail4")
                }
            }.apply()
        } else {
            val updated = playlist.copy(lastModified = System.currentTimeMillis())
            localPlaylistDao.upsertPlaylist(updated.toEntity())
            localPlaylistDao.replacePlaylistSongs(updated.id, updated.songIds)
        }
    }

    suspend fun addSongsToPlaylist(playlistId: String, songIdsToAdd: List<String>) {
        ensureMigratedIfNeeded()
        val normalizedId = playlistId.removePrefix("VL")
        val ytPlaylist = AppDatabase.getInstance(context).playlistRepository().getPlaylistById(normalizedId) ?: AppDatabase.getInstance(context).playlistRepository().getPlaylistById(playlistId)
        if (ytPlaylist != null) {
            val songRepository = AppDatabase.getInstance(context).songRepository()
            val playlistRepository = AppDatabase.getInstance(context).playlistRepository()
            val songEntities = songIdsToAdd.mapNotNull { songIdStr ->
                val songIdLong = songIdStr.toLongOrNull()
                if (songIdLong != null) {
                    musicDao.getSongByIdOnce(songIdLong)
                } else if (songIdStr.startsWith("youtube_")) {
                    val yId = songIdStr.removePrefix("youtube_")
                    val expectedLongId = -(15_000_000_000_000L + yId.hashCode().toLong().absoluteValue)
                    musicDao.getSongByIdOnce(expectedLongId)
                } else {
                    null
                }
            }
            val ytSongs = songEntities.map { entity ->
                val yId = entity.contentUriString.removePrefix("youtube://")
                    .takeIf { it != entity.contentUriString }
                    ?: if (entity.id < 0) {
                        entity.contentUriString.removePrefix("youtube://")
                    } else {
                        entity.id.toString()
                    }
                com.unshoo.pixelmusic.data.model.youtube.Song(
                    youtubeId = yId,
                    title = entity.title,
                    artist = entity.artistName,
                    duration = com.unshoo.pixelmusic.utils.formatDuration(entity.duration),
                    thumbnailHref = entity.albumArtUriString ?: "",
                    thumbnailPath = if (entity.filePath.isNotBlank()) entity.albumArtUriString else null,
                    audioFilePath = if (entity.filePath.isNotBlank()) entity.filePath else null
                )
            }
            if (ytSongs.isNotEmpty()) {
                songRepository.createAll(ytSongs)
                val currentSize = ytPlaylist.songs.size
                val refs = ytSongs.mapIndexed { index, song ->
                    com.unshoo.pixelmusic.data.model.youtube.PlaylistSongCrossRef(ytPlaylist.info.id, song.youtubeId, currentSize + index)
                }
                playlistRepository.insertCrossRefs(refs)
            }
        } else {
            val existing = userPlaylistsFlow.first().find { it.id == playlistId || it.id == normalizedId } ?: return
            val merged = (existing.songIds + songIdsToAdd).distinct()
            updatePlaylist(existing.copy(songIds = merged))
        }
    }

    private suspend fun getSongIdVariants(songId: String): Set<String> {
        val variants = mutableSetOf(songId)
        if (songId.startsWith("youtube_")) {
            val raw = songId.removePrefix("youtube_")
            variants.add(raw)
            val expectedLongId = -(15_000_000_000_000L + raw.hashCode().toLong().absoluteValue)
            variants.add(expectedLongId.toString())
        } else {
            val longId = songId.toLongOrNull()
            if (longId != null) {
                val songEntity = musicDao.getSongByIdOnce(longId)
                if (songEntity != null) {
                    if (songEntity.contentUriString.startsWith("youtube://")) {
                        val raw = songEntity.contentUriString.removePrefix("youtube://")
                        variants.add(raw)
                        variants.add("youtube_$raw")
                    }
                }
            } else {
                variants.add("youtube_$songId")
                val expectedLongId = -(15_000_000_000_000L + songId.hashCode().toLong().absoluteValue)
                variants.add(expectedLongId.toString())
            }
        }
        return variants
    }

    suspend fun addOrRemoveSongFromPlaylists(songId: String, playlistIds: List<String>): MutableList<String> {
        ensureMigratedIfNeeded()
        val variants = getSongIdVariants(songId)
        val currentPlaylists = userPlaylistsFlow.first()
        val removedPlaylistIds = mutableListOf<String>()

        currentPlaylists.forEach { playlist ->
            val shouldContain = playlist.id in playlistIds
            val hasSong = playlist.songIds.any { it in variants }
            when {
                shouldContain && !hasSong -> {
                    addSongsToPlaylist(playlist.id, listOf(songId))
                }
                !shouldContain && hasSong -> {
                    removeSongFromPlaylist(playlist.id, songId)
                    removedPlaylistIds.add(playlist.id)
                }
            }
        }
        return removedPlaylistIds
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songIdToRemove: String) {
        ensureMigratedIfNeeded()
        val normalizedId = playlistId.removePrefix("VL")
        val variants = getSongIdVariants(songIdToRemove)
        val ytPlaylist = AppDatabase.getInstance(context).playlistRepository().getPlaylistById(normalizedId) ?: AppDatabase.getInstance(context).playlistRepository().getPlaylistById(playlistId)
        if (ytPlaylist != null) {
            val rawYtId = variants.find { !it.startsWith("youtube_") && it.toLongOrNull() == null }
                ?: songIdToRemove.removePrefix("youtube_")
            if (ytPlaylist.info.id == "_downloaded_") {
                DownloadRepository(context).deleteSong(rawYtId)
            } else {
                AppDatabase.getInstance(context).playlistRepository().deleteCrossRef(ytPlaylist.info.id, rawYtId)
            }
        } else {
            val existing = userPlaylistsFlow.first().find { it.id == playlistId || it.id == normalizedId } ?: return
            updatePlaylist(existing.copy(songIds = existing.songIds.filterNot { it in variants }))
        }
    }

    suspend fun reorderSongsInPlaylist(playlistId: String, newSongOrderIds: List<String>) {
        ensureMigratedIfNeeded()
        val existing = userPlaylistsFlow.first().find { it.id == playlistId } ?: return
        updatePlaylist(existing.copy(songIds = newSongOrderIds))
    }

    suspend fun setPlaylistSongOrderMode(playlistId: String, modeValue: String) =
        userPreferencesRepository.setPlaylistSongOrderMode(playlistId, modeValue)

    suspend fun clearPlaylistSongOrderMode(playlistId: String) =
        userPreferencesRepository.clearPlaylistSongOrderMode(playlistId)

    suspend fun setPlaylistSongOrderModes(modes: Map<String, String>) =
        userPreferencesRepository.setPlaylistSongOrderModes(modes)

    suspend fun setPlaylistsSortOption(optionKey: String) =
        userPreferencesRepository.setPlaylistsSortOption(optionKey)

    suspend fun setShowTelegramCloudPlaylists(show: Boolean) =
        userPreferencesRepository.setShowTelegramCloudPlaylists(show)

    suspend fun getPlaylistsOnce(): List<Playlist> {
        ensureMigratedIfNeeded()
        return userPlaylistsFlow.first()
    }

    suspend fun replaceAllPlaylists(playlists: List<Playlist>) {
        ensureMigratedIfNeeded()
        localPlaylistDao.replaceAllPlaylistsTransactional(
            playlists.map { playlist -> playlist.toEntity() to playlist.songIds }
        )
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    suspend fun removeSongFromAllPlaylists(songId: String) {
        ensureMigratedIfNeeded()
        val playlists = userPlaylistsFlow.first()
        playlists.forEach { playlist ->
            if (songId in playlist.songIds) {
                if (playlist.source == "YOUTUBE") {
                    removeSongFromPlaylist(playlist.id, songId)
                } else {
                    updatePlaylist(
                        playlist.copy(
                            songIds = playlist.songIds.filterNot { it == songId }
                        )
                    )
                }
            }
        }
    }

    suspend fun resetPlaylistPreferencesToDefaults() {
        setPlaylistSongOrderModes(emptyMap())
        setPlaylistsSortOption(SortOption.PlaylistNameAZ.storageKey)
    }

    private suspend fun ensureMigratedIfNeeded() {
        if (migrationChecked) return
        migrationMutex.withLock {
            if (migrationChecked) return
            val roomCount = localPlaylistDao.getPlaylistCount()
            if (roomCount == 0) {
                val legacy = userPreferencesRepository.getLegacyUserPlaylistsOnce()
                legacy.forEach { playlist ->
                    localPlaylistDao.upsertPlaylist(playlist.toEntity())
                    localPlaylistDao.replacePlaylistSongs(playlist.id, playlist.songIds)
                }
                if (legacy.isNotEmpty()) {
                    userPreferencesRepository.clearLegacyUserPlaylists()
                }
            }
            migrationChecked = true
        }
    }

    suspend fun pruneExpiredPlaylists() {
        try {
            val period = userPreferencesRepository.generatedPlaylistsRetentionPeriodFlow.first()
            if (period == "permanent") return

            val maxAgeMs = when (period) {
                "24_hours" -> 24L * 60 * 60 * 1000
                "7_days" -> 7L * 24 * 60 * 60 * 1000
                "30_days" -> 30L * 24 * 60 * 60 * 1000
                else -> return
            }

            val threshold = System.currentTimeMillis() - maxAgeMs
            val playlists = getPlaylistsOnce()
            playlists.forEach { playlist ->
                if (playlist.isAiGenerated && playlist.lastModified < threshold) {
                    deletePlaylist(playlist.id)
                    Timber.d("Pruned expired AI playlist: ${playlist.name} (last modified: ${playlist.lastModified})")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error pruning expired playlists")
        }
    }
}
