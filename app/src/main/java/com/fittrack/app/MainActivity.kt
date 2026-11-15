package com.fittrack.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.graphics.Brush
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
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.Calendar
import kotlin.math.roundToInt

// --- OPTION B: FRIENDLY COZY SLATE & PASTEL PALETTE ---
val BgCozySlate = Color(0xFF1E2235)
val CardSlate = Color(0xFF282D46)
val CardElevated = Color(0xFF323854)
val PastelMint = Color(0xFF6EE7B7)
val PastelLavender = Color(0xFFC4B5FD)
val PastelCoral = Color(0xFFFDA4AF)
val PastelPeach = Color(0xFFFDBA74)
val PastelSky = Color(0xFF93C5FD)
val TextSoftWhite = Color(0xFFF8FAFC)
val TextMuted = Color(0xFF94A3B8)

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
    val completedSets: Int = 0,
    val selectedWeightLabel: String = "5 kg/side",
    val loadMultiplierKg: Float = 10f,
    val previousWeightMemory: String = "5 kg/side"
)

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

// --- DEFAULT MON-SUN WORKOUT PLAN ---
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

val weightPresetOptions = listOf(
    "Empty Bar" to 0f,
    "5 kg/side" to 10f,
    "7.5 kg/side" to 15f,
    "10 kg/side" to 20f,
    "5 kg DB" to 10f,
    "7.5 kg DB" to 15f,
    "10 kg DB" to 20f,
    "12.5 kg DB" to 25f,
    "15 kg DB" to 30f
)

