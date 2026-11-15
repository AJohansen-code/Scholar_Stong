package com.academicstrong.nutrition.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

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

/** NutriTrack's own small database. Food, goals and mood come from the fitness app's database instead. */
@Database(entities = [NutritionProfile::class, PantryItem::class], version = 2, exportSchema = false)
abstract class NutritionDatabase : RoomDatabase() {
    abstract fun profile(): ProfileDao
    abstract fun pantry(): PantryDao

    companion object {
        @Volatile private var instance: NutritionDatabase? = null
        fun get(context: Context): NutritionDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, NutritionDatabase::class.java, "nutrition.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
