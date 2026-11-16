package com.fittrack.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

// ============================================================================
// DYNAMIC LIGHT & DARK THEME PALETTE (NO BLUE TINT!)
// ============================================================================
data class AppThemeColors(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val cardSubtle: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentGreen: Color,
    val accentOrange: Color,
    val accentPurple: Color,
    val accentBlue: Color,
    val accentRose: Color
)

fun getAppTheme(isDark: Boolean): AppThemeColors {
    return if (isDark) {
        // True Neutral Dark Mode (Obsidian & Charcoal)
        AppThemeColors(
            isDark = true,
            bg = Color(0xFF121212),
            card = Color(0xFF1E1E1E),
            cardSubtle = Color(0xFF2A2A2A),
            border = Color(0xFF333333),
            textPrimary = Color(0xFFF5F5F7),
            textSecondary = Color(0xFF9E9E9E),
            accentGreen = Color(0xFF10B981),
            accentOrange = Color(0xFFF97316),
            accentPurple = Color(0xFFA855F7),
            accentBlue = Color(0xFF38BDF8),
            accentRose = Color(0xFFF43F5E)
        )
    } else {
        // Clean Daylight Mode (Warm Alabaster & Pure White)
        AppThemeColors(
            isDark = false,
            bg = Color(0xFFF5F6F8),
            card = Color(0xFFFFFFFF),
            cardSubtle = Color(0xFFEEF0F4),
            border = Color(0xFFE2E5EC),
            textPrimary = Color(0xFF18181B),
            textSecondary = Color(0xFF64748B),
            accentGreen = Color(0xFF059669),
            accentOrange = Color(0xFFEA580C),
            accentPurple = Color(0xFF7C3AED),
            accentBlue = Color(0xFF0284C7),
            accentRose = Color(0xFFE11D48)
        )
    }
}

val LocalAppTheme = compositionLocalOf { getAppTheme(false) }

// --- DATA MODELS ---
data class WorkoutExercise(
    val id: String,
    val day: String,
    val muscleGroup: String,
    val name: String,
    val targetSets: Int,
    val targetNote: String = "3 sets",
    val isBodyweightOrTimed: Boolean = false,
    val baseCalPerSet: Int = 12,
    val visualType: String = "barbell"
)

data class ExerciseLogState(
    val setCompletedList: List<Boolean> = listOf(false, false, false),
    val setWeightsList: List<String> = listOf("2.5 kg/side", "5 kg/side", "5 kg/side"),
    val previousSetSummaryMemory: String = "None yet"
) {
    val completedSetsCount: Int get() = setCompletedList.count { it }
}

data class FoodItem(val id: Long, val name: String, val calories: Int, val date: String)

data class WorkoutHistoryEntry(
    val id: Long,
    val date: String,
    val dayName: String,
    val exerciseName: String,
    val setsDone: Int,
    val weightUsed: String,
    val caloriesBurned: Int
)

data class StepHistoryEntry(
    val date: String,
    val steps: Int,
    val goal: Int,
    val calories: Int
)

val weightPresetOptions = listOf(
    "Empty Bar" to 0f,
    "2.5 kg/side" to 5f,
    "5 kg/side" to 10f,
    "7.5 kg/side" to 15f,
    "10 kg/side" to 20f,
    "12.5 kg/side" to 25f,
    "2.5 kg DB" to 5f,
    "5 kg DB" to 10f,
    "7.5 kg DB" to 15f,
    "10 kg DB" to 20f,
    "12.5 kg DB" to 25f,
    "15 kg DB" to 30f
)

fun getKgForWeightLabel(label: String): Float {
    return weightPresetOptions.firstOrNull { it.first == label }?.second ?: 0f
}

fun calculateExerciseCalories(ex: WorkoutExercise, state: ExerciseLogState): Int {
    var total = 0f
    for (i in 0 until ex.targetSets) {
        val isDone = state.setCompletedList.getOrElse(i) { false }
        if (isDone) {
            val weightLbl = state.setWeightsList.getOrElse(i) { "5 kg/side" }
            val kg = if (ex.isBodyweightOrTimed) 0f else getKgForWeightLabel(weightLbl)
            total += (ex.baseCalPerSet + (kg * 0.45f))
        }
    }
    return total.roundToInt()
}

fun getDefaultExercises(): List<WorkoutExercise> = listOf(
    WorkoutExercise("m1", "Mon", "Biceps", "Barbell Biceps Curl", 3, "3 sets", visualType = "barbell"),
    WorkoutExercise("m2", "Mon", "Biceps", "Concentration Curl", 3, "3 sets", visualType = "dumbbell"),
    WorkoutExercise("m3", "Mon", "Biceps", "Hammer Curl", 3, "3 sets", visualType = "dumbbell"),
    WorkoutExercise("m4", "Mon", "Biceps", "Bicep Curl", 3, "3 sets", visualType = "dumbbell"),
    WorkoutExercise("m5", "Mon", "Biceps", "Seated Cable Curl", 3, "3 sets", visualType = "cable"),

    WorkoutExercise("tu1", "Tue", "Triceps", "Push Down Cable", 3, "3 sets", visualType = "cable"),
    WorkoutExercise("tu2", "Tue", "Triceps", "Push Up (Close Grip)", 3, "3 sets", isBodyweightOrTimed = true, visualType = "bodyweight"),
    WorkoutExercise("tu3", "Tue", "Triceps", "Tricep Dips", 3, "3 sets", isBodyweightOrTimed = true, visualType = "bodyweight"),
    WorkoutExercise("tu4", "Tue", "Triceps", "Overhead Tricep Extension DB", 3, "3 sets", visualType = "dumbbell"),
    WorkoutExercise("tu5", "Tue", "Triceps", "Tricep Kettlebell", 3, "3 sets", visualType = "dumbbell"),

    WorkoutExercise("w1", "Wed", "Back/Legs", "Calf Extensions", 3, "3 sets", baseCalPerSet = 14, visualType = "legs"),
    WorkoutExercise("w2", "Wed", "Back/Legs", "Barbell Squats", 4, "3-4 sets", baseCalPerSet = 22, visualType = "barbell"),
    WorkoutExercise("w3", "Wed", "Back/Legs", "Dumbbell Lunges", 3, "3 sets", baseCalPerSet = 18, visualType = "dumbbell"),
    WorkoutExercise("w7", "Wed", "Back/Legs", "Bulgarian Split Squat", 3, "3 sets", baseCalPerSet = 20, visualType = "legs"),
    WorkoutExercise("w4", "Wed", "Back/Legs", "Cable Seated Row", 3, "3 sets", baseCalPerSet = 16, visualType = "cable"),
    WorkoutExercise("w5", "Wed", "Back/Legs", "Cable Lat Pulldown", 3, "3 sets", baseCalPerSet = 16, visualType = "cable"),
    WorkoutExercise("w6", "Wed", "Back/Legs", "Landmine Rows", 3, "3 sets", baseCalPerSet = 18, visualType = "barbell"),

    WorkoutExercise("th1", "Thu", "Chest", "Pec Deck Fly", 3, "3 sets", baseCalPerSet = 14, visualType = "cable"),
    WorkoutExercise("th2", "Thu", "Chest", "Flat Barbell Bench Press", 3, "3 sets", baseCalPerSet = 18, visualType = "barbell"),
    WorkoutExercise("th3", "Thu", "Chest", "Inclined Press Dumbbell", 3, "3 sets", baseCalPerSet = 17, visualType = "dumbbell"),
    WorkoutExercise("th4", "Thu", "Chest", "Decline Press Dumbbell", 3, "3 sets", baseCalPerSet = 17, visualType = "dumbbell"),
    WorkoutExercise("th5", "Thu", "Shoulder", "Pec Deck Fly Shoulder Press", 3, "3 sets", baseCalPerSet = 15, visualType = "dumbbell"),

    WorkoutExercise("f1", "Fri", "Abs", "Crunches", 3, "3 sets", isBodyweightOrTimed = true, baseCalPerSet = 10, visualType = "bodyweight"),
    WorkoutExercise("f2", "Fri", "Abs", "Decline Crunches", 3, "3 sets", isBodyweightOrTimed = true, baseCalPerSet = 12, visualType = "bodyweight"),
    WorkoutExercise("f3", "Fri", "Abs", "Leg Raises (Set 1)", 3, "3 sets", isBodyweightOrTimed = true, baseCalPerSet = 11, visualType = "bodyweight"),
    WorkoutExercise("f4", "Fri", "Abs", "Plank", 3, "3 x 50 secs", isBodyweightOrTimed = true, baseCalPerSet = 12, visualType = "bodyweight"),
    WorkoutExercise("f5", "Fri", "Abs", "Mountain Climbers", 3, "3 x 20 each", isBodyweightOrTimed = true, baseCalPerSet = 16, visualType = "bodyweight"),
    WorkoutExercise("f6", "Fri", "Abs", "Leg Raises (Finisher)", 3, "3 sets", isBodyweightOrTimed = true, baseCalPerSet = 11, visualType = "bodyweight")
)

// ============================================================================
// REAL-TIME 24/7 FOREGROUND PEDOMETER SERVICE
// ============================================================================
class StepForegroundService : Service(), SensorEventListener {
    private var sensorManager: SensorManager? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        val notif = NotificationCompat.Builder(this, "fittrack_step_service")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("FitTrack Live Pedometer Active")
            .setContentText("Counting your steps in real time...")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            startForeground(9001, notif)
        } catch (_: Exception) {}

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 0)
        }
        sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 0)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onSensorChanged(event: SensorEvent?) {
        val prefs = getSharedPreferences("fittrack_pro_prefs", Context.MODE_PRIVATE)
        val todayKey = LocalDate.now().toString()

        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalHardwareSteps = event.values[0].toInt()
            val prevSensorSteps = prefs.getInt("sensor_steps_$todayKey", 0)
            var savedOffset = prefs.getInt("offset_$todayKey", -1)

            if (savedOffset == -1 || totalHardwareSteps < savedOffset) {
                savedOffset = totalHardwareSteps - prevSensorSteps.coerceAtLeast(0)
                prefs.edit().putInt("offset_$todayKey", savedOffset).apply()
            }

            val newSensorSteps = (totalHardwareSteps - savedOffset).coerceAtLeast(0)
            val manualOffset = prefs.getInt("manual_steps_$todayKey", 0)
            val totalSteps = (newSensorSteps + manualOffset).coerceAtLeast(0)

            prefs.edit()
                .putInt("sensor_steps_$todayKey", newSensorSteps)
                .putInt("steps_$todayKey", totalSteps)
                .apply()

            updateAllHomeWidgets(this)
        } else if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
            val currentTotal = prefs.getInt("steps_$todayKey", 0) + 1
            val currentSensor = prefs.getInt("sensor_steps_$todayKey", 0) + 1
            prefs.edit()
                .putInt("sensor_steps_$todayKey", currentSensor)
                .putInt("steps_$todayKey", currentTotal)
                .apply()
            updateAllHomeWidgets(this)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null
}

