package com.example.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FocusFlowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FocusFlowRepository
    
    // Core database flows
    val tasks = MutableStateFlow<List<Task>>(emptyList())
    val habits = MutableStateFlow<List<Habit>>(emptyList())
    val focusSessions = MutableStateFlow<List<FocusSession>>(emptyList())

    // UI state states
    var aiPlanLoading by mutableStateOf(false)
        private set
    var aiPlanResult by mutableStateOf<String?>(null)
        private set

    // Timer active states
    var isTimerRunning by mutableStateOf(false)
        private set
    var timerSecondsRemaining by mutableStateOf(1500)
        private set
    var defaultFocusDurationMinutes by mutableStateOf(25)
        private set
    var activeTimerTaskName by mutableStateOf("Strategic Design")
        private set
    var activeTimerTimeTotal by mutableStateOf(1500)
        private set
    var isBreakMode by mutableStateOf(false)
        private set

    // Persistent User Profile State variables via SharedPreferences
    private val sharedPrefs = application.getSharedPreferences("FocusFlowProfilePrefs", android.content.Context.MODE_PRIVATE)

    // Simple user configuration variables with backing persistent fields
    var breakDurationMinutes: Int
        get() = _breakDurationMinutesState.value
        set(value) {
            _breakDurationMinutesState.value = value
            sharedPrefs.edit().putInt("break_duration_minutes", value).apply()
        }
    private val _breakDurationMinutesState = mutableStateOf(sharedPrefs.getInt("break_duration_minutes", 5))

    var isBlockDistractingSitesEnabled: Boolean
        get() = _isBlockDistractingSitesEnabledState.value
        set(value) {
            _isBlockDistractingSitesEnabledState.value = value
            sharedPrefs.edit().putBoolean("is_block_distracting_sites", value).apply()
        }
    private val _isBlockDistractingSitesEnabledState = mutableStateOf(sharedPrefs.getBoolean("is_block_distracting_sites", true))

    var aiCoachTone: String
        get() = _aiCoachToneState.value
        set(value) {
            _aiCoachToneState.value = value
            sharedPrefs.edit().putString("ai_coach_tone", value).apply()
        }
    private val _aiCoachToneState = mutableStateOf(sharedPrefs.getString("ai_coach_tone", "Gentle") ?: "Gentle")

    var isDarkThemeEnabled: Boolean
        get() = _isDarkThemeEnabledState.value
        set(value) {
            _isDarkThemeEnabledState.value = value
            sharedPrefs.edit().putBoolean("is_dark_theme_enabled", value).apply()
        }
    private val _isDarkThemeEnabledState = mutableStateOf(sharedPrefs.getBoolean("is_dark_theme_enabled", false))

    var isFocusReminderEnabled by mutableStateOf(sharedPrefs.getBoolean("is_focus_reminder_enabled", true))
        private set
    var isHabitReminderEnabled by mutableStateOf(sharedPrefs.getBoolean("is_habit_reminder_enabled", true))
        private set
    var isQuietHoursEnabled by mutableStateOf(sharedPrefs.getBoolean("is_quiet_hours_enabled", true))
        private set
    var quietHoursStart by mutableStateOf(sharedPrefs.getString("quiet_hours_start", "22:00") ?: "22:00")
        private set
    var quietHoursEnd by mutableStateOf(sharedPrefs.getString("quiet_hours_end", "08:00") ?: "08:00")
        private set

    fun toggleFocusReminders(enabled: Boolean) {
        isFocusReminderEnabled = enabled
        sharedPrefs.edit().putBoolean("is_focus_reminder_enabled", enabled).apply()
    }

    fun toggleHabitReminders(enabled: Boolean) {
        isHabitReminderEnabled = enabled
        sharedPrefs.edit().putBoolean("is_habit_reminder_enabled", enabled).apply()
    }

    fun toggleQuietHours(enabled: Boolean) {
        isQuietHoursEnabled = enabled
        sharedPrefs.edit().putBoolean("is_quiet_hours_enabled", enabled).apply()
    }

    fun updateQuietHours(start: String, end: String) {
        quietHoursStart = start
        quietHoursEnd = end
        sharedPrefs.edit().putString("quiet_hours_start", start).putString("quiet_hours_end", end).apply()
    }

    var isOnboardingCompleted by mutableStateOf(sharedPrefs.getBoolean("is_onboarding_completed", false))
        private set

    var profileName by mutableStateOf(sharedPrefs.getString("profile_name", "") ?: "")
        private set
    var profileAge by mutableStateOf(sharedPrefs.getString("profile_age", "") ?: "")
        private set
    var profileHeight by mutableStateOf(sharedPrefs.getString("profile_height", "") ?: "")
        private set
    var profileWeight by mutableStateOf(sharedPrefs.getString("profile_weight", "") ?: "")
        private set
    var profileLifestyle by mutableStateOf(sharedPrefs.getString("profile_lifestyle", "Mindful Creator") ?: "Mindful Creator")
        private set
    var profileFocusDuration by mutableStateOf(sharedPrefs.getInt("profile_focus_duration", 25))
        private set
    var profileActiveHours by mutableStateOf(sharedPrefs.getString("profile_active_hours", "08:00 AM - 10:00 PM") ?: "08:00 AM - 10:00 PM")
        private set
    var profileGoalNote by mutableStateOf(sharedPrefs.getString("profile_goal_note", "") ?: "")
        private set
    var profilePictureUri by mutableStateOf(sharedPrefs.getString("profile_picture_uri", null))
        private set

    fun completeOnboarding() {
        isOnboardingCompleted = true
        sharedPrefs.edit().putBoolean("is_onboarding_completed", true).apply()
    }

    fun resetOnboarding() {
        isOnboardingCompleted = false
        sharedPrefs.edit().putBoolean("is_onboarding_completed", false).apply()
    }

    fun saveProfile(
        name: String,
        age: String,
        height: String,
        weight: String,
        lifestyle: String,
        focusDuration: Int,
        activeHours: String,
        goalNote: String,
        pictureUri: String?
    ) {
        profileName = name.trim()
        profileAge = age.trim()
        profileHeight = height.trim()
        profileWeight = weight.trim()
        profileLifestyle = lifestyle
        profileFocusDuration = focusDuration
        profileActiveHours = activeHours.trim()
        profileGoalNote = goalNote.trim()

        var finalPictureUri = pictureUri
        val context = getApplication<Application>()
        if (pictureUri != null && pictureUri.startsWith("content://")) {
            try {
                val srcUri = android.net.Uri.parse(pictureUri)
                val inputStream = context.contentResolver.openInputStream(srcUri)
                if (inputStream != null) {
                    val file = java.io.File(context.filesDir, "user_profile_pic.jpg")
                    val outputStream = java.io.FileOutputStream(file)
                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    finalPictureUri = android.net.Uri.fromFile(file).toString()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (pictureUri == null) {
            try {
                val file = java.io.File(context.filesDir, "user_profile_pic.jpg")
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        profilePictureUri = finalPictureUri

        sharedPrefs.edit().apply {
            putString("profile_name", profileName)
            putString("profile_age", profileAge)
            putString("profile_height", profileHeight)
            putString("profile_weight", profileWeight)
            putString("profile_lifestyle", profileLifestyle)
            putInt("profile_focus_duration", profileFocusDuration)
            putString("profile_active_hours", profileActiveHours)
            putString("profile_goal_note", profileGoalNote)
            putString("profile_picture_uri", profilePictureUri)
        }.apply()

        // Sync default focus duration
        setTimerDuration(focusDuration)
    }

    init {
        TimerManager.initialize(application)
        TimerManager.setTimerDuration(application, profileFocusDuration)

        viewModelScope.launch {
            TimerManager.isTimerRunning.collect { isTimerRunning = it }
        }
        viewModelScope.launch {
            TimerManager.timerSecondsRemaining.collect { timerSecondsRemaining = it }
        }
        viewModelScope.launch {
            TimerManager.defaultFocusDurationMinutes.collect { defaultFocusDurationMinutes = it }
        }
        viewModelScope.launch {
            TimerManager.activeTimerTaskName.collect { activeTimerTaskName = it }
        }
        viewModelScope.launch {
            TimerManager.activeTimerTimeTotal.collect { activeTimerTimeTotal = it }
        }
        viewModelScope.launch {
            TimerManager.isBreakMode.collect { isBreakMode = it }
        }

        val database = AppDatabase.getDatabase(application)
        repository = FocusFlowRepository(database)

        // Reactively collect database fields
        viewModelScope.launch {
            repository.tasks.collectLatest { tasks.value = it }
        }
        viewModelScope.launch {
            repository.habits.collectLatest { habits.value = it }
        }
        viewModelScope.launch {
            repository.focusSessions.collectLatest { focusSessions.value = it }
        }

        // Populate initial tasks and habits if table is completely empty (first run setup)
        viewModelScope.launch {
            val list = repository.tasks.first()
            if (list.isEmpty()) {
                repository.insertTask(Task(title = "Finalize Design System Proposal", priority = "HIGH", timeframe = "TOP_PRIORITY"))
                repository.insertTask(Task(title = "Client Onboarding Presentation", priority = "MEDIUM", timeframe = "TOP_PRIORITY"))
                repository.insertTask(Task(title = "Inbox Zero (30 mins)", priority = "LOW", timeframe = "LATER"))
                repository.insertTask(Task(title = "Read Industry Report", priority = "LOW", timeframe = "LATER"))
            }
        }
        viewModelScope.launch {
            val list = repository.habits.first()
            if (list.isEmpty()) {
                repository.insertHabit(Habit(title = "Morning Meditation", category = "mindfulness", streak = 12, completedToday = false))
                repository.insertHabit(Habit(title = "Drink 2L Water", category = "hydration", streak = 3, completedToday = false))
                repository.insertHabit(Habit(title = "Read 20 Pages", category = "reading", streak = 5, completedToday = false))
            }
        }
    }

    // ---------------- TASK OPERATIONS ----------------

    fun addTask(title: String, priority: String, timeframe: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.insertTask(
                Task(
                    title = title,
                    priority = priority,
                    timeframe = timeframe,
                    completed = false
                )
            )
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task.copy(completed = !task.completed))
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    // ---------------- HABIT OPERATIONS ----------------

    fun addHabit(title: String, category: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.insertHabit(
                Habit(
                    title = title,
                    category = category.lowercase(),
                    streak = 0,
                    completedToday = false
                )
            )
        }
    }

    fun toggleHabitCompletion(habit: Habit) {
        viewModelScope.launch {
            val newCompleted = !habit.completedToday
            val newStreak = if (newCompleted) habit.streak + 1 else maxOf(0, habit.streak - 1)
            repository.updateHabit(
                habit.copy(
                    completedToday = newCompleted,
                    streak = newStreak,
                    lastCompletedTimestamp = if (newCompleted) System.currentTimeMillis() else habit.lastCompletedTimestamp
                )
            )
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            repository.deleteHabit(habit)
        }
    }

    // ---------------- TIMER ACTIONS ----------------

    fun setTimerDuration(minutes: Int) {
        TimerManager.setTimerDuration(getApplication(), minutes)
    }

    fun startTimer(taskName: String) {
        TimerManager.startTimer(getApplication(), taskName, defaultFocusDurationMinutes, isBreak = false)
    }

    fun startBreakTimer(durationMinutes: Int) {
        TimerManager.startTimer(getApplication(), "Short Mindfulness Break", durationMinutes, isBreak = true)
    }

    fun pauseTimer() {
        TimerManager.pauseTimer(getApplication())
    }

    fun resumeTimer() {
        TimerManager.resumeTimer(getApplication())
    }

    fun endAndSaveSessionEarly() {
        TimerManager.endAndSaveSessionEarly(getApplication(), repository)
    }

    fun resetTimerState() {
        TimerManager.resetTimer(getApplication())
    }

    fun recordFocusSessionDirectly(taskTitle: String, minutes: Int) {
        viewModelScope.launch {
            repository.insertFocusSession(
                FocusSession(
                    taskTitle = taskTitle,
                    durationMinutes = minutes
                )
            )
        }
    }

    // ---------------- AI PLANNING ACTIONS ----------------

    fun suggestPlanWithAI() {
        viewModelScope.launch {
            aiPlanLoading = true
            aiPlanResult = null
            
            val currentTasks = tasks.value
            val currentHabits = habits.value
            
            val promptStr = buildString {
                append("Synthesize a realistic daily plan from the following items for a person. ")
                append("Ensure they maintain deep work and a calm mood. ")
                append("Suggest when to do what based on standard guidelines. ")
                append("Keep the response concise, formatted in 4 bullet points, without introductory chatter.\n\n")
                append("Today's Tasks:\n")
                currentTasks.forEach { append("- ${it.title} (Priority: ${it.priority}, Status: ${if (it.completed) "Done" else "Pending"})\n") }
                append("\nToday's Habits to Maintain:\n")
                currentHabits.forEach { append("- ${it.title} (${it.category})\n") }
            }

            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY") {
                try {
                    val request = MoshiGenerateRequest(
                        contents = listOf(MoshiContent(parts = listOf(MoshiPart(text = promptStr)))),
                        systemInstruction = MoshiContent(parts = listOf(MoshiPart(text = "You are FocusFlow, a friendly AI coach specialized in Luminous Calm, emotional productivity, and healthy pacing. Return your plan directly in concise bullets without preamble.")))
                    )
                    val response = GeminiClient.service.generateContent(apiKey, request)
                    val aiText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!aiText.isNullOrBlank()) {
                        aiPlanResult = aiText
                    } else {
                        aiPlanResult = buildLocalOfflinePlan(currentTasks)
                    }
                } catch (e: Exception) {
                    aiPlanResult = buildLocalOfflinePlan(currentTasks)
                } finally {
                    aiPlanLoading = false
                }
            } else {
                // Return offline generated local plan! Incredibly elegant fallback.
                delay(1000) // Simulate cognitive delay
                aiPlanResult = buildLocalOfflinePlan(currentTasks)
                aiPlanLoading = false
            }
        }
    }

    private fun buildLocalOfflinePlan(currentTasks: List<Task>): String {
        val highPriority = currentTasks.filter { it.priority == "HIGH" && !it.completed }
        val medPriority = currentTasks.filter { it.priority == "MEDIUM" && !it.completed }
        val totalPending = currentTasks.filter { !it.completed }.size

        return buildString {
            append("• **9:00 AM - 11:30 AM**: Leverage high mental energy. Focus on **")
            if (highPriority.isNotEmpty()) {
                append(highPriority.first().title)
            } else if (medPriority.isNotEmpty()) {
                append(medPriority.first().title)
            } else {
                append("Deep Work Session")
            }
            append("**. Skip email notifications during this time for optimal flow.\n")
            append("• **1:30 PM - 2:30 PM**: Direct attention toward collaborative or low-cognitive efforts. Perfect hour for administrative check-ins.\n")
            append("• **3:00 PM - 4:00 PM**: Take an active hydration break and do a 10-minute mindfulness stretch to avoid afternoon brain fatigue.\n")
            append("• **4:30 PM - Evening**: Execute your secondary objectives and outline tomorrow's three high-impact tasks. You currently have **$totalPending total pending items** to carry forward.")
        }
    }

    fun dismissAIPlan() {
        aiPlanResult = null
    }

    fun getAIEngouragement(): String {
        val lifestyle = profileLifestyle
        val task = activeTimerTaskName.ifBlank { "your current objective" }
        val greeting = if (profileName.isNotBlank()) profileName.split(" ").first() else "Companion"

        return when (lifestyle) {
            "Focused Professional" -> {
                if (aiCoachTone == "Gentle") {
                    "Hello $greeting, you're doing excellent. Let's tackle $task at a steady, sustainable pace."
                } else {
                    "Peak work block active. Safeguard your metrics by delivering undivided focus to $task right now."
                }
            }
            "Student Pacing" -> {
                if (aiCoachTone == "Gentle") {
                    "Great work, $greeting. Take this study block step-by-step; consistency is the key to mastery."
                } else {
                    "Study focus engaged. Minimize all browser tabs and focus fully on digesting $task."
                }
            }
            "Mindful Creator" -> {
                if (aiCoachTone == "Gentle") {
                    "Create gracefully, $greeting. There is no rush; fluid intervals keep your creative spark alive."
                } else {
                    "Inspiration thrives on structure. Dedicate this block entirely to $task with serene intent."
                }
            }
            else -> { // Balanced Elite
                if (aiCoachTone == "Gentle") {
                    "Stay present, $greeting. Remember to keep a balanced posture and take deep breaths while completing $task."
                } else {
                    "Balanced elite ritual. Guard this active interval with maximum presence for $task."
                }
            }
        }
    }
}
