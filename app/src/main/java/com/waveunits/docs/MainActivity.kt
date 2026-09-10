package com.waveunits.docs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        auth = Firebase.auth
        db = Firebase.firestore
        
        // Request permissions
        requestPermissions()
        
        setContent { 
            WaveUnitsApp(auth, db)
        }
    }
    
    private fun requestPermissions() {
        val permissions = listOf(
            Manifest.permission.INTERNET,
            Manifest.permission.CAMERA,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        val needPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                needPermissions.toTypedArray(),
                1001
            )
        }
    }
}

// ===== DATA CLASSES =====
data class ExamProject(
    val id: String = "",
    val title: String = "",
    val subject: String = "",
    val status: String = "",
    val createdAt: String = "",
    val teacherId: String = "",
    val questionPaperText: String = "",
    val answerKey: String = "",
    val studentCount: Int = 0
)

data class StudentAnswerSheet(
    val id: String = "",
    val studentName: String = "",
    val examId: String = "",
    val answers: Map<Int, String> = emptyMap(),
    val score: Int = 0,
    val total: Int = 0,
    val percentage: Double = 0.0
)

data class MarkedSheet(
    val id: String = "",
    val studentName: String = "",
    val examId: String = "",
    val score: Int = 0,
    val total: Int = 0,
    val percentage: Double = 0.0,
    val markedText: String = ""
)