fun createChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val alertChannel = NotificationChannel(
            "fittrack_reminders",
            "Smart Workout & Hydration Alerts",
            NotificationManager.IMPORTANCE_HIGH
        )
        val serviceChannel = NotificationChannel(
            "fittrack_step_service",
            "Live Pedometer Background Sync",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(alertChannel)
        manager.createNotificationChannel(serviceChannel)
    }
}

// ============================================================================
// MAIN ACTIVITY
// ============================================================================
class MainActivity : ComponentActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var liveStepsState by mutableIntStateOf(0)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        val todayKey = LocalDate.now().toString()
        if (key == "steps_$todayKey" || key == "sensor_steps_$todayKey") {
            liveStepsState = prefs.getInt("steps_$todayKey", 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createChannels(this)
        scheduleAllDailyAlarms(this)

        val prefs = getSharedPreferences("fittrack_pro_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        val todayKey = LocalDate.now().toString()
        liveStepsState = prefs.getInt("steps_$todayKey", 0)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 0)
        }
        sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST, 0)
        }

        try {
            val serviceIntent = Intent(this, StepForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (_: Exception) {}

        setContent {
            var isDarkTheme by remember { mutableStateOf(prefs.getBoolean("is_dark_theme", false)) } // Default Light & Friendly!
            val themeColors = getAppTheme(isDarkTheme)

            CompositionLocalProvider(LocalAppTheme provides themeColors) {
                MaterialTheme(
                    colorScheme = if (isDarkTheme) {
                        darkColorScheme(
                            background = themeColors.bg,
                            surface = themeColors.card,
                            primary = themeColors.accentGreen,
                            secondary = themeColors.accentPurple
                        )
                    } else {
                        lightColorScheme(
                            background = themeColors.bg,
                            surface = themeColors.card,
                            primary = themeColors.accentGreen,
                            secondary = themeColors.accentPurple
                        )
                    }
                ) {
                    FitTrackMainRoot(
                        prefs = prefs,
                        steps = liveStepsState,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { newDark ->
                            isDarkTheme = newDark
                            prefs.edit().putBoolean("is_dark_theme", newDark).apply()
                            updateAllHomeWidgets(this@MainActivity)
                        },
                        onCalibrateExactSteps = { exactTargetSteps ->
                            val currentKey = LocalDate.now().toString()
                            val currentSensor = prefs.getInt("sensor_steps_$currentKey", 0)
                            val manualDiff = exactTargetSteps - currentSensor
                            prefs.edit()
                                .putInt("manual_steps_$currentKey", manualDiff)
                                .putInt("steps_$currentKey", exactTargetSteps)
                                .apply()
                            liveStepsState = exactTargetSteps
                            updateAllHomeWidgets(this@MainActivity)
                        },
                        onRefreshWidgets = {
                            updateAllHomeWidgets(this@MainActivity)
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("fittrack_pro_prefs", Context.MODE_PRIVATE)
        val todayKey = LocalDate.now().toString()
        liveStepsState = prefs.getInt("steps_$todayKey", 0)
        sensorManager?.flush(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val prefs = getSharedPreferences("fittrack_pro_prefs", Context.MODE_PRIVATE)
        val todayKey = LocalDate.now().toString()

        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toInt()
            val prevSensor = prefs.getInt("sensor_steps_$todayKey", 0)
            var savedBootOffset = prefs.getInt("offset_$todayKey", -1)

            if (savedBootOffset == -1 || totalStepsSinceBoot < savedBootOffset) {
                savedBootOffset = totalStepsSinceBoot - prevSensor.coerceAtLeast(0)
                prefs.edit().putInt("offset_$todayKey", savedBootOffset).apply()
            }

            val sensorStepsToday = (totalStepsSinceBoot - savedBootOffset).coerceAtLeast(0)
            val manualStepsToday = prefs.getInt("manual_steps_$todayKey", 0)
            val totalToday = (sensorStepsToday + manualStepsToday).coerceAtLeast(0)

            liveStepsState = totalToday
            prefs.edit()
                .putInt("sensor_steps_$todayKey", sensorStepsToday)
                .putInt("steps_$todayKey", totalToday)
                .apply()

            updateAllHomeWidgets(this)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

// ============================================================================
// EXACT SYSTEM NOTIFICATIONS
// ============================================================================
fun scheduleAllDailyAlarms(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleExactTimeAlarm(requestCode: Int, hour: Int, minute: Int, type: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.fittrack.app.ACTION_EXACT_ALARM"
            putExtra("REMINDER_TYPE", type)
            putExtra("REQ_CODE", requestCode)
            putExtra("HOUR", hour)
            putExtra("MINUTE", minute)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        }
    }

    scheduleExactTimeAlarm(101, 10, 30, "WORKOUT_CHECK_MORNING")
    scheduleExactTimeAlarm(102, 16, 0, "WORKOUT_CHECK_AFTERNOON")
    scheduleExactTimeAlarm(200, 7, 30, "WATER_MORNING_730")
    for (hr in 11..21) {
        scheduleExactTimeAlarm(200 + hr, hr, 0, "WATER_HOURLY")
    }
    scheduleExactTimeAlarm(301, 11, 30, "WATER_4HR_CHECKIN")
    scheduleExactTimeAlarm(302, 15, 30, "WATER_4HR_CHECKIN")
    scheduleExactTimeAlarm(303, 19, 30, "WATER_4HR_CHECKIN")
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        createChannels(context)
        val prefs = context.getSharedPreferences("fittrack_pro_prefs", Context.MODE_PRIVATE)
        val todayStr = LocalDate.now().toString()

        when (intent.action) {
            "com.fittrack.app.ACTION_WIDGET_ADD_WATER" -> {
                val water = prefs.getInt("water_ml_$todayStr", 0) + 250
                prefs.edit()
                    .putInt("water_ml_$todayStr", water)
                    .putLong("last_water_check_ms", System.currentTimeMillis())
                    .apply()
                updateAllHomeWidgets(context)
                return
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                scheduleAllDailyAlarms(context)
                return
            }
        }

        val reqCode = intent.getIntExtra("REQ_CODE", -1)
        val hour = intent.getIntExtra("HOUR", -1)
        val type = intent.getStringExtra("REMINDER_TYPE") ?: return
        if (reqCode != -1 && hour != -1) {
            scheduleAllDailyAlarms(context)
        }

        if (!prefs.getBoolean("notif_enabled", true)) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openAppIntent = Intent(context, MainActivity::class.java)
        val contentPending = PendingIntent.getActivity(
            context, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val addWaterIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.fittrack.app.ACTION_WIDGET_ADD_WATER"
        }
        val addWaterPending = PendingIntent.getBroadcast(
            context, 555, addWaterIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        when (type) {
            "WORKOUT_CHECK_MORNING", "WORKOUT_CHECK_AFTERNOON" -> {
                val loggedToday = prefs.getBoolean("workout_updated_$todayStr", false)
                if (!loggedToday) {
                    val timeLabel = if (type == "WORKOUT_CHECK_MORNING") "10:30 AM" else "4:00 PM"
                    val notif = NotificationCompat.Builder(context, "fittrack_reminders")
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("🏋️ $timeLabel Workout Reminder")
                        .setContentText("You haven't updated your workout sets or 30m routine today! Tap to log.")
                        .setContentIntent(contentPending)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                    manager.notify(3001, notif)
                }
            }
            "WATER_MORNING_730" -> {
                val notif = NotificationCompat.Builder(context, "fittrack_reminders")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("💧 7:30 AM Morning Water Reminder")
                    .setContentText("Start your morning fresh with a glass of water (250 ml)!")
                    .addAction(android.R.drawable.ic_input_add, "+250ml Water", addWaterPending)
                    .setContentIntent(contentPending)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                manager.notify(3002, notif)
            }
            "WATER_HOURLY" -> {
                val currentWater = prefs.getInt("water_ml_$todayStr", 0)
                val notif = NotificationCompat.Builder(context, "fittrack_reminders")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("💧 Hourly Water Reminder")
                    .setContentText("Time to drink a glass of water! Today's level: $currentWater ml.")
                    .addAction(android.R.drawable.ic_input_add, "+250ml Water", addWaterPending)
                    .setContentIntent(contentPending)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                manager.notify(3003, notif)
            }
            "WATER_4HR_CHECKIN" -> {
                val currentWater = prefs.getInt("water_ml_$todayStr", 0)
                val goalWater = prefs.getInt("water_goal_ml", 3000)
                val notif = NotificationCompat.Builder(context, "fittrack_reminders")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("🚰 4-Hour Water Level Check!")
                    .setContentText("Check-in: You are at $currentWater / $goalWater ml. Tap '+250ml' or open app to update!")
                    .addAction(android.R.drawable.ic_input_add, "+250ml Water", addWaterPending)
                    .setContentIntent(contentPending)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                manager.notify(3004, notif)
            }
        }
    }
}

// ============================================================================
// LIGHT & DARK ADAPTIVE GRAPHICAL CANVAS RENDERER FOR ALL 3 WIDGETS
// ============================================================================
object WidgetGraphicRenderer {

    // 1. 2x2 WIDGET: Circular Step Gauge (Supports Dark & Light Theme)
    fun draw2x2CircularWidget(steps: Int, target: Int, cals: Int, isDark: Boolean): Bitmap {
        val size = 420
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val bgHex = if (isDark) "#161618" else "#FFFFFF"
        val borderHex = if (isDark) "#2C2C30" else "#E2E8F0"
        val trackHex = if (isDark) "#2A2A2E" else "#EEF2F6"
        val textMainHex = if (isDark) "#FFFFFF" else "#18181B"
        val textSubHex = if (isDark) "#9E9E9E" else "#64748B"
        val accentGreenHex = if (isDark) "#10B981" else "#059669"

        paint.color = AndroidColor.parseColor(bgHex)
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 68f, 68f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = AndroidColor.parseColor(borderHex)
        canvas.drawRoundRect(RectF(4f, 4f, size - 4f, size - 4f), 64f, 64f, paint)

        val pad = 54f
        val arcRect = RectF(pad, pad, size - pad, size - pad)
        paint.strokeWidth = 32f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = AndroidColor.parseColor(trackHex)
        canvas.drawArc(arcRect, 135f, 270f, false, paint)

        val progress = (steps.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
        paint.shader = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            AndroidColor.parseColor("#10B981"),
            AndroidColor.parseColor("#F97316"),
            Shader.TileMode.CLAMP
        )
        canvas.drawArc(arcRect, 135f, 270f * progress, false, paint)
        paint.shader = null

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER

        paint.color = AndroidColor.parseColor(textMainHex)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 66f
        canvas.drawText("$steps", size / 2f, size * 0.48f, paint)

        paint.color = AndroidColor.parseColor(textSubHex)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 26f
        canvas.drawText("/ $target steps", size / 2f, size * 0.58f, paint)

        paint.color = AndroidColor.parseColor(accentGreenHex)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 28f
        canvas.drawText("🔥 $cals kcal", size / 2f, size * 0.84f, paint)

        return bmp
    }

    // 2. 4x2 WIDGET: Graphical 4-Card KPI Grid (Supports Dark & Light Theme)
    fun draw4x2KpiWidget(steps: Int, target: Int, totalCals: Int, km: String, waterMl: Int, score: Int, isDark: Boolean): Bitmap {
        val w = 840
        val h = 400
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val bgHex = if (isDark) "#141416" else "#FFFFFF"
        val tileHex = if (isDark) "#222226" else "#F4F5F8"
        val trackHex = if (isDark) "#141416" else "#E2E6EE"
        val textMainHex = if (isDark) "#FFFFFF" else "#18181B"
        val textSubHex = if (isDark) "#9E9E9E" else "#64748B"

        paint.color = AndroidColor.parseColor(bgHex)
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 56f, 56f, paint)

        paint.color = AndroidColor.parseColor(textMainHex)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 32f
        canvas.drawText("FitTrack Daily KPIs", 36f, 58f, paint)

        paint.textAlign = Paint.Align.RIGHT
        paint.color = AndroidColor.parseColor("#10B981")
        canvas.drawText("Health Score: $score/100", w - 36f, 58f, paint)
        paint.textAlign = Paint.Align.LEFT

        fun drawKpiTile(left: Float, top: Float, right: Float, bottom: Float, title: String, value: String, sub: String, accentHex: String, pct: Float) {
            paint.style = Paint.Style.FILL
            paint.color = AndroidColor.parseColor(tileHex)
            canvas.drawRoundRect(RectF(left, top, right, bottom), 30f, 30f, paint)

            val accent = AndroidColor.parseColor(accentHex)
            paint.color = AndroidColor.parseColor(textSubHex)
            paint.textSize = 21f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(title, left + 24f, top + 40f, paint)

            paint.color = AndroidColor.parseColor(textMainHex)
            paint.textSize = 36f
            canvas.drawText(value, left + 24f, top + 86f, paint)

            paint.color = accent
            paint.textSize = 20f
            canvas.drawText(sub, right - 135f, top + 40f, paint)

            val barLeft = left + 24f
            val barRight = right - 24f
            val barTop = bottom - 26f
            val barBottom = bottom - 14f
            paint.color = AndroidColor.parseColor(trackHex)
            canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barBottom), 8f, 8f, paint)

            paint.color = accent
            val fillRight = barLeft + (barRight - barLeft) * pct.coerceIn(0.05f, 1f)
            canvas.drawRoundRect(RectF(barLeft, barTop, fillRight, barBottom), 8f, 8f, paint)
        }

        drawKpiTile(30f, 82f, 405f, 222f, "👟 STEPS", "$steps", "/ $target", "#8B5CF6", steps.toFloat() / target.coerceAtLeast(1))
        drawKpiTile(435f, 82f, 810f, 222f, "🔥 CALORIES", "$totalCals kcal", "Active", "#10B981", totalCals / 600f)
        drawKpiTile(30f, 240f, 405f, 380f, "📍 DISTANCE", "$km km", "Walked", "#F97316", (steps * 0.0007f) / 6f)
        drawKpiTile(435f, 240f, 810f, 380f, "💧 WATER", "$waterMl ml", "/ 3000ml", "#0EA5E9", waterMl / 3000f)

        return bmp
    }

    // 3. 4x3 WIDGET: Full Graphical Dashboard (Supports Dark & Light Theme)
    fun draw4x3DashboardWidget(
        steps: Int,
        target: Int,
        stepBurn: Int,
        routineBurn: Int,
        gymBurn: Int,
        waterMl: Int,
        sleepHrs: Float,
        score: Int,
        isDark: Boolean
    ): Bitmap {
        val w = 860
        val h = 620
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val totalBurn = stepBurn + routineBurn + gymBurn
        val bgHex = if (isDark) "#141416" else "#FFFFFF"
        val tileHex = if (isDark) "#222226" else "#F4F5F8"
        val textMainHex = if (isDark) "#FFFFFF" else "#18181B"

        paint.color = AndroidColor.parseColor(bgHex)
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), 60f, 60f, paint)

        paint.color = AndroidColor.parseColor(textMainHex)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 34f
        canvas.drawText("FitTrack Pro Dashboard", 40f, 64f, paint)

        paint.color = AndroidColor.parseColor(tileHex)
        canvas.drawRoundRect(RectF(w - 250f, 24f, w - 36f, 80f), 28f, 28f, paint)
        paint.color = AndroidColor.parseColor("#F97316")
        paint.textSize = 26f
        canvas.drawText("⭐ Score: $score/100", w - 230f, 60f, paint)

        val ringBox = RectF(50f, 115f, 350f, 415f)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 26f
        paint.color = AndroidColor.parseColor(tileHex)
        canvas.drawArc(ringBox, 0f, 360f, false, paint)

        val stepSweep = 360f * (steps.toFloat() / target.coerceAtLeast(1)).coerceIn(0.04f, 1f)
        paint.color = AndroidColor.parseColor("#8B5CF6")
        canvas.drawArc(ringBox, -90f, stepSweep, false, paint)

        val innerBox = RectF(86f, 151f, 314f, 379f)
        paint.color = AndroidColor.parseColor(tileHex)
        canvas.drawArc(innerBox, 0f, 360f, false, paint)

        val calSweep = 360f * (totalBurn / 600f).coerceIn(0.04f, 1f)
        paint.color = AndroidColor.parseColor("#10B981")
        canvas.drawArc(innerBox, -90f, calSweep, false, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.color = AndroidColor.parseColor(textMainHex)
        paint.textSize = 46f
        canvas.drawText("$steps", 200f, 255f, paint)
        paint.color = AndroidColor.parseColor("#10B981")
        paint.textSize = 26f
        canvas.drawText("🔥 $totalBurn kcal", 200f, 295f, paint)
        paint.textAlign = Paint.Align.LEFT

        fun drawStatBar(y: Float, label: String, kcal: Int, maxKcal: Float, hex: String) {
            paint.color = AndroidColor.parseColor(textMainHex)
            paint.textSize = 25f
            canvas.drawText(label, 395f, y, paint)

            paint.textAlign = Paint.Align.RIGHT
            paint.color = AndroidColor.parseColor(hex)
            canvas.drawText("$kcal kcal", w - 45f, y, paint)
            paint.textAlign = Paint.Align.LEFT

            val barTop = y + 14f
            paint.color = AndroidColor.parseColor(tileHex)
            canvas.drawRoundRect(RectF(395f, barTop, w - 45f, barTop + 22f), 11f, 11f, paint)

            val fillW = (w - 440f) * (kcal / maxKcal).coerceIn(0.05f, 1f)
            paint.color = AndroidColor.parseColor(hex)
            canvas.drawRoundRect(RectF(395f, barTop, 395f + fillW, barTop + 22f), 11f, 11f, paint)
        }

        drawStatBar(155f, "👟 Walk Burn", stepBurn, 350f, "#8B5CF6")
        drawStatBar(255f, "⏱️ 30m Routine", routineBurn, 250f, "#F97316")
        drawStatBar(355f, "🏋️ Gym Split", gymBurn, 350f, "#10B981")

        paint.color = AndroidColor.parseColor(tileHex)
        canvas.drawRoundRect(RectF(40f, 465f, 300f, 575f), 28f, 28f, paint)
        canvas.drawRoundRect(RectF(320f, 465f, 560f, 575f), 28f, 28f, paint)

        paint.color = AndroidColor.parseColor("#0EA5E9")
        paint.textSize = 22f
        canvas.drawText("💧 WATER LEVEL", 64f, 508f, paint)
        paint.color = AndroidColor.parseColor(textMainHex)
        paint.textSize = 30f
        canvas.drawText("$waterMl / 3000 ml", 64f, 552f, paint)

        paint.color = AndroidColor.parseColor("#F97316")
        paint.textSize = 22f
        canvas.drawText("😴 SLEEP LOG", 344f, 508f, paint)
        paint.color = AndroidColor.parseColor(textMainHex)
        paint.textSize = 30f
        canvas.drawText("$sleepHrs hrs", 344f, 552f, paint)

        return bmp
    }
}

fun calculateHealthScoreFromPrefs(prefs: SharedPreferences, todayStr: String): Int {
    val steps = prefs.getInt("steps_$todayStr", 0)
    val stepGoal = prefs.getInt("step_target", 8000).coerceAtLeast(1)
    val waterMl = prefs.getInt("water_ml_$todayStr", 0)
    val sleepHrs = prefs.getFloat("sleep_hrs_$todayStr", 7.5f)
    val workoutBurn = prefs.getInt("workout_burn_$todayStr", 0)

    val stepPts = ((steps.toFloat() / stepGoal) * 25f).coerceIn(0f, 25f)
    val waterPts = ((waterMl.toFloat() / 3000f) * 25f).coerceIn(0f, 25f)
    val sleepPts = ((sleepHrs / 7.5f) * 25f).coerceIn(0f, 25f)
    val workoutPts = ((workoutBurn.toFloat() / 300f) * 25f).coerceIn(0f, 25f)
    return (stepPts + waterPts + sleepPts + workoutPts).roundToInt()
}

fun updateAllHomeWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val prefs = context.getSharedPreferences("fittrack_pro_prefs", Context.MODE_PRIVATE)
    val todayStr = LocalDate.now().toString()
    val isDark = prefs.getBoolean("is_dark_theme", false)

    val steps = prefs.getInt("steps_$todayStr", 0)
    val stepGoal = prefs.getInt("step_target", 8000)
    val stepBurn = (steps * 0.04f).roundToInt()
    val routineBurn = prefs.getInt("routine_burn_$todayStr", 0)
    val splitBurn = prefs.getInt("split_burn_$todayStr", 0)
    val totalBurn = stepBurn + routineBurn + splitBurn
    val distanceKm = String.format(Locale.US, "%.2f", steps * 0.0007f)
    val waterMl = prefs.getInt("water_ml_$todayStr", 0)
    val sleepHrs = prefs.getFloat("sleep_hrs_$todayStr", 7.5f)
    val score = calculateHealthScoreFromPrefs(prefs, todayStr)

    val openAppIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val ids2x2 = manager.getAppWidgetIds(ComponentName(context, WidgetCompact2x2::class.java))
    if (ids2x2.isNotEmpty()) {
        val bmp2x2 = WidgetGraphicRenderer.draw2x2CircularWidget(steps, stepGoal, totalBurn, isDark)
        for (id in ids2x2) {
            val views = RemoteViews(context.packageName, R.layout.widget_2x2)
            views.setImageViewBitmap(R.id.img_canvas_2x2, bmp2x2)
            views.setOnClickPendingIntent(R.id.widget_root_2x2, openAppIntent)
            manager.updateAppWidget(id, views)
        }
    }

    val ids4x2 = manager.getAppWidgetIds(ComponentName(context, WidgetSummary4x2::class.java))
    if (ids4x2.isNotEmpty()) {
        val bmp4x2 = WidgetGraphicRenderer.draw4x2KpiWidget(steps, stepGoal, totalBurn, distanceKm, waterMl, score, isDark)
        for (id in ids4x2) {
            val views = RemoteViews(context.packageName, R.layout.widget_4x2)
            views.setImageViewBitmap(R.id.img_canvas_4x2, bmp4x2)
            views.setOnClickPendingIntent(R.id.widget_root_4x2, openAppIntent)
            manager.updateAppWidget(id, views)
        }
    }

    val ids4x3 = manager.getAppWidgetIds(ComponentName(context, WidgetDashboard4x3::class.java))
    if (ids4x3.isNotEmpty()) {
        val bmp4x3 = WidgetGraphicRenderer.draw4x3DashboardWidget(
            steps, stepGoal, stepBurn, routineBurn, splitBurn, waterMl, sleepHrs, score, isDark
        )
        val addWaterPending = PendingIntent.getBroadcast(
            context, 502, Intent(context, ReminderReceiver::class.java).apply { action = "com.fittrack.app.ACTION_WIDGET_ADD_WATER" },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        for (id in ids4x3) {
            val views = RemoteViews(context.packageName, R.layout.widget_4x3)
            views.setImageViewBitmap(R.id.img_canvas_4x3, bmp4x3)
            views.setOnClickPendingIntent(R.id.widget_root_4x3, openAppIntent)
            views.setOnClickPendingIntent(R.id.btn_widget_water, addWaterPending)
            manager.updateAppWidget(id, views)
        }
    }
}

class WidgetCompact2x2 : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllHomeWidgets(context)
    }
}

class WidgetSummary4x2 : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllHomeWidgets(context)
    }
}

