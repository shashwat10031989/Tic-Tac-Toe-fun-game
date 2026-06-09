package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Database
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "match_records")
data class MatchRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val playerX: String,
    val playerO: String,
    val winner: String, // "X", "O", or "Draw"
    val gameMode: String, // "Local 2-Player", "Local Network", "Online Match"
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface MatchRecordDao {
    @Query("SELECT * FROM match_records ORDER BY timestamp DESC")
    fun getAllMatches(): Flow<List<MatchRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: MatchRecord)

    @Query("DELETE FROM match_records")
    suspend fun clearHistory()
}

@Database(entities = [MatchRecord::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun matchRecordDao(): MatchRecordDao
}

class MatchRepository(private val dao: MatchRecordDao) {
    val allMatches: Flow<List<MatchRecord>> = dao.getAllMatches()

    suspend fun insert(match: MatchRecord) {
        dao.insertMatch(match)
    }

    suspend fun clearAll() {
        dao.clearHistory()
    }
}