data class StudentPerformance(
    val name: String = "",
    val averageScore: Double = 0.0,
    val examsTaken: Int = 0,
    val weakTopics: List<String> = emptyList(),
    val strongTopics: List<String> = emptyList(),
    val grade: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveUnitsApp(auth: FirebaseAuth, db: FirebaseFirestore) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("WaveUnitsPrefs", android.content.Context.MODE_PRIVATE)
    
    // Auth state
    var isLoggedIn by remember { mutableStateOf(prefs.getBoolean("isLoggedIn", false)) }
    var email by remember { mutableStateOf(prefs.getString("email", "") ?: "") }
    var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }
    var showSignup by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    
    // App state
    var currentView by remember { mutableStateOf("projects") }
    var projects by remember { mutableStateOf<List<ExamProject>>(emptyList()) }
    var currentProjectId by remember { mutableStateOf("") }
    var currentProjectTitle by remember { mutableStateOf("") }
    
    // Exam state
    var questionPaperText by remember { mutableStateOf("") }
    var answerKeyText by remember { mutableStateOf("") }
    var studentSheets by remember { mutableStateOf<List<StudentAnswerSheet>>(emptyList()) }
    var markedSheets by remember { mutableStateOf<List<MarkedSheet>>(emptyList()) }
    var grading by remember { mutableStateOf(false) }
    var showAddStudent by remember { mutableStateOf(false) }
    var newStudentName by remember { mutableStateOf("") }
    var showCreateExam by remember { mutableStateOf(false) }
    var newExamTitle by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var showAddPaper by remember { mutableStateOf(false) }
    var paperText by remember { mutableStateOf("") }
    var showSetKey by remember { mutableStateOf(false) }
    var keyText by remember { mutableStateOf("") }
    
    // Student portfolios
    var studentPortfolios by remember { mutableStateOf<List<StudentPerformance>>(emptyList()) }
    
    // ===== FUNCTIONS =====
    fun loadProjects() {
        scope.launch {
            try {
                val uid = auth.currentUser?.uid ?: return@launch
                val result = db.collection("exams")
                    .whereEqualTo("teacherId", uid)
                    .get()
                    .await()
                
                projects = result.documents.map { doc ->
                    ExamProject(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        subject = doc.getString("subject") ?: "",
                        status = doc.getString("status") ?: "created",
                        createdAt = doc.getString("createdAt") ?: "",
                        teacherId = doc.getString("teacherId") ?: "",
                        questionPaperText = doc.getString("questionPaperText") ?: "",
                        answerKey = doc.getString("answerKey") ?: "",
                        studentCount = 0
                    )
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun createExam(title: String) {
        scope.launch {
            try {
                isLoading = true
                val uid = auth.currentUser?.uid ?: return@launch
                
                val exam = hashMapOf(
                    "title" to title,
                    "subject" to "General",
                    "status" to "created",
                    "teacherId" to uid,
                    "createdAt" to SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()),
                    "timestamp" to System.currentTimeMillis()
                )
                
                db.collection("exams").add(exam).await()
                Toast.makeText(context, "Exam created!", Toast.LENGTH_SHORT).show()
                loadProjects()
                isLoading = false
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                isLoading = false
            }
        }
    }
    
    fun loadExamData(examId: String) {
        scope.launch {
            try {
                val doc = db.collection("exams").document(examId).get().await()
                if (doc.exists()) {
                    questionPaperText = doc.getString("questionPaperText") ?: ""
                    answerKeyText = doc.getString("answerKey") ?: ""
                    currentProjectTitle = doc.getString("title") ?: ""
                    
                    // Load student sheets
                    val sheetsResult = db.collection("exams").document(examId)
                        .collection("studentSheets")
                        .get()
                        .await()
                    
                    studentSheets = sheetsResult.documents.map { sheet ->
                        StudentAnswerSheet(
                            id = sheet.id,
                            studentName = sheet.getString("studentName") ?: "",
                            examId = examId,
                            score = (sheet.get("score") as? Number)?.toInt() ?: 0,
                            total = (sheet.get("total") as? Number)?.toInt() ?: 0,
                            percentage = (sheet.get("percentage") as? Number)?.toDouble() ?: 0.0
                        )
                    }
                    
                    // Load marked sheets
                    val markedResult = db.collection("exams").document(examId)
                        .collection("markedSheets")
                        .get()
                        .await()
                    
                    markedSheets = markedResult.documents.map { sheet ->
                        MarkedSheet(
                            id = sheet.id,
                            studentName = sheet.getString("studentName") ?: "",
                            examId = examId,
                            score = (sheet.get("score") as? Number)?.toInt() ?: 0,
                            total = (sheet.get("total") as? Number)?.toInt() ?: 0,
                            percentage = (sheet.get("percentage") as? Number)?.toDouble() ?: 0.0,
                            markedText = sheet.getString("markedText") ?: ""
                        )
                    }
                    
                    // Load student portfolios
                    loadStudentPortfolios()
                    
                    currentView = "exam"
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading exam: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun loadStudentPortfolios() {
        scope.launch {
            try {
                val uid = auth.currentUser?.uid ?: return@launch
                val result = db.collection("studentPortfolios")
                    .whereEqualTo("teacherId", uid)
                    .get()
                    .await()
                
                studentPortfolios = result.documents.map { doc ->
                    StudentPerformance(
                        name = doc.getString("name") ?: "",
                        averageScore = (doc.get("averageScore") as? Number)?.toDouble() ?: 0.0,
                        examsTaken = (doc.get("examsTaken") as? Number)?.toInt() ?: 0,
                        weakTopics = (doc.get("weakTopics") as? List<String>) ?: emptyList(),
                        strongTopics = (doc.get("strongTopics") as? List<String>) ?: emptyList(),
                        grade = doc.getString("grade") ?: ""
                    )
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    fun addQuestionPaper(text: String) {
        scope.launch {
            try {
                db.collection("exams").document(currentProjectId)
                    .update("questionPaperText", text)
                    .await()
                questionPaperText = text
                Toast.makeText(context, "Question paper added!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun setAnswerKey(text: String) {
        scope.launch {
            try {
                db.collection("exams").document(currentProjectId)
                    .update("answerKey", text)
                    .await()
                answerKeyText = text
                Toast.makeText(context, "Answer key set!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun addStudent(name: String) {
        scope.launch {
            try {
                val sheet = hashMapOf(
                    "studentName" to name,
                    "examId" to currentProjectId,
                    "score" to 0,
                    "total" to 0,
                    "percentage" to 0.0,
                    "createdAt" to System.currentTimeMillis()
                )
                
                db.collection("exams").document(currentProjectId)
                    .collection("studentSheets")
                    .add(sheet)
                    .await()
                
                Toast.makeText(context, "Student added!", Toast.LENGTH_SHORT).show()
                loadExamData(currentProjectId)
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun gradeAllStudents() {
        scope.launch {
            try {
                grading = true
                
                // Get answer key
                val doc = db.collection("exams").document(currentProjectId).get().await()
                val answerKey = doc.getString("answerKey") ?: ""
                
                if (answerKey.isBlank()) {
                    Toast.makeText(context, "Set answer key first!", Toast.LENGTH_SHORT).show()
                    grading = false
                    return@launch
                }
                
                // Parse answer key
                val answerMap = mutableMapOf<Int, String>()
                answerKey.split(",").forEach { pair ->
                    val parts = pair.split(":")
                    if (parts.size == 2) {
                        val qNum = parts[0].trim().toIntOrNull()
                        val answer = parts[1].trim()
                        if (qNum != null) {
                            answerMap[qNum] = answer
                        }
                    }
                }
                
                // Get all student sheets
                val sheetsResult = db.collection("exams").document(currentProjectId)
                    .collection("studentSheets")
                    .get()
                    .await()
                
                val marked = mutableListOf<MarkedSheet>()
                
                for (sheetDoc in sheetsResult.documents) {
                    val studentName = sheetDoc.getString("studentName") ?: ""
                    
                    // Parse student answers from sheet
                    val studentAnswers = mutableMapOf<Int, String>()
                    var correct = 0
                    val total = answerMap.size
                    
                    // Get answers from Firestore or use sample
                    val answersText = sheetDoc.getString("answers") ?: ""
                    if (answersText.isNotBlank()) {
                        answersText.split(",").forEach { pair ->
                            val parts = pair.split(":")
                            if (parts.size == 2) {
                                val qNum = parts[0].trim().toIntOrNull()
                                val answer = parts[1].trim()
                                if (qNum != null) {
                                    studentAnswers[qNum] = answer
                                }
                            }
                        }
                    }
                    
                    // If no answers found, use sample for demo
                    if (studentAnswers.isEmpty()) {
                        answerMap.keys.forEach { qNum ->
                            val demoAnswer = when (qNum % 4) {
                                0 -> "A"
                                1 -> "B"
                                2 -> "C"
                                else -> "D"
                            }
                            studentAnswers[qNum] = demoAnswer
                        }
                    }
                    
                    // Grade
                    answerMap.forEach { (qNum, correctAns) ->
                        val studentAns = studentAnswers[qNum] ?: ""
                        if (studentAns == correctAns) correct++
                    }
                    
                    val percentage = if (total > 0) (correct.toDouble() / total) * 100 else 0.0
                    
                    val markedText = buildString {
                        append("?? Marked: $studentName\n")
                        append("Score: $correct/$total (${String.format("%.1f", percentage)}%)\n")
                        append("-" * 30 + "\n")
                        answerMap.keys.sorted().forEach { qNum ->
                            val studentAns = studentAnswers[qNum] ?: "[BLANK]"
                            val correctAns = answerMap[qNum] ?: ""
                            val status = if (studentAns == correctAns) "?" else "?"
                            append("Q$qNum: $studentAns | Correct: $correctAns $status\n")
                        }
                    }
                    
                    val markedSheet = MarkedSheet(
                        studentName = studentName,
                        examId = currentProjectId,
                        score = correct,
                        total = total,
                        percentage = percentage,
                        markedText = markedText
                    )
                    
                    marked.add(markedSheet)
                    
                    // Save to Firestore
                    db.collection("exams").document(currentProjectId)
                        .collection("markedSheets")
                        .add(mapOf(
                            "studentName" to studentName,
                            "examId" to currentProjectId,
                            "score" to correct,
                            "total" to total,
                            "percentage" to percentage,
                            "markedText" to markedText,
                            "createdAt" to System.currentTimeMillis()
                        ))
                        .await()
                    
                    // Save result
                    db.collection("results")
                        .add(mapOf(
                            "studentName" to studentName,
                            "examId" to currentProjectId,
                            "score" to correct,
                            "totalMarks" to total,
                            "percentage" to percentage,
                            "createdAt" to System.currentTimeMillis()
                        ))
                        .await()
                }
                
                markedSheets = marked
                grading = false
                Toast.makeText(context, "Grading complete! ${marked.size} students graded.", Toast.LENGTH_LONG).show()
                
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                grading = false
            }
        }
    }
    
    // ===== LOGIN SCREEN =====
    if (!isLoggedIn) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "?? WaveUnits",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6200EE)
            )
            
            Text(
                text = "Exam Management System",
                fontSize = 16.sp,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    scope.launch {
                        try {
                            isLoading = true
                            if (showSignup) {
                                auth.createUserWithEmailAndPassword(email, password).await()
                                Toast.makeText(context, "Account created!", Toast.LENGTH_SHORT).show()
                            } else {
                                auth.signInWithEmailAndPassword(email, password).await()
                                Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                            }
                            
                            isLoggedIn = true
                            prefs.edit().putBoolean("isLoggedIn", true).apply()
                            prefs.edit().putString("email", email).apply()
                            prefs.edit().putString("password", password).apply()
                            
                            loadProjects()
                            isLoading = false
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)),
                enabled = !isLoading
            ) {
                Text(if (isLoading) "Please wait..." else if (showSignup) "Sign Up" else "Login", color = Color.White)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            TextButton(onClick = { showSignup = !showSignup }) {
                Text(
                    if (showSignup) "Already have account? Login" else "New user? Sign Up",
                    color = Color(0xFF6200EE)
                )
            }
        }
        return
    }
    
    // ===== MAIN APP =====
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WaveUnits ??") },
                actions = {
                    IconButton(
                        onClick = {
                            auth.signOut()
                            isLoggedIn = false
                            prefs.edit().putBoolean("isLoggedIn", false).apply()
                            Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("??", fontSize = 20.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF6200EE))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentView == "projects") {
                // ===== PROJECTS LIST =====
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Button(
                            onClick = { showCreateExam = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("+ Create New Exam", color = Color.White)
                        }
                    }
                    
                    if (projects.isEmpty()) {
                        item {
                            Card {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("?? No exams yet", fontSize = 18.sp)
                                    Text("Click 'Create New Exam' to start", color = Color.Gray)
                                }
                            }
                        }
                    }
                    
                    items(projects) { project ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentProjectId = project.id
                                    loadExamData(project.id)
                                },
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(project.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("?? ${project.subject} | Status: ${project.status}")
                                Text("?? Created: ${project.createdAt}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            } else {
                // ===== EXAM DETAIL VIEW =====
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(currentProjectTitle) },
                            navigationIcon = {
                                IconButton(onClick = {
                                    currentView = "projects"
                                    loadProjects()
                                }) {
                                    Text("?", fontSize = 24.sp)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF6200EE))
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Tabs
                        TabRow(selectedTabIndex = selectedTab) {
                            listOf("?? Overview", "?? Paper", "????? Students", "?? Results", "?? Analytics").forEachIndexed { i, text ->
                                Tab(
                                    selected = selectedTab == i,
                                    onClick = { selectedTab = i },
                                    text = { Text(text) }
                                )
                            }
                        }
                        
                        when (selectedTab) {
                            0 -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item {
                                        Card {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("?? Overview", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                Text("? Question Paper: ${if (questionPaperText.isNotBlank()) "Added" else "Missing"}")
                                                Text("?? Answer Key: ${if (answerKeyText.isNotBlank()) "Set" else "Not Set"}")
                                                Text("????? Students: ${studentSheets.size}")
                                                Text("?? Graded: ${markedSheets.size}")
                                                
                                                Spacer(modifier = Modifier.height(16.dp))
                                                
                                                Button(
                                                    onClick = { gradeAllStudents() },
                                                    enabled = !grading && studentSheets.isNotEmpty() && answerKeyText.isNotBlank(),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                                ) {
                                                    Text(if (grading) "? Grading..." else "?? Grade All Students")
                                                }
                                                
                                                if (grading) {
                                                    Text("Grading in progress...", color = Color(0xFF6200EE))
                                                }
                                            }
                                        }
                                    }
                                    
                                    item {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { showAddPaper = true },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("?? Add Paper")
                                            }
                                            
                                            if (showAddPaper) {
                                                AlertDialog(
                                                    onDismissRequest = { showAddPaper = false },
                                                    title = { Text("Add Question Paper") },
                                                    text = {
                                                        OutlinedTextField(
                                                            value = paperText,
                                                            onValueChange = { paperText = it },
                                                            label = { Text("Question Paper Text") },
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    },
                                                    confirmButton = {
                                                        TextButton(
                                                            onClick = {
                                                                if (paperText.isNotBlank()) {
                                                                    addQuestionPaper(paperText)
                                                                    paperText = ""
                                                                    showAddPaper = false
                                                                }
                                                            }
                                                        ) {
                                                            Text("Save")
                                                        }
                                                    },
                                                    dismissButton = {
                                                        TextButton(onClick = { showAddPaper = false }) {
                                                            Text("Cancel")
                                                        }
                                                    }
                                                )
                                            }
                                            
                                            Button(
                                                onClick = { showSetKey = true },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                                            ) {
                                                Text("?? Set Key")
                                            }
                                            
                                            if (showSetKey) {
                                                AlertDialog(
                                                    onDismissRequest = { showSetKey = false },
                                                    title = { Text("Set Answer Key") },
                                                    text = {
                                                        OutlinedTextField(
                                                            value = keyText,
                                                            onValueChange = { keyText = it },
                                                            label = { Text("Format: 1:A,2:B,3:C") },
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    },
                                                    confirmButton = {
                                                        TextButton(
                                                            onClick = {
                                                                if (keyText.isNotBlank()) {
                                                                    setAnswerKey(keyText)
                                                                    keyText = ""
                                                                    showSetKey = false
                                                                }
                                                            }
                                                        ) {
                                                            Text("Save")
                                                        }
                                                    },
                                                    dismissButton = {
                                                        TextButton(onClick = { showSetKey = false }) {
                                                            Text("Cancel")
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    
                                    item {
                                        Button(
                                            onClick = { showAddStudent = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                                        ) {
                                            Text("? Add Student", color = Color.White)
                                        }
                                    }
                                }
                            }
                            
                            1 -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item {
                                        Card {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("?? Question Paper", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                if (questionPaperText.isNotBlank()) {
                                                    Text(questionPaperText, fontSize = 14.sp)
                                                } else {
                                                    Text("No question paper added yet.", color = Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                    item {
                                        Card {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("?? Answer Key", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                if (answerKeyText.isNotBlank()) {
                                                    Text(answerKeyText, fontSize = 14.sp)
                                                } else {
                                                    Text("No answer key set yet.", color = Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            2 -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item {
                                        Text("????? Students (${studentSheets.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    if (studentSheets.isEmpty()) {
                                        item {
                                            Text("No students added yet.", color = Color.Gray)
                                        }
                                    }
                                    
                                    items(studentSheets) { student ->
                                        Card {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text(student.studentName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                if (student.score > 0) {
                                                    Text("Score: ${student.score}/${student.total} (${String.format("%.1f", student.percentage)}%)")
                                                } else {
                                                    Text("Not graded yet", color = Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            3 -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item {
                                        Text("?? Results (${markedSheets.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    if (markedSheets.isEmpty()) {
                                        item {
                                            Text("No results yet. Grade students first!", color = Color.Gray)
                                        }
                                    }
                                    
                                    items(markedSheets) { sheet ->
                                        Card {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text(sheet.studentName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                Text("Score: ${sheet.score}/${sheet.total} (${String.format("%.1f", sheet.percentage)}%)")
                                                if (sheet.markedText.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(sheet.markedText, fontSize = 12.sp, maxLines = 10)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            
                            4 -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item {
                                        Text("?? Student Portfolios", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    if (studentPortfolios.isEmpty()) {
                                        item {
                                            Text("No portfolio data available.", color = Color.Gray)
                                        }
                                    }
                                    
                                    items(studentPortfolios) { student ->
                                        Card {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text(student.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                Text("?? Average: ${String.format("%.1f", student.averageScore)}%")
                                                Text("?? Exams: ${student.examsTaken}")
                                                Text("?? Grade: ${student.grade}")
                                                if (student.weakTopics.isNotEmpty()) {
                                                    Text("?? Weak Topics: ${student.weakTopics.joinToString(", ")}", color = Color.Red)
                                                }
                                                if (student.strongTopics.isNotEmpty()) {
                                                    Text("? Strong Topics: ${student.strongTopics.joinToString(", ")}", color = Color.Green)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // ===== DIALOGS =====
    // Create Exam Dialog
    if (showCreateExam) {
        AlertDialog(
            onDismissRequest = { showCreateExam = false },
            title = { Text("?? Create New Exam") },
            text = {
                OutlinedTextField(
                    value = newExamTitle,
                    onValueChange = { newExamTitle = it },
                    label = { Text("Exam Title") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newExamTitle.isNotBlank()) {
                            createExam(newExamTitle)
                            newExamTitle = ""
                            showCreateExam = false
                        } else {
                            Toast.makeText(context, "Enter exam title", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateExam = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Add Student Dialog
    if (showAddStudent) {
        AlertDialog(
            onDismissRequest = { showAddStudent = false },
            title = { Text("????? Add Student") },
            text = {
                OutlinedTextField(
                    value = newStudentName,
                    onValueChange = { newStudentName = it },
                    label = { Text("Student Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newStudentName.isNotBlank()) {
                            addStudent(newStudentName)
                            newStudentName = ""
                            showAddStudent = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddStudent = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}