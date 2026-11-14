package com.fittrack.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

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
    val visualType: String = "barbell" // barbell, dumbbell, cable, bodyweight, legs
)

data class ExerciseLogState(
    val completedSets: Int = 0,
    val selectedWeightLabel: String = "5 kg/side",
    val loadMultiplierKg: Float = 10f,
    val previousWeightMemory: String = "None yet"
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
    // MONDAY - BICEPS
    WorkoutExercise("m1", "Mon", "Biceps", "Barbell Biceps Curl", 3, "3 sets", visualType = "barbell"),
    WorkoutExercise("m2", "Mon", "Biceps", "Concentration Curl", 3, "3 sets", visualType = "dumbbell"),
    WorkoutExercise("m3", "Mon", "Biceps", "Hammer Curl", 3, "3 sets", visualType = "dumbbell"),
    WorkoutExercise("m4", "Mon", "Biceps", "Bicep Curl", 3, "3 sets", visualType = "dumbbell"),
    WorkoutExercise("m5", "Mon", "Biceps", "Seated Cable Curl", 3, "3 sets", visualType = "cable"),

    // TUESDAY - TRICEPS
    WorkoutExercise("tu1", "Tue", "Triceps", "Push Down Cable", 3, "3 sets", visualType = "cable"),
    WorkoutExercise("tu2", "Tue", "Triceps", "Push Up (Close Grip)", 3, "3 sets", isBodyweightOrTimed = true, visualType = "bodyweight"),
    WorkoutExercise("tu3", "Tue", "Triceps", "Tricep Dips", 3, "3 sets", isBodyweightOrTimed = true, visualType = "bodyweight"),
    WorkoutExercise("tu4", "Tue", "Triceps", "Overhead Tricep Extension DB", 3, "3 sets", visualType = "dumbbell"),
    WorkoutExercise("tu5", "Tue", "Triceps", "Tricep Kettlebell", 3, "3 sets", visualType = "dumbbell"),

    // WEDNESDAY - BACK / LEGS (Added Bulgarian Split Squat)
    WorkoutExercise("w1", "Wed", "Back/Legs", "Calf Extensions", 3, "3 sets", baseCalPerSet = 14, visualType = "legs"),
    WorkoutExercise("w2", "Wed", "Back/Legs", "Barbell Squats", 4, "3-4 sets", baseCalPerSet = 22, visualType = "barbell"),
    WorkoutExercise("w3", "Wed", "Back/Legs", "Dumbbell Lunges", 3, "3 sets", baseCalPerSet = 18, visualType = "dumbbell"),
    WorkoutExercise("w7", "Wed", "Back/Legs", "Bulgarian Split Squat", 3, "3 sets", baseCalPerSet = 20, visualType = "legs"),
    WorkoutExercise("w4", "Wed", "Back/Legs", "Cable Seated Row", 3, "3 sets", baseCalPerSet = 16, visualType = "cable"),
    WorkoutExercise("w5", "Wed", "Back/Legs", "Cable Lat Pulldown", 3, "3 sets", baseCalPerSet = 16, visualType = "cable"),
    WorkoutExercise("w6", "Wed", "Back/Legs", "Landmine Rows", 3, "3 sets", baseCalPerSet = 18, visualType = "barbell"),

    // THURSDAY - CHEST & SHOULDER
    WorkoutExercise("th1", "Thu", "Chest", "Pec Deck Fly", 3, "3 sets", baseCalPerSet = 14, visualType = "cable"),
    WorkoutExercise("th2", "Thu", "Chest", "Flat Barbell Bench Press", 3, "3 sets", baseCalPerSet = 18, visualType = "barbell"),
    WorkoutExercise("th3", "Thu", "Chest", "Inclined Press Dumbbell", 3, "3 sets", baseCalPerSet = 17, visualType = "dumbbell"),
    WorkoutExercise("th4", "Thu", "Chest", "Decline Press Dumbbell", 3, "3 sets", baseCalPerSet = 17, visualType = "dumbbell"),
    WorkoutExercise("th5", "Thu", "Shoulder", "Pec Deck Fly Shoulder Press", 3, "3 sets", baseCalPerSet = 15, visualType = "dumbbell"),

    // FRIDAY - ABS
    WorkoutExercise("f1", "Fri", "Abs", "Crunches", 3, "3 sets", isBodyweightOrTimed = true, baseCalPerSet = 10, visualType = "bodyweight"),
    WorkoutExercise("f2", "Fri", "Abs", "Decline Crunches", 3, "3 sets", isBodyweightOrTimed = true, baseCalPerSet = 12, visualType = "bodyweight"),
    WorkoutExercise("f3", "Fri", "Abs", "Leg Raises (Set 1)", 3, "3 sets", isBodyweightOrTimed = true, baseCalPerSet = 11, visualType = "bodyweight"),
    WorkoutExercise("f4", "Fri", "Abs", "Plank", 3, "3 x 50 secs", isBodyweightOrTimed = true, baseCalPerSet = 12, visualType = "bodyweight"),
    WorkoutExercise("f5", "Fri", "Abs", "Mountain Climbers", 3, "3 x 20 each", isBodyweightOrTimed = true, baseCalPerSet = 16, visualType = "bodyweight"),
    WorkoutExercise("f6", "Fri", "Abs", "Leg Raises (Finisher)", 3, "3 sets", isBodyweightOrTimed = true, baseCalPerSet = 11, visualType = "bodyweight")
    // SAT & SUN are empty by default so user can relax or use + Add Workout
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

class MainActivity : ComponentActivity(), SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var liveSteps by mutableIntStateOf(0)
    private var initialSensorSteps = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        val prefs = getSharedPreferences("fittrack_pro_prefs", Context.MODE_PRIVATE)
        val todayKey = LocalDate.now().toString()
        liveSteps = prefs.getInt("steps_$todayKey", 0)

        createNotificationChannel()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0B131E),
                    surface = Color(0xFF15202E),
                    primary = Color(0xFF00E676),
                    secondary = Color(0xFF00B0FF)
                )
            ) {
                FitTrackMainRoot(
                    prefs = prefs,
                    steps = liveSteps,
                    onManualAddSteps = { added ->
                        liveSteps = (liveSteps + added).coerceAtLeast(0)
                        prefs.edit().putInt("steps_$todayKey", liveSteps).apply()
                    },
                    onTriggerNotification = { title, message ->
                        sendWorkoutReminderNotification(title, message)
                    }
                )
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "fittrack_reminders",
                "Daily Workout & Step Reminders",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendWorkoutReminderNotification(title: String, message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, "fittrack_reminders")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(1001, notification)
    }

    override fun onResume() {
        super.onResume()
        stepSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            val totalStepsSinceBoot = event.values[0].toInt()
            val prefs = getSharedPreferences("fittrack_pro_prefs", Context.MODE_PRIVATE)
            val todayKey = LocalDate.now().toString()
            val savedBootOffset = prefs.getInt("offset_$todayKey", -1)

            if (savedBootOffset == -1 || totalStepsSinceBoot < savedBootOffset) {
                prefs.edit().putInt("offset_$todayKey", totalStepsSinceBoot).apply()
                initialSensorSteps = totalStepsSinceBoot
            } else {
                initialSensorSteps = savedBootOffset
            }

            liveSteps = (totalStepsSinceBoot - initialSensorSteps).coerceAtLeast(0)
            prefs.edit().putInt("steps_$todayKey", liveSteps).apply()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun FitTrackMainRoot(
    prefs: SharedPreferences,
    steps: Int,
    onManualAddSteps: (Int) -> Unit,
    onTriggerNotification: (String, String) -> Unit
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val todayStr = today.toString()
    val currentMondayStr = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

    // Permissions (Activity Recognition + Notifications)
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

    // --- SETTINGS STATE ---
    var stepTarget by remember { mutableIntStateOf(prefs.getInt("step_target", 8000)) }
    var userBodyWeightKg by remember { mutableIntStateOf(prefs.getInt("body_weight_kg", 70)) }
    var daily30MinDefaultCals by remember { mutableIntStateOf(prefs.getInt("daily_30m_cals", 185)) }
    var daily30MinCompletedToday by remember {
        mutableStateOf(prefs.getBoolean("daily30_done_$todayStr", true)) // Enabled by default as requested
    }
    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("notif_enabled", true)) }

    // --- EXERCISES LIST (With Custom Add / Edit / Delete Persistence) ---
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
            val o = JSONObject().apply {
                put("id", ex.id)
                put("day", ex.day)
                put("muscleGroup", ex.muscleGroup)
                put("name", ex.name)
                put("targetSets", ex.targetSets)
                put("targetNote", ex.targetNote)
                put("isBody", ex.isBodyweightOrTimed)
                put("baseCal", ex.baseCalPerSet)
                put("visualType", ex.visualType)
            }
            arr.put(o)
        }
        prefs.edit().putString("custom_exercises_json", arr.toString()).apply()
    }

    // --- WORKOUT LOGS & PREVIOUS WEIGHT MEMORY ---
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
            .apply()
    }

    // --- HISTORY LISTS (WORKOUT HISTORY & STEP HISTORY) ---
    val workoutHistoryList = remember { mutableStateListOf<WorkoutHistoryEntry>() }
    val stepHistoryList = remember { mutableStateListOf<StepHistoryEntry>() }

    fun loadHistoryFromPrefs() {
        workoutHistoryList.clear()
        val wJson = prefs.getString("workout_history_json", "[]") ?: "[]"
        val wArr = JSONArray(wJson)
        for (i in 0 until wArr.length()) {
            val o = wArr.getJSONObject(i)
            workoutHistoryList.add(
                WorkoutHistoryEntry(
                    o.getLong("id"),
                    o.getString("date"),
                    o.getString("dayName"),
                    o.getString("exerciseName"),
                    o.getInt("setsDone"),
                    o.getString("weightUsed"),
                    o.getInt("caloriesBurned")
                )
            )
        }

        stepHistoryList.clear()
        val sJson = prefs.getString("step_history_json", "[]") ?: "[]"
        val sArr = JSONArray(sJson)
        for (i in 0 until sArr.length()) {
            val o = sArr.getJSONObject(i)
            stepHistoryList.add(
                StepHistoryEntry(
                    o.getString("date"),
                    o.getInt("steps"),
                    o.getInt("goal"),
                    o.getInt("calories")
                )
            )
        }
    }

    fun appendWorkoutHistory(entry: WorkoutHistoryEntry) {
        workoutHistoryList.add(0, entry)
        val arr = JSONArray()
        workoutHistoryList.take(200).forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("date", item.date)
                put("dayName", item.dayName)
                put("exerciseName", item.exerciseName)
                put("setsDone", item.setsDone)
                put("weightUsed", item.weightUsed)
                put("caloriesBurned", item.caloriesBurned)
            })
        }
        prefs.edit().putString("workout_history_json", arr.toString()).apply()
    }

    fun archiveAndResetWeekForMonday() {
        exercisesList.forEach { ex ->
            val st = exerciseLogs[ex.id]
            if (st != null && st.completedSets > 0) {
                val cals = (st.completedSets * (ex.baseCalPerSet + (st.loadMultiplierKg * 0.45f))).roundToInt()
                appendWorkoutHistory(
                    WorkoutHistoryEntry(
                        id = System.currentTimeMillis() + ex.id.hashCode(),
                        date = todayStr,
                        dayName = ex.day,
                        exerciseName = ex.name,
                        setsDone = st.completedSets,
                        weightUsed = st.selectedWeightLabel,
                        caloriesBurned = cals
                    )
                )
                // Save last used weight into memory & reset sets to 0 for the new week
                val resetState = st.copy(
                    completedSets = 0,
                    previousWeightMemory = st.selectedWeightLabel
                )
                saveSingleLogToPrefs(ex.id, resetState)
            }
        }
    }

    // Initialize & Check Automatic Monday Weekly Reset
    LaunchedEffect(Unit) {
        loadLogsFromPrefs()
        loadHistoryFromPrefs()

        val lastSavedWeekMonday = prefs.getString("last_week_monday", "") ?: ""
        if (lastSavedWeekMonday.isNotEmpty() && lastSavedWeekMonday != currentMondayStr) {
            archiveAndResetWeekForMonday()
        }
        prefs.edit().putString("last_week_monday", currentMondayStr).apply()

        // Update today's step history entry
        val stepCals = (steps * 0.04f).roundToInt()
        val existingIdx = stepHistoryList.indexOfFirst { it.date == todayStr }
        val todayEntry = StepHistoryEntry(todayStr, steps, stepTarget, stepCals)
        if (existingIdx >= 0) stepHistoryList[existingIdx] = todayEntry else stepHistoryList.add(0, todayEntry)

        // Check if user hasn't logged anything today and notifications are enabled
        val anySetsLogged = exerciseLogs.values.any { it.completedSets > 0 }
        if (notificationsEnabled && steps < 500 && !anySetsLogged) {
            onTriggerNotification(
                "Don't forget your FitTrack log today! 💪",
                "You haven't updated your workout sets or reached your step target ($stepTarget) yet."
            )
        }
    }

    // Save step history whenever steps change
    LaunchedEffect(steps, stepTarget) {
        val stepCals = (steps * 0.04f).roundToInt()
        val existingIdx = stepHistoryList.indexOfFirst { it.date == todayStr }
        val todayEntry = StepHistoryEntry(todayStr, steps, stepTarget, stepCals)
        if (existingIdx >= 0) stepHistoryList[existingIdx] = todayEntry else stepHistoryList.add(0, todayEntry)

        val sArr = JSONArray()
        stepHistoryList.take(60).forEach {
            sArr.put(JSONObject().apply {
                put("date", it.date)
                put("steps", it.steps)
                put("goal", it.goal)
                put("calories", it.calories)
            })
        }
        prefs.edit().putString("step_history_json", sArr.toString()).apply()
    }

    // --- FOOD LOGS ---
    val foodLogs = remember { mutableStateListOf<FoodItem>() }

    // --- CALORIE CALCULATIONS ---
    val stepCalories = (steps * (userBodyWeightKg / 70f) * 0.04f).roundToInt()
    val daily30MinCalories = if (daily30MinCompletedToday) daily30MinDefaultCals else 0
    val splitWorkoutCalories = exercisesList.sumOf { ex ->
        val st = exerciseLogs[ex.id] ?: ExerciseLogState()
        (st.completedSets * (ex.baseCalPerSet + (st.loadMultiplierKg * 0.45f))).roundToInt()
    }
    val totalBurnedCalories = stepCalories + daily30MinCalories + splitWorkoutCalories
    val totalEatenCalories = foodLogs.sumOf { it.calories }

    // --- BOTTOM NAVIGATION TAB STATE ---
    var currentTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Color(0xFF0B131E),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF121C29),
                tonalElevation = 8.dp
            ) {
                val navItems = listOf(
                    Triple(0, "Home & Steps", Icons.Default.Home),
                    Triple(1, "Workouts", Icons.Default.FitnessCenter),
                    Triple(2, "History", Icons.Default.History),
                    Triple(3, "Settings", Icons.Default.Settings)
                )
                navItems.forEach { (index, label, icon) ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF0B131E),
                            selectedTextColor = Color(0xFF00E676),
                            indicatorColor = Color(0xFF00E676),
                            unselectedIconColor = Color(0xFF8A99AD),
                            unselectedTextColor = Color(0xFF8A99AD)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> Tab1HomeStepsAndFood(
                    steps = steps,
                    stepTarget = stepTarget,
                    stepCalories = stepCalories,
                    daily30MinCalories = daily30MinCalories,
                    splitWorkoutCalories = splitWorkoutCalories,
                    totalBurnedCalories = totalBurnedCalories,
                    totalEatenCalories = totalEatenCalories,
                    foodLogs = foodLogs,
                    onManualAddSteps = onManualAddSteps,
                    onChangeStepTarget = { newGoal ->
                        stepTarget = newGoal
                        prefs.edit().putInt("step_target", newGoal).apply()
                    }
                )
                1 -> Tab2WorkoutSplitAndDaily30(
                    exercisesList = exercisesList,
                    exerciseLogs = exerciseLogs,
                    daily30MinCompletedToday = daily30MinCompletedToday,
                    daily30MinDefaultCals = daily30MinDefaultCals,
                    onToggleDaily30Min = { checked ->
                        daily30MinCompletedToday = checked
                        prefs.edit().putBoolean("daily30_done_$todayStr", checked).apply()
                    },
                    onUpdateExerciseLog = { id, newState ->
                        saveSingleLogToPrefs(id, newState)
                    },
                    onAddExercise = { newEx ->
                        exercisesList.add(newEx)
                        saveExercisesToPrefs()
                        saveSingleLogToPrefs(
                            newEx.id,
                            ExerciseLogState(0, if (newEx.isBodyweightOrTimed) "Bodyweight" else "5 kg/side", 10f, "New")
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
                    onFinishAndArchiveWeek = {
                        archiveAndResetWeekForMonday()
                    }
                )
                2 -> Tab3HistoryScreen(
                    workoutHistoryList = workoutHistoryList,
                    stepHistoryList = stepHistoryList,
                    onArchiveCurrentWeekNow = {
                        archiveAndResetWeekForMonday()
                    }
                )
                3 -> Tab4SettingsScreen(
                    stepTarget = stepTarget,
                    userBodyWeightKg = userBodyWeightKg,
                    daily30MinDefaultCals = daily30MinDefaultCals,
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
                    onToggleNotifications = {
                        notificationsEnabled = it
                        prefs.edit().putBoolean("notif_enabled", it).apply()
                    },
                    onSendTestNotification = {
                        onTriggerNotification(
                            "FitTrack Reminder Active 🔔",
                            "We'll remind you on days you haven't logged your steps or workout sets!"
                        )
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
// TAB 1: STEP COUNTER, TARGET, SUMMARY OF CALORIES & EAT/TRACK
// ============================================================================
@Composable
fun Tab1HomeStepsAndFood(
    steps: Int,
    stepTarget: Int,
    stepCalories: Int,
    daily30MinCalories: Int,
    splitWorkoutCalories: Int,
    totalBurnedCalories: Int,
    totalEatenCalories: Int,
    foodLogs: MutableList<FoodItem>,
    onManualAddSteps: (Int) -> Unit,
    onChangeStepTarget: (Int) -> Unit
) {
    var showAddFoodDialog by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }
    val distanceKm = String.format("%.2f", steps * 0.0007f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("FitTrack Dashboard", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Step Counter, Calorie Summary & Treats", color = Color(0xFF8A99AD), fontSize = 13.sp)
                }
                Surface(color = Color(0xFF15202E), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = "Net: ${totalBurnedCalories - totalEatenCalories} kcal",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // PEDOMETER CARD WITH EDITABLE STEP TARGET
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15202E)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Pedometer & Step Target", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text(
                                "Minimum Target: $stepTarget steps (Tap to edit)",
                                color = Color(0xFF00B0FF),
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { showTargetDialog = true }
                            )
                        }
                        Row {
                            TextButton(onClick = { showTargetDialog = true }) {
                                Text("Set Target", color = Color(0xFF00E676), fontSize = 12.sp)
                            }
                            TextButton(onClick = { onManualAddSteps(500) }) {
                                Text("+500", color = Color(0xFF00B0FF), fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFF223247),
                                strokeWidth = 12.dp,
                            )
                            CircularProgressIndicator(
                                progress = { (steps.toFloat() / stepTarget.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFF00E676),
                                strokeWidth = 12.dp,
                                strokeCap = StrokeCap.Round
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$steps", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                                Text("/ $stepTarget", color = Color(0xFF8A99AD), fontSize = 11.sp)
                                Text("$distanceKm km", color = Color(0xFF00B0FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatRow("Total Burned Today", "$totalBurnedCalories kcal", Color(0xFFFF9100))
                            StatRow("Pedometer Walk Burn", "$stepCalories kcal", Color(0xFF00B0FF))
                            StatRow("Daily 30m Routine", "$daily30MinCalories kcal", Color(0xFFE040FB))
                            StatRow("Gym Split Burn", "$splitWorkoutCalories kcal", Color(0xFF00E676))
                        }
                    }
                }
            }
        }

        // EAT & TRACK CARD (CHOCOLATES / ICE CREAMS / DESSERTS)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15202E)),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Eat & Track (Treats / Food)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            Text("Log chocolates, ice cream, desserts or meals", color = Color(0xFF8A99AD), fontSize = 12.sp)
                        }
                        Button(
                            onClick = { showAddFoodDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A34)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Food", color = Color(0xFF00E676), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickTreats = listOf(
                            "🍫 Chocolate Bar" to 230,
                            "🍨 Ice Cream Scoop" to 210,
                            "🍦 Magnum / Cone" to 280,
                            "🍰 Cake / Dessert" to 320,
                            "🥤 Soft Drink" to 150
                        )
                        quickTreats.forEach { (label, cal) ->
                            AssistChip(
                                onClick = {
                                    foodLogs.add(FoodItem(System.currentTimeMillis(), label, cal, LocalDate.now().toString()))
                                },
                                label = { Text("$label (+$cal)", color = Color.White, fontSize = 12.sp) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E2D40))
                            )
                        }
                    }

                    if (foodLogs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Total Eaten: $totalEatenCalories kcal", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        foodLogs.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(Color(0xFF1B2838), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.name, color = Color.White, fontSize = 14.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${item.calories} kcal", color = Color(0xFF00E676), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { foodLogs.remove(item) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFF8A99AD), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("No treats or extra meals logged today.", color = Color(0xFF6C7D93), fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showTargetDialog) {
        var inputGoal by remember { mutableStateOf(stepTarget.toString()) }
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            containerColor = Color(0xFF15202E),
            title = { Text("Set Minimum Step Target", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = inputGoal,
                    onValueChange = { inputGoal = it },
                    label = { Text("Daily Step Target (e.g. 6000, 8000, 10000)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val g = inputGoal.toIntOrNull() ?: 8000
                    onChangeStepTarget(g.coerceAtLeast(500))
                    showTargetDialog = false
                }) { Text("Save Target") }
            },
            dismissButton = {
                TextButton(onClick = { showTargetDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddFoodDialog) {
        var foodName by remember { mutableStateOf("") }
        var foodCal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddFoodDialog = false },
            containerColor = Color(0xFF15202E),
            title = { Text("Log Treat / Dessert / Food", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = foodName,
                        onValueChange = { foodName = it },
                        label = { Text("What did you eat? (e.g. Chocolate, Ice Cream)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = foodCal,
                        onValueChange = { foodCal = it },
                        label = { Text("Approx Calories (default 250)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (foodName.isNotBlank()) {
                        val cal = foodCal.toIntOrNull() ?: 250
                        foodLogs.add(FoodItem(System.currentTimeMillis(), foodName, cal, LocalDate.now().toString()))
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

// ============================================================================
// TAB 2: WORKOUT SPLIT (MON-SUN), DAILY 30-MIN DEFAULT ROUTINE, +WORKOUT, EDIT/DELETE & VISUALS
// ============================================================================
@Composable
fun Tab2WorkoutSplitAndDaily30(
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

    val dayTitles = mapOf(
        "Mon" to "Biceps",
        "Tue" to "Triceps",
        "Wed" to "Back/Leg",
        "Thu" to "Chest",
        "Fri" to "Abs",
        "Sat" to "Rest/+Add",
        "Sun" to "Rest/+Add"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 18.dp)
    ) {
        // DEFAULT DAILY 30-MIN WORKOUT ROUTINE CARD (Always Active by Default)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2639)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF00E676), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🔥 Daily 30-Min Routine & Warm-Up",
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                "Counted by default every day (+$daily30MinDefaultCals kcal)",
                                color = Color(0xFF8A99AD),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = daily30MinCompletedToday,
                            onCheckedChange = { onToggleDaily30Min(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E676))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Normal Warm-up", color = Color.White, fontSize = 12.sp)
                    Text("• Squats: 1 set normal + 2 sets weighted (5 kg)", color = Color.White, fontSize = 12.sp)
                    Text("• High Knees: 2 sets", color = Color.White, fontSize = 12.sp)
                    Text("• Incline Pushups: 3 × 15 reps", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        // MON - SUN SELECTOR BAR + "+ WORKOUT" BUTTON
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Weekly Workout Split", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Button(
                        onClick = { showAddWorkoutModal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF0B131E), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Workout", color = Color(0xFF0B131E), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    days.forEach { day ->
                        val isSelected = selectedDay == day
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(64.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) Brush.verticalGradient(listOf(Color(0xFF00E676), Color(0xFF00B0FF)))
                                    else Brush.verticalGradient(listOf(Color(0xFF15202E), Color(0xFF15202E)))
                                )
                                .clickable { selectedDay = day }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                day,
                                color = if (isSelected) Color(0xFF0B131E) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                dayTitles[day] ?: "",
                                color = if (isSelected) Color(0xFF0B131E) else Color(0xFF8A99AD),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        val dayExercises = exercisesList.filter { it.day == selectedDay }

        if (dayExercises.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF15202E)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No Preset Workouts for $selectedDay", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Saturday & Sunday are open rest/custom days. Tap '+ Workout' above to log any exercise you do today!",
                            color = Color(0xFF8A99AD),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // EXERCISE CARDS WITH VISUAL ILLUSTRATION, PREVIOUS MEMORY, WEIGHT CHIPS, EDIT & DELETE
        items(dayExercises, key = { it.id }) { exercise ->
            val logState = exerciseLogs[exercise.id] ?: ExerciseLogState()
            val exerciseBurn = (logState.completedSets * (exercise.baseCalPerSet + (logState.loadMultiplierKg * 0.45f))).roundToInt()

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15202E)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // VISUAL EXERCISE ILLUSTRATION BADGE
                        ExerciseVisualThumbnail(visualType = exercise.visualType, muscleGroup = exercise.muscleGroup)

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(exercise.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "${exercise.muscleGroup} • ${exercise.targetNote}",
                                color = Color(0xFF8A99AD),
                                fontSize = 12.sp
                            )
                            Text(
                                "Last memory: ${logState.previousWeightMemory} | Now: ${logState.selectedWeightLabel}",
                                color = Color(0xFF00B0FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Edit & Delete Icons
                        IconButton(onClick = { editingExercise = exercise }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF8A99AD), modifier = Modifier.size(17.dp))
                        }
                        IconButton(onClick = { onDeleteExercise(exercise.id) }, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(17.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Weight Selector Chips
                    if (!exercise.isBodyweightOrTimed) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            weightPresetOptions.forEach { (label, totalKg) ->
                                val selected = logState.selectedWeightLabel == label
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) Color(0xFF00B0FF) else Color(0xFF1E2D40))
                                        .clickable {
                                            onUpdateExerciseLog(
                                                exercise.id,
                                                logState.copy(
                                                    selectedWeightLabel = label,
                                                    loadMultiplierKg = totalKg,
                                                    previousWeightMemory = label
                                                )
                                            )
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) Color(0xFF0B131E) else Color.White,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Set Completion Row + Burn Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Sets:", color = Color.White, fontSize = 12.sp)
                            for (setNum in 1..exercise.targetSets) {
                                val isDone = logState.completedSets >= setNum
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isDone) Color(0xFF00E676) else Color(0xFF1E2D40))
                                        .border(1.dp, Color(0xFF2E435E), CircleShape)
                                        .clickable {
                                            val newCount = if (logState.completedSets == setNum) setNum - 1 else setNum
                                            onUpdateExerciseLog(
                                                exercise.id,
                                                logState.copy(
                                                    completedSets = newCount,
                                                    previousWeightMemory = logState.selectedWeightLabel
                                                )
                                            )
                                        }
                                ) {
                                    if (isDone) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF0B131E), modifier = Modifier.size(16.dp))
                                    } else {
                                        Text("$setNum", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Surface(color = Color(0xFF1E332A), shape = RoundedCornerShape(10.dp)) {
                            Text(
                                "$exerciseBurn kcal",
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Manual Weekly Refresh Button
        item {
            OutlinedButton(
                onClick = onFinishAndArchiveWeek,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF00E676))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Completed Sets to History & Refresh Week", color = Color(0xFF00E676), fontSize = 13.sp)
            }
        }
    }

    // ADD NEW WORKOUT DIALOG
    if (showAddWorkoutModal) {
        var name by remember { mutableStateOf("") }
        var muscle by remember { mutableStateOf(dayTitles[selectedDay] ?: "Custom") }
        var sets by remember { mutableStateOf("3") }
        var isBodyweight by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddWorkoutModal = false },
            containerColor = Color(0xFF15202E),
            title = { Text("Add Exercise to $selectedDay", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Exercise Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = muscle,
                        onValueChange = { muscle = it },
                        label = { Text("Muscle Group (e.g. Legs, Chest, Cardio)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { sets = it },
                        label = { Text("Number of Sets (e.g. 3 or 4)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isBodyweight, onCheckedChange = { isBodyweight = it })
                        Text("Bodyweight / Timed Exercise", color = Color.White, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        val s = sets.toIntOrNull()?.coerceIn(1, 10) ?: 3
                        onAddExercise(
                            WorkoutExercise(
                                id = "custom_${System.currentTimeMillis()}",
                                day = selectedDay,
                                muscleGroup = muscle,
                                name = name,
                                targetSets = s,
                                targetNote = "$s sets",
                                isBodyweightOrTimed = isBodyweight,
                                baseCalPerSet = 15,
                                visualType = if (isBodyweight) "bodyweight" else "dumbbell"
                            )
                        )
                        showAddWorkoutModal = false
                    }
                }) { Text("Add Workout") }
            },
            dismissButton = {
                TextButton(onClick = { showAddWorkoutModal = false }) { Text("Cancel") }
            }
        )
    }

    // EDIT WORKOUT DIALOG
    editingExercise?.let { ex ->
        var name by remember { mutableStateOf(ex.name) }
        var sets by remember { mutableStateOf(ex.targetSets.toString()) }
        var note by remember { mutableStateOf(ex.targetNote) }

        AlertDialog(
            onDismissRequest = { editingExercise = null },
            containerColor = Color(0xFF15202E),
            title = { Text("Edit Exercise", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Exercise Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sets,
                        onValueChange = { sets = it },
                        label = { Text("Target Sets") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Target Note (e.g. 3 sets / 3x50s)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val s = sets.toIntOrNull()?.coerceIn(1, 10) ?: ex.targetSets
                    onEditExercise(ex.copy(name = name, targetSets = s, targetNote = note))
                    editingExercise = null
                }) { Text("Save Changes") }
            },
            dismissButton = {
                TextButton(onClick = { editingExercise = null }) { Text("Cancel") }
            }
        )
    }
}

// --- CUSTOM VECTOR EXERCISE DIAGRAM THUMBNAIL ---
@Composable
fun ExerciseVisualThumbnail(visualType: String, muscleGroup: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1E2D40))
            .border(1.dp, Color(0xFF2E435E), RoundedCornerShape(14.dp))
    ) {
        Canvas(modifier = Modifier.size(36.dp)) {
            val w = size.width
            val h = size.height
            when (visualType) {
                "barbell" -> {
                    drawLine(Color(0xFF00B0FF), Offset(w * 0.1f, h * 0.5f), Offset(w * 0.9f, h * 0.5f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawRoundRect(Color(0xFF00E676), Offset(w * 0.15f, h * 0.22f), Size(w * 0.12f, h * 0.56f), CornerRadius(4f, 4f))
                    drawRoundRect(Color(0xFF00E676), Offset(w * 0.73f, h * 0.22f), Size(w * 0.12f, h * 0.56f), CornerRadius(4f, 4f))
                }
                "cable" -> {
                    drawCircle(Color(0xFF00B0FF), radius = w * 0.16f, center = Offset(w * 0.5f, h * 0.22f), style = Stroke(width = 4f))
                    drawLine(Color(0xFF00E676), Offset(w * 0.5f, h * 0.38f), Offset(w * 0.5f, h * 0.78f), strokeWidth = 5f)
                    drawLine(Color(0xFF00E676), Offset(w * 0.3f, h * 0.78f), Offset(w * 0.7f, h * 0.78f), strokeWidth = 6f, cap = StrokeCap.Round)
                }
                "legs" -> {
                    drawCircle(Color(0xFF00E676), radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.2f))
                    drawLine(Color(0xFF00B0FF), Offset(w * 0.5f, h * 0.32f), Offset(w * 0.35f, h * 0.6f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawLine(Color(0xFF00E676), Offset(w * 0.35f, h * 0.6f), Offset(w * 0.55f, h * 0.88f), strokeWidth = 6f, cap = StrokeCap.Round)
                }
                "bodyweight" -> {
                    drawCircle(Color(0xFF00E676), radius = w * 0.11f, center = Offset(w * 0.22f, h * 0.35f))
                    drawLine(Color(0xFF00B0FF), Offset(w * 0.32f, h * 0.42f), Offset(w * 0.85f, h * 0.68f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawLine(Color(0xFF00E676), Offset(w * 0.32f, h * 0.42f), Offset(w * 0.28f, h * 0.75f), strokeWidth = 5f, cap = StrokeCap.Round)
                }
                else -> { // Dumbbell default
                    drawLine(Color(0xFF00B0FF), Offset(w * 0.25f, h * 0.5f), Offset(w * 0.75f, h * 0.5f), strokeWidth = 6f, cap = StrokeCap.Round)
                    drawRoundRect(Color(0xFF00E676), Offset(w * 0.14f, h * 0.26f), Size(w * 0.14f, h * 0.48f), CornerRadius(6f, 6f))
                    drawRoundRect(Color(0xFF00E676), Offset(w * 0.72f, h * 0.26f), Size(w * 0.14f, h * 0.48f), CornerRadius(6f, 6f))
                }
            }
        }
        Text(
            text = muscleGroup.take(4).uppercase(),
            color = Color(0xFF8A99AD),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
        )
    }
}

// ============================================================================
// TAB 3: HISTORY (WORKOUT HISTORY & STEP COUNT HISTORY)
// ============================================================================
@Composable
fun Tab3HistoryScreen(
    workoutHistoryList: List<WorkoutHistoryEntry>,
    stepHistoryList: List<StepHistoryEntry>,
    onArchiveCurrentWeekNow: () -> Unit
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Activity History", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Auto-refreshes & archives every Monday", color = Color(0xFF8A99AD), fontSize = 12.sp)
            }
            TextButton(onClick = onArchiveCurrentWeekNow) {
                Text("Archive Now", color = Color(0xFF00E676), fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF15202E))
                .padding(4.dp)
        ) {
            val tabs = listOf("Workout History", "Step Count History")
            tabs.forEachIndexed { idx, title ->
                val active = selectedSubTab == idx
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) Color(0xFF00E676) else Color.Transparent)
                        .clickable { selectedSubTab = idx }
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        title,
                        color = if (active) Color(0xFF0B131E) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedSubTab == 0) {
            if (workoutHistoryList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No archived workouts yet.\nCompleted sets automatically move here every Monday (or tap 'Archive Now').",
                        color = Color(0xFF8A99AD),
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(workoutHistoryList, key = { it.id }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF15202E)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.exerciseName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("${item.date} (${item.dayName}) • ${item.setsDone} sets • Weight: ${item.weightUsed}", color = Color(0xFF8A99AD), fontSize = 12.sp)
                                }
                                Text("${item.caloriesBurned} kcal", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(stepHistoryList, key = { it.date }) { entry ->
                    val hitGoal = entry.steps >= entry.goal
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF15202E)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(entry.date, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    "Target: ${entry.goal} steps • ${if (hitGoal) "Goal Reached ✅" else "In Progress"}",
                                    color = if (hitGoal) Color(0xFF00E676) else Color(0xFF8A99AD),
                                    fontSize = 12.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${entry.steps} steps", color = Color(0xFF00B0FF), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${entry.calories} kcal", color = Color(0xFF00E676), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// TAB 4: SETTINGS (STEP GOAL, BODY WEIGHT, DEFAULT 30M ROUTINE, NOTIFICATIONS)
// ============================================================================
@Composable
fun Tab4SettingsScreen(
    stepTarget: Int,
    userBodyWeightKg: Int,
    daily30MinDefaultCals: Int,
    notificationsEnabled: Boolean,
    onUpdateStepTarget: (Int) -> Unit,
    onUpdateBodyWeight: (Int) -> Unit,
    onUpdateDaily30Cals: (Int) -> Unit,
    onToggleNotifications: (Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    onResetDefaultWorkouts: () -> Unit
) {
    var goalText by remember(stepTarget) { mutableStateOf(stepTarget.toString()) }
    var weightText by remember(userBodyWeightKg) { mutableStateOf(userBodyWeightKg.toString()) }
    var routineCalText by remember(daily30MinDefaultCals) { mutableStateOf(daily30MinDefaultCals.toString()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Settings & Preferences", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Customize your targets, body metrics & reminders", color = Color(0xFF8A99AD), fontSize = 13.sp)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15202E)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Targets & Calorie Calibration", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    OutlinedTextField(
                        value = goalText,
                        onValueChange = {
                            goalText = it
                            it.toIntOrNull()?.let { v -> onUpdateStepTarget(v) }
                        },
                        label = { Text("Minimum Daily Step Target") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = weightText,
                        onValueChange = {
                            weightText = it
                            it.toIntOrNull()?.let { v -> onUpdateBodyWeight(v) }
                        },
                        label = { Text("Your Body Weight (kg) - for accurate burn") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = routineCalText,
                        onValueChange = {
                            routineCalText = it
                            it.toIntOrNull()?.let { v -> onUpdateDaily30Cals(v) }
                        },
                        label = { Text("Daily 30-Min Default Workout Burn (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15202E)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Missing Log Reminder Notifications", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Alerts you if you haven't updated workouts or steps", color = Color(0xFF8A99AD), fontSize = 12.sp)
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = onToggleNotifications
                        )
                    }
                    Button(
                        onClick = onSendTestNotification,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B0FF)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Send Test Notification Now", color = Color(0xFF0B131E), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF15202E)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Workout Split Management", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Restore the default Mon–Fri workout list (including Bulgarian Split Squats) if you deleted an exercise by mistake.", color = Color(0xFF8A99AD), fontSize = 12.sp)
                    OutlinedButton(
                        onClick = onResetDefaultWorkouts,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Restore Default Mon–Fri Exercises", color = Color(0xFFFF5252))
                    }
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, color = Color(0xFF8A99AD), fontSize = 11.sp)
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
