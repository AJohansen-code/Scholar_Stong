package com.team2.studentfitness

import android.app.Application
import com.academicstrong.nutrition.ai.installAppCheck
import com.team2.studentfitness.database.Database

class DatabaseCreation : Application() {

    override fun onCreate() {
        super.onCreate()
        installAppCheck(this, BuildConfig.DEBUG) // App Check for the nutrition AI
    }

    // Creates a singleton instance of the database
    val database: Database by lazy { Database.getDatabase(this) }
}