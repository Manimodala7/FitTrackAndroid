package com.fittrack.app

import android.Manifest
import android.content.Context
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalDate
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
    val baseCalPerSet: Int = 12
)

data class ExerciseLogState(
    val completedSets: Int = 0,
    val selectedWeightLabel: String = "5 kg/side",
    val loadMultiplierKg: Float = 10f // Total added weight in kg for calorie math
)

data class FoodItem(
    val id: Long,
    val name: String,
    val calories: Int
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

        // Load saved baseline steps for today
        val prefs = getSharedPreferences("fittrack_prefs", Context.MODE_PRIVATE)
        val todayKey = LocalDate.now().toString()
        liveSteps = prefs.getInt("steps_$todayKey", 0)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0B131E),
                    surface = Color(0xFF15202E),
                    primary = Color(0xFF00E676),
                    secondary = Color(0xFF00B0FF)
                )
            ) {
                FitTrackAppScreen(
                    steps = liveSteps,
                    onManualAddSteps = { added ->
                        liveSteps += added
                        prefs.edit().putInt("steps_$todayKey", liveSteps).apply()
                    }
                )
            }
        }
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
            val prefs = getSharedPreferences("fittrack_prefs", Context.MODE_PRIVATE)
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

// --- PRE-LOADED USER WORKOUT SPLIT ---
val userWorkoutPlan = listOf(
    // MONDAY - BICEPS
    WorkoutExercise("m1", "Mon", "Biceps", "Barbell Biceps Curl", 3, "3 sets"),
    WorkoutExercise("m2", "Mon", "Biceps", "Concentration Curl", 3, "3 sets"),
    WorkoutExercise("m3", "Mon", "Biceps", "Hammer Curl", 3, "3 sets"),
    WorkoutExercise("m4", "Mon", "Biceps", "Bicep Curl", 3, "3 sets"),
    WorkoutExercise("m5", "Mon", "Biceps", "Seated Cable Curl", 3, "3 sets"),

    // TUESDAY - TRICEPS
    WorkoutExercise("tu1", "Tue", "Triceps", "Push Down Cable", 3, "3 sets"),
    WorkoutExercise("tu2", "Tue", "Triceps", "Push Up (Close Grip)", 3, "3 sets", isBodyweightOrTimed = true),
    WorkoutExercise("tu3", "Tue", "Triceps", "Tricep Dips", 3, "3 sets", isBodyweightOrTimed = true),
    WorkoutExercise("tu4", "Tue", "Triceps", "Overhead Tricep Extension Dumbbell", 3, "3 sets"),
    WorkoutExercise("tu5", "Tue", "Triceps", "Tricep Kettlebell", 3, "3 sets"),

    // WEDNESDAY - BACK / LEGS
    WorkoutExercise("w1", "Wed", "Back/Legs", "Calf Extensions", 3, "3 sets", baseCalPerSet = 14),
    WorkoutExercise("w2", "Wed", "Back/Legs", "Barbell Squats", 4, "3-4 sets", baseCalPerSet = 22),
    WorkoutExercise("w3", "Wed", "Back/Legs", "Dumbbell Lunges", 3, "3 sets", baseCalPerSet = 18),
    WorkoutExercise("w4", "Wed", "Back/Legs", "Cable Seated Row", 3, "3 sets", baseCalPerSet = 16),
    WorkoutExercise("w5", "Wed", "Back/Legs", "Cable Lat Pulldown", 3, "3 sets", baseCalPerSet = 16),
    WorkoutExercise("w6", "Wed", "Back/Legs", "Landmine Rows", 3, "3 sets", baseCalPerSet = 18),

    // THURSDAY - CHEST & SHOULDER
    WorkoutExercise("th1", "Thu", "Chest", "Pec Deck Fly", 3, "3 sets", baseCalPerSet = 14),
    WorkoutExercise("th2", "Thu", "Chest", "Flat Barbell Bench Press", 3, "3 sets", baseCalPerSet = 18),
    WorkoutExercise("th3", "Thu", "Chest", "Inclined Press Dumbbell", 3, "3 sets", baseCalPerSet = 17),
    WorkoutExercise("th4", "Thu", "Chest", "Decline Press Dumbbell", 3, "3 sets", baseCalPerSet = 17),
    WorkoutExercise("th5", "Thu", "Shoulder", "Pec Deck Fly / Shoulder Press", 3, "3 sets", baseCalPerSet = 15),

    // FRIDAY - ABS
    WorkoutExercise("f1", "Fri", "Abs", "Crunches", 3, "3 sets", isBodyweightOrTimed = true, baseCalPerSet = 10),
    WorkoutExercise("f2", "Fri", "Abs", "Decline Crunches", 3, "3 sets", isBodyweightOrTimed = true, baseCalPerSet = 12),
    WorkoutExercise("f3", "Fri", "Abs", "Leg Raises (Set 1)", 3, "3 sets", isBodyweightOrTimed = true, baseCalPerSet = 11),
    WorkoutExercise("f4", "Fri", "Abs", "Plank", 3, "3 x 50 secs", isBodyweightOrTimed = true, baseCalPerSet = 12),
    WorkoutExercise("f5", "Fri", "Abs", "Mountain Climbers", 3, "3 x 20 each", isBodyweightOrTimed = true, baseCalPerSet = 16),
    WorkoutExercise("f6", "Fri", "Abs", "Leg Raises (Finisher)", 3, "3 sets", isBodyweightOrTimed = true, baseCalPerSet = 11)
)