class WidgetDashboard4x3 : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllHomeWidgets(context)
    }
}

fun calculateSleepHours(bedHour12: Int, bedMin: Int, bedAmPm: String, wakeHour12: Int, wakeMin: Int, wakeAmPm: String): Float {
    fun to24HrMinutes(h12: Int, min: Int, amPm: String): Int {
        var h = h12 % 12
        if (amPm.uppercase() == "PM") h += 12
        return h * 60 + min
    }
    val bedTotalMin = to24HrMinutes(bedHour12, bedMin, bedAmPm)
    val wakeTotalMin = to24HrMinutes(wakeHour12, wakeMin, wakeAmPm)
    var diffMin = wakeTotalMin - bedTotalMin
    if (diffMin <= 0) diffMin += 24 * 60
    return ((diffMin / 60f) * 10f).roundToInt() / 10f
}

// ============================================================================
// COMPOSABLE ROOT
// ============================================================================
@Composable
fun FitTrackMainRoot(
    prefs: SharedPreferences,
    steps: Int,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onCalibrateExactSteps: (Int) -> Unit,
    onRefreshWidgets: () -> Unit
) {
    val theme = LocalAppTheme.current
    val context = LocalContext.current
    val today = LocalDate.now()
    val todayStr = today.toString()
    val currentMondayStr = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    LaunchedEffect(Unit) {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
    }

    var stepTarget by remember { mutableIntStateOf(prefs.getInt("step_target", 8000)) }
    var userBodyWeightKg by remember { mutableIntStateOf(prefs.getInt("body_weight_kg", 70)) }
    var daily30MinDefaultCals by remember { mutableIntStateOf(prefs.getInt("daily_30m_cals", 185)) }
    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("notif_enabled", true)) }

    val daily30MinByDay = remember {
        mutableStateMapOf<String, Boolean>().apply {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                put(day, prefs.getBoolean("daily30_${currentMondayStr}_$day", false))
            }
        }
    }

    var waterIntakeMl by remember { mutableIntStateOf(prefs.getInt("water_ml_$todayStr", 0)) }
    var waterGoalMl by remember { mutableIntStateOf(prefs.getInt("water_goal_ml", 3000)) }
    var sleepHoursToday by remember { mutableFloatStateOf(prefs.getFloat("sleep_hrs_$todayStr", 7.5f)) }
    var sleepBedtime by remember { mutableStateOf(prefs.getString("sleep_bed_$todayStr", "11:30 PM") ?: "11:30 PM") }
    var sleepWakeTime by remember { mutableStateOf(prefs.getString("sleep_wake_$todayStr", "07:00 AM") ?: "07:00 AM") }

    val exercisesList = remember {
        mutableStateListOf<WorkoutExercise>().apply {
            val savedJson = prefs.getString("custom_exercises_json", null)
            if (savedJson != null) {
                val arr = JSONArray(savedJson)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        WorkoutExercise(
                            id = o.getString("id"),
                            day = o.getString("day"),
                            muscleGroup = o.getString("muscleGroup"),
                            name = o.getString("name"),
                            targetSets = o.getInt("targetSets"),
                            targetNote = o.getString("targetNote"),
                            isBodyweightOrTimed = o.getBoolean("isBody"),
                            baseCalPerSet = o.getInt("baseCal"),
                            visualType = o.optString("visualType", "dumbbell")
                        )
                    )
                }
            } else {
                addAll(getDefaultExercises())
            }
        }
    }

    fun saveExercisesToPrefs() {
        val arr = JSONArray()
        exercisesList.forEach { ex ->
            arr.put(JSONObject().apply {
                put("id", ex.id); put("day", ex.day); put("muscleGroup", ex.muscleGroup)
                put("name", ex.name); put("targetSets", ex.targetSets); put("targetNote", ex.targetNote)
                put("isBody", ex.isBodyweightOrTimed); put("baseCal", ex.baseCalPerSet); put("visualType", ex.visualType)
            })
        }
        prefs.edit().putString("custom_exercises_json", arr.toString()).apply()
    }

    val exerciseLogs = remember { mutableStateMapOf<String, ExerciseLogState>() }

    fun loadLogsFromPrefs() {
        exercisesList.forEach { ex ->
            val doneCsv = prefs.getString("set_done_csv_${ex.id}", null)
            val weightsCsv = prefs.getString("set_weights_csv_${ex.id}", null)
            val prevMem = prefs.getString("mem_weight_${ex.id}", "Set 1: 2.5 kg/side") ?: "Set 1: 2.5 kg/side"

            val doneList = if (doneCsv != null) doneCsv.split(",").map { it == "1" } else List(ex.targetSets) { false }
            val weightsList = if (weightsCsv != null) {
                weightsCsv.split("|")
            } else {
                List(ex.targetSets) { idx ->
                    if (ex.isBodyweightOrTimed) "Bodyweight" else if (idx == 0) "2.5 kg/side" else "5 kg/side"
                }
            }
            exerciseLogs[ex.id] = ExerciseLogState(doneList, weightsList, prevMem)
        }
    }

    fun saveSingleLogToPrefs(id: String, state: ExerciseLogState) {
        exerciseLogs[id] = state
        val doneCsv = state.setCompletedList.joinToString(",") { if (it) "1" else "0" }
        val weightsCsv = state.setWeightsList.joinToString("|")
        prefs.edit()
            .putString("set_done_csv_$id", doneCsv)
            .putString("set_weights_csv_$id", weightsCsv)
            .putString("mem_weight_$id", state.previousSetSummaryMemory)
            .putBoolean("workout_updated_$todayStr", true)
            .apply()
    }

    val workoutHistoryList = remember { mutableStateListOf<WorkoutHistoryEntry>() }
    val stepHistoryList = remember { mutableStateListOf<StepHistoryEntry>() }

    fun loadHistoryFromPrefs() {
        workoutHistoryList.clear()
        val wArr = JSONArray(prefs.getString("workout_history_json", "[]") ?: "[]")
        for (i in 0 until wArr.length()) {
            val o = wArr.getJSONObject(i)
            workoutHistoryList.add(
                WorkoutHistoryEntry(
                    o.getLong("id"), o.getString("date"), o.getString("dayName"),
                    o.getString("exerciseName"), o.getInt("setsDone"), o.getString("weightUsed"), o.getInt("caloriesBurned")
                )
            )
        }
        stepHistoryList.clear()
        val sArr = JSONArray(prefs.getString("step_history_json", "[]") ?: "[]")
        for (i in 0 until sArr.length()) {
            val o = sArr.getJSONObject(i)
            stepHistoryList.add(StepHistoryEntry(o.getString("date"), o.getInt("steps"), o.getInt("goal"), o.getInt("calories")))
        }
    }

    fun archiveAndResetWeekForMonday() {
        exercisesList.forEach { ex ->
            val st = exerciseLogs[ex.id]
            if (st != null && st.completedSetsCount > 0) {
                val cals = calculateExerciseCalories(ex, st)
                val usedSummary = st.setWeightsList.mapIndexedNotNull { idx, w ->
                    if (st.setCompletedList.getOrElse(idx) { false }) "S${idx + 1}:$w" else null
                }.joinToString(", ")

                workoutHistoryList.add(
                    0,
                    WorkoutHistoryEntry(
                        System.currentTimeMillis() + ex.id.hashCode(),
                        todayStr, ex.day, ex.name, st.completedSetsCount, usedSummary, cals
                    )
                )
                saveSingleLogToPrefs(
                    ex.id,
                    st.copy(
                        setCompletedList = List(ex.targetSets) { false },
                        previousSetSummaryMemory = usedSummary
                    )
                )
            }
        }

        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
            if (daily30MinByDay[day] == true) {
                workoutHistoryList.add(
                    0,
                    WorkoutHistoryEntry(
                        System.currentTimeMillis() + day.hashCode(),
                        todayStr, day, "Daily 30-Min Routine & Warm-Up", 1, "5 kg Squats + Pushups", daily30MinDefaultCals
                    )
                )
                daily30MinByDay[day] = false
                prefs.edit().putBoolean("daily30_${currentMondayStr}_$day", false).apply()
            }
        }

        val arr = JSONArray()
        workoutHistoryList.take(200).forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id); put("date", item.date); put("dayName", item.dayName)
                put("exerciseName", item.exerciseName); put("setsDone", item.setsDone)
                put("weightUsed", item.weightUsed); put("caloriesBurned", item.caloriesBurned)
            })
        }
        prefs.edit().putString("workout_history_json", arr.toString()).apply()
    }

    LaunchedEffect(Unit) {
        loadLogsFromPrefs()
        loadHistoryFromPrefs()
        val lastSavedWeekMonday = prefs.getString("last_week_monday", "") ?: ""
        if (lastSavedWeekMonday.isNotEmpty() && lastSavedWeekMonday != currentMondayStr) {
            archiveAndResetWeekForMonday()
        }
        prefs.edit().putString("last_week_monday", currentMondayStr).apply()
    }

    val foodLogs = remember { mutableStateListOf<FoodItem>() }

    val stepCalories = (steps * (userBodyWeightKg / 70f) * 0.04f).roundToInt()
    val todayShortDay = today.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    val daily30MinCalories = if (daily30MinByDay[todayShortDay] == true) daily30MinDefaultCals else 0
    val splitWorkoutCalories = exercisesList.sumOf { ex ->
        val st = exerciseLogs[ex.id] ?: ExerciseLogState()
        calculateExerciseCalories(ex, st)
    }
    val totalWorkoutBurn = daily30MinCalories + splitWorkoutCalories
    val totalBurnedCalories = stepCalories + totalWorkoutBurn
    val totalEatenCalories = foodLogs.sumOf { it.calories }

    LaunchedEffect(daily30MinCalories, splitWorkoutCalories, steps, waterIntakeMl, sleepHoursToday, isDarkTheme) {
        prefs.edit()
            .putInt("routine_burn_$todayStr", daily30MinCalories)
            .putInt("split_burn_$todayStr", splitWorkoutCalories)
            .putInt("workout_burn_$todayStr", totalWorkoutBurn)
            .apply()

        val stepCals = (steps * 0.04f).roundToInt()
        val existingIdx = stepHistoryList.indexOfFirst { it.date == todayStr }
        val todayEntry = StepHistoryEntry(todayStr, steps, stepTarget, stepCals)
        if (existingIdx >= 0) stepHistoryList[existingIdx] = todayEntry else stepHistoryList.add(0, todayEntry)

        val sArr = JSONArray()
        stepHistoryList.take(60).forEach {
            sArr.put(JSONObject().apply {
                put("date", it.date); put("steps", it.steps); put("goal", it.goal); put("calories", it.calories)
            })
        }
        prefs.edit().putString("step_history_json", sArr.toString()).apply()
        onRefreshWidgets()
    }

    val stepScore = ((steps.toFloat() / stepTarget.coerceAtLeast(1)) * 25f).coerceIn(0f, 25f).roundToInt()
    val workoutScore = ((totalWorkoutBurn.toFloat() / 300f) * 25f).coerceIn(0f, 25f).roundToInt()
    val waterScore = ((waterIntakeMl.toFloat() / waterGoalMl.coerceAtLeast(1)) * 25f).coerceIn(0f, 25f).roundToInt()
    val sleepScore = ((sleepHoursToday / 7.5f) * 25f).coerceIn(0f, 25f).roundToInt()
    val totalHealthScore = (stepScore + workoutScore + waterScore + sleepScore).coerceIn(0, 100)

    var currentTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = theme.bg,
        bottomBar = {
            NavigationBar(containerColor = theme.card, tonalElevation = 12.dp) {
                val navItems = listOf(
                    Triple(0, "Dashboard", Icons.Default.Home),
                    Triple(1, "Workouts", Icons.Default.FitnessCenter),
                    Triple(2, "History", Icons.Default.History),
                    Triple(3, "Settings", Icons.Default.Settings)
                )
                navItems.forEach { (index, label, icon) ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = theme.accentGreen,
                            indicatorColor = theme.accentGreen,
                            unselectedIconColor = theme.textSecondary,
                            unselectedTextColor = theme.textSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (currentTab) {
                0 -> Tab1SpaciousUserFriendlyDashboard(
                    steps = steps,
                    stepTarget = stepTarget,
                    stepCalories = stepCalories,
                    daily30MinCalories = daily30MinCalories,
                    splitWorkoutCalories = splitWorkoutCalories,
                    totalBurnedCalories = totalBurnedCalories,
                    totalEatenCalories = totalEatenCalories,
                    waterIntakeMl = waterIntakeMl,
                    waterGoalMl = waterGoalMl,
                    sleepHoursToday = sleepHoursToday,
                    sleepBedtime = sleepBedtime,
                    sleepWakeTime = sleepWakeTime,
                    totalHealthScore = totalHealthScore,
                    stepScore = stepScore,
                    workoutScore = workoutScore,
                    waterScore = waterScore,
                    sleepScore = sleepScore,
                    foodLogs = foodLogs,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onCalibrateExactSteps = onCalibrateExactSteps,
                    onChangeStepTarget = { newGoal ->
                        stepTarget = newGoal
                        prefs.edit().putInt("step_target", newGoal).apply()
                    },
                    onAddWaterMl = { addedMl ->
                        waterIntakeMl = (waterIntakeMl + addedMl).coerceAtLeast(0)
                        prefs.edit().putInt("water_ml_$todayStr", waterIntakeMl).apply()
                        onRefreshWidgets()
                    },
                    onUpdateSleep = { hrs, bed, wake ->
                        sleepHoursToday = hrs
                        sleepBedtime = bed
                        sleepWakeTime = wake
                        prefs.edit()
                            .putFloat("sleep_hrs_$todayStr", hrs)
                            .putString("sleep_bed_$todayStr", bed)
                            .putString("sleep_wake_$todayStr", wake)
                            .apply()
                        onRefreshWidgets()
                    }
                )
                1 -> Tab2FriendlyWorkouts(
                    exercisesList = exercisesList,
                    exerciseLogs = exerciseLogs,
                    daily30MinByDay = daily30MinByDay,
                    daily30MinDefaultCals = daily30MinDefaultCals,
                    onToggleDaily30MinForDay = { day, checked ->
                        daily30MinByDay[day] = checked
                        prefs.edit()
                            .putBoolean("daily30_${currentMondayStr}_$day", checked)
                            .putBoolean("workout_updated_$todayStr", true)
                            .apply()
                    },
                    onUpdateExerciseLog = { id, newState -> saveSingleLogToPrefs(id, newState) },
                    onAddExercise = { newEx ->
                        exercisesList.add(newEx)
                        saveExercisesToPrefs()
                        saveSingleLogToPrefs(
                            newEx.id,
                            ExerciseLogState(
                                List(newEx.targetSets) { false },
                                List(newEx.targetSets) { if (newEx.isBodyweightOrTimed) "Bodyweight" else "2.5 kg/side" },
                                "New"
                            )
                        )
                    },
                    onEditExercise = { updatedEx ->
                        val idx = exercisesList.indexOfFirst { it.id == updatedEx.id }
                        if (idx >= 0) {
                            exercisesList[idx] = updatedEx
                            saveExercisesToPrefs()
                        }
                    },
                    onDeleteExercise = { exId ->
                        exercisesList.removeAll { it.id == exId }
                        saveExercisesToPrefs()
                    },
                    onFinishAndArchiveWeek = { archiveAndResetWeekForMonday() }
                )
                2 -> Tab3FriendlyHistory(
                    workoutHistoryList = workoutHistoryList,
                    stepHistoryList = stepHistoryList,
                    onArchiveCurrentWeekNow = { archiveAndResetWeekForMonday() }
                )
                3 -> Tab4FriendlySettings(
                    stepTarget = stepTarget,
                    userBodyWeightKg = userBodyWeightKg,
                    daily30MinDefaultCals = daily30MinDefaultCals,
                    waterGoalMl = waterGoalMl,
                    notificationsEnabled = notificationsEnabled,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onUpdateStepTarget = {
                        stepTarget = it
                        prefs.edit().putInt("step_target", it).apply()
                    },
                    onUpdateBodyWeight = {
                        userBodyWeightKg = it
                        prefs.edit().putInt("body_weight_kg", it).apply()
                    },
                    onUpdateDaily30Cals = {
                        daily30MinDefaultCals = it
                        prefs.edit().putInt("daily_30m_cals", it).apply()
                    },
                    onUpdateWaterGoal = {
                        waterGoalMl = it
                        prefs.edit().putInt("water_goal_ml", it).apply()
                    },
                    onToggleNotifications = {
                        notificationsEnabled = it
                        prefs.edit().putBoolean("notif_enabled", it).apply()
                    },
                    onTestSystemNotification = {
                        val intent = Intent(context, ReminderReceiver::class.java).apply {
                            action = "com.fittrack.app.ACTION_EXACT_ALARM"
                            putExtra("REMINDER_TYPE", "WATER_4HR_CHECKIN")
                        }
                        context.sendBroadcast(intent)
                    },
                    onResetDefaultWorkouts = {
                        exercisesList.clear()
                        exercisesList.addAll(getDefaultExercises())
                        saveExercisesToPrefs()
                        loadLogsFromPrefs()
                    }
                )
            }
        }
    }
}

