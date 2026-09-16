package com.waveunits.docs

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WaveUnitsApp() }
    }
}

// ===== DATA CLASSES =====
data class ExamProject(
    val id: String = "",
    val title: String = "",
    val subjectKey: String = "",
    val grade: String = "",
    val status: String = "",
    val createdAt: String = "",
    val createdAtMillis: Long = 0L,
    val questionPaperText: String = "",
    val aiAnswerSheet: String = "",
    val simplifiedAnswerKey: String = "",
    val questionPaperImages: List<String> = emptyList(),
    val allQuestionTexts: List<String> = emptyList(),
    val answerKey: String = ""
)

data class QuestionData(val number: Int, val topic: String, val correctAnswer: String, val subTopic: String = "")
data class QuestionResultData(val questionNumber: Int, val topic: String, val studentAnswer: String, val correctAnswer: String, val isCorrect: Boolean)
data class StudentResult(val studentName: String, val score: Int, val totalMarks: Int, val percentage: Double, val questions: List<QuestionResultData>)
data class StudentPortfolio(val name: String, val averageScore: Double, val examsTaken: Int, val weakTopics: List<String>, val strongTopics: List<String>)

data class StudentAnswerSheetData(
    val studentName: String,
    val extractedText: String,
    val image: String,
    val answers: Map<Int, String> = emptyMap()
)

data class StudentPerformance(
    val name: String,
    val averageScore: Double,
    val examCount: Int,
    val weakTopics: List<String>,
    val strongTopics: List<String>,
    val totalScore: Double,
    val grade: String,
    val rank: Int
)

data class ScoreDistribution(
    val gradeA: Int, val gradeAMinus: Int, val gradeBPlus: Int,
    val gradeB: Int, val gradeBMinus: Int, val gradeCPlus: Int,
    val gradeC: Int, val gradeCMinus: Int, val gradeDPlus: Int,
    val gradeD: Int, val gradeDMinus: Int, val gradeE: Int
)

data class QuestionBreakdown(
    val questionNumber: Int, val topic: String,
    val correctCount: Int, val totalCount: Int, val difficulty: String
)

data class ClassTopicPerformance(
    val topic: String, val averageScore: Double,
    val questionsAttempted: Int, val correctAnswers: Int
)

data class ActionableInsight(val type: String, val message: String, val topic: String, val score: Double)
data class TrendData(val topic: String, val previousScore: Double, val currentScore: Double, val change: Double)
data class StudentComparisonData(val studentA: String, val studentB: String, val scoreDifference: Double, val studentAImproved: Boolean, val studentBImproved: Boolean)

data class ExamAnalyticsBundle(
    val studentLeaderboard: List<StudentPerformance>,
    val scoreDistribution: ScoreDistribution,
    val questionBreakdown: List<QuestionBreakdown>,
    val studentPortfolios: List<StudentPortfolio>,
    val classTopicPerformance: List<ClassTopicPerformance>,
    val actionableInsights: List<ActionableInsight>,
    val trends: List<TrendData>,
    val studentComparisons: List<StudentComparisonData>
)

data class ExamPoint(val examId: String, val examTitle: String, val timestamp: Long, val classAverage: Double, val studentCount: Int)
data class TopicSeriesPoint(val topic: String, val scores: List<Pair<Long, Double>>, val isPersistentlyWeak: Boolean)
data class StudentSeriesPoint(val studentName: String, val scores: List<Pair<Long, Double>>, val change: Double, val trend: String)
data class ClusterPoint(val topic: String, val students: List<String>, val examCount: Int)

data class SeriesAnalyticsBundle(
    val grade: String,
    val subjectKey: String,
    val examPoints: List<ExamPoint>,
    val classAverage: Double,
    val classTrend: Double,
    val topicSeries: List<TopicSeriesPoint>,
    val studentSeries: List<StudentSeriesPoint>,
    val clusters: List<ClusterPoint>,
    val examCount: Int
)

data class MarkedAnswerSheetData(
    val studentName: String, val markedText: String, val image: String,
    val score: Int, val total: Int, val percentage: Double
)

data class AIQuestionAnswer(val id: String = "", val question: String = "", val answer: String = "", val createdAt: String = "")
data class RosterTemplate(val id: String = "", val name: String = "", val names: List<String> = emptyList())

data class SectionViewState(
    val isDashboardOpen: Boolean = false,
    val isQuestionPaperOpen: Boolean = false,
    val isAIAnswerSheetOpen: Boolean = false,
    val isManualAnswerSheetOpen: Boolean = false,
    val isCollectedSheetsOpen: Boolean = false,
    val isMarkedSheetsOpen: Boolean = false,
    val isPrintableReportOpen: Boolean = false,
    val isRosterSetupOpen: Boolean = false,
    val isSavedRostersOpen: Boolean = false,
    val isAnswerSheetGeneratorOpen: Boolean = false
)

private var OPENAI_API_KEY: String = ""

val CBC_SUBJECTS = listOf(
    "Mathematics",
    "English",
    "Kiswahili",
    "Integrated Science",
    "Social Studies",
    "Agriculture and Nutrition",
    "Creative Arts and Sports",
    "Religious Education (CRE)",
    "Religious Education (IRE)",
    "Religious Education (HRE)",
    "Pre-Technical Studies"
)

val CBC_GRADES = listOf(
    "Grade 1", "Grade 2", "Grade 3", "Grade 4", "Grade 5", "Grade 6",
    "Grade 7", "Grade 8", "Grade 9", "Grade 10", "Grade 11", "Grade 12"
)

// ===== UTILITY FUNCTIONS =====
fun parseCreatedAt(value: Any?): Long = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is Number -> value.toLong()
    is String -> try { value.toLong() } catch (e: Exception) { 0L }
    else -> 0L
}

fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "Unknown"
    return try {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
    } catch (e: Exception) { "Unknown" }
}

fun decodeBase64(base64: String): Bitmap? = try {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (e: Exception) { null }

fun parseAnswerKeyToQuestions(answerKeyString: String): List<QuestionData> {
    val questions = mutableListOf<QuestionData>()
    if (answerKeyString.isBlank()) return questions
    val pattern1 = Regex("""Q(\d+):\s*Answer:\s*([A-Da-d])\s*\|\s*Topic:\s*([^|]+)\s*\|\s*Sub-topic:\s*([^|]+)""")
    for (match in pattern1.findAll(answerKeyString)) {
        val number = match.groupValues[1].toIntOrNull() ?: continue
        val answer = match.groupValues[2].uppercase()
        val topic = match.groupValues[3].trim()
        val subTopic = match.groupValues[4].trim()
        questions.add(QuestionData(number, topic, answer, subTopic))
    }
    if (questions.isNotEmpty()) return questions
    val pattern2 = Regex("""(\d+)\s*:\s*([A-Da-d])""")
    for (match in pattern2.findAll(answerKeyString)) {
        val number = match.groupValues[1].toIntOrNull() ?: continue
        val answer = match.groupValues[2].uppercase()
        questions.add(QuestionData(number, "General", answer))
    }
    return questions
}

fun parseAISheetToSimplified(aiAnswerSheet: String): String {
    if (aiAnswerSheet.isBlank()) return ""
    val answers = mutableListOf<String>()
    val pattern1 = Regex("""Q?(\d+)\s*:\s*Answer:\s*([A-Da-d]|null|NULL)""", RegexOption.IGNORE_CASE)
    for (match in pattern1.findAll(aiAnswerSheet)) {
        val number = match.groupValues[1].toIntOrNull() ?: continue
        val answer = match.groupValues[2].uppercase()
        if (answer == "NULL" || answer.isBlank()) continue
        answers.add("$number:$answer")
    }
    if (answers.isNotEmpty()) return answers.joinToString(",")
    val pattern2 = Regex("""(\d+)\s*[.\-:)]\s*([A-Da-d])""")
    for (match in pattern2.findAll(aiAnswerSheet)) {
        val number = match.groupValues[1].toIntOrNull() ?: continue
        answers.add("$number:${match.groupValues[2].uppercase()}")
    }
    return answers.joinToString(",")
}

fun getKenyanGrade(percentage: Double): String = when {
    percentage >= 75.0 -> "A"
    percentage >= 70.0 -> "A-"
    percentage >= 65.0 -> "B+"
    percentage >= 60.0 -> "B"
    percentage >= 55.0 -> "B-"
    percentage >= 45.0 -> "C+"
    percentage >= 40.0 -> "C"
    percentage >= 35.0 -> "C-"
    percentage >= 30.0 -> "D+"
    percentage >= 25.0 -> "D"
    percentage >= 20.0 -> "D-"
    else -> "E"
}

fun generateActionableInsights(
    classTopicPerformance: List<ClassTopicPerformance>,
    studentLeaderboard: List<StudentPerformance>
): List<ActionableInsight> {
    val insights = mutableListOf<ActionableInsight>()
    classTopicPerformance.filter { it.averageScore < 50.0 }.forEach {
        insights.add(ActionableInsight("RETEACH", "Re-teach ${it.topic} (Class avg: ${"%.1f".format(it.averageScore)}%)", it.topic, it.averageScore))
    }
    classTopicPerformance.filter { it.averageScore >= 80.0 }.forEach {
        insights.add(ActionableInsight("STRONG", "Strong topic: ${it.topic} (Class avg: ${"%.1f".format(it.averageScore)}%)", it.topic, it.averageScore))
    }
    studentLeaderboard.filter { it.averageScore < 40.0 }.forEach {
        insights.add(ActionableInsight("FOCUS", "Focus on ${it.name} (Avg: ${"%.1f".format(it.averageScore)}%)", it.name, it.averageScore))
    }
    return insights
}

fun generateTrends(current: List<ClassTopicPerformance>, previous: List<ClassTopicPerformance>): List<TrendData> {
    val trends = mutableListOf<TrendData>()
    for (c in current) {
        val p = previous.find { it.topic == c.topic } ?: continue
        trends.add(TrendData(c.topic, p.averageScore, c.averageScore, c.averageScore - p.averageScore))
    }
    return trends
}

fun generateStudentComparisons(leaderboard: List<StudentPerformance>): List<StudentComparisonData> {
    val comparisons = mutableListOf<StudentComparisonData>()
    if (leaderboard.size >= 2) {
        val a = leaderboard[0]; val b = leaderboard[1]
        comparisons.add(StudentComparisonData(a.name, b.name, a.averageScore - b.averageScore, a.averageScore >= 50.0, b.averageScore >= 50.0))
    }
    return comparisons
}

fun generatePrintableMarkedSheets(markedSheets: List<MarkedAnswerSheetData>): String {
    val sb = StringBuilder()
    sb.append("========================================\n")
    sb.append("  WAVEUNITS - MARKED ANSWER SHEETS REPORT\n")
    sb.append("  Generated: ${formatTimestamp(System.currentTimeMillis())}\n")
    sb.append("========================================\n\n")
    var countOnPage = 0
    val maxPerPage = 3
    for (sheet in markedSheets) {
        if (countOnPage >= maxPerPage) {
            sb.append("\n============= NEW PAGE =============\n\n")
            countOnPage = 0
        }
        sb.append(sheet.markedText).append("\n\n")
        countOnPage++
    }
    sb.append("\n============= END OF REPORT =============\n")
    return sb.toString()
}

fun generateAnswerSheetTemplate(questionCount: Int): String {
    val sb = StringBuilder()
    sb.append("=========================================\n")
    sb.append("     [SCHOOL NAME] - [SCHOOL LOGO]\n")
    sb.append("     ANSWER SHEET - [SUBJECT] - [GRADE]\n")
    sb.append("     TERM: _______  DATE: _______\n")
    sb.append("     STUDENT NAME: ______________________\n")
    sb.append("=========================================\n")
    sb.append("| NO. |   A   |   B   |   C   |   D   |\n")
    sb.append("|-----|-------|-------|-------|-------|\n")
    for (i in 1..questionCount) {
        sb.append("| ${i.toString().padEnd(3)} |  [ ]  |  [ ]  |  [ ]  |  [ ]  |\n")
    }
    sb.append("=========================================\n")
    sb.append("Instructions: Write the letter inside the bracket of the column you choose.\n")
    sb.append("=========================================\n")
    return sb.toString()
}

// ===== AI FUNCTIONS (unchanged from previous version) =====
private suspend fun transcribeAnswerSheet(context: Context, uri: Uri): String {
    return withContext(Dispatchers.IO) {
        try {
            val isr = context.contentResolver.openInputStream(uri) ?: return@withContext "ERR: cannot open image"
            val bmp = BitmapFactory.decodeStream(isr)
            isr.close()
            val scaled = Bitmap.createScaledBitmap(bmp, 1400, 2000, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val prompt = """
                Transcribe everything in the image as plain text.
                Keep the table layout, all columns, all rows.
                Do not add. Do not remove. Do not summarise.
                IMPORTANT: Look VERY carefully at the bracket cells for
                handwritten marks. Print the letter you see inside each bracket.
                If a bracket has a mark but the letter is unclear, print the column
                letter (A/B/C/D) inside that bracket. Print EMPTY brackets as [   ].
            """.trimIndent()
            val content = JSONArray()
                .put(JSONObject().put("type", "text").put("text", prompt))
                .put(JSONObject().put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64")))
            val body = JSONObject()
                .put("model", "gpt-5.6-luna")
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                .put("max_completion_tokens", 4000)
                .toString()
            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
                .build()
            client.newCall(req).execute().use { res ->
                val js = res.body?.string() ?: return@use "ERR: empty response body"
                if (!res.isSuccessful) return@use "ERR HTTP ${res.code}: ${js.take(400)}"
                try {
                    JSONObject(js).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content").trim()
                } catch (e: Exception) { "ERR parse: ${e.message} | raw: ${js.take(400)}" }
            }
        } catch (e: Exception) { "ERR exception: ${e.message}" }
    }
}

private suspend fun gradeWithAI(
    printout: String,
    aiAnswerSheetText: String,
    studentName: String
): StudentResult? {
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val prompt = """
            TASK: Grade a student's answer sheet by reading a GRID.

            The printout below is a TABLE. Each row starts with a question number,
            followed by FOUR BRACKET CELLS under columns A | B | C | D.

            HOW TO READ THE STUDENT'S ANSWER ON EACH ROW:
            - Look only at the four bracket cells on that row.
            - If a cell contains a letter, that letter is the answer.
            - If two cells have letters, take the LEFTMOST one.
            - If a bracket has a mark but the letter is unreadable, use the
              COLUMN HEADER of that bracket as the answer.
            - If all four cells are empty, the answer is blank.

            MATCHING: Compare each student answer to the correct answer from
            the ANSWER KEY below, matched by question number.

            ============ PART 1 — HUMAN-READABLE SHEET ============
            Write one line per graded question:

            Q1: Student chose A, correct answer is B -> WRONG
            Q2: Student chose C, correct answer is C -> CORRECT

            Then:
            SCORE: <number correct> / <total>
            PERCENTAGE: <number>%
            GRADE: <letter>

            ============ PART 2 — MACHINE-READABLE BLOCK ============
            After the score, print this line on its own:

            ---ANALYTICS---

            Then ONE line per question, EXACTLY this format:

            Q<number>|<studentLetter>|<correctLetter>|<C or W>|<topic>

            Topic = the strand and sub-strand copied from the ANSWER KEY for
            that question, in the form "Strand -> Sub-strand".

            ===== STUDENT PRINTOUT =====
            $printout

            ===== ANSWER KEY =====
            $aiAnswerSheetText
        """.trimIndent()
        val body = JSONObject()
            .put("model", "gpt-5.6-luna")
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("max_completion_tokens", 5000)
            .toString()
        val req = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
            .build()
        var result: StudentResult? = null
        client.newCall(req).execute().use { res ->
            val js = res.body?.string() ?: return@use
            if (!res.isSuccessful) throw RuntimeException("HTTP ${res.code}: ${js.take(400)}")
            val fullReply = JSONObject(js).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
            if (fullReply.isBlank()) throw RuntimeException("Empty AI reply")
            val marker = "---ANALYTICS---"
            val humanPart = if (fullReply.contains(marker)) fullReply.substringBefore(marker).trim() else fullReply
            val machinePart = if (fullReply.contains(marker)) fullReply.substringAfter(marker).trim() else ""
            val questions = mutableListOf<QuestionResultData>()
            val lineRegex = Regex("""Q\s*(\d+)\s*\|\s*([A-Da-d]?)\s*\|\s*([A-Da-d]?)\s*\|\s*([CWcw])\s*\|\s*(.+)$""")
            for (line in machinePart.lines()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val m = lineRegex.find(trimmed) ?: continue
                val num = m.groupValues[1].toIntOrNull() ?: continue
                val stu = m.groupValues[2].uppercase().take(1)
                val cor = m.groupValues[3].uppercase().take(1)
                val isC = m.groupValues[4].uppercase() == "C"
                val topic = m.groupValues[5].trim()
                questions.add(QuestionResultData(num, topic, stu, cor, isC))
            }
            var correct = questions.count { it.isCorrect }
            var total = questions.size
            var pct = if (total > 0) correct * 100.0 / total else 0.0
            if (questions.isEmpty()) {
                val scoreRegex = Regex("""SCORE\s*:\s*(\d+)\s*/\s*(\d+)""", RegexOption.IGNORE_CASE)
                scoreRegex.find(humanPart)?.let {
                    correct = it.groupValues[1].toIntOrNull() ?: 0
                    total = it.groupValues[2].toIntOrNull() ?: 0
                    if (total > 0) pct = correct * 100.0 / total
                }
            }
            val single = QuestionResultData(0, "ai-text", humanPart, "", correct > 0)
            result = StudentResult(studentName, correct, total, pct, listOf(single) + questions)
        }
        result
    }
}

private suspend fun askAIWithContext(contextText: String, question: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val prompt = "Context:\n$contextText\n\nQuestion:\n$question\n\nAnswer clearly based ONLY on the data above."
            val body = JSONObject()
                .put("model", "gpt-5.6-luna")
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                .put("max_completion_tokens", 3000)
                .toString()
            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
                .build()
            client.newCall(req).execute().use { res ->
                val js = res.body?.string() ?: return@use "Error: No response"
                if (!res.isSuccessful) return@use "Error: HTTP ${res.code}"
                try {
                    JSONObject(js).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                } catch (e: Exception) { "Error parsing: ${e.message}" }
            }
        } catch (e: Exception) { "Error: ${e.message}" }
    }
}