val weightPresetOptions = listOf(
    "Empty Bar" to 0f,
    "5 kg/side" to 10f,
    "7.5 kg/side" to 15f,
    "10 kg/side" to 20f,
    "7.5 kg DB" to 15f,
    "10 kg DB" to 20f,
    "12.5 kg DB" to 25f,
    "15 kg DB" to 30f
)

@Composable
fun FitTrackAppScreen(steps: Int, onManualAddSteps: (Int) -> Unit) {
    val context = LocalContext.current

    // Request Android 10+ Step Sensor Permission automatically
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
    }

    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
    var selectedDay by remember { mutableStateOf("Mon") }

    // State for each exercise's sets and weights
    val exerciseLogs = remember {
        mutableStateMapOf<String, ExerciseLogState>().apply {
            userWorkoutPlan.forEach { ex ->
                put(
                    ex.id,
                    ExerciseLogState(
                        completedSets = 0,
                        selectedWeightLabel = if (ex.isBodyweightOrTimed) "Bodyweight" else "5 kg/side",
                        loadMultiplierKg = if (ex.isBodyweightOrTimed) 0f else 10f
                    )
                )
            }
        }
    }

    // State for optional Treats / Desserts / Meals
    val foodLogs = remember { mutableStateListOf<FoodItem>() }
    var showAddFoodDialog by remember { mutableStateOf(false) }

    // Calorie Calculations
    val stepCalories = (steps * 0.04f).roundToInt()
    val workoutCalories = userWorkoutPlan.sumOf { ex ->
        val state = exerciseLogs[ex.id] ?: ExerciseLogState()
        // Calorie formula: completed sets * (base burn + 0.45 kcal per kg of plates/dumbbells)
        (state.completedSets * (ex.baseCalPerSet + (state.loadMultiplierKg * 0.45f))).roundToInt()
    }
    val totalBurnedCalories = stepCalories + workoutCalories
    val totalEatenCalories = foodLogs.sumOf { it.calories }
    val distanceKm = String.format("%.2f", steps * 0.0007f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B131E))
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 20.dp)
    ) {
        // --- HEADER ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Good tracker!", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Daily Pedometer, Split & Treats", color = Color(0xFF8A99AD), fontSize = 13.sp)
                }
                Surface(
                    color = Color(0xFF15202E),
                    shape = RoundedCornerShape(12.dp)
                ) {
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

        // --- 1. DAILY ACTIVITY & PEDOMETER CARD (From Design) ---
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
                        Text("Daily Activity (Pedometer)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        TextButton(onClick = { onManualAddSteps(500) }) {
                            Text("+500 Steps", color = Color(0xFF00B0FF), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Circular Step Gauge
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(135.dp)) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFF223247),
                                strokeWidth = 11.dp,
                            )
                            CircularProgressIndicator(
                                progress = { (steps / 10000f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFF00E676),
                                strokeWidth = 11.dp,
                                strokeCap = StrokeCap.Round
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$steps", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                                Text("steps", color = Color(0xFF8A99AD), fontSize = 12.sp)
                                Text("$distanceKm km", color = Color(0xFF00B0FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Right-side Stats
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatRow("Total Burned", "$totalBurnedCalories kcal", Color(0xFFFF9100))
                            StatRow("Step Burn", "$stepCalories kcal", Color(0xFF00B0FF))
                            StatRow("Gym Split Burn", "$workoutCalories kcal", Color(0xFF00E676))
                        }
                    }
                }
            }
        }

        // --- 2. EAT & TRACK (OPTIONAL / DESSERTS & SWEETS) ---
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
                            Text("Eat & Track (Optional)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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
                            Text("Add Treat", color = Color(0xFF00E676), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Preset Buttons for Chocolates / Ice Cream
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickTreats = listOf(
                            "🍫 Chocolate Bar" to 230,
                            "🍨 Ice Cream Scoop" to 210,
                            "🍦 Cone / Magnum" to 280,
                            "🍰 Sweet / Dessert" to 320,
                            "🥤 Cold Drink" to 150
                        )
                        quickTreats.forEach { (label, cal) ->
                            AssistChip(
                                onClick = {
                                    foodLogs.add(FoodItem(System.currentTimeMillis(), label, cal))
                                },
                                label = { Text("$label (+$cal)", color = Color.White, fontSize = 12.sp) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1E2D40))
                            )
                        }
                    }

                    if (foodLogs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Logged Today: $totalEatenCalories kcal",
                            color = Color(0xFFFFB74D),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
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
                                    IconButton(
                                        onClick = { foodLogs.remove(item) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color(0xFF8A99AD), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "No desserts or extra food logged today. Leave blank if eating normal routine meals.",
                            color = Color(0xFF6C7D93),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // --- 3. WORKOUT SPLIT SELECTOR (MON - FRI) ---
        item {
            Column {
                Text("My Weekly Workout Split", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val dayTitles = mapOf(
                        "Mon" to "Biceps",
                        "Tue" to "Triceps",
                        "Wed" to "Back/Leg",
                        "Thu" to "Chest",
                        "Fri" to "Abs"
                    )
                    days.forEach { day ->
                        val isSelected = selectedDay == day
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) Brush.verticalGradient(
                                        listOf(Color(0xFF00E676), Color(0xFF00B0FF))
                                    ) else Brush.verticalGradient(
                                        listOf(Color(0xFF15202E), Color(0xFF15202E))
                                    )
                                )
                                .clickable { selectedDay = day }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                day,
                                color = if (isSelected) Color(0xFF0B131E) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                dayTitles[day] ?: "",
                                color = if (isSelected) Color(0xFF0B131E) else Color(0xFF8A99AD),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // --- 4. EXERCISES FOR SELECTED DAY WITH WEIGHT & SET PICKER ---
        val dayExercises = userWorkoutPlan.filter { it.day == selectedDay }
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(exercise.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                "${exercise.muscleGroup} • Target: ${exercise.targetNote} • Weight: ${logState.selectedWeightLabel}",
                                color = Color(0xFF8A99AD),
                                fontSize = 12.sp
                            )
                        }
                        Surface(
                            color = Color(0xFF1E332A),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "$exerciseBurn kcal",
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Weight Selector Row (Empty / 5kg side / 7.5kg side / DB sizes)
                    if (!exercise.isBodyweightOrTimed) {
                        Text("Select Weight Used:", color = Color(0xFF8A99AD), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
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
                                            exerciseLogs[exercise.id] = logState.copy(
                                                selectedWeightLabel = label,
                                                loadMultiplierKg = totalKg
                                            )
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) Color(0xFF0B131E) else Color.White,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Set Completion Buttons (Set 1, Set 2, Set 3...)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sets Done:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        for (setNum in 1..exercise.targetSets) {
                            val isDone = logState.completedSets >= setNum
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isDone) Color(0xFF00E676) else Color(0xFF1E2D40))
                                    .border(1.dp, Color(0xFF2E435E), CircleShape)
                                    .clickable {
                                        val newCount = if (logState.completedSets == setNum) setNum - 1 else setNum
                                        exerciseLogs[exercise.id] = logState.copy(completedSets = newCount)
                                    }
                            ) {
                                if (isDone) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF0B131E), modifier = Modifier.size(18.dp))
                                } else {
                                    Text("$setNum", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- POPUP DIALOG FOR CUSTOM SWEETS / DESSERTS / FOOD ---
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
                        label = { Text("Item (e.g. Brownie, Dairy Milk, Biryani)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = foodCal,
                        onValueChange = { foodCal = it },
                        label = { Text("Approx Calories (leave blank for 250 kcal default)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (foodName.isNotBlank()) {
                            val cal = foodCal.toIntOrNull() ?: 250
                            foodLogs.add(FoodItem(System.currentTimeMillis(), foodName, cal))
                            showAddFoodDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFoodDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
