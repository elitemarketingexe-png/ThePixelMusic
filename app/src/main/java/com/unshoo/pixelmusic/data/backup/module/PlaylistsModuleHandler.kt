package com.unshoo.pixelmusic.data.backup.module

import kotlin.math.absoluteValue
import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.data.backup.model.BackupSection
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.SongSummary
import com.unshoo.pixelmusic.data.preferences.PlaylistPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.PreferenceBackupEntry
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.di.BackupGson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistsModuleHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicDao: MusicDao,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.PLAYLISTS

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        val allPlaylists = playlistPreferencesRepository.getPlaylistsOnce()

        // Only export local/AI/Spotify playlists — cloud playlists (Telegram, Netease, QQMusic)
        // are tied to service auth and would be empty on restore
        val playlists = allPlaylists.filter { it.source in LOCAL_SOURCES }

        // Build a set of cloud song IDs to exclude from backup
        val cloudSongIds = buildCloudSongIdSet()

        // Get metadata for local/YouTube songs so we can match them on restore
        val allLocalSummaries = musicDao.getAllSongsList()
        val summaryById = mutableMapOf<String, com.unshoo.pixelmusic.data.database.SongEntity>()
        allLocalSummaries.forEach { summary ->
            summaryById[summary.id.toString()] = summary
            if (summary.sourceType == com.unshoo.pixelmusic.data.database.SourceType.YOUTUBE ||
                summary.contentUriString.startsWith("youtube://")
            ) {
                val videoId = summary.contentUriString.substringAfter("youtube://")
                if (videoId.isNotBlank()) {
                    summaryById["youtube_$videoId"] = summary
                    summaryById[videoId] = summary
                }
            }
        }

        // Filter cloud songs out of playlists and collect metadata
        val songMetadata = mutableMapOf<String, SongMetadataEntry>()
        val filteredPlaylists = playlists.map { playlist ->
            val localSongIds = playlist.songIds.filter { id -> id !in cloudSongIds }
            // Collect metadata for matched local/YouTube songs
            localSongIds.forEach { id ->
                if (id !in songMetadata) {
                    val summary = summaryById[id] ?: run {
                        if (id.startsWith("youtube_")) {
                            val vId = id.removePrefix("youtube_")
                            val unifiedLong = runCatching { com.unshoo.pixelmusic.utils.YouTubeIdUtils.toUnifiedYoutubeSongId(vId).toString() }.getOrNull()
                            if (unifiedLong != null) summaryById[unifiedLong] else null
                        } else null
                    }
                    if (summary != null) {
                        val videoId = summary.contentUriString.removePrefix("youtube://").takeIf { summary.contentUriString.startsWith("youtube://") }
                            ?: id.removePrefix("youtube_").takeIf { id.startsWith("youtube_") }
                        songMetadata[id] = SongMetadataEntry(
                            title = summary.title,
                            artist = summary.artistName,
                            album = summary.albumName,
                            duration = summary.duration,
                            youtubeId = videoId,
                            contentUriString = summary.contentUriString,
                            albumArtUriString = summary.albumArtUriString,
                            path = summary.filePath,
                            sourceType = summary.sourceType
                        )
                    } else if (id.startsWith("youtube_")) {
                        val videoId = id.removePrefix("youtube_")
                        songMetadata[id] = SongMetadataEntry(
                            title = "",
                            artist = "",
                            album = "",
                            duration = 0L,
                            youtubeId = videoId,
                            contentUriString = "youtube://$videoId",
                            sourceType = com.unshoo.pixelmusic.data.database.SourceType.YOUTUBE
                        )
                    }
                }
            }
            playlist.copy(songIds = localSongIds)
        }

        // Encode cover images as Base64
        val coverImages = mutableMapOf<String, String>()
        filteredPlaylists.forEach { playlist ->
            val uri = playlist.coverImageUri ?: return@forEach
            readFileAsBase64(uri)?.let { coverImages[playlist.id] = it }
        }

        val likedAlbumIds = userPreferencesRepository.likedAlbumIdsFlow.first()
        val allAlbums = musicDao.getAllAlbumsList()
        val likedAlbums = if (likedAlbumIds.isNotEmpty()) {
            allAlbums.filter { album ->
                val browseId = com.unshoo.pixelmusic.presentation.viewmodel.AlbumIdMapper.getBrowseId(context, album.id)
                browseId in likedAlbumIds || album.id.toString() in likedAlbumIds
            }.map { album ->
                LikedAlbumBackupEntry(
                    id = album.id,
                    title = album.title,
                    artistName = album.artistName,
                    artistId = album.artistId,
                    albumArtUriString = album.albumArtUriString,
                    songCount = album.songCount,
                    dateAdded = album.dateAdded,
                    year = album.year,
                    albumArtist = album.albumArtist,
                    browseId = com.unshoo.pixelmusic.presentation.viewmodel.AlbumIdMapper.getBrowseId(context, album.id)
                )
            }
        } else {
            emptyList()
        }

        val payload = PlaylistsBackupPayload(
            playlists = filteredPlaylists,
            playlistSongOrderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first(),
            playlistsSortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first(),
            songMetadata = songMetadata.ifEmpty { null },
            coverImages = coverImages.ifEmpty { null },
            likedAlbums = likedAlbums.ifEmpty { null },
            likedAlbumIds = likedAlbumIds.ifEmpty { null }
        )
        gson.toJson(payload)
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        val playlists = playlistPreferencesRepository.getPlaylistsOnce()
            .count { it.source in LOCAL_SOURCES }
        val orderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first()
        val sortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first()
        val likedCount = userPreferencesRepository.likedAlbumIdsFlow.first().size
        playlists + orderModes.size + (if (sortOption.isNotBlank()) 1 else 0) + likedCount
    }

    override suspend fun snapshot(): String = withContext(Dispatchers.IO) {
        // Snapshot captures the current state as-is (including cloud songs) for rollback
        val payload = PlaylistsBackupPayload(
            playlists = playlistPreferencesRepository.getPlaylistsOnce(),
            playlistSongOrderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first(),
            playlistsSortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first()
        )
        gson.toJson(payload)
    }

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val element = JsonParser.parseString(payload)
        if (element.isJsonArray) {
            restoreLegacyPreferenceEntries(payload)
            return@withContext
        }

        val parsed = runCatching {
            gson.fromJson(payload, PlaylistsBackupPayload::class.java)
        }.getOrNull() ?: PlaylistsBackupPayload()

        val backupPlaylists = parsed.playlists.orEmpty()
        val songMetadata = parsed.songMetadata
        val coverImages = parsed.coverImages

        // Check for missing YouTube songs and incrementally restore their skeleton entities
        if (songMetadata != null && songMetadata.isNotEmpty()) {
            val localSongs = musicDao.getAllSongsList()
            val currentSongKeys = mutableSetOf<String>()
            localSongs.forEach { entity ->
                currentSongKeys.add(entity.id.toString())
                if (entity.contentUriString.startsWith("youtube://")) {
                    val vId = entity.contentUriString.substringAfter("youtube://")
                    if (vId.isNotBlank()) {
                        currentSongKeys.add("youtube_$vId")
                        currentSongKeys.add(vId)
                    }
                }
            }
            
            val songsToInsert = mutableListOf<com.unshoo.pixelmusic.data.database.SongEntity>()
            val albumsToInsert = mutableListOf<com.unshoo.pixelmusic.data.database.AlbumEntity>()
            val artistsToInsert = mutableListOf<com.unshoo.pixelmusic.data.database.ArtistEntity>()
            val crossRefsToInsert = mutableListOf<com.unshoo.pixelmusic.data.database.SongArtistCrossRef>()
            
            songMetadata.forEach { (songIdStr, entry) ->
                val isYoutube = entry.sourceType == com.unshoo.pixelmusic.data.database.SourceType.YOUTUBE || 
                    entry.youtubeId != null || 
                    entry.contentUriString?.startsWith("youtube://") == true ||
                    songIdStr.startsWith("youtube_")
                val videoId = entry.youtubeId 
                    ?: entry.contentUriString?.substringAfter("youtube://")
                    ?: if (songIdStr.startsWith("youtube_")) songIdStr.removePrefix("youtube_") else null

                if (isYoutube && !videoId.isNullOrBlank()) {
                    val unifiedLongId = com.unshoo.pixelmusic.utils.YouTubeIdUtils.toUnifiedYoutubeSongId(videoId)
                    val songId = songIdStr.toLongOrNull() ?: unifiedLongId

                    val existsInDb = currentSongKeys.contains(songIdStr) ||
                        currentSongKeys.contains(songId.toString()) ||
                        currentSongKeys.contains("youtube_$videoId") ||
                        currentSongKeys.contains(videoId)

                    if (!existsInDb) {
                        val albumName = entry.album.ifBlank { "YouTube Music" }
                        val albumId = -(16_000_000_000_000L + albumName.lowercase().hashCode().toLong().absoluteValue)
                        
                        val artistNames = com.unshoo.pixelmusic.data.stream.CloudMusicUtils.parseArtistNames(entry.artist)
                        val primaryArtistName = artistNames.firstOrNull() ?: "Unknown Artist"
                        val primaryArtistId = -(17_000_000_000_000L + primaryArtistName.lowercase().hashCode().toLong().absoluteValue)
                        
                        artistNames.forEachIndexed { index, name ->
                            val artistId = -(17_000_000_000_000L + name.lowercase().hashCode().toLong().absoluteValue)
                            artistsToInsert.add(
                                com.unshoo.pixelmusic.data.database.ArtistEntity(
                                    id = artistId,
                                    name = name,
                                    trackCount = 1,
                                    imageUrl = null
                                )
                            )
                            crossRefsToInsert.add(
                                com.unshoo.pixelmusic.data.database.SongArtistCrossRef(
                                    songId = songId,
                                    artistId = artistId,
                                    isPrimary = index == 0
                                )
                            )
                        }
                        
                        albumsToInsert.add(
                            com.unshoo.pixelmusic.data.database.AlbumEntity(
                                id = albumId,
                                title = albumName,
                                artistName = primaryArtistName,
                                artistId = primaryArtistId,
                                songCount = 1,
                                dateAdded = System.currentTimeMillis(),
                                year = 0,
                                albumArtUriString = entry.albumArtUriString
                            )
                        )
                        
                        val artistRefs = artistNames.mapIndexed { idx, name ->
                            com.unshoo.pixelmusic.data.model.ArtistRef(
                                id = -(17_000_000_000_000L + name.lowercase().hashCode().toLong().absoluteValue),
                                name = name,
                                isPrimary = idx == 0
                            )
                        }
                        val artistsJson = com.unshoo.pixelmusic.data.database.serializeArtistRefs(artistRefs)
                        
                        songsToInsert.add(
                            com.unshoo.pixelmusic.data.database.SongEntity(
                                id = songId,
                                title = entry.title.ifBlank { "YouTube Track" },
                                artistName = entry.artist.ifBlank { "Unknown Artist" },
                                artistId = primaryArtistId,
                                albumArtist = null,
                                albumName = albumName,
                                albumId = albumId,
                                contentUriString = entry.contentUriString ?: "youtube://$videoId",
                                albumArtUriString = entry.albumArtUriString,
                                duration = entry.duration,
                                genre = "YouTube",
                                filePath = entry.path ?: "",
                                parentDirectoryPath = "/Cloud/YouTube",
                                isFavorite = false,
                                lyrics = null,
                                trackNumber = 0,
                                discNumber = null,
                                year = 0,
                                dateAdded = System.currentTimeMillis(),
                                mimeType = "audio/opus",
                                bitrate = null,
                                sampleRate = null,
                                telegramChatId = null,
                                telegramFileId = null,
                                artistsJson = artistsJson,
                                sourceType = com.unshoo.pixelmusic.data.database.SourceType.YOUTUBE
                            )
                        )
                    }
                }
            }
            
            if (songsToInsert.isNotEmpty()) {
                musicDao.incrementalSyncMusicData(
                    songs = songsToInsert,
                    albums = albumsToInsert.distinctBy { it.id },
                    artists = artistsToInsert.distinctBy { it.id },
                    crossRefs = crossRefsToInsert,
                    deletedSongIds = emptyList()
                )
            }
        }

        // Resolve song IDs against the current device library
        val resolvedPlaylists = if (songMetadata != null && songMetadata.isNotEmpty()) {
            resolvePlaylists(backupPlaylists, songMetadata)
        } else {
            // No metadata available (legacy backup or snapshot rollback) — keep IDs as-is
            backupPlaylists
        }

        // Restore cover images and update playlist URIs
        val finalPlaylists = if (coverImages != null && coverImages.isNotEmpty()) {
            restoreCoverImages(resolvedPlaylists, coverImages)
        } else {
            resolvedPlaylists
        }

        val restoredIds = finalPlaylists.map { it.id }.toSet()
        val nonRestoredPlaylists = playlistPreferencesRepository.getPlaylistsOnce()
            .filter { it.source !in LOCAL_SOURCES && it.id !in restoredIds }
        val finalPlaylistsWithOthers = nonRestoredPlaylists + finalPlaylists

        playlistPreferencesRepository.replaceAllPlaylists(finalPlaylistsWithOthers)
        playlistPreferencesRepository.setPlaylistSongOrderModes(parsed.playlistSongOrderModes.orEmpty())
        playlistPreferencesRepository.setPlaylistsSortOption(
            parsed.playlistsSortOption ?: SortOption.PlaylistNameAZ.storageKey
        )

        // Restore liked albums if present
        val backupLikedAlbums = parsed.likedAlbums
        val backupLikedAlbumIds = parsed.likedAlbumIds
        if (!backupLikedAlbums.isNullOrEmpty()) {
            val albumEntities = backupLikedAlbums.map { entry ->
                entry.browseId?.let { bId ->
                    com.unshoo.pixelmusic.presentation.viewmodel.AlbumIdMapper.putMapping(context, entry.id, bId)
                }
                com.unshoo.pixelmusic.data.database.AlbumEntity(
                    id = entry.id,
                    title = entry.title,
                    artistName = entry.artistName,
                    artistId = entry.artistId,
                    albumArtUriString = entry.albumArtUriString,
                    songCount = entry.songCount,
                    dateAdded = entry.dateAdded,
                    year = entry.year,
                    albumArtist = entry.albumArtist
                )
            }
            musicDao.insertAlbums(albumEntities)
        }
        if (!backupLikedAlbumIds.isNullOrEmpty()) {
            val existingLiked = userPreferencesRepository.likedAlbumIdsFlow.first()
            userPreferencesRepository.setLikedAlbumIds(existingLiked + backupLikedAlbumIds)
        }

        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)

    // ---- Cover image helpers ----

    private fun readFileAsBase64(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cover image: $path", e)
            null
        }
    }

    private fun restoreCoverImages(
        playlists: List<Playlist>,
        coverImages: Map<String, String>
    ): List<Playlist> {
        return playlists.map { playlist ->
            val base64 = coverImages[playlist.id] ?: return@map playlist
            try {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                val fileName = "playlist_cover_${playlist.id}.jpg"
                val file = File(context.filesDir, fileName)
                file.writeBytes(bytes)
                playlist.copy(coverImageUri = file.absolutePath)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore cover image for playlist ${playlist.id}", e)
                playlist.copy(coverImageUri = null)
            }
        }
    }

    // ---- Song matching ----

    /**
     * Resolves backup song IDs to current device song IDs using metadata matching.
     *
     * Strategy:
     * 1. Direct ID match + metadata verification → confirmed
     * 2. If direct ID exists but metadata doesn't match → try metadata match (avoids false positives)
     * 3. If direct ID doesn't exist → try metadata match
     * 4. Metadata match: title + artist (case-insensitive), disambiguate with album + duration
     * 5. No confident match → song is dropped from the playlist (kept as unresolved would risk false matches)
     */
    private suspend fun resolvePlaylists(
        playlists: List<Playlist>,
        songMetadata: Map<String, SongMetadataEntry>
    ): List<Playlist> {
        val localSongs = musicDao.getAllSongsList()
        val localSummaries = localSongs.map { entity ->
            SongSummary(
                id = entity.id,
                title = entity.title,
                artistName = entity.artistName,
                albumName = entity.albumName,
                duration = entity.duration
            )
        }
        val currentSongsById = mutableMapOf<String, SongSummary>()
        localSummaries.forEach { summary ->
            currentSongsById[summary.id.toString()] = summary
        }
        localSongs.forEach { entity ->
            if (entity.contentUriString.startsWith("youtube://")) {
                val vId = entity.contentUriString.substringAfter("youtube://")
                if (vId.isNotBlank()) {
                    val summary = currentSongsById[entity.id.toString()]
                    if (summary != null) {
                        currentSongsById["youtube_$vId"] = summary
                        currentSongsById[vId] = summary
                    }
                }
            }
        }

        // Build index for metadata matching: normalized "title|artist" → list of candidates
        val metadataIndex = mutableMapOf<String, MutableList<SongSummary>>()
        localSummaries.forEach { song ->
            val key = normalizeMatchKey(song.title, song.artistName)
            metadataIndex.getOrPut(key) { mutableListOf() }.add(song)
        }

        // Build the resolution map: backup songId → resolved songId (or null if unresolved)
        val resolutionCache = mutableMapOf<String, String?>()
        var totalSongs = 0
        var resolvedCount = 0
        var unresolvedCount = 0

        playlists.forEach { playlist ->
            playlist.songIds.forEach { songId ->
                if (songId !in resolutionCache) {
                    totalSongs++
                    val resolved = resolveSongId(songId, songMetadata, currentSongsById, metadataIndex)
                    resolutionCache[songId] = resolved
                    if (resolved != null) resolvedCount++ else unresolvedCount++
                }
            }
        }

        if (unresolvedCount > 0) {
            Log.w(TAG, "Playlist restore: $resolvedCount/$totalSongs songs resolved, $unresolvedCount unresolved")
        }

        // Apply resolution to playlists, preserving YouTube and Spotify songs even if unresolved
        return playlists.map { playlist ->
            val resolvedSongIds = playlist.songIds.mapNotNull { songId ->
                resolutionCache[songId] ?: if (songId.startsWith("youtube_") || songId.startsWith("spotify_")) songId else null
            }
            playlist.copy(songIds = resolvedSongIds)
        }
    }

    private fun resolveSongId(
        backupSongId: String,
        songMetadata: Map<String, SongMetadataEntry>,
        currentSongsById: Map<String, SongSummary>,
        metadataIndex: Map<String, List<SongSummary>>
    ): String? {
        val meta = songMetadata[backupSongId]

        // 1. Try direct ID match (checking backupSongId and potential YouTube variants)
        var directMatch = currentSongsById[backupSongId]
        if (directMatch == null && backupSongId.startsWith("youtube_")) {
            val vId = backupSongId.removePrefix("youtube_")
            directMatch = currentSongsById["youtube_$vId"]
                ?: currentSongsById[vId]
                ?: currentSongsById[com.unshoo.pixelmusic.utils.YouTubeIdUtils.toUnifiedYoutubeSongId(vId).toString()]
        }

        if (directMatch != null) {
            if (meta == null) {
                // No metadata to verify — accept direct match (same-device restore)
                return directMatch.id.toString()
            }
            // For YouTube songs or matching metadata, accept direct match
            val isYt = meta.youtubeId != null || meta.contentUriString?.startsWith("youtube://") == true || backupSongId.startsWith("youtube_")
            if (isYt || metadataMatches(meta, directMatch)) {
                return directMatch.id.toString()
            }
            // Direct ID exists but is a different song — fall through to metadata matching
        }

        // 2. If it's a YouTube or Spotify song, preserve it even if no summary matched
        if (backupSongId.startsWith("youtube_") || backupSongId.startsWith("spotify_")) {
            return backupSongId
        }

        // 3. No metadata available for local song
        if (meta == null) {
            return directMatch?.id?.toString()
        }

        // If meta indicates it's YouTube, try matching by videoId
        if (meta.youtubeId != null || meta.contentUriString?.startsWith("youtube://") == true) {
            val vId = meta.youtubeId ?: meta.contentUriString?.substringAfter("youtube://")
            if (vId != null) {
                val match = currentSongsById["youtube_$vId"]
                    ?: currentSongsById[vId]
                    ?: currentSongsById[com.unshoo.pixelmusic.utils.YouTubeIdUtils.toUnifiedYoutubeSongId(vId).toString()]
                if (match != null) {
                    return match.id.toString()
                }
                return "youtube_$vId"
            }
        }

        // 4. Try metadata matching for local device songs
        val matchKey = normalizeMatchKey(meta.title, meta.artist)
        val candidates = metadataIndex[matchKey] ?: return null

        if (candidates.size == 1) {
            return candidates[0].id.toString()
        }

        // Multiple candidates — disambiguate with album and duration
        val albumMatch = candidates.filter { candidate ->
            normalizeText(candidate.albumName) == normalizeText(meta.album)
        }
        if (albumMatch.size == 1) {
            return albumMatch[0].id.toString()
        }

        // Try duration (within 2 second tolerance)
        val durationCandidates = (albumMatch.ifEmpty { candidates }).filter { candidate ->
            kotlin.math.abs(candidate.duration - meta.duration) <= DURATION_TOLERANCE_MS
        }
        if (durationCandidates.size == 1) {
            return durationCandidates[0].id.toString()
        }

        // Ambiguous — no confident match
        return null
    }

    private fun metadataMatches(meta: SongMetadataEntry, song: SongSummary): Boolean {
        return normalizeText(meta.title) == normalizeText(song.title) &&
            normalizeText(meta.artist) == normalizeText(song.artistName)
    }

    private fun normalizeMatchKey(title: String, artist: String): String {
        return "${normalizeText(title)}|${normalizeText(artist)}"
    }

    private fun normalizeText(text: String): String {
        return text.trim().lowercase()
    }

    private suspend fun buildCloudSongIdSet(): Set<String> {
        val cloudIds = mutableSetOf<String>()
        musicDao.getAllTelegramSongIds().mapTo(cloudIds) { it.toString() }
        musicDao.getAllNeteaseSongIds().mapTo(cloudIds) { it.toString() }
        musicDao.getAllGDriveSongIds().mapTo(cloudIds) { it.toString() }
        musicDao.getAllQqMusicSongIds().mapTo(cloudIds) { it.toString() }
        return cloudIds
    }

    // ---- Legacy format ----

    private suspend fun restoreLegacyPreferenceEntries(payload: String) {
        val type = TypeToken.getParameterized(List::class.java, PreferenceBackupEntry::class.java).type
        val entries: List<PreferenceBackupEntry> = gson.fromJson(payload, type)

        val playlists = entries.firstOrNull { it.key == LEGACY_USER_PLAYLISTS_KEY }
            ?.stringValue
            ?.let { raw ->
                runCatching {
                    val playlistType = TypeToken.getParameterized(List::class.java, Playlist::class.java).type
                    gson.fromJson<List<Playlist>>(raw, playlistType)
                }.getOrDefault(emptyList())
            }
            .orEmpty()

        val playlistSongOrderModes = entries.firstOrNull { it.key == LEGACY_PLAYLIST_ORDER_MODES_KEY }
            ?.stringValue
            ?.let { raw ->
                runCatching {
                    val mapType = TypeToken.getParameterized(
                        Map::class.java,
                        String::class.java,
                        String::class.java
                    ).type
                    gson.fromJson<Map<String, String>>(raw, mapType)
                }.getOrDefault(emptyMap())
            }
            .orEmpty()

        val playlistsSortOption = entries.firstOrNull { it.key == LEGACY_PLAYLIST_SORT_OPTION_KEY }
            ?.stringValue
            ?: SortOption.PlaylistNameAZ.storageKey

        playlistPreferencesRepository.replaceAllPlaylists(playlists)
        playlistPreferencesRepository.setPlaylistSongOrderModes(playlistSongOrderModes)
        playlistPreferencesRepository.setPlaylistsSortOption(playlistsSortOption)
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    // ---- Data classes ----

    /** Song metadata stored alongside playlists for cross-device matching. */
    data class SongMetadataEntry(
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val youtubeId: String? = null,
        val contentUriString: String? = null,
        val albumArtUriString: String? = null,
        val path: String? = null,
        val sourceType: Int? = null
    )

    data class LikedAlbumBackupEntry(
        val id: Long,
        val title: String,
        val artistName: String,
        val artistId: Long,
        val albumArtUriString: String?,
        val songCount: Int,
        val dateAdded: Long,
        val year: Int,
        val albumArtist: String? = null,
        val browseId: String? = null
    )

    private data class PlaylistsBackupPayload(
        val playlists: List<Playlist>? = null,
        val playlistSongOrderModes: Map<String, String>? = null,
        val playlistsSortOption: String? = null,
        /** Song metadata for cross-device matching. Key = songId from backup. Null in legacy/snapshot payloads. */
        val songMetadata: Map<String, SongMetadataEntry>? = null,
        /** Base64-encoded cover images. Key = playlist ID. Null if no custom covers. */
        val coverImages: Map<String, String>? = null,
        val likedAlbums: List<LikedAlbumBackupEntry>? = null,
        val likedAlbumIds: Set<String>? = null
    )

    companion object {
        private const val TAG = "PlaylistsModuleHandler"
        private const val DURATION_TOLERANCE_MS = 2000L

        /** Playlist sources that are backed up. Cloud-sourced playlists are excluded. */
        private val LOCAL_SOURCES = setOf("LOCAL", "AI", "SPOTIFY", "SMART", "LASTFM_MIX")

        const val LEGACY_USER_PLAYLISTS_KEY = "user_playlists_json_v1"
        const val LEGACY_PLAYLIST_ORDER_MODES_KEY = "playlist_song_order_modes"
        const val LEGACY_PLAYLIST_SORT_OPTION_KEY = "playlists_sort_option"
        val PLAYLIST_KEYS = setOf(
            LEGACY_USER_PLAYLISTS_KEY,
            LEGACY_PLAYLIST_ORDER_MODES_KEY,
            LEGACY_PLAYLIST_SORT_OPTION_KEY
        )
    }
}