// ============================================================================
// TAB 1: SPACIOUS, OPEN & USER-FRIENDLY DASHBOARD (WITH LIGHT/DARK TOGGLE)
// ============================================================================
@Composable
fun Tab1SpaciousUserFriendlyDashboard(
    steps: Int,
    stepTarget: Int,
    stepCalories: Int,
    daily30MinCalories: Int,
    splitWorkoutCalories: Int,
    totalBurnedCalories: Int,
    totalEatenCalories: Int,
    waterIntakeMl: Int,
    waterGoalMl: Int,
    sleepHoursToday: Float,
    sleepBedtime: String,
    sleepWakeTime: String,
    totalHealthScore: Int,
    stepScore: Int,
    workoutScore: Int,
    waterScore: Int,
    sleepScore: Int,
    foodLogs: MutableList<FoodItem>,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onCalibrateExactSteps: (Int) -> Unit,
    onChangeStepTarget: (Int) -> Unit,
    onAddWaterMl: (Int) -> Unit,
    onUpdateSleep: (Float, String, String) -> Unit
) {
    val theme = LocalAppTheme.current
    var showCalibrateDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showAddFoodDialog by remember { mutableStateOf(false) }
    val distanceKm = String.format(Locale.US, "%.2f", steps * 0.0007f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        // --- 1. SPACIOUS TOP HEADER & INSTANT LIGHT/DARK MODE SWITCHER ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FitTrack Pro",
                        color = theme.textPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Your daily fitness & wellness overview",
                        color = theme.textSecondary,
                        fontSize = 14.sp
                    )
                }

                // Friendly Light / Dark Theme Pill Button
                Surface(
                    color = theme.card,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .border(1.dp, theme.border, RoundedCornerShape(50))
                        .clickable { onToggleTheme(!isDarkTheme) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isDarkTheme) "🌙 Dark" else "☀️ Light",
                            color = theme.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // --- 2. EXPANSIVE HERO CARD: PEDOMETER RING + CLEAR CALORIE BREAKDOWN ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.border, RoundedCornerShape(28.dp))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Today's Movement & Burn",
                                color = theme.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "24/7 Live Sensor • $distanceKm km walked",
                                color = theme.accentGreen,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(onClick = { showCalibrateDialog = true }) {
                            Text("Sync / Goal", color = theme.accentPurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Spacious Hero Row: Large Step Ring on Left, Vertical Burn Stack on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxSize(),
                                color = theme.cardSubtle,
                                strokeWidth = 14.dp
                            )
                            CircularProgressIndicator(
                                progress = { (steps.toFloat() / stepTarget.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxSize(),
                                color = theme.accentGreen,
                                strokeWidth = 14.dp,
                                strokeCap = StrokeCap.Round
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$steps",
                                    color = theme.textPrimary,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "of $stepTarget",
                                    color = theme.textSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "STEPS",
                                    color = theme.accentGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        // Right Column: Spacious Calorie Summary Stack
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = theme.cardSubtle,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text("TOTAL BURNED", color = theme.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "🔥 $totalBurnedCalories kcal",
                                        color = theme.accentOrange,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            StatRow("👟 Walk Burn", "$stepCalories kcal", theme.accentPurple)
                            StatRow("⏱️ 30m Routine", "$daily30MinCalories kcal", theme.accentOrange)
                            StatRow("🏋️ Gym Split", "$splitWorkoutCalories kcal", theme.accentGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    FilledTonalButton(
                        onClick = { showCalibrateDialog = true },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = theme.cardSubtle)
                    ) {
                        Text(
                            "Sync with Google Fit / Edit Step Goal",
                            color = theme.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- 3. CLEAN 2-COLUMN BENTO GRID: WATER HYDRATION & SLEEP SCHEDULE ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Left Bento Card: Water Intake
                Card(
                    colors = CardDefaults.cardColors(containerColor = theme.card),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, theme.border, RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("💧 Hydration", color = theme.accentBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "$waterIntakeMl ml",
                            color = theme.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        )
                        Text("Goal: $waterGoalMl ml", color = theme.textSecondary, fontSize = 12.sp)

                        LinearProgressIndicator(
                            progress = { (waterIntakeMl.toFloat() / waterGoalMl.coerceAtLeast(1)).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                            color = theme.accentBlue,
                            trackColor = theme.cardSubtle
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onAddWaterMl(250) },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accentBlue),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text("+250", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { onAddWaterMl(500) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Text("+500", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Right Bento Card: Auto-Calculated Sleep
                Card(
                    colors = CardDefaults.cardColors(containerColor = theme.card),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, theme.border, RoundedCornerShape(24.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("😴 Sleep Log", color = theme.accentOrange, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            "$sleepHoursToday hrs",
                            color = theme.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        )
                        Text(
                            "$sleepBedtime ➔ $sleepWakeTime",
                            color = theme.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )

                        LinearProgressIndicator(
                            progress = { (sleepHoursToday / 8f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                            color = theme.accentOrange,
                            trackColor = theme.cardSubtle
                        )

                        Button(
                            onClick = { showSleepDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.cardSubtle),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Set Sleep Times", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- 4. SPACIOUS DAILY HEALTH SCORE BANNER ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.border, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Overall Health Score", color = theme.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "$totalHealthScore / 100",
                                color = theme.textPrimary,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Surface(
                            color = theme.cardSubtle,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = when {
                                    totalHealthScore >= 85 -> "🌟 Peak Day"
                                    totalHealthScore >= 65 -> "👍 On Track"
                                    else -> "💪 Keep Going"
                                },
                                color = theme.accentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ScorePillarChip("👟 Steps", "$stepScore/25", theme.accentPurple)
                        ScorePillarChip("🏋️ Gym", "$workoutScore/25", theme.accentGreen)
                        ScorePillarChip("💧 Water", "$waterScore/25", theme.accentBlue)
                        ScorePillarChip("😴 Sleep", "$sleepScore/25", theme.accentOrange)
                    }
                }
            }
        }

        // --- 5. EAT & TRACK (TREATS / DESSERTS) ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, theme.border, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🍫 Eat & Track (Treats)", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Log chocolates, ice cream or extra snacks", color = theme.textSecondary, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { showAddFoodDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accentRose),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("+ Add Treat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickTreats = listOf(
                            "🍫 Chocolate" to 230, "🍨 Ice Cream" to 210,
                            "🍦 Magnum Cone" to 280, "🍰 Sweet/Cake" to 320, "🥤 Cold Drink" to 150
                        )
                        quickTreats.forEach { (label, cal) ->
                            AssistChip(
                                onClick = { foodLogs.add(FoodItem(System.currentTimeMillis(), label, cal, LocalDate.now().toString())) },
                                label = { Text("$label (+$cal)", color = theme.textPrimary, fontSize = 12.sp) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = theme.cardSubtle)
                            )
                        }
                    }

                    if (foodLogs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        foodLogs.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(theme.cardSubtle, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.name, color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${item.calories} kcal", color = theme.accentRose, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    IconButton(onClick = { foodLogs.remove(item) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCalibrateDialog) {
        var exactGoogleFitSteps by remember { mutableStateOf(steps.toString()) }
        var targetInput by remember { mutableStateOf(stepTarget.toString()) }
        AlertDialog(
            onDismissRequest = { showCalibrateDialog = false },
            containerColor = theme.card,
            title = { Text("Sync with Google Fit & Step Goal", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = exactGoogleFitSteps,
                        onValueChange = { exactGoogleFitSteps = it },
                        label = { Text("Current Google Fit Steps Today") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it },
                        label = { Text("Minimum Daily Step Goal") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    exactGoogleFitSteps.toIntOrNull()?.let { onCalibrateExactSteps(it) }
                    targetInput.toIntOrNull()?.let { onChangeStepTarget(it.coerceAtLeast(500)) }
                    showCalibrateDialog = false
                }) { Text("Sync & Save", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showCalibrateDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSleepDialog) {
        var bedHr by remember { mutableStateOf("11") }
        var bedMin by remember { mutableStateOf("30") }
        var bedAmPm by remember { mutableStateOf("PM") }

        var wakeHr by remember { mutableStateOf("7") }
        var wakeMin by remember { mutableStateOf("00") }
        var wakeAmPm by remember { mutableStateOf("AM") }

        val previewCalculatedHours = calculateSleepHours(
            bedHr.toIntOrNull() ?: 11,
            bedMin.toIntOrNull() ?: 30,
            bedAmPm,
            wakeHr.toIntOrNull() ?: 7,
            wakeMin.toIntOrNull() ?: 0,
            wakeAmPm
        )

        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            containerColor = theme.card,
            title = { Text("😴 Enter Sleep & Wake-Up Time", color = theme.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sleep Start Time (Bedtime):", color = theme.accentPurple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = bedHr,
                            onValueChange = { bedHr = it },
                            label = { Text("Hr") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = bedMin,
                            onValueChange = { bedMin = it },
                            label = { Text("Min") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        FilledTonalButton(
                            onClick = { bedAmPm = if (bedAmPm == "PM") "AM" else "PM" },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = theme.cardSubtle)
                        ) {
                            Text(bedAmPm, color = theme.accentGreen, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text("Wake-Up Time:", color = theme.accentOrange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = wakeHr,
                            onValueChange = { wakeHr = it },
                            label = { Text("Hr") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = wakeMin,
                            onValueChange = { wakeMin = it },
                            label = { Text("Min") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        FilledTonalButton(
                            onClick = { wakeAmPm = if (wakeAmPm == "AM") "PM" else "AM" },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = theme.cardSubtle)
                        ) {
                            Text(wakeAmPm, color = theme.accentGreen, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(color = theme.cardSubtle, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "✨ Auto-Calculated Sleep: $previewCalculatedHours hours",
                            color = theme.accentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val bedFormatted = "${bedHr.padStart(2, '0')}:${bedMin.padStart(2, '0')} $bedAmPm"
                    val wakeFormatted = "${wakeHr.padStart(2, '0')}:${wakeMin.padStart(2, '0')} $wakeAmPm"
                    onUpdateSleep(previewCalculatedHours, bedFormatted, wakeFormatted)
                    showSleepDialog = false
                }) { Text("Save Sleep", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showSleepDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddFoodDialog) {
        var foodName by remember { mutableStateOf("") }
        var foodCal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddFoodDialog = false },
            containerColor = theme.card,
            title = { Text("Log Treat / Dessert", color = theme.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = foodName, onValueChange = { foodName = it }, label = { Text("Item Name") }, singleLine = true)
                    OutlinedTextField(
                        value = foodCal,
                        onValueChange = { foodCal = it },
                        label = { Text("Calories (default 250)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (foodName.isNotBlank()) {
                        foodLogs.add(FoodItem(System.currentTimeMillis(), foodName, foodCal.toIntOrNull() ?: 250, LocalDate.now().toString()))
                        showAddFoodDialog = false
                    }
                }) { Text("Add", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showAddFoodDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ScorePillarChip(title: String, score: String, color: Color) {
    val theme = LocalAppTheme.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(theme.cardSubtle)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(title, color = theme.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(score, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
    }
}

// ============================================================================
// TAB 2: WORKOUT SPLIT (ADAPTS TO LIGHT & DARK THEME)
// ============================================================================
@Composable
fun Tab2FriendlyWorkouts(
    exercisesList: List<WorkoutExercise>,
    exerciseLogs: Map<String, ExerciseLogState>,
    daily30MinByDay: Map<String, Boolean>,
    daily30MinDefaultCals: Int,
    onToggleDaily30MinForDay: (String, Boolean) -> Unit,
    onUpdateExerciseLog: (String, ExerciseLogState) -> Unit,
    onAddExercise: (WorkoutExercise) -> Unit,
    onEditExercise: (WorkoutExercise) -> Unit,
    onDeleteExercise: (String) -> Unit,
    onFinishAndArchiveWeek: () -> Unit
) {
    val theme = LocalAppTheme.current
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val todayDefaultDay = LocalDate.now().dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    var selectedDay by remember { mutableStateOf(if (todayDefaultDay in days) todayDefaultDay else "Mon") }
    var showAddWorkoutModal by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<WorkoutExercise?>(null) }

    var restSecondsLeft by remember { mutableIntStateOf(0) }
    LaunchedEffect(restSecondsLeft) {
        if (restSecondsLeft > 0) {
            delay(1000L)
            restSecondsLeft -= 1
        }
    }

    val dayTitles = mapOf(
        "Mon" to "Biceps", "Tue" to "Triceps", "Wed" to "Back/Leg",
        "Thu" to "Chest", "Fri" to "Abs", "Sat" to "Rest/+Add", "Sun" to "Rest/+Add"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 22.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.border, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (restSecondsLeft > 0) "⏱️ Rest Timer: ${restSecondsLeft}s" else "⏱️ Quick Rest Timer:",
                        color = if (restSecondsLeft > 0) theme.accentGreen else theme.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30, 60, 90).forEach { sec ->
                            AssistChip(
                                onClick = { restSecondsLeft = sec },
                                label = { Text("${sec}s", color = theme.textPrimary, fontSize = 12.sp) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = theme.cardSubtle)
                            )
                        }
                    }
                }
            }
        }

        item {
            val isDoneForSelectedDay = daily30MinByDay[selectedDay] == true
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, if (isDoneForSelectedDay) theme.accentGreen else theme.border, RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🔥 $selectedDay: 30-Min Routine & Warm-Up",
                                color = if (isDoneForSelectedDay) theme.accentGreen else theme.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                if (isDoneForSelectedDay) "Completed for $selectedDay (+$daily30MinDefaultCals kcal)"
                                else "Toggle ON when finished for $selectedDay",
                                color = if (isDoneForSelectedDay) theme.accentGreen else theme.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = isDoneForSelectedDay,
                            onCheckedChange = { checked -> onToggleDaily30MinForDay(selectedDay, checked) }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Normal Warm-up  •  1 set normal + 2 sets 5kg squats", color = theme.textPrimary, fontSize = 12.sp)
                    Text("• High Knees (2 sets)  •  Incline Pushups (3 × 15)", color = theme.textPrimary, fontSize = 12.sp)
                }
            }
        }

        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Weekly Workout Split", color = theme.textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Button(
                        onClick = { showAddWorkoutModal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accentGreen),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("+ Workout", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    days.forEach { day ->
                        val isSelected = selectedDay == day
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(66.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) theme.accentPurple else theme.card)
                                .border(1.dp, if (isSelected) theme.accentPurple else theme.border, RoundedCornerShape(16.dp))
                                .clickable { selectedDay = day }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(day, color = if (isSelected) Color.White else theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(dayTitles[day] ?: "", color = if (isSelected) Color.White.copy(alpha = 0.85f) else theme.textSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        val dayExercises = exercisesList.filter { it.day == selectedDay }
        if (dayExercises.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = theme.card),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, theme.border, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Rest & Recovery Day ($selectedDay)", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Tap '+ Workout' above if you do any custom session today.", color = theme.textSecondary, fontSize = 13.sp)
                    }
                }
            }
        }

        items(dayExercises, key = { it.id }) { exercise ->
            val rawState = exerciseLogs[exercise.id] ?: ExerciseLogState()
            val doneList = List(exercise.targetSets) { idx -> rawState.setCompletedList.getOrElse(idx) { false } }
            val weightsList = List(exercise.targetSets) { idx ->
                rawState.setWeightsList.getOrElse(idx) {
                    if (exercise.isBodyweightOrTimed) "Bodyweight" else if (idx == 0) "2.5 kg/side" else "5 kg/side"
                }
            }
            val currentState = rawState.copy(setCompletedList = doneList, setWeightsList = weightsList)
            val exerciseBurn = calculateExerciseCalories(exercise, currentState)

            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.border, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ExerciseVisualThumbnail(visualType = exercise.visualType, muscleGroup = exercise.muscleGroup)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exercise.name, color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${exercise.muscleGroup} • ${exercise.targetNote}", color = theme.textSecondary, fontSize = 12.sp)
                            Text("Prev: ${currentState.previousSetSummaryMemory}", color = theme.accentPurple, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Surface(color = theme.cardSubtle, shape = RoundedCornerShape(10.dp)) {
                            Text(
                                "$exerciseBurn kcal",
                                color = theme.accentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                        IconButton(onClick = { editingExercise = exercise }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onDeleteExercise(exercise.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = theme.accentRose, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    for (setIdx in 0 until exercise.targetSets) {
                        val isSetDone = doneList[setIdx]
                        val setWeight = weightsList[setIdx]

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(theme.cardSubtle)
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        val mutableDone = doneList.toMutableList()
                                        mutableDone[setIdx] = !isSetDone
                                        val summary = weightsList.mapIndexedNotNull { i, w ->
                                            if (mutableDone[i]) "S${i + 1}:$w" else null
                                        }.joinToString(", ").ifEmpty { currentState.previousSetSummaryMemory }

                                        onUpdateExerciseLog(
                                            exercise.id,
                                            currentState.copy(setCompletedList = mutableDone, previousSetSummaryMemory = summary)
                                        )
                                        if (!isSetDone) restSecondsLeft = 60
                                    }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(if (isSetDone) theme.accentGreen else theme.card)
                                    ) {
                                        if (isSetDone) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        } else {
                                            Text("${setIdx + 1}", color = theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Set ${setIdx + 1}",
                                        color = if (isSetDone) theme.accentGreen else theme.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Text(
                                    text = if (exercise.isBodyweightOrTimed) "Bodyweight / Timed" else "Weight: $setWeight",
                                    color = theme.accentPurple,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (!exercise.isBodyweightOrTimed) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    weightPresetOptions.forEach { (label, _) ->
                                        val selected = setWeight == label
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (selected) theme.accentPurple else theme.card)
                                                .clickable {
                                                    val mutableWeights = weightsList.toMutableList()
                                                    mutableWeights[setIdx] = label
                                                    val summary = mutableWeights.mapIndexedNotNull { i, w ->
                                                        if (doneList[i]) "S${i + 1}:$w" else null
                                                    }.joinToString(", ").ifEmpty { "S${setIdx + 1}:$label" }

                                                    onUpdateExerciseLog(
                                                        exercise.id,
                                                        currentState.copy(setWeightsList = mutableWeights, previousSetSummaryMemory = summary)
                                                    )
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (selected) Color.White else theme.textPrimary,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(onClick = onFinishAndArchiveWeek, modifier = Modifier.fillMaxWidth()) {
                Text("Archive Week to History & Reset Sets", color = theme.accentGreen, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showAddWorkoutModal) {
        var name by remember { mutableStateOf("") }
        var muscle by remember { mutableStateOf(dayTitles[selectedDay] ?: "Custom") }
        var sets by remember { mutableStateOf("3") }
        var isBodyweight by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddWorkoutModal = false },
            containerColor = theme.card,
            title = { Text("Add Exercise to $selectedDay", color = theme.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Exercise Name") }, singleLine = true)
                    OutlinedTextField(value = muscle, onValueChange = { muscle = it }, label = { Text("Muscle Group") }, singleLine = true)
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { sets = it },
                        label = { Text("Target Sets") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isBodyweight, onCheckedChange = { isBodyweight = it })
                        Text("Bodyweight / Timed", color = theme.textPrimary, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        val s = sets.toIntOrNull()?.coerceIn(1, 10) ?: 3
                        onAddExercise(
                            WorkoutExercise(
                                "custom_${System.currentTimeMillis()}", selectedDay, muscle, name, s, "$s sets", isBodyweight, 15,
                                if (isBodyweight) "bodyweight" else "dumbbell"
                            )
                        )
                        showAddWorkoutModal = false
                    }
                }) { Text("Add", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showAddWorkoutModal = false }) { Text("Cancel") }
            }
        )
    }

    editingExercise?.let { ex ->
        var name by remember { mutableStateOf(ex.name) }
        var sets by remember { mutableStateOf(ex.targetSets.toString()) }
        var note by remember { mutableStateOf(ex.targetNote) }
        AlertDialog(
            onDismissRequest = { editingExercise = null },
            containerColor = theme.card,
            title = { Text("Edit Exercise", color = theme.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { sets = it },
                        label = { Text("Sets") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val s = sets.toIntOrNull()?.coerceIn(1, 10) ?: ex.targetSets
                    onEditExercise(ex.copy(name = name, targetSets = s, targetNote = note))
                    editingExercise = null
                }) { Text("Save", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { editingExercise = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ExerciseVisualThumbnail(visualType: String, muscleGroup: String) {
    val theme = LocalAppTheme.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(theme.cardSubtle)
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val w = size.width
            val h = size.height
            when (visualType) {
                "barbell" -> {
                    drawLine(theme.accentPurple, Offset(w * 0.1f, h * 0.5f), Offset(w * 0.9f, h * 0.5f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawRoundRect(theme.accentGreen, Offset(w * 0.15f, h * 0.22f), Size(w * 0.12f, h * 0.56f), CornerRadius(4f, 4f))
                    drawRoundRect(theme.accentGreen, Offset(w * 0.73f, h * 0.22f), Size(w * 0.12f, h * 0.56f), CornerRadius(4f, 4f))
                }
                "cable" -> {
                    drawCircle(theme.accentPurple, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.22f), style = Stroke(width = 4f))
                    drawLine(theme.accentGreen, Offset(w * 0.5f, h * 0.38f), Offset(w * 0.5f, h * 0.78f), strokeWidth = 5f)
                    drawLine(theme.accentGreen, Offset(w * 0.3f, h * 0.78f), Offset(w * 0.7f, h * 0.78f), strokeWidth = 6f, cap = StrokeCap.Round)
                }
                "legs" -> {
                    drawCircle(theme.accentGreen, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.2f))
                    drawLine(theme.accentPurple, Offset(w * 0.5f, h * 0.32f), Offset(w * 0.35f, h * 0.6f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawLine(theme.accentGreen, Offset(w * 0.35f, h * 0.6f), Offset(w * 0.55f, h * 0.88f), strokeWidth = 6f, cap = StrokeCap.Round)
                }
                "bodyweight" -> {
                    drawCircle(theme.accentGreen, radius = w * 0.11f, center = Offset(w * 0.22f, h * 0.35f))
                    drawLine(theme.accentPurple, Offset(w * 0.32f, h * 0.42f), Offset(w * 0.85f, h * 0.68f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawLine(theme.accentGreen, Offset(w * 0.32f, h * 0.42f), Offset(w * 0.28f, h * 0.75f), strokeWidth = 5f, cap = StrokeCap.Round)
                }
                else -> {
                    drawLine(theme.accentPurple, Offset(w * 0.25f, h * 0.5f), Offset(w * 0.75f, h * 0.5f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawRoundRect(theme.accentGreen, Offset(w * 0.14f, h * 0.26f), Size(w * 0.14f, h * 0.48f), CornerRadius(6f, 6f))
                    drawRoundRect(theme.accentGreen, Offset(w * 0.72f, h * 0.26f), Size(w * 0.14f, h * 0.48f), CornerRadius(6f, 6f))
                }
            }
        }
        Text(
            muscleGroup.take(4).uppercase(),
            color = theme.textSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)
        )
    }
}

// ============================================================================
// TAB 3: HISTORY
// ============================================================================
@Composable
fun Tab3FriendlyHistory(
    workoutHistoryList: List<WorkoutHistoryEntry>,
    stepHistoryList: List<StepHistoryEntry>,
    onArchiveCurrentWeekNow: () -> Unit
) {
    val theme = LocalAppTheme.current
    var selectedSubTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Workout & Step History", color = theme.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Auto-saves & resets every Monday", color = theme.textSecondary, fontSize = 13.sp)
            }
            TextButton(onClick = onArchiveCurrentWeekNow) {
                Text("Archive Week", color = theme.accentGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(theme.card)
                .border(1.dp, theme.border, RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            listOf("Workout History", "Step Count History").forEachIndexed { idx, title ->
                val active = selectedSubTab == idx
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) theme.accentPurple else Color.Transparent)
                        .clickable { selectedSubTab = idx }
                        .padding(vertical = 10.dp)
                ) {
                    Text(title, color = if (active) Color.White else theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedSubTab == 0) {
            if (workoutHistoryList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No archived workouts yet.", color = theme.textSecondary, fontSize = 14.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(workoutHistoryList, key = { it.id }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = theme.card),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, theme.border, RoundedCornerShape(18.dp))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.exerciseName, color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${item.date} (${item.dayName}) • ${item.setsDone} sets • ${item.weightUsed}", color = theme.textSecondary, fontSize = 12.sp)
                                }
                                Text("${item.caloriesBurned} kcal", color = theme.accentGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(stepHistoryList, key = { it.date }) { entry ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = theme.card),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, theme.border, RoundedCornerShape(18.dp))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(entry.date, color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Goal: ${entry.goal} steps", color = theme.textSecondary, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${entry.steps} steps", color = theme.accentPurple, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${entry.calories} kcal", color = theme.accentGreen, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// TAB 4: SETTINGS (WITH THEME SWITCHER)
// ============================================================================
@Composable
fun Tab4FriendlySettings(
    stepTarget: Int,
    userBodyWeightKg: Int,
    daily30MinDefaultCals: Int,
    waterGoalMl: Int,
    notificationsEnabled: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onUpdateStepTarget: (Int) -> Unit,
    onUpdateBodyWeight: (Int) -> Unit,
    onUpdateDaily30Cals: (Int) -> Unit,
    onUpdateWaterGoal: (Int) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onTestSystemNotification: () -> Unit,
    onResetDefaultWorkouts: () -> Unit
) {
    val theme = LocalAppTheme.current
    var goalText by remember(stepTarget) { mutableStateOf(stepTarget.toString()) }
    var weightText by remember(userBodyWeightKg) { mutableStateOf(userBodyWeightKg.toString()) }
    var routineCalText by remember(daily30MinDefaultCals) { mutableStateOf(daily30MinDefaultCals.toString()) }
    var waterGoalText by remember(waterGoalMl) { mutableStateOf(waterGoalMl.toString()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Settings & Appearance", color = theme.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("Customize Light/Dark mode, goals & system alerts", color = theme.textSecondary, fontSize = 13.sp)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.border, RoundedCornerShape(22.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dark Theme (App & Widgets)", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Switch between Clean Light Mode and True Dark Mode", color = theme.textSecondary, fontSize = 12.sp)
                    }
                    Switch(checked = isDarkTheme, onCheckedChange = onToggleTheme)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.border, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Daily Goals & Calibration", color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    OutlinedTextField(
                        value = goalText,
                        onValueChange = { goalText = it; it.toIntOrNull()?.let(onUpdateStepTarget) },
                        label = { Text("Minimum Step Target") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = waterGoalText,
                        onValueChange = { waterGoalText = it; it.toIntOrNull()?.let(onUpdateWaterGoal) },
                        label = { Text("Daily Water Goal (ml)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it; it.toIntOrNull()?.let(onUpdateBodyWeight) },
                        label = { Text("Body Weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = routineCalText,
                        onValueChange = { routineCalText = it; it.toIntOrNull()?.let(onUpdateDaily30Cals) },
                        label = { Text("Daily 30-Min Routine Default Burn (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, theme.border, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Exact System Notifications", color = theme.textPrimary, fontWeight = FontWeight.Bold)
                            Text("• Workouts: 10:30 AM & 4:00 PM\n• Water Kickoff: 7:30 AM\n• Hourly Water: 11:00 AM – 9:00 PM\n• 4-Hour Water Level Check Notification", color = theme.textSecondary, fontSize = 11.sp)
                        }
                        Switch(checked = notificationsEnabled, onCheckedChange = onToggleNotifications)
                    }
                    Button(
                        onClick = onTestSystemNotification,
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accentPurple),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Send Test System Notification Now", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            OutlinedButton(onClick = onResetDefaultWorkouts, modifier = Modifier.fillMaxWidth()) {
                Text("Restore Default Mon–Fri Exercises", color = theme.accentRose, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String, accent: Color) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = theme.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text(value, color = theme.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