private fun parseManualAnswerKey(text: String): List<Pair<Int, String>> {
    val answers = mutableListOf<Pair<Int, String>>()
    val bracketPattern = Regex("""\|\s*(\d{1,2})\s*\|\s*\[([A-Da-d]?)\]\s*\|\s*\[([A-Da-d]?)\]\s*\|\s*\[([A-Da-d]?)\]\s*\|\s*\[([A-Da-d]?)\]\s*\|""")
    for (match in bracketPattern.findAll(text)) {
        val qNum = match.groupValues[1].toIntOrNull() ?: continue
        val answer = (match.groupValues[2].takeIf { it.isNotBlank() }
            ?: match.groupValues[3].takeIf { it.isNotBlank() }
            ?: match.groupValues[4].takeIf { it.isNotBlank() }
            ?: match.groupValues[5].takeIf { it.isNotBlank() })?.uppercase() ?: continue
        if (qNum in 1..100) answers.add(Pair(qNum, answer))
    }
    if (answers.isEmpty()) {
        val pattern = Regex("""(\d{1,2})\s*[.\-:)]?\s*([A-Da-d])""")
        for (match in pattern.findAll(text)) {
            val qNum = match.groupValues[1].toIntOrNull() ?: continue
            if (qNum in 1..100) answers.add(Pair(qNum, match.groupValues[2].uppercase()))
        }
    }
    return answers
}

private suspend fun generateAIAnswerSheetWithTopics(questions: List<QuestionData>): String {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder().build()
            val prompt = """
                Generate an answer sheet with Kenyan CBC curriculum topics for each question.
                The answer MUST be A, B, C, or D. If unknown, choose A.
                Questions:
                ${questions.joinToString("\n") { "Q${it.number}: ${it.topic} - Answer: ${it.correctAnswer}" }}
                Format:
                Q1: Answer: D | Topic: Life of Prophets / Messengers | Sub-topic: Parables
            """.trimIndent()
            val body = JSONObject()
                .put("model", "gpt-5.6-luna")
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                .put("max_completion_tokens", 3000)
                .toString()
            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
                .build()
            client.newCall(req).execute().use { res ->
                val js = res.body?.string() ?: return@use ""
                if (!res.isSuccessful) return@use ""
                try {
                    JSONObject(js).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
                } catch (e: Exception) {
                    questions.joinToString("\n") { "Q${it.number}: Answer: ${it.correctAnswer} | Topic: ${it.topic}" }
                }
            }
        } catch (e: Exception) {
            questions.joinToString("\n") { "Q${it.number}: Answer: ${it.correctAnswer} | Topic: ${it.topic}" }
        }
    }
}

private suspend fun parseQuestionsWithTopics(rawText: String): List<QuestionData> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder().build()
            val prompt = """
                You are analyzing a Kenyan CBC exam paper. For every question,
                identify its STRAND and SUB-STRAND from the official KICD
                rationalized curriculum, and the correct answer.

                ## CBC STRANDS BY LEARNING AREA

                English:
                - Listening and Speaking (subs: Polite expressions, Directions, Debates, Oral presentation)
                - Reading (subs: Comprehension, Fluency, Extensive reading, Critical reading)
                - Grammar / Language Structures (subs: Nouns, Verbs, Tenses, Prepositions, Adjectives, Adverbs, Conjunctions, Pronouns, Punctuation)
                - Writing (subs: Handwriting, Creative writing, Functional writing, Paragraph writing)

                Kiswahili:
                - Kusikiliza na Kuzungumza (subs: Salamu, Maelekezo, Mazungumzo, Kutoa taarifa)
                - Kusoma (subs: Ufahamu, Kusoma kwa sauti, Fasihi)
                - Sarufi (subs: Nomino, Vitenzi, Nyakati, Viunganishi, Ngeli)
                - Kuandika (subs: Insha, Barua, Muhtasari)

                Mathematics:
                - Numbers (subs: Counting, Place value, Addition, Subtraction, Multiplication, Division, Fractions, Decimals, Percentages, Ratios, Integers, Algebra, Equations, Patterns)
                - Measurement (subs: Length, Mass, Capacity, Time, Money, Area, Perimeter, Volume)
                - Geometry (subs: Shapes, Angles, Lines, Polygons, Coordinates)
                - Data Handling (subs: Pictographs, Bar graphs, Pie charts, Averages)

                Integrated Science:
                - Living Things (subs: Plants, Animals, Human body, Health)
                - Environment (subs: Pollution, Conservation, Weather, Soil)
                - Matter (subs: States of matter, Mixtures, Acids and bases)
                - Force and Energy (subs: Simple machines, Energy conversion, Light, Sound)

                Social Studies:
                - Physical Environment (subs: Map work, Physical features, Climate)
                - People and Population (subs: Communities, Migration)
                - Resources and Economic Activities (subs: Agriculture, Mining, Forestry, Trade)
                - Citizenship (subs: Rights, Governance, National unity)

                Agriculture and Nutrition:
                - Food Production (subs: Crops, Livestock, Kitchen garden)
                - Conservation (subs: Soil, Water)
                - Consumer Education (subs: Food safety, Cooking, Nutrition)
                - Needlework (subs: Stitches, Garment making)

                Creative Arts and Sports:
                - Visual Arts (subs: Drawing, Painting, Modelling, Craft)
                - Music (subs: Singing, Instruments, Rhythm, Composition)
                - Performing Arts (subs: Dance, Drama, Puppetry)
                - Physical Education (subs: Athletics, Ball games, Gymnastics)

                Religious Education (CRE / IRE / HRE):
                - Creation (subs: God's creation, Family, Environment)
                - Holy Books (subs: Scripture, Commandments)
                - Life of Prophets / Messengers (subs: Prophets, Miracles, Parables)
                - Values (subs: Love, Honesty, Obedience, Sharing, Integrity)

                Pre-Technical Studies:
                - Technical Drawing (subs: Orthographic projection, Sketching)
                - Digital Literacy (subs: Computing, Coding, Spreadsheets)
                - Business Basics (subs: Money, Trade, Entrepreneurship)

                ## TASK
                Return one entry per question:
                - number (integer)
                - strand (from the list above)
                - subStrand (from the list above)
                - correctAnswer (MUST be A, B, C, or D.)

                Return ONLY JSON:
                [{"number":1,"strand":"Life of Prophets / Messengers","subStrand":"Parables","correctAnswer":"D"}, ...]

                Question paper:
                $rawText
            """.trimIndent()
            val body = JSONObject()
                .put("model", "gpt-5.6-luna")
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                .put("max_completion_tokens", 6000)
                .toString()
            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
                .build()
            client.newCall(req).execute().use { res ->
                val js = res.body?.string() ?: return@use emptyList()
                if (!res.isSuccessful) return@use emptyList()
                val text = JSONObject(js).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                var clean = text.replace("```json", "").replace("```", "").trim()
                val s = clean.indexOf('['); val e = clean.lastIndexOf(']')
                if (s >= 0 && e > s) clean = clean.substring(s, e + 1)
                val arr = JSONArray(clean)
                val qs = mutableListOf<QuestionData>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val answer = o.optString("correctAnswer", "A").uppercase().take(1)
                    val safe = if (answer in listOf("A", "B", "C", "D")) answer else "A"
                    val strand = o.optString("strand", "General")
                    val subStrand = o.optString("subStrand", "General")
                    val topic = if (subStrand.isBlank() || subStrand == "General") strand else "$strand -> $subStrand"
                    qs.add(QuestionData(o.getInt("number"), topic, safe, subStrand))
                }
                qs
            }
        } catch (e: Exception) { emptyList() }
    }
}

