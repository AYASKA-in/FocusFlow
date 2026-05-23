package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import android.util.Log
import com.example.data.FocusSession
import com.example.data.FocusFlowRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object TimerManager {
    private const val TAG = "TimerManager"
    private const val PREFS_NAME = "FocusFlowTimerPrefs"

    private val timerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var timerJob: Job? = null

    // State flows for Compose observing
    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning = _isTimerRunning.asStateFlow()

    private val _timerSecondsRemaining = MutableStateFlow(1500)
    val timerSecondsRemaining = _timerSecondsRemaining.asStateFlow()

    private val _activeTimerTaskName = MutableStateFlow("Strategic Design")
    val activeTimerTaskName = _activeTimerTaskName.asStateFlow()

    private val _activeTimerTimeTotal = MutableStateFlow(1500)
    val activeTimerTimeTotal = _activeTimerTimeTotal.asStateFlow()

    private val _isBreakMode = MutableStateFlow(false)
    val isBreakMode = _isBreakMode.asStateFlow()

    private val _defaultFocusDurationMinutes = MutableStateFlow(25)
    val defaultFocusDurationMinutes = _defaultFocusDurationMinutes.asStateFlow()

    // Flag indicating a session just finished to show visual congratulations
    private val _sessionCompletedTrigger = MutableStateFlow(false)
    val sessionCompletedTrigger = _sessionCompletedTrigger.asStateFlow()

    fun resetCompletionTrigger() {
        _sessionCompletedTrigger.value = false
    }

    // Initialize state from persistent storage
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultMinutes = prefs.getInt("default_focus_duration_minutes", 25)
        _defaultFocusDurationMinutes.value = defaultMinutes

        val wasRunning = prefs.getBoolean("is_running", false)
        val savedTask = prefs.getString("task_name", "Strategic Design") ?: "Strategic Design"
        val totalSecs = prefs.getInt("total_seconds", defaultMinutes * 60)
        val remainingSecs = prefs.getInt("remaining_seconds", defaultMinutes * 60)
        val breakMode = prefs.getBoolean("is_break_mode", false)
        val lastSavedTime = prefs.getLong("last_saved_time", 0L)

        _activeTimerTaskName.value = savedTask
        _activeTimerTimeTotal.value = totalSecs
        _isBreakMode.value = breakMode

        if (wasRunning && lastSavedTime > 0L) {
            val elapsedMs = System.currentTimeMillis() - lastSavedTime
            val elapsedSecs = (elapsedMs / 1000).toInt()
            val adjustedRemaining = remainingSecs - elapsedSecs
            if (adjustedRemaining > 0) {
                _timerSecondsRemaining.value = adjustedRemaining
                // Automatically restart the background service to keep it going
                _isTimerRunning.value = true
                startTimerJob(context)
                startForegroundTimerService(context)
            } else {
                // Completed while closed
                _timerSecondsRemaining.value = 0
                _isTimerRunning.value = false
                _sessionCompletedTrigger.value = true
                triggerVibration(context)
                saveState(context)
            }
        } else {
            _timerSecondsRemaining.value = remainingSecs
            _isTimerRunning.value = wasRunning
            if (wasRunning) {
                startTimerJob(context)
            }
        }
    }

    private fun saveState(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("is_running", _isTimerRunning.value)
            putInt("remaining_seconds", _timerSecondsRemaining.value)
            putString("task_name", _activeTimerTaskName.value)
            putInt("total_seconds", _activeTimerTimeTotal.value)
            putBoolean("is_break_mode", _isBreakMode.value)
            putInt("default_focus_duration_minutes", _defaultFocusDurationMinutes.value)
            putLong("last_saved_time", System.currentTimeMillis())
        }.apply()
    }

    fun setTimerDuration(context: Context, minutes: Int) {
        _defaultFocusDurationMinutes.value = minutes
        if (!_isTimerRunning.value) {
            val seconds = minutes * 60
            _timerSecondsRemaining.value = seconds
            _activeTimerTimeTotal.value = seconds
            _isBreakMode.value = false
            saveState(context)
        }
    }

    fun startTimer(context: Context, taskName: String, durationMinutes: Int, isBreak: Boolean = false) {
        _activeTimerTaskName.value = taskName
        _isBreakMode.value = isBreak
        _isTimerRunning.value = true
        _sessionCompletedTrigger.value = false

        val seconds = durationMinutes * 60
        _timerSecondsRemaining.value = seconds
        _activeTimerTimeTotal.value = seconds

        saveState(context)
        startTimerJob(context)
        startForegroundTimerService(context)
    }

    fun resumeTimer(context: Context) {
        if (_timerSecondsRemaining.value <= 0) return
        _isTimerRunning.value = true
        saveState(context)
        startTimerJob(context)
        startForegroundTimerService(context)
    }

    fun pauseTimer(context: Context) {
        _isTimerRunning.value = false
        timerJob?.cancel()
        saveState(context)
        // Tell foreground service to update or stop
        startForegroundTimerService(context, pause = true)
    }

    fun resetTimer(context: Context) {
        _isTimerRunning.value = false
        timerJob?.cancel()
        _isBreakMode.value = false
        val defaultSecs = _defaultFocusDurationMinutes.value * 60
        _timerSecondsRemaining.value = defaultSecs
        _activeTimerTimeTotal.value = defaultSecs
        saveState(context)
        stopForegroundTimerService(context)
    }

    fun endAndSaveSessionEarly(context: Context, repository: FocusFlowRepository) {
        val elapsedSeconds = _activeTimerTimeTotal.value - _timerSecondsRemaining.value
        _isTimerRunning.value = false
        timerJob?.cancel()

        if (!_isBreakMode.value && elapsedSeconds >= 5) {
            val elapsedMinutes = maxOf(1, elapsedSeconds / 60)
            val sessionName = _activeTimerTaskName.value
            CoroutineScope(Dispatchers.IO).launch {
                repository.insertFocusSession(
                    FocusSession(
                        taskTitle = sessionName,
                        durationMinutes = elapsedMinutes
                    )
                )
            }
        }

        _isBreakMode.value = false
        val defaultSecs = _defaultFocusDurationMinutes.value * 60
        _timerSecondsRemaining.value = defaultSecs
        _activeTimerTimeTotal.value = defaultSecs
        saveState(context)
        stopForegroundTimerService(context)
    }

    private fun startTimerJob(context: Context) {
        timerJob?.cancel()
        timerJob = timerScope.launch {
            while (_timerSecondsRemaining.value > 0 && _isTimerRunning.value) {
                delay(1000)
                _timerSecondsRemaining.value -= 1
                // Let service update notification automatically
                updateForegroundServiceTick(context)
            }
            if (_timerSecondsRemaining.value <= 0 && _isTimerRunning.value) {
                // Completed!
                onTimerCompleted(context)
            }
        }
    }

    private suspend fun onTimerCompleted(context: Context) {
        _isTimerRunning.value = false
        timerJob?.cancel()
        triggerVibration(context)

        val completedBreak = _isBreakMode.value
        val completedTaskName = _activeTimerTaskName.value
        val durationMins = _activeTimerTimeTotal.value / 60

        // Handle auto-saved sessions
        if (!completedBreak) {
            // Direct injection into database
            val db = com.example.data.AppDatabase.getDatabase(context)
            withContext(Dispatchers.IO) {
                db.focusSessionDao.insertFocusSession(
                    FocusSession(
                        taskTitle = completedTaskName,
                        durationMinutes = durationMins
                    )
                )
            }
            _sessionCompletedTrigger.value = true
        }

        // Clean transition or offer break
        if (!completedBreak) {
            // Enable break mode Automatically or switch to layout
            _isBreakMode.value = true
            _timerSecondsRemaining.value = 5 * 60 // 5-minute break
            _activeTimerTimeTotal.value = 5 * 60
            _activeTimerTaskName.value = "Short Mindfulness Break"
        } else {
            // Reset to focus
            _isBreakMode.value = false
            val defaultSecs = _defaultFocusDurationMinutes.value * 60
            _timerSecondsRemaining.value = defaultSecs
            _activeTimerTimeTotal.value = defaultSecs
            _activeTimerTaskName.value = "Strategic Design"
        }

        saveState(context)
        
        // Notify service of completion state to send a fresh sound/alert
        val serviceIntent = Intent(context, FocusTimerService::class.java).apply {
            action = "COMPLETED"
        }
        context.startService(serviceIntent)
    }

    // Helper functions to interact with service
    private fun startForegroundTimerService(context: Context, pause: Boolean = false) {
        try {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                action = if (pause) "PAUSE" else "START"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground timer service", e)
        }
    }

    private fun updateForegroundServiceTick(context: Context) {
        try {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                action = "TICK"
            }
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send tick to service", e)
        }
    }

    fun stopForegroundTimerService(context: Context) {
        try {
            val intent = Intent(context, FocusTimerService::class.java)
            context.stopService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop foreground timer service", e)
        }
    }

    private fun triggerVibration(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    it.vibrate(800)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }
}
