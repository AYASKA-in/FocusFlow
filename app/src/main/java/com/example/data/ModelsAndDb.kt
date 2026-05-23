package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ---------------- LOCAL PERSISTENT ENTITIES ----------------

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val priority: String, // "HIGH", "MEDIUM", "LOW"
    val timeframe: String, // "TOP_PRIORITY", "LATER"
    val completed: Boolean = false,
    val dateCreated: Long = System.currentTimeMillis()
)

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val category: String, // "hydration", "mindfulness", "reading", "fitness", "custom"
    val streak: Int = 0,
    val completedToday: Boolean = false,
    val lastCompletedTimestamp: Long = 0L
)

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskTitle: String,
    val durationMinutes: Int,
    val timestamp: Long = System.currentTimeMillis()
)

// ---------------- DAOs (DATA ACCESS OBJECTS) ----------------

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY dateCreated DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY id ASC")
    fun getAllHabits(): Flow<List<Habit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: Habit)

    @Update
    suspend fun updateHabit(habit: Habit)

    @Delete
    suspend fun deleteHabit(habit: Habit)
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY timestamp DESC")
    fun getAllFocusSessions(): Flow<List<FocusSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusSession(session: FocusSession)
}

// ---------------- DATABASE DEFINITION ----------------

@Database(entities = [Task::class, Habit::class, FocusSession::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract val taskDao: TaskDao
    abstract val habitDao: HabitDao
    abstract val focusSessionDao: FocusSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focusflow_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ---------------- REPOSITORY OVERVIEW ----------------

class FocusFlowRepository(private val db: AppDatabase) {
    val tasks: Flow<List<Task>> = db.taskDao.getAllTasks()
    val habits: Flow<List<Habit>> = db.habitDao.getAllHabits()
    val focusSessions: Flow<List<FocusSession>> = db.focusSessionDao.getAllFocusSessions()

    suspend fun insertTask(task: Task) = db.taskDao.insertTask(task)
    suspend fun updateTask(task: Task) = db.taskDao.updateTask(task)
    suspend fun deleteTask(task: Task) = db.taskDao.deleteTask(task)

    suspend fun insertHabit(habit: Habit) = db.habitDao.insertHabit(habit)
    suspend fun updateHabit(habit: Habit) = db.habitDao.updateHabit(habit)
    suspend fun deleteHabit(habit: Habit) = db.habitDao.deleteHabit(habit)

    suspend fun insertFocusSession(session: FocusSession) = db.focusSessionDao.insertFocusSession(session)
}
