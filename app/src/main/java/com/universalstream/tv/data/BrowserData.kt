package com.universalstream.tv.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val url: String,
    val title: String,
    val lastVisited: Long,
    val visitCount: Int,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val url: String,
    val title: String,
    val addedAt: Long,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY lastVisited DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun get(url: String): HistoryEntity?

    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query("UPDATE history SET title = :title WHERE url = :url")
    suspend fun updateTitle(url: String, title: String)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE url NOT IN (SELECT url FROM history ORDER BY lastVisited DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE url = :url)")
    fun observeIsFavorite(url: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE url = :url)")
    suspend fun isFavorite(url: String): Boolean

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE url = :url")
    suspend fun delete(url: String)
}

@Database(entities = [HistoryEntity::class, FavoriteEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "universal_stream.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

class BrowserRepository(
    private val historyDao: HistoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyLimit: Int,
) {
    fun recentHistory(limit: Int): Flow<List<HistoryEntity>> = historyDao.observeRecent(limit)

    fun favorites(): Flow<List<FavoriteEntity>> = favoriteDao.observeAll()

    fun isFavorite(url: String): Flow<Boolean> = favoriteDao.observeIsFavorite(url)

    suspend fun recordVisit(url: String, title: String?) {
        val existing = historyDao.get(url)
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: existing?.title ?: ""
        historyDao.upsert(
            HistoryEntity(
                url = url,
                title = resolvedTitle,
                lastVisited = System.currentTimeMillis(),
                visitCount = (existing?.visitCount ?: 0) + 1,
            ),
        )
        historyDao.trimTo(historyLimit)
    }

    suspend fun updateTitle(url: String, title: String) {
        if (title.isNotBlank()) historyDao.updateTitle(url, title)
    }

    suspend fun deleteHistory(url: String) = historyDao.delete(url)

    suspend fun clearHistory() = historyDao.clear()

    suspend fun addFavorite(url: String, title: String) {
        favoriteDao.upsert(FavoriteEntity(url = url, title = title, addedAt = System.currentTimeMillis()))
    }

    suspend fun removeFavorite(url: String) = favoriteDao.delete(url)

    /** Returns true when the URL is a favorite after the call. */
    suspend fun toggleFavorite(url: String, title: String): Boolean =
        if (favoriteDao.isFavorite(url)) {
            favoriteDao.delete(url)
            false
        } else {
            addFavorite(url, title)
            true
        }
}