private suspend fun extractTextFromImage(context: Context, uri: Uri): String {
    return withContext(Dispatchers.IO) {
        try {
            val isr = context.contentResolver.openInputStream(uri) ?: return@withContext ""
            val bmp = BitmapFactory.decodeStream(isr)
            isr.close()
            val scaled = Bitmap.createScaledBitmap(bmp, 800, 1200, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 50, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val content = JSONArray()
                .put(JSONObject().put("type", "text").put("text", "Transcribe ALL visible text word for word. If unreadable, write [unclear]."))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64")))
            val body = JSONObject()
                .put("model", "gpt-5.6-luna")
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                .put("max_completion_tokens", 4000)
                .toString()
            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
                .build()
            client.newCall(req).execute().use { res ->
                val js = res.body?.string() ?: return@use ""
                if (!res.isSuccessful) return@use "HTTP ${res.code}"
                try {
                    JSONObject(js).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                } catch (e: Exception) { "" }
            }
        } catch (e: Exception) { "" }
    }
}

private suspend fun imageToBase64(context: Context, uri: Uri): String {
    return withContext(Dispatchers.IO) {
        try {
            val isr = context.contentResolver.openInputStream(uri) ?: return@withContext ""
            val bmp = BitmapFactory.decodeStream(isr)
            isr.close()
            val scaled = Bitmap.createScaledBitmap(bmp, 400, 600, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 30, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { "" }
    }
}

// ===== MAIN COMPOSABLE =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveUnitsApp() {
    val auth = Firebase.auth
    val db = Firebase.firestore
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as Activity

    LaunchedEffect(Unit) {
        try {
            val rc = Firebase.remoteConfig
            rc.fetchAndActivate().await()
            val k = rc.getString("openai_api_key")
            if (k.isNotBlank()) OPENAI_API_KEY = k
        } catch (_: Exception) {}
    }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("705925410232-d010rgrfnp8o755007sakn8u1b4ndorp.apps.googleusercontent.com")
            .requestEmail()
            .build()
    }
    val googleSignInClient: GoogleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val prefs = context.getSharedPreferences("WaveUnitsPrefs", Context.MODE_PRIVATE)
    var email by remember { mutableStateOf(prefs.getString("email", "") ?: "") }
    var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }
    var isLoggedIn by remember { mutableStateOf(prefs.getBoolean("isLoggedIn", false)) }
    var showSignup by remember { mutableStateOf(false) }
    var currentView by remember { mutableStateOf("home") }
    var projects by remember { mutableStateOf<List<ExamProject>>(emptyList()) }
    var currentProject by remember { mutableStateOf<ExamProject?>(null) }
    var currentProjectId by remember { mutableStateOf("") }
    var currentProjectTitle by remember { mutableStateOf("") }

    // ===== PERSISTENT TREE STATE =====
    // treeGrades = ["Grade 5", "Grade 6"]
    // treeSubjects = {"Grade 5": ["Mathematics", "CRE"], "Grade 6": ["English"]}
    var treeGrades by remember { mutableStateOf<List<String>>(emptyList()) }
    var treeSubjects by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    var selectedGrade by remember { mutableStateOf<String?>(null) }
    var selectedSubject by remember { mutableStateOf<String?>(null) }

    // Dialog state
    var showAddGradeDialog by remember { mutableStateOf(false) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var showNewExamDialog by remember { mutableStateOf(false) }

    var scanPhase by remember { mutableStateOf("") }
    var progressText by remember { mutableStateOf("") }
    var extractedQuestions by remember { mutableStateOf<List<QuestionData>>(emptyList()) }
    var aiAnswerSheet by remember { mutableStateOf("") }
    var simplifiedAnswerKey by remember { mutableStateOf("") }
    var questionPaperText by remember { mutableStateOf("") }
    var allQuestionPaperTexts by remember { mutableStateOf<List<String>>(emptyList()) }
    var allQuestionPaperImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var manualAnswerSheetText by remember { mutableStateOf("") }
    var manualAnswerKey by remember { mutableStateOf("") }
    var answerKey by remember { mutableStateOf("") }
    var isGrading by remember { mutableStateOf(false) }

    var rosterNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var rosterInput by remember { mutableStateOf("") }
    var rosterTemplateName by remember { mutableStateOf("") }
    var rosterIndex by remember { mutableStateOf(0) }
    var isRosterMode by remember { mutableStateOf(false) }
    var savedRosters by remember { mutableStateOf<List<RosterTemplate>>(emptyList()) }

    var manualStudentName by remember { mutableStateOf("") }
    var printableReportText by remember { mutableStateOf("") }
    var answerSheetQuestionCount by remember { mutableStateOf("20") }
    var generatedAnswerSheetText by remember { mutableStateOf("") }
    var previousTopicPerformance by remember { mutableStateOf<List<ClassTopicPerformance>>(emptyList()) }
    var isExtracting by remember { mutableStateOf(false) }
    var collectedStudentAnswerSheets by remember { mutableStateOf<List<StudentAnswerSheetData>>(emptyList()) }
    var markedAnswerSheets by remember { mutableStateOf<List<MarkedAnswerSheetData>>(emptyList()) }
    var sectionState by remember { mutableStateOf(SectionViewState()) }
    var aiQuestion by remember { mutableStateOf("") }
    var aiAnswer by remember { mutableStateOf("") }
    var isAIThinking by remember { mutableStateOf(false) }
    var savedAIResponses by remember { mutableStateOf<List<AIQuestionAnswer>>(emptyList()) }
    var examAnalytics by remember { mutableStateOf<ExamAnalyticsBundle?>(null) }
    var seriesAnalytics by remember { mutableStateOf<SeriesAnalyticsBundle?>(null) }
    var selectedStudent by remember { mutableStateOf<StudentPerformance?>(null) }
    var allResults by remember { mutableStateOf<List<StudentResult>>(emptyList()) }

    fun saveLoginState(isLogged: Boolean) {
        prefs.edit().putBoolean("isLoggedIn", isLogged)
            .putString("email", email).putString("password", password).apply()
    }
    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken == null) {
                Toast.makeText(context, "Google sign-in failed: no ID token", Toast.LENGTH_LONG).show()
                return@rememberLauncherForActivityResult
            }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).addOnCompleteListener { t ->
                if (t.isSuccessful) {
                    isLoggedIn = true
                    saveLoginState(true)
                    Toast.makeText(context, "Signed in", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Sign-in failed: ${t.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: ApiException) {
            Toast.makeText(context, "Google sign-in error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ===== TREE LOAD / SAVE =====
    fun loadTree() {
        scope.launch {
            try {
                val uid = auth.currentUser?.uid ?: return@launch
                val doc = db.collection("teacherTree").document(uid).get().await()
                if (doc.exists()) {
                    val grades = (doc.get("grades") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val subjectsRaw = doc.get("subjectsByGrade") as? Map<String, Any> ?: emptyMap()
                    val subjectsMap = mutableMapOf<String, List<String>>()
                    for ((g, v) in subjectsRaw) {
                        val list = (v as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        subjectsMap[g] = list
                    }
                    treeGrades = grades
                    treeSubjects = subjectsMap
                } else {
                    treeGrades = emptyList()
                    treeSubjects = emptyMap()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Tree load error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveTree(newGrades: List<String>, newSubjects: Map<String, List<String>>) {
        scope.launch {
            try {
                val uid = auth.currentUser?.uid ?: return@launch
                val subjectsForFirestore = newSubjects.mapValues { it.value }
                db.collection("teacherTree").document(uid).set(mapOf(
                    "teacherId" to uid,
                    "grades" to newGrades,
                    "subjectsByGrade" to subjectsForFirestore,
                    "updatedAt" to System.currentTimeMillis()
                )).await()
                treeGrades = newGrades
                treeSubjects = newSubjects
            } catch (e: Exception) {
                Toast.makeText(context, "Tree save error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addGradeToTree(grade: String) {
        if (grade.isBlank() || treeGrades.contains(grade)) return
        val newGrades = (treeGrades + grade).sorted()
        val newSubjects = treeSubjects.toMutableMap()
        if (!newSubjects.containsKey(grade)) newSubjects[grade] = emptyList()
        saveTree(newGrades, newSubjects)
    }

    fun addSubjectToTree(grade: String, subject: String) {
        if (grade.isBlank() || subject.isBlank()) return
        val current = treeSubjects[grade] ?: emptyList()
        if (current.contains(subject)) return
        val newSubjects = treeSubjects.toMutableMap()
        newSubjects[grade] = (current + subject).sorted()
        val newGrades = if (treeGrades.contains(grade)) treeGrades else (treeGrades + grade).sorted()
        saveTree(newGrades, newSubjects)
    }

    fun deleteGradeFromTree(grade: String) {
        val newGrades = treeGrades.filter { it != grade }
        val newSubjects = treeSubjects.toMutableMap()
        newSubjects.remove(grade)
        saveTree(newGrades, newSubjects)
    }

    fun deleteSubjectFromTree(grade: String, subject: String) {
        val current = treeSubjects[grade] ?: emptyList()
        val newSubjects = treeSubjects.toMutableMap()
        newSubjects[grade] = current.filter { it != subject }
        saveTree(treeGrades, newSubjects)
    }

    fun loadProjects() {
        scope.launch {
            try {
                val uid = auth.currentUser?.uid ?: return@launch
                val result = db.collection("exams").whereEqualTo("teacherId", uid).get().await()
                projects = result.documents.map { doc ->
                    val createdAtMillis = parseCreatedAt(doc.get("createdAt"))
                    ExamProject(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        subjectKey = doc.getString("subjectKey") ?: "",
                        grade = doc.getString("grade") ?: "",
                        status = doc.getString("status") ?: "created",
                        createdAt = formatTimestamp(createdAtMillis),
                        createdAtMillis = createdAtMillis,
                        questionPaperText = doc.getString("questionPaperText") ?: "",
                        aiAnswerSheet = doc.getString("aiAnswerSheet") ?: "",
                        simplifiedAnswerKey = doc.getString("simplifiedAnswerKey") ?: "",
                        questionPaperImages = doc.get("questionPaperImages") as? List<String> ?: emptyList(),
                        allQuestionTexts = doc.get("allQuestionTexts") as? List<String> ?: emptyList(),
                        answerKey = doc.getString("answerKey") ?: ""
                    )
                }.sortedByDescending { it.createdAtMillis }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun loadExamData(examId: String) {
        scope.launch {
            try {
                val doc = db.collection("exams").document(examId).get().await()
                if (doc.exists()) {
                    questionPaperText = doc.getString("questionPaperText") ?: ""
                    allQuestionPaperTexts = doc.get("allQuestionTexts") as? List<String> ?: emptyList()
                    allQuestionPaperImages = doc.get("questionPaperImages") as? List<String> ?: emptyList()
                    aiAnswerSheet = doc.getString("aiAnswerSheet") ?: ""
                    simplifiedAnswerKey = doc.getString("simplifiedAnswerKey") ?: ""
                    answerKey = if (simplifiedAnswerKey.isNotBlank()) simplifiedAnswerKey
                                else if (aiAnswerSheet.isNotBlank()) parseAISheetToSimplified(aiAnswerSheet)
                                else ""
                    manualAnswerSheetText = doc.getString("manualAnswerSheet") ?: ""
                    manualAnswerKey = doc.getString("manualAnswerKey") ?: ""
                    if (answerKey.isBlank() && manualAnswerKey.isNotBlank()) answerKey = manualAnswerKey
                    if (answerKey.isNotBlank()) extractedQuestions = parseAnswerKeyToQuestions(answerKey)

                    val sheetsData = (doc.get("studentAnswerSheets") as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()
                    collectedStudentAnswerSheets = sheetsData.map { sheet ->
                        StudentAnswerSheetData(
                            studentName = sheet["studentName"] as? String ?: "",
                            extractedText = sheet["extractedText"] as? String ?: "",
                            image = sheet["image"] as? String ?: "",
                            answers = (sheet["answers"] as? Map<String, Any>)
                                ?.mapKeys { it.key.toIntOrNull() ?: 0 }
                                ?.mapValues { it.value as? String ?: "" } ?: emptyMap()
                        )
                    }

                    val markedSheetsResult = db.collection("exams").document(examId)
                        .collection("markedSheets")
                        .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .get().await()
                    markedAnswerSheets = markedSheetsResult.documents.map { d ->
                        MarkedAnswerSheetData(
                            studentName = d.getString("studentName") ?: "",
                            markedText = d.getString("markedText") ?: "",
                            image = d.getString("image") ?: "",
                            score = (d.get("score") as? Number)?.toInt() ?: 0,
                            total = (d.get("total") as? Number)?.toInt() ?: 0,
                            percentage = (d.get("percentage") as? Number)?.toDouble() ?: 0.0
                        )
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun loadAIResponses(examId: String) {
        scope.launch {
            try {
                val result = db.collection("exams").document(examId).collection("aiResponses")
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get().await()
                savedAIResponses = result.documents.map { doc ->
                    AIQuestionAnswer(
                        id = doc.id,
                        question = doc.getString("question") ?: "",
                        answer = doc.getString("answer") ?: "",
                        createdAt = formatTimestamp(doc.getLong("createdAt") ?: 0L)
                    )
                }
            } catch (e: Exception) { savedAIResponses = emptyList() }
        }
    }

    fun loadSavedRosters() {
        scope.launch {
            try {
                val uid = auth.currentUser?.uid ?: return@launch
                val result = db.collection("rosterTemplates").whereEqualTo("teacherId", uid).get().await()
                savedRosters = result.documents.map { doc ->
                    RosterTemplate(doc.id, doc.getString("name") ?: "", (doc.get("names") as? List<String>) ?: emptyList())
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveRosterTemplate(templateName: String, names: List<String>) {
        if (templateName.isBlank() || names.isEmpty()) {
            Toast.makeText(context, "Enter name and names", Toast.LENGTH_SHORT).show(); return
        }
        scope.launch {
            try {
                db.collection("rosterTemplates").add(mapOf(
                    "teacherId" to auth.currentUser?.uid,
                    "name" to templateName, "names" to names,
                    "createdAt" to System.currentTimeMillis()
                ))
                Toast.makeText(context, "Saved '$templateName'", Toast.LENGTH_SHORT).show()
                loadSavedRosters()
            } catch (e: Exception) {
                Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteRosterTemplate(templateId: String) {
        scope.launch {
            try {
                db.collection("rosterTemplates").document(templateId).delete()
                Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                loadSavedRosters()
            } catch (e: Exception) {
                Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun loadExamAnalytics(examId: String) {
        scope.launch {
            try {
                val resultsQuery = db.collection("results").whereEqualTo("examId", examId).get().await()
                val results = resultsQuery.documents.map { doc ->
                    val questionsData = doc.get("questions") as? List<Map<String, Any>> ?: emptyList()
                    StudentResult(
                        studentName = doc.getString("studentName") ?: "Unknown",
                        score = (doc.get("score") as? Number)?.toInt() ?: 0,
                        totalMarks = (doc.get("totalMarks") as? Number)?.toInt() ?: 0,
                        percentage = (doc.get("percentage") as? Number)?.toDouble() ?: 0.0,
                        questions = questionsData.map { q ->
                            QuestionResultData(
                                (q["questionNumber"] as? Number)?.toInt() ?: 0,
                                q["topic"] as? String ?: "General",
                                q["studentAnswer"] as? String ?: "",
                                q["correctAnswer"] as? String ?: "",
                                q["isCorrect"] as? Boolean ?: false
                            )
                        }
                    )
                }
                allResults = results
                val studentMap = mutableMapOf<String, MutableList<Double>>()
                val weak = mutableMapOf<String, MutableList<String>>()
                val strong = mutableMapOf<String, MutableList<String>>()
                for (r in results) {
                    studentMap.getOrPut(r.studentName) { mutableListOf() }.add(r.percentage)
                    weak.getOrPut(r.studentName) { mutableListOf() }.addAll(r.questions.filter { !it.isCorrect }.map { it.topic })
                    strong.getOrPut(r.studentName) { mutableListOf() }.addAll(r.questions.filter { it.isCorrect }.map { it.topic })
                }
                val sorted = studentMap.map { (name, scores) ->
                    StudentPerformance(
                        name, if (scores.isNotEmpty()) scores.average() else 0.0, scores.size,
                        weak[name]?.distinct()?.take(3) ?: emptyList(),
                        strong[name]?.distinct()?.take(3) ?: emptyList(),
                        scores.sum(), if (scores.isNotEmpty()) getKenyanGrade(scores.average()) else "E", 0
                    )
                }.sortedByDescending { it.averageScore }
                val ranked = sorted.mapIndexed { i, s -> s.copy(rank = i + 1) }

                val dist = ScoreDistribution(
                    results.count { it.percentage >= 75.0 },
                    results.count { it.percentage in 70.0..74.99 },
                    results.count { it.percentage in 65.0..69.99 },
                    results.count { it.percentage in 60.0..64.99 },
                    results.count { it.percentage in 55.0..59.99 },
                    results.count { it.percentage in 45.0..54.99 },
                    results.count { it.percentage in 40.0..44.99 },
                    results.count { it.percentage in 35.0..39.99 },
                    results.count { it.percentage in 30.0..34.99 },
                    results.count { it.percentage in 25.0..29.99 },
                    results.count { it.percentage in 20.0..24.99 },
                    results.count { it.percentage < 20.0 }
                )

                val qMap = mutableMapOf<Int, Pair<String, MutableList<Boolean>>>()
                for (r in results) for (q in r.questions) {
                    if (q.questionNumber <= 0) continue
                    val e = qMap.getOrPut(q.questionNumber) { Pair(q.topic, mutableListOf()) }
                    e.second.add(q.isCorrect)
                }
                val qBreakdown = qMap.map { (num, pair) ->
                    val correctCount = pair.second.count { it }
                    val totalCount = pair.second.size
                    val wrongCount = totalCount - correctCount
                    val accuracy = if (totalCount > 0) correctCount.toDouble() / totalCount else 0.0
                    val difficulty = when {
                        accuracy >= 0.8 -> "Easy"
                        accuracy >= 0.5 -> "Moderate"
                        else -> "Hard"
                    }
                    QuestionBreakdown(num, pair.first, correctCount, totalCount, "$difficulty | $correctCount/$totalCount correct | $wrongCount failed")
                }.sortedBy { it.questionNumber }

                val topicMap = mutableMapOf<String, MutableList<Boolean>>()
                for (r in results) for (q in r.questions) {
                    if (q.questionNumber <= 0) continue
                    topicMap.getOrPut(q.topic) { mutableListOf() }.add(q.isCorrect)
                }
                val classTopicPerformance = topicMap.map { (topic, list) ->
                    val c = list.count { it }; val t = list.size
                    ClassTopicPerformance(topic, if (t > 0) (c.toDouble() / t) * 100 else 0.0, t, c)
                }.sortedByDescending { it.averageScore }

                val portfolios = studentMap.map { (name, scores) ->
                    StudentPortfolio(name, if (scores.isNotEmpty()) scores.average() else 0.0, scores.size,
                        weak[name]?.distinct()?.take(3) ?: emptyList(),
                        strong[name]?.distinct()?.take(3) ?: emptyList())
                }

                examAnalytics = ExamAnalyticsBundle(
                    ranked, dist, qBreakdown, portfolios, classTopicPerformance,
                    generateActionableInsights(classTopicPerformance, ranked),
                    generateTrends(classTopicPerformance, previousTopicPerformance),
                    generateStudentComparisons(ranked)
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Analytics error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun loadSeriesAnalytics(grade: String, subjectKey: String) {
        scope.launch {
            try {
                val seriesExams = projects
                    .filter { it.grade == grade && it.subjectKey == subjectKey }
                    .sortedByDescending { it.createdAtMillis }

                if (seriesExams.isEmpty()) {
                    seriesAnalytics = SeriesAnalyticsBundle(grade, subjectKey, emptyList(), 0.0, 0.0, emptyList(), emptyList(), emptyList(), 0)
                    return@launch
                }

                val examPoints = mutableListOf<ExamPoint>()
                val perExamResults = mutableMapOf<String, List<StudentResult>>()

                for (exam in seriesExams) {
                    val resultsQuery = db.collection("results").whereEqualTo("examId", exam.id).get().await()
                    val results = resultsQuery.documents.map { doc ->
                        val questionsData = doc.get("questions") as? List<Map<String, Any>> ?: emptyList()
                        StudentResult(
                            studentName = doc.getString("studentName") ?: "Unknown",
                            score = (doc.get("score") as? Number)?.toInt() ?: 0,
                            totalMarks = (doc.get("totalMarks") as? Number)?.toInt() ?: 0,
                            percentage = (doc.get("percentage") as? Number)?.toDouble() ?: 0.0,
                            questions = questionsData.map { q ->
                                QuestionResultData(
                                    (q["questionNumber"] as? Number)?.toInt() ?: 0,
                                    q["topic"] as? String ?: "General",
                                    q["studentAnswer"] as? String ?: "",
                                    q["correctAnswer"] as? String ?: "",
                                    q["isCorrect"] as? Boolean ?: false
                                )
                            }
                        )
                    }
                    perExamResults[exam.id] = results
                    val avg = if (results.isNotEmpty()) results.map { it.percentage }.average() else 0.0
                    examPoints.add(ExamPoint(exam.id, exam.title, exam.createdAtMillis, avg, results.size))
                }

                val classAverage = if (examPoints.isNotEmpty()) examPoints.map { it.classAverage }.average() else 0.0
                val classTrend = if (examPoints.size >= 2) examPoints.first().classAverage - examPoints.last().classAverage else 0.0

                val topicSeriesMap = mutableMapOf<String, MutableList<Pair<Long, Double>>>()
                for (exam in seriesExams) {
                    val results = perExamResults[exam.id] ?: continue
                    val topicMap = mutableMapOf<String, MutableList<Boolean>>()
                    for (r in results) for (q in r.questions) {
                        if (q.questionNumber <= 0) continue
                        topicMap.getOrPut(q.topic) { mutableListOf() }.add(q.isCorrect)
                    }
                    for ((topic, list) in topicMap) {
                        val pct = if (list.isNotEmpty()) list.count { it } * 100.0 / list.size else 0.0
                        topicSeriesMap.getOrPut(topic) { mutableListOf() }.add(Pair(exam.createdAtMillis, pct))
                    }
                }
                val topicSeries = topicSeriesMap.map { (topic, points) ->
                    val sortedPoints = points.sortedBy { it.first }
                    val weakEveryTime = sortedPoints.isNotEmpty() && sortedPoints.all { it.second < 50.0 }
                    TopicSeriesPoint(topic, sortedPoints, weakEveryTime)
                }.sortedBy { it.topic }

                val studentSeriesMap = mutableMapOf<String, MutableList<Pair<Long, Double>>>()
                for (exam in seriesExams) {
                    val results = perExamResults[exam.id] ?: continue
                    for (r in results) {
                        if (r.studentName.isBlank() || r.studentName == "Unknown") continue
                        studentSeriesMap.getOrPut(r.studentName) { mutableListOf() }.add(Pair(exam.createdAtMillis, r.percentage))
                    }
                }
                val studentSeries = studentSeriesMap.map { (name, points) ->
                    val sortedPoints = points.sortedBy { it.first }
                    val change = if (sortedPoints.size >= 2) sortedPoints.last().second - sortedPoints.first().second else 0.0
                    val trend = when {
                        sortedPoints.size < 2 -> "insufficient data"
                        change > 5.0 -> "improving"
                        change < -5.0 -> "declining"
                        else -> "steady"
                    }
                    StudentSeriesPoint(name, sortedPoints, change, trend)
                }.sortedByDescending { it.change }

                val clusters = mutableListOf<ClusterPoint>()
                val examCountInSeries = seriesExams.size
                if (examCountInSeries >= 2) {
                    val topicStudentFailures = mutableMapOf<String, MutableMap<String, Int>>()
                    for (exam in seriesExams) {
                        val results = perExamResults[exam.id] ?: continue
                        for (r in results) {
                            for (q in r.questions) {
                                if (q.questionNumber <= 0) continue
                                if (!q.isCorrect) {
                                    val m = topicStudentFailures.getOrPut(q.topic) { mutableMapOf() }
                                    m[r.studentName] = (m[r.studentName] ?: 0) + 1
                                }
                            }
                        }
                    }
                    for ((topic, studentCounts) in topicStudentFailures) {
                        val consistent = studentCounts.filter { it.value >= examCountInSeries }.keys.toList().sorted()
                        if (consistent.size >= 2) {
                            clusters.add(ClusterPoint(topic, consistent, examCountInSeries))
                        }
                    }
                }

                seriesAnalytics = SeriesAnalyticsBundle(
                    grade, subjectKey, examPoints, classAverage, classTrend,
                    topicSeries, studentSeries, clusters, examPoints.size
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Series error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun gradeCollectedSheets() {
        scope.launch {
            try {
                isGrading = true
                progressText = "Grading ${collectedStudentAnswerSheets.size} sheets..."

                var aiSheetForGrading = aiAnswerSheet
                if (aiSheetForGrading.isBlank()) {
                    val doc = db.collection("exams").document(currentProjectId).get().await()
                    aiSheetForGrading = doc.getString("aiAnswerSheet") ?: ""
                }
                if (aiSheetForGrading.isBlank()) {
                    isGrading = false
                    Toast.makeText(context, "Cannot grade: no AI answer sheet saved. Scan the question paper first.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                if (collectedStudentAnswerSheets.isEmpty()) {
                    isGrading = false
                    Toast.makeText(context, "Cannot grade: no collected answer sheets yet.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val gradable = collectedStudentAnswerSheets.filter {
                    it.studentName.isNotBlank() && it.studentName != "Unknown" &&
                    it.extractedText.isNotBlank() && !it.extractedText.startsWith("ERR")
                }
                if (gradable.isEmpty()) {
                    isGrading = false
                    Toast.makeText(context, "Cannot grade: all collected sheets have empty printouts. Rescan them.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                val p = projects.find { it.id == currentProjectId }
                val grade = p?.grade ?: ""
                val subjectKey = p?.subjectKey ?: ""

                val results = mutableListOf<StudentResult>()
                val marked = mutableListOf<MarkedAnswerSheetData>()

                for (sheet in gradable) {
                    progressText = "Grading ${sheet.studentName}..."
                    val result = try {
                        gradeWithAI(sheet.extractedText, aiSheetForGrading, sheet.studentName)
                    } catch (e: Exception) {
                        Toast.makeText(context, "GRADE ERROR for ${sheet.studentName}: ${e.message?.take(250) ?: "unknown"}", Toast.LENGTH_LONG).show()
                        null
                    }
                    if (result == null || result.questions.isEmpty()) {
                        Toast.makeText(context, "AI could not grade ${sheet.studentName} (empty result)", Toast.LENGTH_LONG).show()
                        continue
                    }
                    results.add(result)
                    val aiText = result.questions.firstOrNull { it.questionNumber == 0 }?.studentAnswer ?: ""
                    val markedText = "STUDENT: ${sheet.studentName}\n--------------------------------\n$aiText\n--------------------------------\n"
                    marked.add(MarkedAnswerSheetData(sheet.studentName, markedText, sheet.image, result.score, result.totalMarks, result.percentage))

                    db.collection("exams").document(currentProjectId).collection("markedSheets").add(mapOf(
                        "studentName" to sheet.studentName, "markedText" to markedText,
                        "image" to sheet.image, "score" to result.score, "total" to result.totalMarks,
                        "percentage" to result.percentage, "createdAt" to System.currentTimeMillis()
                    ))
                    db.collection("results").add(mapOf(
                        "teacherId" to auth.currentUser?.uid,
                        "studentName" to sheet.studentName,
                        "examId" to currentProjectId, "examTitle" to currentProjectTitle,
                        "grade" to grade, "subjectKey" to subjectKey,
                        "score" to result.score, "totalMarks" to result.totalMarks, "percentage" to result.percentage,
                        "gradedBy" to "ai-two-part",
                        "rawText" to sheet.extractedText,
                        "questions" to result.questions.filter { it.questionNumber > 0 }.map { q -> mapOf(
                            "questionNumber" to q.questionNumber, "topic" to q.topic,
                            "studentAnswer" to q.studentAnswer, "correctAnswer" to q.correctAnswer,
                            "isCorrect" to q.isCorrect) },
                        "createdAt" to System.currentTimeMillis()
                    ))
                }

                if (results.isEmpty()) {
                    Toast.makeText(context, "No sheets were graded.", Toast.LENGTH_LONG).show()
                    isGrading = false
                    return@launch
                }
                db.collection("exams").document(currentProjectId).update("status", "graded")
                progressText = "Graded ${results.size} papers!"
                isGrading = false
                markedAnswerSheets = marked
                loadExamAnalytics(currentProjectId)
                Toast.makeText(context, "Graded ${results.size}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "${e.message}", Toast.LENGTH_LONG).show()
                isGrading = false
            }
        }
    }

    fun askAIQuestion(question: String) {
        if (question.isBlank()) {
            Toast.makeText(context, "Enter a question", Toast.LENGTH_SHORT).show(); return
        }
        scope.launch {
            try {
                isAIThinking = true; aiAnswer = ""
                loadExamData(currentProjectId)
                val sb = StringBuilder()
                if (questionPaperText.isNotBlank()) sb.append("=== QUESTION PAPER ===\n$questionPaperText\n\n")
                if (aiAnswerSheet.isNotBlank()) sb.append("=== AI ANSWER SHEET ===\n$aiAnswerSheet\n\n")
                if (manualAnswerSheetText.isNotBlank()) sb.append("=== MANUAL ANSWER SHEET ===\n$manualAnswerSheetText\n\n")
                if (collectedStudentAnswerSheets.isNotEmpty()) {
                    sb.append("=== COLLECTED SHEETS ===\n")
                    collectedStudentAnswerSheets.forEachIndexed { i, s ->
                        sb.append("--- Student ${i + 1}: ${s.studentName} ---\n${s.extractedText}\n\n")
                    }
                }
                aiAnswer = askAIWithContext(sb.toString(), question)
                isAIThinking = false
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                isAIThinking = false
            }
        }
    }

    fun saveAIResponse(question: String, answer: String) {
        if (question.isBlank() || answer.isBlank()) return
        scope.launch {
            try {
                db.collection("exams").document(currentProjectId).collection("aiResponses").add(mapOf(
                    "question" to question, "answer" to answer,
                    "createdAt" to System.currentTimeMillis()
                ))
                Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                loadAIResponses(currentProjectId)
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteAIResponse(id: String) {
        scope.launch {
            try {
                db.collection("exams").document(currentProjectId).collection("aiResponses").document(id).delete()
                loadAIResponses(currentProjectId)
            } catch (e: Exception) {}
        }
    }

    fun saveReportToDownloads(text: String) {
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "WaveUnits_Report_${System.currentTimeMillis()}.txt")
            FileWriter(file).use { it.write(text) }
            Toast.makeText(context, "Saved: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareReport(text: String) {
        try {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "WaveUnits Report")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(i, "Share"))
        } catch (e: Exception) { Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show() }
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uris = scanResult?.pages?.map { it.imageUri } ?: emptyList()
            if (uris.isNotEmpty()) {
                scope.launch {
                    when (scanPhase) {
                        "question_paper" -> {
                            isExtracting = true
                            progressText = "Scanning question paper..."
                            val allTexts = mutableListOf<String>()
                            val allImages = mutableListOf<String>()
                            val allQs = mutableListOf<QuestionData>()
                            for (uri in uris) {
                                val raw = extractTextFromImage(context, uri)
                                val img = imageToBase64(context, uri)
                                allTexts.add(raw); allImages.add(img)
                                allQs.addAll(parseQuestionsWithTopics(raw))
                            }
                            val newTexts = allQuestionPaperTexts + allTexts
                            val newImages = allQuestionPaperImages + allImages
                            val newQs = extractedQuestions + allQs
                            extractedQuestions = newQs
                            questionPaperText = newTexts.joinToString("\n\n--- PAGE BREAK ---\n\n")
                            allQuestionPaperTexts = newTexts
                            allQuestionPaperImages = newImages
                            val newAI = generateAIAnswerSheetWithTopics(allQs)
                            aiAnswerSheet = if (aiAnswerSheet.isBlank()) newAI else "$aiAnswerSheet\n\n$newAI"
                            val newSimp = parseAISheetToSimplified(newAI)
                            simplifiedAnswerKey = if (simplifiedAnswerKey.isBlank()) newSimp else "$simplifiedAnswerKey,$newSimp"
                            answerKey = simplifiedAnswerKey
                            db.collection("exams").document(currentProjectId).update(mapOf(
                                "questionPaperImages" to newImages,
                                "allQuestionTexts" to newTexts,
                                "questionPaperText" to questionPaperText,
                                "aiAnswerSheet" to aiAnswerSheet,
                                "simplifiedAnswerKey" to simplifiedAnswerKey,
                                "answerKey" to simplifiedAnswerKey,
                                "extractedTextSaved" to true,
                                "answerSheetGenerated" to true,
                                "markingMode" to "ai"))
                            progressText = "Extracted ${allQs.size}. Total: ${newQs.size}."
                            isExtracting = false
                            loadProjects(); loadExamData(currentProjectId); scanPhase = ""
                        }
                        "manual_answer_sheet" -> {
                            progressText = "Scanning manual answer sheet..."
                            val raw = extractTextFromImage(context, uris.first())
                            manualAnswerSheetText = if (manualAnswerSheetText.isBlank()) raw else "$manualAnswerSheetText\n\n$raw"
                            val parsed = parseManualAnswerKey(raw)
                            val newKey = parsed.joinToString(",") { "${it.first}:${it.second}" }
                            manualAnswerKey = if (manualAnswerKey.isBlank()) newKey else "$manualAnswerKey,$newKey"
                            answerKey = manualAnswerKey
                            db.collection("exams").document(currentProjectId).update(mapOf(
                                "manualAnswerSheet" to manualAnswerSheetText,
                                "manualAnswerKey" to manualAnswerKey,
                                "answerKey" to manualAnswerKey, "markingMode" to "manual"))
                            progressText = "Manual sheet extracted!"
                            loadProjects(); loadExamData(currentProjectId); scanPhase = ""
                        }
                        "answer_sheets" -> {
                            isExtracting = true
                            val newSheets = mutableListOf<StudentAnswerSheetData>()
                            for ((idx, uri) in uris.withIndex()) {
                                progressText = "Transcribing page ${idx + 1} of ${uris.size}..."
                                val printout = transcribeAnswerSheet(context, uri)
                                val imgB64 = imageToBase64(context, uri)
                                if (printout.isBlank() || printout.startsWith("ERR")) {
                                    Toast.makeText(context, "Transcribe failed: ${printout.take(200)}", Toast.LENGTH_LONG).show()
                                }
                                val studentName = when {
                                    isRosterMode && rosterNames.isNotEmpty() -> {
                                        val n = rosterNames.getOrNull(rosterIndex) ?: "Unknown"
                                        rosterIndex++; n
                                    }
                                    manualStudentName.isNotBlank() -> manualStudentName
                                    else -> "Unknown"
                                }
                                newSheets.add(StudentAnswerSheetData(studentName, printout, imgB64))
                            }
                            val newCollected = collectedStudentAnswerSheets + newSheets
                            val sheetsData = newCollected.map { s ->
                                mapOf("studentName" to s.studentName, "extractedText" to s.extractedText, "image" to s.image)
                            }
                            db.collection("exams").document(currentProjectId).update(mapOf(
                                "studentAnswerSheets" to sheetsData, "status" to "collected"))
                            collectedStudentAnswerSheets = newCollected
                            manualStudentName = ""
                            isExtracting = false
                            progressText = "Transcribed ${newSheets.size}. Total: ${newCollected.size}."
                            loadProjects(); loadExamData(currentProjectId); scanPhase = ""
                        }
                    }
                }
            }
        }
    }

    fun launchScan(phase: String) {
        scanPhase = phase
        val scanner = GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setGalleryImportAllowed(false)
                .setPageLimit(30)
                .build()
        )
        scanner.getStartScanIntent(activity).addOnSuccessListener { intent ->
            scannerLauncher.launch(IntentSenderRequest.Builder(intent).build())
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) { loadTree(); loadProjects(); loadSavedRosters() }
    }

    // ===== DIALOGS =====
    if (showAddGradeDialog) {
        var selected by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddGradeDialog = false },
            title = { Text("Add a Grade") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Pick a grade:", color = Color(0xFF94a3b8), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    CBC_GRADES.forEach { g ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                .clickable { selected = g },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected == g) Color(0xFF2563eb) else Color(0xFF1e293b)
                            )
                        ) {
                            Text(g, color = Color.White, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selected.isNotBlank()) {
                            selectedGrade = selected
                            addGradeToTree(selected)
                            showAddGradeDialog = false
                            showAddSubjectDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                ) { Text("Next: Pick Subject") }
            },
            dismissButton = { TextButton(onClick = { showAddGradeDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddSubjectDialog) {
        var selected by remember { mutableStateOf("") }
        val gradeForDialog = selectedGrade ?: ""
        AlertDialog(
            onDismissRequest = { showAddSubjectDialog = false },
            title = { Text("Add a Subject") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("$gradeForDialog", color = Color(0xFF60a5fa), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    CBC_SUBJECTS.forEach { s ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                .clickable { selected = s },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected == s) Color(0xFF2563eb) else Color(0xFF1e293b)
                            )
                        ) {
                            Text(s, color = Color.White, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selected.isNotBlank()) {
                            selectedSubject = selected
                            addSubjectToTree(gradeForDialog, selected)
                            showAddSubjectDialog = false
                            showNewExamDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                ) { Text("Next: Create Exam") }
            },
            dismissButton = { TextButton(onClick = { showAddSubjectDialog = false }) { Text("Cancel") } }
        )
    }

    if (showNewExamDialog) {
        var title by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewExamDialog = false },
            title = { Text("Create First Exam") },
            text = {
                Column {
                    Text("${selectedGrade ?: ""}  •  ${selectedSubject ?: ""}",
                        color = Color(0xFF60a5fa), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Exam Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() && selectedGrade != null && selectedSubject != null) {
                            val grade = selectedGrade!!
                            val subject = selectedSubject!!
                            scope.launch {
                                try {
                                    val doc = db.collection("exams").add(mapOf(
                                        "teacherId" to auth.currentUser?.uid,
                                        "title" to title,
                                        "grade" to grade,
                                        "subjectKey" to subject,
                                        "status" to "created",
                                        "createdAt" to System.currentTimeMillis()
                                    )).await()
                                    currentProjectId = doc.id
                                    currentProjectTitle = title
                                    loadProjects()
                                    showNewExamDialog = false
                                    currentView = "subject"
                                    Toast.makeText(context, "Exam created", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNewExamDialog = false }) { Text("Cancel") } }
        )
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF2563eb),
            secondary = Color(0xFF10b981),
            surface = Color(0xFF1e293b),
            background = Color(0xFF0f172a)
        )
    ) {
        if (!isLoggedIn) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0f172a)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("WaveUnits", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = {
                            val signInIntent = googleSignInClient.signInIntent
                            googleLauncher.launch(signInIntent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDB4437))
                    ) { Text("Sign in with Google") }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("— or —", color = Color(0xFF94a3b8), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = email, onValueChange = { email = it },
                        label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = password, onValueChange = { password = it },
                        label = { Text("Password") }, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation())
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    auth.signInWithEmailAndPassword(email, password).await()
                                    isLoggedIn = true; saveLoginState(true)
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563eb))
                    ) { Text("Login") }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { showSignup = !showSignup }) {
                        Text(if (showSignup) "Back" else "Create Account", color = Color(0xFF60a5fa))
                    }
                    if (showSignup) {
                        var name by remember { mutableStateOf("") }
                        OutlinedTextField(value = name, onValueChange = { name = it },
                            label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        auth.createUserWithEmailAndPassword(email, password).await()
                                        isLoggedIn = true; saveLoginState(true)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                        ) { Text("Create") }
                    }
                }
            }
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("WaveUnits", fontWeight = FontWeight.Bold) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1e293b)),
                        actions = {
                            IconButton(onClick = {
                                scope.launch {
                                    try { googleSignInClient.signOut().await() } catch (_: Exception) {}
                                    auth.signOut(); isLoggedIn = false; saveLoginState(false)
                                }
                            }) { Text("Exit", fontSize = 14.sp) }
                        }
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    when (currentView) {
                        "home" -> {
                            // ===== HOME: list of grades =====
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("My Classes", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Button(
                                    onClick = { showAddGradeDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                ) { Text("+ Add Grade") }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            if (treeGrades.isEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("??", fontSize = 48.sp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("Welcome to WaveUnits", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Add the grades and subjects you teach.",
                                        color = Color(0xFF94a3b8), fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(32.dp))
                                    Button(
                                        onClick = { showAddGradeDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981)),
                                        modifier = Modifier.fillMaxWidth(0.7f)
                                    ) { Text("+ Add Subjects I Teach") }
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(treeGrades) { grade ->
                                        val subjects = treeSubjects[grade] ?: emptyList()
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("?? $grade", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa), fontSize = 17.sp)
                                                    Row {
                                                        Button(
                                                            onClick = {
                                                                selectedGrade = grade
                                                                showAddSubjectDialog = true
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                                        ) { Text("+ Subject") }
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Button(
                                                            onClick = { deleteGradeFromTree(grade) },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFef4444))
                                                        ) { Text("???") }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(10.dp))
                                                if (subjects.isEmpty()) {
                                                    Text("No subjects yet. Tap + Subject.",
                                                        color = Color(0xFF64748b), fontSize = 12.sp)
                                                } else {
                                                    subjects.forEach { subject ->
                                                        val count = projects.count { it.grade == grade && it.subjectKey == subject }
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column(
                                                                    modifier = Modifier.weight(1f).clickable {
                                                                        selectedGrade = grade
                                                                        selectedSubject = subject
                                                                        currentView = "subject"
                                                                    }
                                                                ) {
                                                                    Text("?? $subject", color = Color.White, fontSize = 14.sp)
                                                                    Text("$count exams", color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                                }
                                                                Button(
                                                                    onClick = { deleteSubjectFromTree(grade, subject) },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7f1d1d))
                                                                ) { Text("???", fontSize = 12.sp) }
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
                        "subject" -> {
                            val grade = selectedGrade ?: ""
                            val subject = selectedSubject ?: ""

                            // Header row with Back
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { currentView = "home" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                ) { Text("? Back") }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(grade, color = Color(0xFF94a3b8), fontSize = 11.sp)
                                    Text(subject, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            // Action buttons
                            Button(
                                onClick = { showNewExamDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                            ) { Text("+ New Exam") }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    loadSeriesAnalytics(grade, subject)
                                    currentView = "seriesAnalytics"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa))
                            ) { Text("?? Class Path Analytics") }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    selectedGrade = grade
                                    showAddSubjectDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf59e0b))
                            ) { Text("+ Add Another Subject") }
                            Spacer(modifier = Modifier.height(16.dp))

                            val exams = projects.filter { it.grade == grade && it.subjectKey == subject }
                                .sortedByDescending { it.createdAtMillis }

                            if (exams.isEmpty()) {
                                Text("No exams in this class path yet. Tap + New Exam.",
                                    color = Color(0xFF94a3b8), fontSize = 13.sp)
                            } else {
                                LazyColumn {
                                    items(exams) { exam ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                .clickable {
                                                    currentProject = exam
                                                    currentProjectId = exam.id
                                                    currentProjectTitle = exam.title
                                                    currentView = "projectDetail"
                                                    loadExamData(exam.id)
                                                    loadAIResponses(exam.id)
                                                },
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text(exam.title, fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                Text("Created: ${exam.createdAt}", color = Color(0xFF64748b), fontSize = 12.sp)
                                                Text("Status: ${exam.status}", color = Color(0xFF64748b), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "seriesAnalytics" -> {
                            val s = seriesAnalytics
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { currentView = "subject" },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                        ) { Text("? Back") }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("Class Path Analytics", color = Color(0xFF94a3b8), fontSize = 11.sp)
                                            Text("${s?.grade ?: ""} • ${s?.subjectKey ?: ""}",
                                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                if (s == null || s.examCount == 0) {
                                    item { Text("No exams in this class path yet.", color = Color(0xFF94a3b8)) }
                                } else {
                                    item {
                                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("Class Average", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                Text("${"%.1f".format(s.classAverage)}%", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                Text(
                                                    when {
                                                        s.examCount < 2 -> "Need 2+ exams to show trend"
                                                        s.classTrend > 3 -> "Improving (+${"%.1f".format(s.classTrend)})"
                                                        s.classTrend < -3 -> "Declining (${"%.1f".format(s.classTrend)})"
                                                        else -> "Steady"
                                                    },
                                                    color = when {
                                                        s.classTrend > 3 -> Color(0xFF10b981)
                                                        s.classTrend < -3 -> Color(0xFFef4444)
                                                        else -> Color(0xFF94a3b8)
                                                    }, fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                    item {
                                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("Timeline", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                Spacer(modifier = Modifier.height(8.dp))
                                                s.examPoints.forEach { ep ->
                                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                                        .clickable {
                                                            currentProjectId = ep.examId
                                                            currentProjectTitle = ep.examTitle
                                                            currentView = "projectDetail"
                                                            loadExamData(ep.examId)
                                                            loadAIResponses(ep.examId)
                                                        },
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))) {
                                                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(ep.examTitle, color = Color.White, fontSize = 14.sp)
                                                                Text(formatTimestamp(ep.timestamp), color = Color(0xFF64748b), fontSize = 11.sp)
                                                            }
                                                            Text("${"%.1f".format(ep.classAverage)}%",
                                                                color = if (ep.classAverage >= 50) Color(0xFF10b981) else Color(0xFFef4444),
                                                                fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    val weakTopics = s.topicSeries.filter { it.isPersistentlyWeak }
                                    if (weakTopics.isNotEmpty()) {
                                        item {
                                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text("?? Persistent Weaknesses", fontWeight = FontWeight.Bold, color = Color(0xFFef4444))
                                                    Text("Below 50% in every exam. Re-teach these.",
                                                        color = Color(0xFF94a3b8), fontSize = 12.sp)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    weakTopics.forEach { t ->
                                                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))) {
                                                            Column(modifier = Modifier.padding(12.dp)) {
                                                                Text(t.topic, color = Color.White, fontWeight = FontWeight.Medium)
                                                                t.scores.sortedByDescending { it.first }.forEach { p ->
                                                                    Text("${formatTimestamp(p.first)}  ${"%.1f".format(p.second)}%",
                                                                        color = Color(0xFFef4444), fontSize = 12.sp)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    item {
                                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("Student Progress", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                Spacer(modifier = Modifier.height(8.dp))
                                                s.studentSeries.forEach { sp ->
                                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))) {
                                                        Column(modifier = Modifier.padding(12.dp)) {
                                                            Row(modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween) {
                                                                Text(sp.studentName, color = Color.White, fontWeight = FontWeight.Medium)
                                                                Text(
                                                                    when {
                                                                        sp.trend == "improving" -> "? +${"%.1f".format(sp.change)}"
                                                                        sp.trend == "declining" -> "? ${"%.1f".format(sp.change)}"
                                                                        sp.trend == "steady" -> "— steady"
                                                                        else -> "—"
                                                                    },
                                                                    color = when (sp.trend) {
                                                                        "improving" -> Color(0xFF10b981)
                                                                        "declining" -> Color(0xFFef4444)
                                                                        else -> Color(0xFF94a3b8)
                                                                    },
                                                                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                            sp.scores.sortedByDescending { it.first }.forEach { p ->
                                                                Text("${formatTimestamp(p.first)}  ${"%.1f".format(p.second)}%",
                                                                    color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (s.clusters.isNotEmpty()) {
                                        item {
                                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text("?? Small-Group Focus", fontWeight = FontWeight.Bold, color = Color(0xFFf59e0b))
                                                    Text("These students fail the same topic in every exam.",
                                                        color = Color(0xFF94a3b8), fontSize = 12.sp)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    s.clusters.forEach { c ->
                                                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))) {
                                                            Column(modifier = Modifier.padding(12.dp)) {
                                                                Text(c.topic, color = Color.White, fontWeight = FontWeight.Medium)
                                                                Text("Failed in all ${c.examCount} exams:",
                                                                    color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                                c.students.forEach { n -> Text("  - $n", color = Color.White, fontSize = 12.sp) }
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
                        "projectDetail" -> {
                            LaunchedEffect(currentProjectId) {
                                if (currentProjectId.isNotBlank()) {
                                    loadExamData(currentProjectId)
                                    loadAIResponses(currentProjectId)
                                    loadSavedRosters()
                                }
                            }
                            val p = projects.find { it.id == currentProjectId }
                            Column(modifier = Modifier.fillMaxSize()) {
                                val nothingOpen = !sectionState.isDashboardOpen && !sectionState.isMarkedSheetsOpen &&
                                    !sectionState.isQuestionPaperOpen && !sectionState.isAIAnswerSheetOpen &&
                                    !sectionState.isManualAnswerSheetOpen && !sectionState.isCollectedSheetsOpen &&
                                    !sectionState.isPrintableReportOpen && !sectionState.isRosterSetupOpen &&
                                    !sectionState.isSavedRostersOpen && !sectionState.isAnswerSheetGeneratorOpen
                                if (nothingOpen) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { currentView = "subject" },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                        ) { Text("? Back") }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${p?.grade ?: ""} • ${p?.subjectKey ?: ""}",
                                                color = Color(0xFF94a3b8), fontSize = 11.sp)
                                            Text(currentProjectTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            sectionState = sectionState.copy(isDashboardOpen = true)
                                            loadAIResponses(currentProjectId)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa))
                                    ) { Text("Data Dashboard") }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (collectedStudentAnswerSheets.isNotEmpty()) {
                                        Button(
                                            onClick = { gradeCollectedSheets() },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf59e0b))
                                        ) { Text(if (isGrading) "Grading..." else "Grade Collected Sheets (${collectedStudentAnswerSheets.size})") }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    if (markedAnswerSheets.isNotEmpty()) {
                                        Button(
                                            onClick = { sectionState = sectionState.copy(isMarkedSheetsOpen = true) },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                        ) { Text("Marked Sheets (${markedAnswerSheets.size})") }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    Button(
                                        onClick = {
                                            currentView = "examAnalytics"
                                            loadExamAnalytics(currentProjectId)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                    ) { Text("View Exam Analytics") }
                                    Spacer(modifier = Modifier.height(16.dp))

                                    if (isExtracting) {
                                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                            Column(modifier = Modifier.padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator()
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(progressText.ifBlank { "Working..." }, color = Color(0xFF94a3b8))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }

                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isRosterSetupOpen = true) },
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa))
                                            ) { Text("Set Up Class Roster") }
                                        }
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isSavedRostersOpen = true); loadSavedRosters() },
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                            ) { Text("Saved Rosters (${savedRosters.size})") }
                                        }
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isAnswerSheetGeneratorOpen = true) },
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                            ) { Text("Generate Answer Sheet") }
                                        }
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isQuestionPaperOpen = true) },
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa))
                                            ) { Text("Question Paper") }
                                        }
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isAIAnswerSheetOpen = true) },
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                            ) { Text("AI Answer Sheet") }
                                        }
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isManualAnswerSheetOpen = true) },
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf59e0b))
                                            ) { Text("Manual Answer Sheet") }
                                        }
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isCollectedSheetsOpen = true) },
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                            ) { Text("Collected Sheets (${collectedStudentAnswerSheets.size})") }
                                        }
                                        item {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            db.collection("exams").document(currentProjectId).delete().await()
                                                            loadProjects()
                                                            currentView = "subject"
                                                        } catch (e: Exception) {}
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFef4444))
                                            ) { Text("Delete Exam") }
                                        }
                                    }
                                }

                                if (sectionState.isAnswerSheetGeneratorOpen) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isAnswerSheetGeneratorOpen = false) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("? Back") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Generate Answer Sheet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10b981))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(value = answerSheetQuestionCount,
                                                onValueChange = { answerSheetQuestionCount = it },
                                                label = { Text("Number of Questions") },
                                                modifier = Modifier.fillMaxWidth())
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    val count = answerSheetQuestionCount.toIntOrNull() ?: 20
                                                    generatedAnswerSheetText = generateAnswerSheetTemplate(count)
                                                    Toast.makeText(context, "Generated!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                            ) { Text("Generate") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            if (generatedAnswerSheetText.isNotBlank()) {
                                                Text("Preview:", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                Text(generatedAnswerSheetText, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(onClick = { saveReportToDownloads(generatedAnswerSheetText) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf59e0b))
                                                ) { Text("Save to Downloads") }
                                            }
                                        }
                                    }
                                }

                                if (sectionState.isRosterSetupOpen) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isRosterSetupOpen = false) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("? Back") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Class Roster", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(value = rosterInput, onValueChange = { rosterInput = it },
                                                label = { Text("Names separated by commas") },
                                                modifier = Modifier.fillMaxWidth(), minLines = 3)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(value = rosterTemplateName, onValueChange = { rosterTemplateName = it },
                                                label = { Text("Template Name") }, modifier = Modifier.fillMaxWidth())
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    rosterNames = rosterInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                                    rosterIndex = 0; isRosterMode = true
                                                    Toast.makeText(context, "Loaded ${rosterNames.size}", Toast.LENGTH_SHORT).show()
                                                    sectionState = sectionState.copy(isRosterSetupOpen = false)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                            ) { Text("Use This Roster") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    rosterNames = rosterInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                                    saveRosterTemplate(rosterTemplateName, rosterNames)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa))
                                            ) { Text("Save as Template") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            if (rosterNames.isNotEmpty()) {
                                                Text("Current Roster (${rosterNames.size}):", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                rosterNames.forEachIndexed { i, n -> Text("${i + 1}. $n", color = Color.White, fontSize = 12.sp) }
                                            }
                                        }
                                    }
                                }

                                if (sectionState.isSavedRostersOpen) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isSavedRostersOpen = false) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("? Back") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Saved Rosters", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10b981))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            if (savedRosters.isEmpty()) {
                                                Text("None yet.", color = Color(0xFF94a3b8))
                                            } else {
                                                savedRosters.forEach { r ->
                                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                        Column(modifier = Modifier.padding(16.dp)) {
                                                            Row(modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween) {
                                                                Text(r.name, fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                                Row {
                                                                    Button(
                                                                        onClick = {
                                                                            rosterNames = r.names
                                                                            rosterIndex = 0; isRosterMode = true
                                                                            sectionState = sectionState.copy(isSavedRostersOpen = false)
                                                                        },
                                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                                                    ) { Text("Use") }
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Button(onClick = { deleteRosterTemplate(r.id) },
                                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFef4444))
                                                                    ) { Text("X") }
                                                                }
                                                            }
                                                            Text("${r.names.size} students", color = Color(0xFF94a3b8), fontSize = 12.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (sectionState.isDashboardOpen) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isDashboardOpen = false) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("? Back") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text("Ask AI", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    OutlinedTextField(value = aiQuestion, onValueChange = { aiQuestion = it },
                                                        label = { Text("Your question...") },
                                                        modifier = Modifier.fillMaxWidth(), minLines = 2)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Button(onClick = { askAIQuestion(aiQuestion) },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                                    ) { Text(if (isAIThinking) "Thinking..." else "Ask") }
                                                    if (aiAnswer.isNotBlank()) {
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        Card(modifier = Modifier.fillMaxWidth(),
                                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))) {
                                                            Column(modifier = Modifier.padding(16.dp)) {
                                                                Text("Response:", fontWeight = FontWeight.Bold, color = Color(0xFF10b981))
                                                                Text(aiAnswer, color = Color.White, fontSize = 14.sp)
                                                                Spacer(modifier = Modifier.height(8.dp))
                                                                Row {
                                                                    Button(onClick = { saveAIResponse(aiQuestion, aiAnswer) },
                                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                                                    ) { Text("Save") }
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Button(onClick = { aiAnswer = ""; aiQuestion = "" },
                                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFef4444))
                                                                    ) { Text("Clear") }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    if (savedAIResponses.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        Text("Saved:", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                        savedAIResponses.forEach { r ->
                                                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                                Column(modifier = Modifier.padding(12.dp)) {
                                                                    Text("Q: ${r.question}", color = Color(0xFF60a5fa), fontSize = 12.sp)
                                                                    Text("A: ${r.answer}", color = Color.White, fontSize = 12.sp)
                                                                    Row {
                                                                        Button(onClick = { aiQuestion = r.question; aiAnswer = r.answer },
                                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa))
                                                                        ) { Text("View") }
                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                        Button(onClick = { deleteAIResponse(r.id) },
                                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFef4444))
                                                                        ) { Text("Delete") }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else if (sectionState.isPrintableReportOpen) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isPrintableReportOpen = false) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("? Back") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Printable Report", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFf59e0b))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(printableReportText, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(onClick = { saveReportToDownloads(printableReportText) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                            ) { Text("Save to Downloads") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(onClick = { shareReport(printableReportText) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa))
                                            ) { Text("Share") }
                                        }
                                    }
                                } else if (sectionState.isMarkedSheetsOpen) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isMarkedSheetsOpen = false) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("? Back") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Marked Sheets", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10b981))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    printableReportText = generatePrintableMarkedSheets(markedAnswerSheets)
                                                    sectionState = sectionState.copy(isPrintableReportOpen = true)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf59e0b))
                                            ) { Text("Printable Report") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            markedAnswerSheets.forEach { sheet ->
                                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                    Column(modifier = Modifier.padding(16.dp)) {
                                                        Text("Student: ${sheet.studentName}", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                        Text("${sheet.score}/${sheet.total} (${"%.1f".format(sheet.percentage)}%) | ${getKenyanGrade(sheet.percentage)}",
                                                            color = Color.White, fontSize = 16.sp)
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        Text(sheet.markedText, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        val bmp = remember(sheet.image) { decodeBase64(sheet.image) }
                                                        bmp?.let {
                                                            Image(it.asImageBitmap(), null,
                                                                modifier = Modifier.fillMaxWidth().height(200.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else if (sectionState.isQuestionPaperOpen) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isQuestionPaperOpen = false) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("? Back") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Question Paper", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(onClick = { launchScan("question_paper") },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa))
                                            ) { Text("Add Question Paper") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            if (questionPaperText.isNotBlank()) {
                                                Text("Extracted Text:", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                Text(questionPaperText, color = Color.White, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                } else if (sectionState.isAIAnswerSheetOpen) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isAIAnswerSheetOpen = false) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("? Back") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("AI Answer Sheet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10b981))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            if (simplifiedAnswerKey.isNotBlank()) {
                                                Text("Key:", fontWeight = FontWeight.Bold, color = Color(0xFFf59e0b))
                                                Text(simplifiedAnswerKey, color = Color.White, fontSize = 12.sp)
                                            }
                                            if (aiAnswerSheet.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("Full:", fontWeight = FontWeight.Bold, color = Color(0xFF10b981))
                                                Text(aiAnswerSheet, color = Color.White, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                } else if (sectionState.isManualAnswerSheetOpen) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isManualAnswerSheetOpen = false) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("? Back") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Manual Answer Sheet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFf59e0b))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(onClick = { launchScan("manual_answer_sheet") },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf59e0b))
                                            ) { Text("Add") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            if (manualAnswerKey.isNotBlank()) {
                                                Text("Key:", fontWeight = FontWeight.Bold, color = Color(0xFFf59e0b))
                                                Text(manualAnswerKey, color = Color.White, fontSize = 12.sp)
                                            }
                                            if (manualAnswerSheetText.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("Text:", fontWeight = FontWeight.Bold, color = Color(0xFFf59e0b))
                                                Text(manualAnswerSheetText, color = Color.White, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                } else if (sectionState.isCollectedSheetsOpen) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isCollectedSheetsOpen = false) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("? Back") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Collected Sheets (${collectedStudentAnswerSheets.size})",
                                                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10b981))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(value = manualStudentName, onValueChange = { manualStudentName = it },
                                                label = { Text("Student Name (optional)") }, modifier = Modifier.fillMaxWidth())
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(onClick = { launchScan("answer_sheets") },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                            ) { Text("Add Answer Sheets") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        items(collectedStudentAnswerSheets) { sheet ->
                                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text("Student: ${sheet.studentName}", fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF60a5fa), fontSize = 16.sp)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    val bmp = remember(sheet.image) { decodeBase64(sheet.image) }
                                                    bmp?.let {
                                                        Image(it.asImageBitmap(), null,
                                                            modifier = Modifier.fillMaxWidth().height(250.dp))
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text("Printout (source of truth):",
                                                        fontWeight = FontWeight.Bold, color = Color(0xFFf59e0b), fontSize = 12.sp)
                                                    Card(modifier = Modifier.fillMaxWidth(),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))) {
                                                        Text(
                                                            text = sheet.extractedText.ifBlank { "[No printout]" },
                                                            color = if (sheet.extractedText.startsWith("ERR")) Color(0xFFef4444) else Color(0xFF10b981),
                                                            fontSize = 9.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            modifier = Modifier.padding(8.dp)
                                                                .horizontalScroll(rememberScrollState())
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Button(
                                                        onClick = {
                                                            try {
                                                                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                                                if (!dir.exists()) dir.mkdirs()
                                                                val f = File(dir, "Printout_${sheet.studentName}_${System.currentTimeMillis()}.txt")
                                                                FileWriter(f).use { it.write(sheet.extractedText) }
                                                                Toast.makeText(context, "Saved: ${f.name}", Toast.LENGTH_LONG).show()
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf59e0b))
                                                    ) { Text("Save Printout") }
                                                }
                                            }
                                        }
                                        item {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            db.collection("exams").document(currentProjectId).update(mapOf(
                                                                "studentAnswerSheets" to emptyList<Map<String, Any>>()))
                                                            collectedStudentAnswerSheets = emptyList()
                                                            loadExamData(currentProjectId)
                                                        } catch (e: Exception) {}
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFef4444))
                                            ) { Text("Delete All") }
                                        }
                                    }
                                }
                            }
                        }
                        "examAnalytics" -> {
                            LaunchedEffect(currentProjectId) { loadExamAnalytics(currentProjectId) }
                            if (selectedStudent != null) {
                                val s = selectedStudent!!
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(onClick = { selectedStudent = null },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                        ) { Text("? Back") }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Student: ${s.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    LazyColumn {
                                        item {
                                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text("Overview", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                    Text("Average: ${"%.1f".format(s.averageScore)}%", color = Color.White)
                                                    Text("Grade: ${s.grade}", color = Color.White)
                                                    Text("Rank: #${s.rank}", color = Color.White)
                                                    Text("Exams: ${s.examCount}", color = Color.White)
                                                }
                                            }
                                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text("Strong", fontWeight = FontWeight.Bold, color = Color(0xFF10b981))
                                                    s.strongTopics.forEach { Text("- $it", color = Color.White) }
                                                }
                                            }
                                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Text("Weak", fontWeight = FontWeight.Bold, color = Color(0xFFef4444))
                                                    s.weakTopics.forEach { Text("- $it", color = Color.White) }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(onClick = { currentView = "projectDetail" },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                        ) { Text("? Back") }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Exam Analytics", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    val a = examAnalytics
                                    if (a == null) {
                                        Text("Loading...", color = Color(0xFF94a3b8), modifier = Modifier.padding(32.dp))
                                    } else {
                                        LazyColumn {
                                            if (a.actionableInsights.isNotEmpty()) {
                                                item {
                                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                        Column(modifier = Modifier.padding(16.dp)) {
                                                            Text("Insights", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10b981))
                                                            a.actionableInsights.forEach {
                                                                Text("- ${it.message}", color = when (it.type) {
                                                                    "RETEACH" -> Color(0xFFef4444); "FOCUS" -> Color(0xFFf59e0b)
                                                                    "STRONG" -> Color(0xFF10b981); else -> Color.White
                                                                }, fontSize = 12.sp)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            if (a.trends.isNotEmpty()) {
                                                item {
                                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                        Column(modifier = Modifier.padding(16.dp)) {
                                                            Text("Trends", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                            a.trends.forEach { t ->
                                                                val txt = if (t.change > 0) "+${"%.1f".format(t.change)}%" else "${"%.1f".format(t.change)}%"
                                                                Text("${t.topic}: $txt (from ${"%.1f".format(t.previousScore)}% to ${"%.1f".format(t.currentScore)}%)",
                                                                    color = if (t.change > 0) Color(0xFF10b981) else Color(0xFFef4444), fontSize = 12.sp)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            item {
                                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                    Column(modifier = Modifier.padding(16.dp)) {
                                                        Text("Topic Analysis", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                        a.classTopicPerformance.forEach { t ->
                                                            Text("${t.topic}: ${"%.1f".format(t.averageScore)}% (${t.correctAnswers}/${t.questionsAttempted})",
                                                                color = if (t.averageScore >= 80) Color(0xFF10b981) else if (t.averageScore >= 50) Color(0xFFf59e0b) else Color(0xFFef4444),
                                                                fontSize = 12.sp)
                                                        }
                                                    }
                                                }
                                            }
                                            item {
                                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                    Column(modifier = Modifier.padding(16.dp)) {
                                                        Text("Distribution", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                        Text("A: ${a.scoreDistribution.gradeA}", color = Color(0xFF10b981))
                                                        Text("A-: ${a.scoreDistribution.gradeAMinus}", color = Color(0xFF10b981))
                                                        Text("B+: ${a.scoreDistribution.gradeBPlus}", color = Color(0xFF60a5fa))
                                                        Text("B: ${a.scoreDistribution.gradeB}", color = Color(0xFF60a5fa))
                                                        Text("B-: ${a.scoreDistribution.gradeBMinus}", color = Color(0xFF60a5fa))
                                                        Text("C+: ${a.scoreDistribution.gradeCPlus}", color = Color(0xFFf59e0b))
                                                        Text("C: ${a.scoreDistribution.gradeC}", color = Color(0xFFf59e0b))
                                                        Text("C-: ${a.scoreDistribution.gradeCMinus}", color = Color(0xFFf59e0b))
                                                        Text("D+: ${a.scoreDistribution.gradeDPlus}", color = Color(0xFFf97316))
                                                        Text("D: ${a.scoreDistribution.gradeD}", color = Color(0xFFf97316))
                                                        Text("D-: ${a.scoreDistribution.gradeDMinus}", color = Color(0xFFf97316))
                                                        Text("E: ${a.scoreDistribution.gradeE}", color = Color(0xFFef4444))
                                                    }
                                                }
                                            }
                                            item {
                                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                    Column(modifier = Modifier.padding(16.dp)) {
                                                        Text("Question-by-Question Analysis",
                                                            fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa), fontSize = 16.sp)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text("Tap any question to see the students who missed it.",
                                                            color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                        Spacer(modifier = Modifier.height(12.dp))

                                                        if (a.questionBreakdown.isEmpty()) {
                                                            Text("No per-question data. Re-grade the sheets.",
                                                                color = Color(0xFFf59e0b), fontSize = 12.sp)
                                                        } else {
                                                            val topicCounts = a.questionBreakdown.groupingBy { it.topic }.eachCount()
                                                            a.questionBreakdown.forEach { q ->
                                                                val failedStudents = allResults
                                                                    .filter { st -> st.questions.any { qr -> qr.questionNumber == q.questionNumber && !qr.isCorrect } }
                                                                    .map { it.studentName }
                                                                    .sorted()
                                                                val parts = q.difficulty.split(" | ")
                                                                val diffLabel = parts.getOrNull(0) ?: q.difficulty
                                                                val correctPart = parts.getOrNull(1) ?: "${q.correctCount}/${q.totalCount} correct"
                                                                val failCount = q.totalCount - q.correctCount
                                                                val color = when (diffLabel) {
                                                                    "Easy" -> Color(0xFF10b981)
                                                                    "Moderate" -> Color(0xFFf59e0b)
                                                                    else -> Color(0xFFef4444)
                                                                }
                                                                var expanded by remember { mutableStateOf(false) }
                                                                Card(
                                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                                        .clickable { expanded = !expanded },
                                                                    colors = CardDefaults.cardColors(
                                                                        containerColor = if (expanded) Color(0xFF16213f) else Color(0xFF0f172a)
                                                                    )
                                                                ) {
                                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                                        Row(modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.SpaceBetween) {
                                                                            Text("Q${q.questionNumber}", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa), fontSize = 14.sp)
                                                                            Text(diffLabel, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                                        }
                                                                        Spacer(modifier = Modifier.height(4.dp))
                                                                        Text(q.topic, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                                        Spacer(modifier = Modifier.height(4.dp))
                                                                        Text(
                                                                            "$correctPart | $failCount failed" +
                                                                            if (topicCounts[q.topic] != null && topicCounts[q.topic]!! > 1)
                                                                                " | topic repeated ${topicCounts[q.topic]}x on this paper"
                                                                            else "",
                                                                            color = Color(0xFF94a3b8), fontSize = 11.sp
                                                                        )
                                                                        if (expanded) {
                                                                            Spacer(modifier = Modifier.height(10.dp))
                                                                            Divider(color = Color(0xFF1e293b), thickness = 1.dp)
                                                                            Spacer(modifier = Modifier.height(10.dp))
                                                                            Text("Strand -> Sub-strand", color = Color(0xFF60a5fa), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                            Text(q.topic, color = Color.White, fontSize = 12.sp)
                                                                            Spacer(modifier = Modifier.height(8.dp))
                                                                            Text("Failed by $failCount student${if (failCount == 1) "" else "s"}",
                                                                                color = Color(0xFFef4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                                            if (failedStudents.isEmpty()) {
                                                                                Text("No one failed this question. Nice.", color = Color(0xFF10b981), fontSize = 12.sp)
                                                                            } else {
                                                                                failedStudents.forEach { name -> Text("  - $name", color = Color.White, fontSize = 12.sp) }
                                                                            }
                                                                            Spacer(modifier = Modifier.height(8.dp))
                                                                            Text("Topic repeated ${topicCounts[q.topic] ?: 1}x on this paper",
                                                                                color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            item {
                                                Text("Leaderboard", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                            items(a.studentLeaderboard) { s ->
                                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                    .clickable { selectedStudent = s },
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                    Row(modifier = Modifier.padding(16.dp),
                                                        verticalAlignment = Alignment.CenterVertically) {
                                                        Text("${s.rank}.", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFf59e0b))
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(s.name, fontWeight = FontWeight.Bold, color = Color.White)
                                                            Text("Avg: ${"%.1f".format(s.averageScore)}% | ${s.grade}",
                                                                color = Color(0xFF94a3b8), fontSize = 12.sp)
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
        }
    }
}