package com.unshoo.pixelmusic.data.database.youtube

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.unshoo.pixelmusic.data.remote.youtube.Constants
import com.unshoo.pixelmusic.data.model.youtube.PlaylistInfo
import com.unshoo.pixelmusic.data.model.youtube.PlaylistSongCrossRef
import com.unshoo.pixelmusic.data.model.youtube.Song
import com.unshoo.pixelmusic.data.model.youtube.Version
import java.util.concurrent.Executors

@Database(
    entities = [Song::class, PlaylistInfo::class, PlaylistSongCrossRef::class, Version::class],
    version = Constants.Database.VERSION,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songRepository(): LocalSongDataSource
    abstract fun playlistRepository(): LocalPlaylistDataSource
    abstract fun versionRepository(): VersionDataSource

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN album TEXT")
                db.execSQL("ALTER TABLE songs ADD COLUMN albumBrowseId TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        suspend fun clearDownloads(context: Context) {
            val instance = getInstance(context)

            instance.songRepository().deleteAll()
            instance.playlistRepository().deleteAll()
        }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java, Constants.Database.NAME
            )
                .addMigrations(MIGRATION_9_10)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        private val IO_EXECUTOR = Executors.newSingleThreadExecutor()
        fun ioThread(f: () -> Unit) {
            IO_EXECUTOR.execute(f)
        }
    }
}
