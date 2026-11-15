package com.academicstrong.nutrition.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodLogDao {
    @Insert suspend fun insert(entry: FoodLogEntry): Long
    @Insert suspend fun insertAll(entries: List<FoodLogEntry>)
    @Delete suspend fun delete(entry: FoodLogEntry)
    @Query("SELECT * FROM food_log WHERE epochDay = :day ORDER BY id")
    fun observeDay(day: Long): Flow<List<FoodLogEntry>>
    @Query("SELECT * FROM food_log WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, id")
    fun observeRange(from: Long, to: Long): Flow<List<FoodLogEntry>>
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM nutrition_profile WHERE id = 1")
    fun observe(): Flow<NutritionProfile?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(profile: NutritionProfile)
}

@Dao
interface PantryDao {
    @Query("SELECT * FROM pantry ORDER BY name") fun observeAll(): Flow<List<PantryItem>>
    @Insert suspend fun insert(item: PantryItem)
    @Delete suspend fun delete(item: PantryItem)
}

@Dao
interface MoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(m: MoodCheckIn)
    @Query("SELECT * FROM mood_checkins WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun observeRange(from: Long, to: Long): Flow<List<MoodCheckIn>>
}

@Database(
    entities = [FoodLogEntry::class, NutritionProfile::class, PantryItem::class, MoodCheckIn::class],
    version = 1, exportSchema = false
)
abstract class NutritionDatabase : RoomDatabase() {
    abstract fun foodLog(): FoodLogDao
    abstract fun profile(): ProfileDao
    abstract fun pantry(): PantryDao
    abstract fun mood(): MoodDao

    companion object {
        @Volatile private var instance: NutritionDatabase? = null
        fun get(context: Context): NutritionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, NutritionDatabase::class.java, "nutrition.db"
            ).build().also { instance = it }
        }
    }
}