// ============================================================================
// MAIN ACTIVITY (WITH PERSISTENT 24/7 STEP SENSOR & SCHEDULED ALARMS)
// ============================================================================
class MainActivity : ComponentActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var sensorStepsToday by mutableIntStateOf(0)
    private var manualStepsToday by mutableIntStateOf(0)
    private var lastRawHardwareSteps = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // Register hardware step counter continuously (BATCHING = 0 for real-time accuracy)
        stepSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, 0)
        }

        val prefs = getSharedPreferences("fittrack_pro_prefs", Context.MODE_PRIVATE)
        val todayKey = LocalDate.now().toString()

        sensorStepsToday = prefs.getInt("sensor_steps_$todayKey", 0)
        manualStepsToday = prefs.getInt("manual_steps_$todayKey", 0)

        createNotificationChannel()
        scheduleAllDailyAlarms(this)

        setContent {
            val totalLiveSteps = (sensorStepsToday + manualStepsToday).coerceAtLeast(0)

            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgCozySlate,
                    surface = CardSlate,
                    primary = PastelMint,
                    secondary = PastelLavender
                )
            ) {
                FitTrackMainRoot(
                    prefs = prefs,
                    steps = totalLiveSteps,
                    onManualAddSteps = { added ->
                        val currentKey = LocalDate.now().toString()
                        manualStepsToday = (manualStepsToday + added).coerceAtLeast(0)
                        val combined = (sensorStepsToday + manualStepsToday).coerceAtLeast(0)
                        prefs.edit()
                            .putInt("manual_steps_$currentKey", manualStepsToday)
                            .putInt("steps_$currentKey", combined)
                            .apply()
                        updateAllHomeWidgets(this@MainActivity)
                    },
                    onCalibrateExactSteps = { exactTargetSteps ->
                        // Matches Google Fit 100%: sets manual offset so total equals Google Fit and hardware sensor continues from there
                        val currentKey = LocalDate.now().toString()
                        manualStepsToday = exactTargetSteps - sensorStepsToday
                        prefs.edit()
                            .putInt("manual_steps_$currentKey", manualStepsToday)
                            .putInt("steps_$currentKey", exactTargetSteps)
                            .apply()
                        updateAllHomeWidgets(this@MainActivity)
                    },
                    onRefreshWidgets = {
                        updateAllHomeWidgets(this@MainActivity)
                    }
                )
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "fittrack_reminders",
                "Workout & Hydration Schedule",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // IMPORTANT FIX: Do NOT unregister step sensor in onPause() so steps keep counting while screen is off!
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toInt()
            lastRawHardwareSteps = totalStepsSinceBoot

            val prefs = getSharedPreferences("fittrack_pro_prefs", Context.MODE_PRIVATE)
            val todayKey = LocalDate.now().toString()
            var savedBootOffset = prefs.getInt("offset_$todayKey", -1)

            // If new day started or phone rebooted, lock baseline accurately
            if (savedBootOffset == -1 || totalStepsSinceBoot < savedBootOffset) {
                savedBootOffset = totalStepsSinceBoot - sensorStepsToday.coerceAtLeast(0)
                prefs.edit().putInt("offset_$todayKey", savedBootOffset).apply()
            }

            sensorStepsToday = (totalStepsSinceBoot - savedBootOffset).coerceAtLeast(0)
            manualStepsToday = prefs.getInt("manual_steps_$todayKey", 0)
            val totalToday = (sensorStepsToday + manualStepsToday).coerceAtLeast(0)

            // Record timestamps for automatic sleep estimation (last movement at night / first step in morning)
            val nowHour = LocalTime.now().hour
            val editor = prefs.edit()
                .putInt("sensor_steps_$todayKey", sensorStepsToday)
                .putInt("steps_$todayKey", totalToday)

            if (nowHour in 5..10 && !prefs.contains("first_step_time_$todayKey")) {
                editor.putString("first_step_time_$todayKey", LocalTime.now().toString().take(5))
            }
            editor.putString("last_active_time", LocalTime.now().toString().take(5)).apply()

            updateAllHomeWidgets(this)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

// ============================================================================
// SCHEDULED NOTIFICATIONS (10:30 AM & 4:00 PM WORKOUT + 7:30 AM & 11 AM+ WATER)
// ============================================================================
fun scheduleAllDailyAlarms(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun setDailyRepeatingAlarm(requestCode: Int, hour: Int, minute: Int, type: String) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("REMINDER_TYPE", type)
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
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    // 1. Workout Not Updated Checks: 10:30 AM and 4:00 PM (16:00)
    setDailyRepeatingAlarm(101, 10, 30, "WORKOUT_CHECK_MORNING")
    setDailyRepeatingAlarm(102, 16, 0, "WORKOUT_CHECK_AFTERNOON")

    // 2. Water Reminders: 7:30 AM Kickoff + Every 1 hour starting from 11:00 AM to 9:00 PM
    setDailyRepeatingAlarm(200, 7, 30, "WATER_MORNING_730")
    for (hr in 11..21) {
        setDailyRepeatingAlarm(200 + hr, hr, 0, "WATER_HOURLY")
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("fittrack_pro_prefs", Context.MODE_PRIVATE)
        val todayStr = LocalDate.now().toString()

        // Handle Home Screen Widget Quick Buttons
        when (intent.action) {
            "com.fittrack.app.ACTION_WIDGET_ADD_STEPS" -> {
                val manual = prefs.getInt("manual_steps_$todayStr", 0) + 500
                val sensor = prefs.getInt("sensor_steps_$todayStr", 0)
                prefs.edit()
                    .putInt("manual_steps_$todayStr", manual)
                    .putInt("steps_$todayStr", sensor + manual)
                    .apply()
                updateAllHomeWidgets(context)
                return
            }
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

        if (!prefs.getBoolean("notif_enabled", true)) return

        val type = intent.getStringExtra("REMINDER_TYPE") ?: return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openAppIntent = Intent(context, MainActivity::class.java)
        val contentPending = PendingIntent.getActivity(
            context, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        when (type) {
            "WORKOUT_CHECK_MORNING", "WORKOUT_CHECK_AFTERNOON" -> {
                val loggedToday = prefs.getBoolean("workout_updated_$todayStr", false)
                if (!loggedToday) {
                    val timeLabel = if (type == "WORKOUT_CHECK_MORNING") "10:30 AM" else "4:00 PM"
                    val notif = NotificationCompat.Builder(context, "fittrack_reminders")
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("🏋️ $timeLabel Workout Check-In")
                        .setContentText("You haven't updated today's workout sets yet! Tap to log your progress.")
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
                    .setContentTitle("💧 7:30 AM Morning Hydration!")
                    .setContentText("Good morning! Start your day with a glass of water (250 ml).")
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
                    .setContentText("Time to drink a glass of water! Logged today: $currentWater ml.")
                    .setContentIntent(contentPending)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                manager.notify(3003, notif)
            }
        }
    }
}

// ============================================================================
// 3 HOME SCREEN WIDGET PROVIDERS (2x2, 4x2, 4x3)
// ============================================================================
fun calculateHealthScoreFromPrefs(prefs: SharedPreferences, todayStr: String): Int {
    val steps = prefs.getInt("steps_$todayStr", 0)
    val stepGoal = prefs.getInt("step_target", 8000).coerceAtLeast(1)
    val waterMl = prefs.getInt("water_ml_$todayStr", 0)
    val sleepHrs = prefs.getFloat("sleep_hrs_$todayStr", 7.5f)
    val workoutBurn = prefs.getInt("workout_burn_$todayStr", 185)

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

    val steps = prefs.getInt("steps_$todayStr", 0)
    val stepGoal = prefs.getInt("step_target", 8000)
    val stepBurn = (steps * 0.04f).roundToInt()
    val workoutBurn = prefs.getInt("workout_burn_$todayStr", 185)
    val totalBurn = stepBurn + workoutBurn
    val waterMl = prefs.getInt("water_ml_$todayStr", 0)
    val sleepHrs = prefs.getFloat("sleep_hrs_$todayStr", 7.5f)
    val score = calculateHealthScoreFromPrefs(prefs, todayStr)

    val openAppIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // 1. Update 2x2 Widgets
    val ids2x2 = manager.getAppWidgetIds(ComponentName(context, WidgetCompact2x2::class.java))
    for (id in ids2x2) {
        val views = RemoteViews(context.packageName, R.layout.widget_2x2)
        views.setTextViewText(R.id.w2x2_steps, "$steps")
        views.setTextViewText(R.id.w2x2_cals, "🔥 $totalBurn kcal")
        views.setTextViewText(R.id.w2x2_score, "Health Score: $score/100")
        views.setOnClickPendingIntent(R.id.widget_root_2x2, openAppIntent)
        manager.updateAppWidget(id, views)
    }

    // 2. Update 4x2 Widgets
    val ids4x2 = manager.getAppWidgetIds(ComponentName(context, WidgetSummary4x2::class.java))
    for (id in ids4x2) {
        val views = RemoteViews(context.packageName, R.layout.widget_4x2)
        views.setTextViewText(R.id.w4x2_total_cal, "🔥 $totalBurn kcal")
        views.setTextViewText(R.id.w4x2_steps_line, "👟 Steps: $steps / $stepGoal ($stepBurn kcal)")
        views.setTextViewText(R.id.w4x2_workout_line, "🏋️ 30m Routine + Split: $workoutBurn kcal")
        views.setTextViewText(R.id.w4x2_water_sleep_line, "💧 Water: $waterMl ml  •  😴 Sleep: $sleepHrs hrs")
        views.setOnClickPendingIntent(R.id.widget_root_4x2, openAppIntent)
        manager.updateAppWidget(id, views)
    }

    // 3. Update 4x3 Interactive Widgets
    val ids4x3 = manager.getAppWidgetIds(ComponentName(context, WidgetDashboard4x3::class.java))
    val addStepsPending = PendingIntent.getBroadcast(
        context, 501, Intent(context, ReminderReceiver::class.java).apply { action = "com.fittrack.app.ACTION_WIDGET_ADD_STEPS" },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val addWaterPending = PendingIntent.getBroadcast(
        context, 502, Intent(context, ReminderReceiver::class.java).apply { action = "com.fittrack.app.ACTION_WIDGET_ADD_WATER" },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    for (id in ids4x3) {
        val views = RemoteViews(context.packageName, R.layout.widget_4x3)
        views.setTextViewText(R.id.w4x3_score, "Score: $score/100")
        views.setTextViewText(R.id.w4x3_summary, "👟 $steps steps  |  🔥 $totalBurn kcal burned")
        views.setTextViewText(R.id.w4x3_sub, "💧 Water: $waterMl / 3000 ml   •   😴 Sleep: $sleepHrs hrs")
        views.setOnClickPendingIntent(R.id.widget_root_4x3, openAppIntent)
        views.setOnClickPendingIntent(R.id.btn_widget_steps, addStepsPending)
        views.setOnClickPendingIntent(R.id.btn_widget_water, addWaterPending)
        manager.updateAppWidget(id, views)
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

// ============================================================================
// COMPOSABLE ROOT (OPTION B FRIENDLY SLATE UI)
// ============================================================================
@Composable
fun FitTrackMainRoot(
    prefs: SharedPreferences,
    steps: Int,
    onManualAddSteps: (Int) -> Unit,
    onCalibrateExactSteps: (Int) -> Unit,
    onRefreshWidgets: () -> Unit
) {
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

    // --- STATE VARIABLES ---
    var stepTarget by remember { mutableIntStateOf(prefs.getInt("step_target", 8000)) }
    var userBodyWeightKg by remember { mutableIntStateOf(prefs.getInt("body_weight_kg", 70)) }
    var daily30MinDefaultCals by remember { mutableIntStateOf(prefs.getInt("daily_30m_cals", 185)) }
    var daily30MinCompletedToday by remember { mutableStateOf(prefs.getBoolean("daily30_done_$todayStr", true)) }
    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("notif_enabled", true)) }

    // Water & Sleep State
    var waterIntakeMl by remember { mutableIntStateOf(prefs.getInt("water_ml_$todayStr", 0)) }
    var waterGoalMl by remember { mutableIntStateOf(prefs.getInt("water_goal_ml", 3000)) }
    var sleepHoursToday by remember { mutableFloatStateOf(prefs.getFloat("sleep_hrs_$todayStr", 7.5f)) }
    var sleepBedtime by remember { mutableStateOf(prefs.getString("sleep_bed_$todayStr", "11:30 PM") ?: "11:30 PM") }
    var sleepWakeTime by remember {
        val autoWake = prefs.getString("first_step_time_$todayStr", "07:00 AM") ?: "07:00 AM"
        mutableStateOf(prefs.getString("sleep_wake_$todayStr", autoWake) ?: autoWake)
    }

    // 4-Hour In-App Water Check-in Dialog
    var show4HourWaterCheckIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val lastCheckMs = prefs.getLong("last_water_check_ms", 0L)
        val fourHoursMs = 4 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - lastCheckMs >= fourHoursMs) {
            show4HourWaterCheckIn = true
        }
    }

    // --- EXERCISES & LOGS ---
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
                put("id", ex.id)
                put("day", ex.day)
                put("muscleGroup", ex.muscleGroup)
                put("name", ex.name)
                put("targetSets", ex.targetSets)
                put("targetNote", ex.targetNote)
                put("isBody", ex.isBodyweightOrTimed)
                put("baseCal", ex.baseCalPerSet)
                put("visualType", ex.visualType)
            })
        }
        prefs.edit().putString("custom_exercises_json", arr.toString()).apply()
    }

    val exerciseLogs = remember { mutableStateMapOf<String, ExerciseLogState>() }

    fun loadLogsFromPrefs() {
        exercisesList.forEach { ex ->
            val sets = prefs.getInt("log_sets_${ex.id}", 0)
            val weightLbl = prefs.getString("log_wlbl_${ex.id}", if (ex.isBodyweightOrTimed) "Bodyweight" else "5 kg/side") ?: "5 kg/side"
            val mult = prefs.getFloat("log_mult_${ex.id}", if (ex.isBodyweightOrTimed) 0f else 10f)
            val prevMem = prefs.getString("mem_weight_${ex.id}", "5 kg/side") ?: "5 kg/side"
            exerciseLogs[ex.id] = ExerciseLogState(sets, weightLbl, mult, prevMem)
        }
    }

    fun saveSingleLogToPrefs(id: String, state: ExerciseLogState) {
        exerciseLogs[id] = state
        prefs.edit()
            .putInt("log_sets_$id", state.completedSets)
            .putString("log_wlbl_$id", state.selectedWeightLabel)
            .putFloat("log_mult_$id", state.loadMultiplierKg)
            .putString("mem_weight_$id", state.previousWeightMemory)
            .putBoolean("workout_updated_$todayStr", true) // Marks today's workout as updated so 10:30 AM / 4:00 PM alert knows!
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
            if (st != null && st.completedSets > 0) {
                val cals = (st.completedSets * (ex.baseCalPerSet + (st.loadMultiplierKg * 0.45f))).roundToInt()
                workoutHistoryList.add(
                    0,
                    WorkoutHistoryEntry(
                        System.currentTimeMillis() + ex.id.hashCode(),
                        todayStr, ex.day, ex.name, st.completedSets, st.selectedWeightLabel, cals
                    )
                )
                saveSingleLogToPrefs(ex.id, st.copy(completedSets = 0, previousWeightMemory = st.selectedWeightLabel))
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

    // Notice: Removed the automatic notification trigger on app launch!
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

    // --- CALORIES & HEALTH SCORE (0 - 100) ---
    val stepCalories = (steps * (userBodyWeightKg / 70f) * 0.04f).roundToInt()
    val daily30MinCalories = if (daily30MinCompletedToday) daily30MinDefaultCals else 0
    val splitWorkoutCalories = exercisesList.sumOf { ex ->
        val st = exerciseLogs[ex.id] ?: ExerciseLogState()
        (st.completedSets * (ex.baseCalPerSet + (st.loadMultiplierKg * 0.45f))).roundToInt()
    }
    val totalWorkoutBurn = daily30MinCalories + splitWorkoutCalories
    val totalBurnedCalories = stepCalories + totalWorkoutBurn
    val totalEatenCalories = foodLogs.sumOf { it.calories }

    // Save workout burn to prefs for Home Screen Widgets
    LaunchedEffect(totalWorkoutBurn, steps, waterIntakeMl, sleepHoursToday) {
        prefs.edit().putInt("workout_burn_$todayStr", totalWorkoutBurn).apply()
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

    // Health Score Calculation (25 pts Steps + 25 pts Workout + 25 pts Water + 25 pts Sleep)
    val stepScore = ((steps.toFloat() / stepTarget.coerceAtLeast(1)) * 25f).coerceIn(0f, 25f).roundToInt()
    val workoutScore = ((totalWorkoutBurn.toFloat() / 300f) * 25f).coerceIn(0f, 25f).roundToInt()
    val waterScore = ((waterIntakeMl.toFloat() / waterGoalMl.coerceAtLeast(1)) * 25f).coerceIn(0f, 25f).roundToInt()
    val sleepScore = ((sleepHoursToday / 7.5f) * 25f).coerceIn(0f, 25f).roundToInt()
    val totalHealthScore = (stepScore + workoutScore + waterScore + sleepScore).coerceIn(0, 100)

    var currentTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = BgCozySlate,
        bottomBar = {
            NavigationBar(containerColor = CardSlate, tonalElevation = 10.dp) {
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
                            selectedIconColor = BgCozySlate,
                            selectedTextColor = PastelMint,
                            indicatorColor = PastelMint,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (currentTab) {
                0 -> Tab1FriendlyDashboard(
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
                    onManualAddSteps = onManualAddSteps,
                    onCalibrateExactSteps = onCalibrateExactSteps,
                    onChangeStepTarget = { newGoal ->
                        stepTarget = newGoal
                        prefs.edit().putInt("step_target", newGoal).apply()
                    },
                    onAddWaterMl = { addedMl ->
                        waterIntakeMl = (waterIntakeMl + addedMl).coerceAtLeast(0)
                        prefs.edit()
                            .putInt("water_ml_$todayStr", waterIntakeMl)
                            .putLong("last_water_check_ms", System.currentTimeMillis())
                            .apply()
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
                    daily30MinCompletedToday = daily30MinCompletedToday,
                    daily30MinDefaultCals = daily30MinDefaultCals,
                    onToggleDaily30Min = { checked ->
                        daily30MinCompletedToday = checked
                        prefs.edit()
                            .putBoolean("daily30_done_$todayStr", checked)
                            .putBoolean("workout_updated_$todayStr", true)
                            .apply()
                    },
                    onUpdateExerciseLog = { id, newState -> saveSingleLogToPrefs(id, newState) },
                    onAddExercise = { newEx ->
                        exercisesList.add(newEx)
                        saveExercisesToPrefs()
                        saveSingleLogToPrefs(newEx.id, ExerciseLogState(0, if (newEx.isBodyweightOrTimed) "Bodyweight" else "5 kg/side", 10f, "New"))
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

    // --- 4-HOUR AUTOMATIC WATER CHECK-IN POPUP ---
    if (show4HourWaterCheckIn) {
        AlertDialog(
            onDismissRequest = {
                prefs.edit().putLong("last_water_check_ms", System.currentTimeMillis()).apply()
                show4HourWaterCheckIn = false
            },
            containerColor = CardSlate,
            title = { Text("💧 4-Hour Hydration Check-In!", color = TextSoftWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "It's been 4 hours! How much water did you drink recently? (Current today: $waterIntakeMl / $waterGoalMl ml)",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(250, 500, 750).forEach { ml ->
                            Button(
                                onClick = {
                                    waterIntakeMl += ml
                                    prefs.edit()
                                        .putInt("water_ml_$todayStr", waterIntakeMl)
                                        .putLong("last_water_check_ms", System.currentTimeMillis())
                                        .apply()
                                    onRefreshWidgets()
                                    show4HourWaterCheckIn = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PastelSky),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("+${ml}ml", color = BgCozySlate, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putLong("last_water_check_ms", System.currentTimeMillis()).apply()
                    show4HourWaterCheckIn = false
                }) {
                    Text("Skip for Now", color = PastelLavender)
                }
            }
        )
    }
}

// ============================================================================
// TAB 1: OPTION B FRIENDLY DASHBOARD (HEALTH SCORE, DUAL RINGS, WATER, SLEEP, TREATS)
// ============================================================================
@Composable
fun Tab1FriendlyDashboard(
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
    onManualAddSteps: (Int) -> Unit,
    onCalibrateExactSteps: (Int) -> Unit,
    onChangeStepTarget: (Int) -> Unit,
    onAddWaterMl: (Int) -> Unit,
    onUpdateSleep: (Float, String, String) -> Unit
) {
    var showCalibrateDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showAddFoodDialog by remember { mutableStateOf(false) }
    val distanceKm = String.format("%.2f", steps * 0.0007f)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 18.dp)
    ) {
        // --- 1. TOP GREETING & DAILY HEALTH SCORE CARD ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Daily Health Score", color = PastelLavender, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "$totalHealthScore / 100",
                                color = TextSoftWhite,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                when {
                                    totalHealthScore >= 85 -> "🌟 Peak Recovery & Activity!"
                                    totalHealthScore >= 65 -> "👍 Solid Daily Rhythm"
                                    else -> "💪 Keep moving & hydrating today!"
                                },
                                color = PastelMint,
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(CardElevated)
                        ) {
                            CircularProgressIndicator(
                                progress = { totalHealthScore / 100f },
                                modifier = Modifier.fillMaxSize(),
                                color = PastelMint,
                                strokeWidth = 7.dp,
                                strokeCap = StrokeCap.Round
                            )
                            Text("$totalHealthScore%", color = TextSoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4 Score Pillars
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ScorePillarChip("👟 Steps", "$stepScore/25", PastelLavender)
                        ScorePillarChip("🏋️ Gym", "$workoutScore/25", PastelMint)
                        ScorePillarChip("💧 Water", "$waterScore/25", PastelSky)
                        ScorePillarChip("😴 Sleep", "$sleepScore/25", PastelPeach)
                    }
                }
            }
        }

        // --- 2. OPTION B DUAL PASTEL RINGS (PEDOMETER + CALORIE BURN SUMMARY) ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pedometer & Calorie Stats", color = TextSoftWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        TextButton(onClick = { showCalibrateDialog = true }) {
                            Text("Sync Google Fit / Goal", color = PastelLavender, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Side-by-Side Option B Pastel Rings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Ring: Lavender Step Gauge
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxSize(),
                                color = CardElevated,
                                strokeWidth = 12.dp
                            )
                            CircularProgressIndicator(
                                progress = { (steps.toFloat() / stepTarget.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxSize(),
                                color = PastelLavender,
                                strokeWidth = 12.dp,
                                strokeCap = StrokeCap.Round
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$steps", color = TextSoftWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                Text("/ $stepTarget steps", color = TextMuted, fontSize = 10.sp)
                                Text("$distanceKm km", color = PastelLavender, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Right Ring: Mint Active Calorie Burn Gauge
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxSize(),
                                color = CardElevated,
                                strokeWidth = 12.dp
                            )
                            CircularProgressIndicator(
                                progress = { (totalBurnedCalories / 600f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxSize(),
                                color = PastelMint,
                                strokeWidth = 12.dp,
                                strokeCap = StrokeCap.Round
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$totalBurnedCalories", color = TextSoftWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                Text("kcal burned", color = TextMuted, fontSize = 10.sp)
                                Text("Net: ${totalBurnedCalories - totalEatenCalories}", color = PastelMint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Breakdown tiles matching Option B
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatRow("Walk Burn", "$stepCalories kcal", PastelLavender)
                        StatRow("30m Routine", "$daily30MinCalories kcal", PastelPeach)
                        StatRow("Gym Split", "$splitWorkoutCalories kcal", PastelMint)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onManualAddSteps(500) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = CardElevated)
                        ) {
                            Text("+500 Steps", color = PastelLavender, fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = { showCalibrateDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = CardElevated)
                        ) {
                            Text("Match Google Fit", color = PastelMint, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- 3. HYDRATION / WATER TRACKER CARD (7:30 AM + 11 AM HOURLY + 4H CHECK-IN) ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("💧 Water & Hydration Tracker", color = TextSoftWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Reminders: 7:30 AM & hourly from 11:00 AM", color = TextMuted, fontSize = 11.sp)
                        }
                        Text(
                            "$waterIntakeMl / $waterGoalMl ml",
                            color = PastelSky,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { (waterIntakeMl.toFloat() / waterGoalMl.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50)),
                        color = PastelSky,
                        trackColor = CardElevated
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onAddWaterMl(250) },
                            colors = ButtonDefaults.buttonColors(containerColor = PastelSky),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🥛 +250 ml", color = BgCozySlate, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onAddWaterMl(500) },
                            colors = ButtonDefaults.buttonColors(containerColor = PastelMint),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🍶 +500 ml", color = BgCozySlate, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { onAddWaterMl(-250) },
                            modifier = Modifier.weight(0.7f)
                        ) {
                            Text("-250", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- 4. SLEEP SCHEDULE & RECOVERY TRACKER ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("😴 Sleep Schedule & Recovery", color = TextSoftWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Slept: $sleepBedtime  •  Woke: $sleepWakeTime", color = PastelPeach, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { showSleepDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CardElevated),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text("${sleepHoursToday}h (Edit)", color = PastelPeach, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // --- 5. EAT & TRACK (CHOCOLATES / ICE CREAMS / DESSERTS) ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("🍫 Eat & Track (Treats / Desserts)", color = TextSoftWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Log only when eating chocolates, ice cream or extras", color = TextMuted, fontSize = 11.sp)
                        }
                        Button(
                            onClick = { showAddFoodDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PastelCoral),
                            shape = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("+ Add Treat", color = BgCozySlate, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickTreats = listOf(
                            "🍫 Chocolate" to 230,
                            "🍨 Ice Cream" to 210,
                            "🍦 Magnum Cone" to 280,
                            "🍰 Sweet/Cake" to 320,
                            "🥤 Cold Drink" to 150
                        )
                        quickTreats.forEach { (label, cal) ->
                            AssistChip(
                                onClick = { foodLogs.add(FoodItem(System.currentTimeMillis(), label, cal, LocalDate.now().toString())) },
                                label = { Text("$label (+$cal)", color = TextSoftWhite, fontSize = 12.sp) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = CardElevated)
                            )
                        }
                    }

                    if (foodLogs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        foodLogs.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .background(CardElevated, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.name, color = TextSoftWhite, fontSize = 13.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${item.calories} kcal", color = PastelCoral, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    IconButton(onClick = { foodLogs.remove(item) }, modifier = Modifier.size(26.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- GOOGLE FIT CALIBRATION & STEP GOAL DIALOG ---
    if (showCalibrateDialog) {
        var exactGoogleFitSteps by remember { mutableStateOf(steps.toString()) }
        var targetInput by remember { mutableStateOf(stepTarget.toString()) }
        AlertDialog(
            onDismissRequest = { showCalibrateDialog = false },
            containerColor = CardSlate,
            title = { Text("Sync with Google Fit & Step Goal", color = TextSoftWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "If Google Fit shows more steps from earlier today, enter your current Google Fit step count below once. The hardware sensor will lock onto it and count every step accurately!",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
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
                }) { Text("Sync & Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCalibrateDialog = false }) { Text("Cancel") }
            }
        )
    }

    // --- SLEEP SCHEDULE DIALOG ---
    if (showSleepDialog) {
        var hrsInput by remember { mutableStateOf(sleepHoursToday.toString()) }
        var bedInput by remember { mutableStateOf(sleepBedtime) }
        var wakeInput by remember { mutableStateOf(sleepWakeTime) }
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            containerColor = CardSlate,
            title = { Text("Update Sleep Schedule", color = TextSoftWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = bedInput,
                        onValueChange = { bedInput = it },
                        label = { Text("When did you sleep? (e.g. 11:30 PM)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = wakeInput,
                        onValueChange = { wakeInput = it },
                        label = { Text("When did you wake up? (e.g. 07:00 AM)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = hrsInput,
                        onValueChange = { hrsInput = it },
                        label = { Text("Total Sleep Hours (e.g. 7.5)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val h = hrsInput.toFloatOrNull()?.coerceIn(1f, 16f) ?: 7.5f
                    onUpdateSleep(h, bedInput, wakeInput)
                    showSleepDialog = false
                }) { Text("Save Sleep") }
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
            containerColor = CardSlate,
            title = { Text("Log Treat / Dessert", color = TextSoftWhite) },
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
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddFoodDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ScorePillarChip(title: String, score: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardElevated)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(title, color = TextMuted, fontSize = 11.sp)
        Text(score, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

// ============================================================================
// TAB 2: WORKOUT SPLIT + BUILT-IN REST TIMER + VISUALS
// ============================================================================
@Composable
fun Tab2FriendlyWorkouts(
    exercisesList: List<WorkoutExercise>,
    exerciseLogs: Map<String, ExerciseLogState>,
    daily30MinCompletedToday: Boolean,
    daily30MinDefaultCals: Int,
    onToggleDaily30Min: (Boolean) -> Unit,
    onUpdateExerciseLog: (String, ExerciseLogState) -> Unit,
    onAddExercise: (WorkoutExercise) -> Unit,
    onEditExercise: (WorkoutExercise) -> Unit,
    onDeleteExercise: (String) -> Unit,
    onFinishAndArchiveWeek: () -> Unit
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    var selectedDay by remember { mutableStateOf("Mon") }
    var showAddWorkoutModal by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<WorkoutExercise?>(null) }

    // BONUS RECOMMENDATION: Built-in Gym Rest Timer (30s / 60s / 90s)
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 18.dp)
    ) {
        // ACTIVE REST TIMER BAR (Bonus Feature)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (restSecondsLeft > 0) "⏱️ Rest Timer: ${restSecondsLeft}s" else "⏱️ Quick Rest Timer:",
                        color = if (restSecondsLeft > 0) PastelMint else TextSoftWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(30, 60, 90).forEach { sec ->
                            AssistChip(
                                onClick = { restSecondsLeft = sec },
                                label = { Text("${sec}s", color = TextSoftWhite, fontSize = 11.sp) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = CardElevated)
                            )
                        }
                    }
                }
            }
        }

        // DEFAULT DAILY 30-MIN WORKOUT ROUTINE
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().border(1.dp, PastelMint, RoundedCornerShape(22.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🔥 Daily 30-Min Routine & Warm-Up", color = PastelMint, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Counted by default (+$daily30MinDefaultCals kcal)", color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(checked = daily30MinCompletedToday, onCheckedChange = onToggleDaily30Min)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Normal Warm-up  •  1 set normal + 2 sets 5kg squats", color = TextSoftWhite, fontSize = 12.sp)
                    Text("• High Knees (2 sets)  •  Incline Pushups (3 × 15)", color = TextSoftWhite, fontSize = 12.sp)
                }
            }
        }

        // MON - SUN SELECTOR
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Weekly Workout Split", color = TextSoftWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Button(
                        onClick = { showAddWorkoutModal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PastelMint),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("+ Workout", color = BgCozySlate, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    days.forEach { day ->
                        val isSelected = selectedDay == day
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) PastelLavender else CardSlate)
                                .clickable { selectedDay = day }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(day, color = if (isSelected) BgCozySlate else TextSoftWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(dayTitles[day] ?: "", color = if (isSelected) BgCozySlate else TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        val dayExercises = exercisesList.filter { it.day == selectedDay }
        if (dayExercises.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CardSlate), shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Rest & Recovery Day ($selectedDay)", color = TextSoftWhite, fontWeight = FontWeight.Bold)
                        Text("Tap '+ Workout' above if you do any custom session today.", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        items(dayExercises, key = { it.id }) { exercise ->
            val logState = exerciseLogs[exercise.id] ?: ExerciseLogState()
            val exerciseBurn = (logState.completedSets * (exercise.baseCalPerSet + (logState.loadMultiplierKg * 0.45f))).roundToInt()

            Card(
                colors = CardDefaults.cardColors(containerColor = CardSlate),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ExerciseVisualThumbnail(visualType = exercise.visualType, muscleGroup = exercise.muscleGroup)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exercise.name, color = TextSoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${exercise.muscleGroup} • ${exercise.targetNote}", color = TextMuted, fontSize = 12.sp)
                            Text("Previous: ${logState.previousWeightMemory}", color = PastelLavender, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = { editingExercise = exercise }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = TextMuted, modifier = Modifier.size(17.dp))
                        }
                        IconButton(onClick = { onDeleteExercise(exercise.id) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = PastelCoral, modifier = Modifier.size(17.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!exercise.isBodyweightOrTimed) {
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            weightPresetOptions.forEach { (label, totalKg) ->
                                val selected = logState.selectedWeightLabel == label
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) PastelLavender else CardElevated)
                                        .clickable {
                                            onUpdateExerciseLog(
                                                exercise.id,
                                                logState.copy(selectedWeightLabel = label, loadMultiplierKg = totalKg, previousWeightMemory = label)
                                            )
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(label, color = if (selected) BgCozySlate else TextSoftWhite, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Sets:", color = TextSoftWhite, fontSize = 12.sp)
                            for (setNum in 1..exercise.targetSets) {
                                val isDone = logState.completedSets >= setNum
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isDone) PastelMint else CardElevated)
                                        .clickable {
                                            val newCount = if (logState.completedSets == setNum) setNum - 1 else setNum
                                            onUpdateExerciseLog(
                                                exercise.id,
                                                logState.copy(completedSets = newCount, previousWeightMemory = logState.selectedWeightLabel)
                                            )
                                            if (newCount > logState.completedSets) restSecondsLeft = 60 // Auto-start 60s rest timer
                                        }
                                ) {
                                    if (isDone) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = BgCozySlate, modifier = Modifier.size(16.dp))
                                    } else {
                                        Text("$setNum", color = TextSoftWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Surface(color = CardElevated, shape = RoundedCornerShape(10.dp)) {
                            Text(
                                "$exerciseBurn kcal",
                                color = PastelMint,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(onClick = onFinishAndArchiveWeek, modifier = Modifier.fillMaxWidth()) {
                Text("Archive Week to History & Reset Sets", color = PastelMint)
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
            containerColor = CardSlate,
            title = { Text("Add Exercise to $selectedDay", color = TextSoftWhite) },
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
                        Text("Bodyweight / Timed", color = TextSoftWhite, fontSize = 13.sp)
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
                }) { Text("Add") }
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
            containerColor = CardSlate,
            title = { Text("Edit Exercise", color = TextSoftWhite) },
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
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingExercise = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun ExerciseVisualThumbnail(visualType: String, muscleGroup: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardElevated)
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val w = size.width
            val h = size.height
            when (visualType) {
                "barbell" -> {
                    drawLine(PastelLavender, Offset(w * 0.1f, h * 0.5f), Offset(w * 0.9f, h * 0.5f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawRoundRect(PastelMint, Offset(w * 0.15f, h * 0.22f), Size(w * 0.12f, h * 0.56f), CornerRadius(4f, 4f))
                    drawRoundRect(PastelMint, Offset(w * 0.73f, h * 0.22f), Size(w * 0.12f, h * 0.56f), CornerRadius(4f, 4f))
                }
                "cable" -> {
                    drawCircle(PastelLavender, radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.22f), style = Stroke(width = 4f))
                    drawLine(PastelMint, Offset(w * 0.5f, h * 0.38f), Offset(w * 0.5f, h * 0.78f), strokeWidth = 5f)
                    drawLine(PastelMint, Offset(w * 0.3f, h * 0.78f), Offset(w * 0.7f, h * 0.78f), strokeWidth = 6f, cap = StrokeCap.Round)
                }
                "legs" -> {
                    drawCircle(PastelMint, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.2f))
                    drawLine(PastelLavender, Offset(w * 0.5f, h * 0.32f), Offset(w * 0.35f, h * 0.6f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawLine(PastelMint, Offset(w * 0.35f, h * 0.6f), Offset(w * 0.55f, h * 0.88f), strokeWidth = 6f, cap = StrokeCap.Round)
                }
                "bodyweight" -> {
                    drawCircle(PastelMint, radius = w * 0.11f, center = Offset(w * 0.22f, h * 0.35f))
                    drawLine(PastelLavender, Offset(w * 0.32f, h * 0.42f), Offset(w * 0.85f, h * 0.68f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawLine(PastelMint, Offset(w * 0.32f, h * 0.42f), Offset(w * 0.28f, h * 0.75f), strokeWidth = 5f, cap = StrokeCap.Round)
                }
                else -> {
                    drawLine(PastelLavender, Offset(w * 0.25f, h * 0.5f), Offset(w * 0.75f, h * 0.5f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawRoundRect(PastelMint, Offset(w * 0.14f, h * 0.26f), Size(w * 0.14f, h * 0.48f), CornerRadius(6f, 6f))
                    drawRoundRect(PastelMint, Offset(w * 0.72f, h * 0.26f), Size(w * 0.14f, h * 0.48f), CornerRadius(6f, 6f))
                }
            }
        }
        Text(
            muscleGroup.take(4).uppercase(),
            color = TextMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp)
        )
    }
}

// ============================================================================
// TAB 3: HISTORY (WORKOUT HISTORY & STEP COUNT HISTORY)
// ============================================================================
@Composable
fun Tab3FriendlyHistory(
    workoutHistoryList: List<WorkoutHistoryEntry>,
    stepHistoryList: List<StepHistoryEntry>,
    onArchiveCurrentWeekNow: () -> Unit
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Workout & Step History", color = TextSoftWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Auto-saves & resets every Monday", color = TextMuted, fontSize = 12.sp)
            }
            TextButton(onClick = onArchiveCurrentWeekNow) {
                Text("Archive Week", color = PastelMint, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardSlate)
                .padding(4.dp)
        ) {
            listOf("Workout History", "Step Count History").forEachIndexed { idx, title ->
                val active = selectedSubTab == idx
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) PastelLavender else Color.Transparent)
                        .clickable { selectedSubTab = idx }
                        .padding(vertical = 10.dp)
                ) {
                    Text(title, color = if (active) BgCozySlate else TextSoftWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedSubTab == 0) {
            if (workoutHistoryList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No archived workouts yet.", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(workoutHistoryList, key = { it.id }) { item ->
                        Card(colors = CardDefaults.cardColors(containerColor = CardSlate), shape = RoundedCornerShape(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.exerciseName, color = TextSoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${item.date} (${item.dayName}) • ${item.setsDone} sets • ${item.weightUsed}", color = TextMuted, fontSize = 12.sp)
                                }
                                Text("${item.caloriesBurned} kcal", color = PastelMint, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(stepHistoryList, key = { it.date }) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = CardSlate), shape = RoundedCornerShape(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(entry.date, color = TextSoftWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Goal: ${entry.goal} steps", color = TextMuted, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${entry.steps} steps", color = PastelLavender, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${entry.calories} kcal", color = PastelMint, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// TAB 4: SETTINGS
// ============================================================================
@Composable
fun Tab4FriendlySettings(
    stepTarget: Int,
    userBodyWeightKg: Int,
    daily30MinDefaultCals: Int,
    waterGoalMl: Int,
    notificationsEnabled: Boolean,
    onUpdateStepTarget: (Int) -> Unit,
    onUpdateBodyWeight: (Int) -> Unit,
    onUpdateDaily30Cals: (Int) -> Unit,
    onUpdateWaterGoal: (Int) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onResetDefaultWorkouts: () -> Unit
) {
    var goalText by remember(stepTarget) { mutableStateOf(stepTarget.toString()) }
    var weightText by remember(userBodyWeightKg) { mutableStateOf(userBodyWeightKg.toString()) }
    var routineCalText by remember(daily30MinDefaultCals) { mutableStateOf(daily30MinDefaultCals.toString()) }
    var waterGoalText by remember(waterGoalMl) { mutableStateOf(waterGoalMl.toString()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Settings & Schedule", color = TextSoftWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Option B Friendly Theme • Scheduled Alerts Active", color = TextMuted, fontSize = 13.sp)
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardSlate), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Daily Goals & Calibration", color = TextSoftWhite, fontWeight = FontWeight.Bold)
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
            Card(colors = CardDefaults.cardColors(containerColor = CardSlate), shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scheduled Smart Reminders", color = TextSoftWhite, fontWeight = FontWeight.Bold)
                            Text("• Workouts: 10:30 AM & 4:00 PM (only if not updated)\n• Water: 7:30 AM & Hourly from 11:00 AM\n• In-App Water Check: Every 4 Hours", color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(checked = notificationsEnabled, onCheckedChange = onToggleNotifications)
                    }
                }
            }
        }

        item {
            OutlinedButton(onClick = onResetDefaultWorkouts, modifier = Modifier.fillMaxWidth()) {
                Text("Restore Default Mon–Fri Exercises", color = PastelCoral)
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(accent))
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, color = TextMuted, fontSize = 10.sp)
            Text(value, color = TextSoftWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
