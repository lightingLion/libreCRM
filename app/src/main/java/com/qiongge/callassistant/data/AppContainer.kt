package com.qiongge.callassistant.data

import android.content.Context
import androidx.room.Room
import com.qiongge.callassistant.data.local.AppDatabase
import com.qiongge.callassistant.data.preferences.UserPreferencesRepository
import com.qiongge.callassistant.data.repository.LocalOcrImportRepository
import com.qiongge.callassistant.data.repository.LocalDataManagementRepository
import com.qiongge.callassistant.data.repository.RoomCustomerRepository
import com.qiongge.callassistant.data.repository.RoomTaskRepository
import com.qiongge.callassistant.ui.MainViewModelFactory

class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "qiongge-assistant.db",
        ).addMigrations(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
        ).build()
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(applicationContext)
    }

    val customerRepository: RoomCustomerRepository by lazy {
        RoomCustomerRepository(database.customerDao())
    }

    val taskRepository: RoomTaskRepository by lazy {
        RoomTaskRepository(database.taskDao())
    }

    val localOcrImportRepository: LocalOcrImportRepository by lazy {
        LocalOcrImportRepository(applicationContext, database)
    }

    val localDataManagementRepository: LocalDataManagementRepository by lazy {
        LocalDataManagementRepository(applicationContext, database)
    }

    val mainViewModelFactory: MainViewModelFactory by lazy {
        MainViewModelFactory(
            preferencesRepository = userPreferencesRepository,
            customerRepository = customerRepository,
            taskRepository = taskRepository,
            localOcrImportRepository = localOcrImportRepository,
            localDataManagementRepository = localDataManagementRepository,
        )
    }
}
