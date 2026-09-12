package com.waveunits.docs

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
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
    val subject: String = "",
    val status: String = "",
    val createdAt: String = "",
    val questionPaperText: String = "",
    val aiAnswerSheet: String = "",
    val simplifiedAnswerKey: String = "",
    val questionPaperImage: String = "",
    val questionPaperImages: List<String> = emptyList(),
    val allQuestionTexts: List<String> = emptyList(),
    val extractedTextSaved: Boolean = false,
    val answerSheetGenerated: Boolean = false,
    val manualAnswerSheet: String = "",
    val manualAnswerKey: String = "",
    val markingMode: String = "ai",
    val answerKey: String = "",
    val studentAnswerSheets: List<StudentAnswerSheetData> = emptyList()
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

data class MarkedAnswerSheetData(
    val studentName: String, val markedText: String, val image: String,
    val score: Int, val total: Int, val percentage: Double
)

data class ScannedAnswerSheet(
    val studentName: String, val image: String, val extractedText: String,
    val answers: Map<Int, String>, val score: Int, val total: Int, val percentage: Double
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

private const val OPENAI_API_KEY = "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"

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

fun generateMarkedAnswerSheetText(
    studentName: String,
    studentAnswers: Map<Int, String>,
    answerKeyMap: Map<Int, String>
): String {
    val sb = StringBuilder()
    sb.append("STUDENT: $studentName\n")
    sb.append("--------------------------------\n")
    var correct = 0
    val allQ = (answerKeyMap.keys + studentAnswers.keys).toSortedSet()
    for (q in allQ) {
        val correctAns = answerKeyMap[q] ?: ""
        val stuAns = studentAnswers[q] ?: ""
        val ok = stuAns.isNotBlank() && stuAns == correctAns
        if (ok) correct++
        val status = if (ok) "[CORRECT]" else "[WRONG]"
        sb.append("Q$q: Student: ${stuAns.ifBlank { "[BLANK]" }} | Correct: $correctAns | $status\n")
    }
    val total = allQ.size
    val pct = if (total > 0) (correct.toDouble() / total) * 100 else 0.0
    sb.append("--------------------------------\n")
    sb.append("Score: $correct/$total (${"%.1f".format(pct)}%) | Grade: ${getKenyanGrade(pct)}\n")
    return sb.toString()
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

// ===== AI: STAGE 1 — TRANSCRIBE ANSWER SHEET (high res for pencil marks) =====
private suspend fun transcribeAnswerSheet(context: Context, uri: Uri): String {
    return withContext(Dispatchers.IO) {
        try {
            val isr = context.contentResolver.openInputStream(uri) ?: return@withContext "ERR: cannot open image"
            val bmp = BitmapFactory.decodeStream(isr)
            isr.close()
            // High resolution + high quality — required for faint pencil marks
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

                IMPORTANT: The student has marked their answers by writing a letter
                (A, B, C, or D) or drawing a line/tick inside the bracket of the
                chosen column. Look VERY carefully at the bracket cells for
                handwritten marks — pencil, pen, cross, or line. Print the letter
                you see inside each bracket. If a bracket has a mark but the letter
                is unclear, print the column letter (A/B/C/D) inside that bracket.
                Print EMPTY brackets as [   ].
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
                } catch (e: Exception) {
                    "ERR parse: ${e.message} | raw: ${js.take(400)}"
                }
            }
        } catch (e: Exception) {
            "ERR exception: ${e.message}"
        }
    }
}

// ===== AI GRADING: printout + AI answer sheet + column rules =====
private suspend fun gradeWithAI(
    printout: String,
    aiAnswerSheetText: String,
    studentName: String
): StudentResult? {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val prompt = """
                You are grading a student's answer sheet.

                ============================================================
                INPUT 1 — STUDENT PRINTOUT
                ============================================================
                This is a transcription of the student's answer sheet.
                The student chose one option per question by marking inside a
                bracket cell on the answer-sheet grid.

                HOW TO READ THE STUDENT'S ANSWERS — COLUMN RULES:
                1. Each row is one question, identified by its number.
                2. The header row gives the column letters (A | B | C | D).
                3. For each question, look at the four bracket cells.
                4. If a readable letter (A, B, C, D) is inside a bracket,
                   that letter is the student's answer.
                5. If a bracket's contents are unreadable (tick, scribble,
                   a line drawn through it, or a letter you cannot make out)
                   BUT a mark is clearly inside that bracket, use the
                   COLUMN HEADER of that bracket as the answer.
                6. If two brackets in the same row have readable letters,
                   choose the LEFTMOST one.
                7. If no bracket in the row has a letter or a mark, the
                   student's answer is blank.

                ============================================================
                INPUT 2 — AI ANSWER SHEET
                ============================================================
                This is the correct answer key generated from the question paper.

                ============================================================
                TASK
                ============================================================
                Go through every question in the answer key.
                For each question:
                  - Extract the student's answer from the printout using the
                    column rules above.
                  - Compare it to the correct answer.
                  - Mark it correct or wrong.

                ============================================================
                OUTPUT — ONLY JSON, no markdown, no commentary
                ============================================================
                {
                  "studentName": "$studentName",
                  "score": 12,
                  "total": 20,
                  "percentage": 60.0,
                  "questions": [
                    {"questionNumber":1,"topic":"Parables","studentAnswer":"A","correctAnswer":"B","isCorrect":false}
                  ]
                }

                Use "" for a blank student answer.
                Include every question that appears in the answer key.

                ============================================================
                STUDENT PRINTOUT
                ============================================================
                $printout

                ============================================================
                AI ANSWER SHEET
                ============================================================
                $aiAnswerSheetText
            """.trimIndent()

            val body = JSONObject()
                .put("model", "gpt-5.6-luna")
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                .put("max_completion_tokens", 4000)
                .put("temperature", 0.0)
                .toString()

            val req = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
                .build()

            client.newCall(req).execute().use { res ->
                val js = res.body?.string() ?: return@use null
                if (!res.isSuccessful) {
                    android.util.Log.e("WAVEUNITS", "Grade HTTP ${res.code}: ${js.take(500)}")
                    return@use null
                }
                val txt = JSONObject(js).getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                var clean = txt.replace("```json", "").replace("```", "").trim()
                val s = clean.indexOf('{'); val e = clean.lastIndexOf('}')
                if (s >= 0 && e > s) clean = clean.substring(s, e + 1)
                val o = JSONObject(clean)
                val name = o.optString("studentName", studentName)
                val score = o.optInt("score", 0)
                val total = o.optInt("total", 0)
                val pct = o.optDouble("percentage", if (total > 0) score * 100.0 / total else 0.0)
                val qa = o.optJSONArray("questions") ?: JSONArray()
                val qs = mutableListOf<QuestionResultData>()
                for (i in 0 until qa.length()) {
                    val q = qa.getJSONObject(i)
                    qs.add(QuestionResultData(
                        q.optInt("questionNumber", 0),
                        q.optString("topic", "General"),
                        q.optString("studentAnswer", "").uppercase().take(1),
                        q.optString("correctAnswer", "").uppercase().take(1),
                        q.optBoolean("isCorrect", false)
                    ))
                }
                StudentResult(name, score, total, pct, qs)
            }
        } catch (e: Exception) {
            android.util.Log.e("WAVEUNITS", "Grade exception: ${e.message}")
            null
        }
    }
}

// ===== AI: QUESTION PAPER / ANSWER KEY GENERATION =====
private suspend fun askAIWithContext(contextText: String, question: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val prompt = """
                You are an AI assistant helping a teacher analyze exam data.
                Context:
                $contextText
                Question:
                $question
                Answer clearly based ONLY on the data above.
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
                Generate an answer sheet with curriculum topics for each question.
                The answer MUST be A, B, C, or D. If unknown, choose A.
                Questions:
                ${questions.joinToString("\n") { "Q${it.number}: ${it.topic} - Answer: ${it.correctAnswer}" }}
                Format:
                Q1: Answer: D | Topic: Number | Sub-topic: Addition
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
                You are analyzing an exam paper. Extract ALL questions. For each, provide:
                - number (integer)
                - topic: a short topic based on the actual subject of the paper.
                  Infer the subject from the paper text itself.
                  (e.g. for CRE: "Parables", "Old Testament", "Christian Values", "Miracles";
                   for Maths: "Number", "Algebra", "Geometry", "Measurement";
                   for Science: "Plants", "Animals", "Matter", "Energy";
                   for English: "Grammar", "Comprehension", "Vocabulary").
                - subTopic: a shorter sub-topic
                - correctAnswer: MUST be A, B, C, or D. Never null. If unknown, choose A.
                Return ONLY JSON:
                [{"number":1,"topic":"Parables","subTopic":"Talents","correctAnswer":"D"}, ...]
                Question paper:
                $rawText
            """.trimIndent()
            val body = JSONObject()
                .put("model", "gpt-5.6-luna")
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                .put("max_completion_tokens", 4000)
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
                    qs.add(QuestionData(
                        number = o.getInt("number"),
                        topic = o.optString("topic", "General"),
                        correctAnswer = safe,
                        subTopic = o.optString("subTopic", "")
                    ))
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

    val prefs = context.getSharedPreferences("WaveUnitsPrefs", Context.MODE_PRIVATE)
    var email by remember { mutableStateOf(prefs.getString("email", "") ?: "") }
    var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }
    var isLoggedIn by remember { mutableStateOf(prefs.getBoolean("isLoggedIn", false)) }
    var showSignup by remember { mutableStateOf(false) }
    var currentView by remember { mutableStateOf("projects") }
    var projects by remember { mutableStateOf<List<ExamProject>>(emptyList()) }
    var currentProject by remember { mutableStateOf<ExamProject?>(null) }
    var currentProjectId by remember { mutableStateOf("") }
    var currentProjectTitle by remember { mutableStateOf("") }
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
    var showMarkingResults by remember { mutableStateOf(false) }
    var isExtracting by remember { mutableStateOf(false) }
    var collectedStudentAnswerSheets by remember { mutableStateOf<List<StudentAnswerSheetData>>(emptyList()) }
    var markedAnswerSheets by remember { mutableStateOf<List<MarkedAnswerSheetData>>(emptyList()) }
    var sectionState by remember { mutableStateOf(SectionViewState()) }
    var aiQuestion by remember { mutableStateOf("") }
    var aiAnswer by remember { mutableStateOf("") }
    var isAIThinking by remember { mutableStateOf(false) }
    var savedAIResponses by remember { mutableStateOf<List<AIQuestionAnswer>>(emptyList()) }
    var examAnalytics by remember { mutableStateOf<ExamAnalyticsBundle?>(null) }
    var selectedStudent by remember { mutableStateOf<StudentPerformance?>(null) }
    var allResults by remember { mutableStateOf<List<StudentResult>>(emptyList()) }

    fun saveLoginState(isLogged: Boolean) {
        prefs.edit().putBoolean("isLoggedIn", isLogged)
            .putString("email", email).putString("password", password).apply()
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
                        subject = doc.getString("subject") ?: "",
                        status = doc.getString("status") ?: "created",
                        createdAt = formatTimestamp(createdAtMillis),
                        questionPaperText = doc.getString("questionPaperText") ?: "",
                        aiAnswerSheet = doc.getString("aiAnswerSheet") ?: "",
                        simplifiedAnswerKey = doc.getString("simplifiedAnswerKey") ?: "",
                        questionPaperImage = doc.getString("questionPaperImage") ?: "",
                        questionPaperImages = doc.get("questionPaperImages") as? List<String> ?: emptyList(),
                        allQuestionTexts = doc.get("allQuestionTexts") as? List<String> ?: emptyList(),
                        extractedTextSaved = doc.getBoolean("extractedTextSaved") ?: false,
                        answerSheetGenerated = doc.getBoolean("answerSheetGenerated") ?: false,
                        manualAnswerSheet = doc.getString("manualAnswerSheet") ?: "",
                        manualAnswerKey = doc.getString("manualAnswerKey") ?: "",
                        markingMode = doc.getString("markingMode") ?: "ai",
                        answerKey = doc.getString("answerKey") ?: ""
                    )
                }.sortedByDescending { parseCreatedAt(it.createdAt) }
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

    fun loadPreviousExamTopicPerformance(currentExamId: String) {
        scope.launch {
            try {
                val uid = auth.currentUser?.uid ?: return@launch
                val examsQuery = db.collection("exams").whereEqualTo("teacherId", uid)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(5).get().await()
                val previousExams = examsQuery.documents.filter { it.id != currentExamId }
                if (previousExams.isEmpty()) return@launch
                val previousExam = previousExams[0]
                val resultsQuery = db.collection("results").whereEqualTo("examId", previousExam.id).get().await()
                val results = resultsQuery.documents.map { doc ->
                    val questionsData = doc.get("questions") as? List<Map<String, Any>> ?: emptyList()
                    StudentResult(
                        studentName = doc.getString("studentName") ?: "Unknown",
                        score = (doc.get("score") as? Number)?.toInt() ?: 0,
                        totalMarks = (doc.get("totalMarks") as? Number)?.toInt() ?: 0,
                        percentage = (doc.get("percentage") as? Number)?.toDouble() ?: 0.0,
                        questions = questionsData.map { q ->
                            QuestionResultData(
                                questionNumber = (q["questionNumber"] as? Number)?.toInt() ?: 0,
                                topic = q["topic"] as? String ?: "",
                                studentAnswer = q["studentAnswer"] as? String ?: "",
                                correctAnswer = q["correctAnswer"] as? String ?: "",
                                isCorrect = q["isCorrect"] as? Boolean ?: false
                            )
                        }
                    )
                }
                val map = mutableMapOf<String, MutableList<Boolean>>()
                for (r in results) for (q in r.questions) map.getOrPut(q.topic) { mutableListOf() }.add(q.isCorrect)
                previousTopicPerformance = map.map { (topic, list) ->
                    val c = list.count { it }; val t = list.size
                    ClassTopicPerformance(topic, if (t > 0) (c.toDouble() / t) * 100 else 0.0, t, c)
                }
            } catch (e: Exception) {}
        }
    }

    fun loadAdvancedAnalytics() {
        scope.launch {
            try {
                val resultsQuery = db.collection("results").whereEqualTo("examId", currentProjectId).get().await()
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
                                q["topic"] as? String ?: "",
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
                    val e = qMap.getOrPut(q.questionNumber) { Pair(q.topic, mutableListOf()) }
                    e.second.add(q.isCorrect)
                }
                val qBreakdown = qMap.map { (num, pair) ->
                    val cc = pair.second.count { it }; val tc = pair.second.size
                    val acc = if (tc > 0) cc.toDouble() / tc else 0.0
                    QuestionBreakdown(num, pair.first, cc, tc,
                        when { acc >= 0.8 -> "Easy"; acc >= 0.5 -> "Moderate"; else -> "Hard" })
                }.sortedBy { it.questionNumber }

                val topicMap = mutableMapOf<String, MutableList<Boolean>>()
                for (r in results) for (q in r.questions) topicMap.getOrPut(q.topic) { mutableListOf() }.add(q.isCorrect)
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
                    Toast.makeText(context, "No AI answer sheet. Scan the question paper first.", Toast.LENGTH_LONG).show()
                    isGrading = false; return@launch
                }

                val results = mutableListOf<StudentResult>()
                val marked = mutableListOf<MarkedAnswerSheetData>()

                for (sheet in collectedStudentAnswerSheets) {
                    if (sheet.studentName.isBlank() || sheet.studentName == "Unknown") continue
                    if (sheet.extractedText.isBlank()) {
                        Toast.makeText(context, "Skipping ${sheet.studentName}: empty printout", Toast.LENGTH_LONG).show()
                        continue
                    }
                    if (sheet.extractedText.startsWith("ERR")) {
                        Toast.makeText(context, "Skipping ${sheet.studentName}: ${sheet.extractedText.take(120)}", Toast.LENGTH_LONG).show()
                        continue
                    }

                    progressText = "Grading ${sheet.studentName}..."

                    // Send printout + AI answer sheet + column rules. AI does the marking.
                    val result = gradeWithAI(sheet.extractedText, aiSheetForGrading, sheet.studentName)

                    if (result == null || result.questions.isEmpty()) {
                        Toast.makeText(context, "Grading failed for ${sheet.studentName}", Toast.LENGTH_LONG).show()
                        continue
                    }

                    results.add(result)

                    val studentAnswers = result.questions.associate { it.questionNumber to it.studentAnswer }
                    val answerKeyMap = result.questions.associate { it.questionNumber to it.correctAnswer }
                    val markedText = generateMarkedAnswerSheetText(sheet.studentName, studentAnswers, answerKeyMap)
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
                        "score" to result.score, "totalMarks" to result.totalMarks, "percentage" to result.percentage,
                        "gradedBy" to "ai-printout-vs-answer-sheet",
                        "rawText" to sheet.extractedText,
                        "questions" to result.questions.map { q -> mapOf(
                            "questionNumber" to q.questionNumber, "topic" to q.topic,
                            "studentAnswer" to q.studentAnswer, "correctAnswer" to q.correctAnswer,
                            "isCorrect" to q.isCorrect) },
                        "createdAt" to System.currentTimeMillis()
                    ))

                    val portfolioRef = db.collection("portfolios")
                        .document("${auth.currentUser?.uid}_${sheet.studentName}")
                    val existing = portfolioRef.get().await()
                    val examCount = (existing.getLong("examsTaken") ?: 0) + 1
                    val totalScore = (existing.getDouble("totalScore") ?: 0.0) + result.percentage
                    portfolioRef.set(mapOf(
                        "teacherId" to auth.currentUser?.uid,
                        "studentName" to sheet.studentName,
                        "averageScore" to (totalScore / examCount),
                        "examsTaken" to examCount, "totalScore" to totalScore,
                        "weakTopics" to result.questions.filter { !it.isCorrect }.map { it.topic }.distinct(),
                        "strongTopics" to result.questions.filter { it.isCorrect }.map { it.topic }.distinct()
                    ))
                }

                if (results.isEmpty()) {
                    Toast.makeText(context, "No sheets were graded", Toast.LENGTH_LONG).show()
                    isGrading = false; return@launch
                }
                db.collection("exams").document(currentProjectId).update("status", "graded")
                progressText = "Graded ${results.size} papers!"
                isGrading = false
                loadAdvancedAnalytics()
                showMarkingResults = true
                markedAnswerSheets = marked
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
                if (markedAnswerSheets.isNotEmpty()) {
                    sb.append("=== MARKED SHEETS ===\n")
                    markedAnswerSheets.forEachIndexed { i, s ->
                        sb.append("--- Student ${i + 1}: ${s.studentName} ---\n${s.markedText}\n\n")
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

    fun saveQuestionPaperText() {
        scope.launch {
            try {
                db.collection("exams").document(currentProjectId).update(mapOf(
                    "questionPaperText" to questionPaperText, "extractedTextSaved" to true))
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    fun saveAIAnswerSheetText() {
        scope.launch {
            try {
                db.collection("exams").document(currentProjectId).update(mapOf(
                    "aiAnswerSheet" to aiAnswerSheet,
                    "simplifiedAnswerKey" to simplifiedAnswerKey,
                    "answerKey" to simplifiedAnswerKey))
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    fun saveManualAnswerSheetText() {
        scope.launch {
            try {
                db.collection("exams").document(currentProjectId).update(mapOf(
                    "manualAnswerSheet" to manualAnswerSheetText,
                    "manualAnswerKey" to manualAnswerKey,
                    "answerKey" to manualAnswerKey))
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show() }
        }
    }

    fun saveCollectedSheets() {
        scope.launch {
            try {
                val sheetsData = collectedStudentAnswerSheets.map { s ->
                    mapOf("studentName" to s.studentName,
                        "extractedText" to s.extractedText,
                        "image" to s.image,
                        "answers" to s.answers.mapKeys { it.key.toString() }.mapValues { it.value })
                }
                db.collection("exams").document(currentProjectId).update(
                    mapOf("studentAnswerSheets" to sheetsData))
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show() }
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

    fun saveAnswerSheetTemplate(text: String) {
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "WaveUnits_AnswerSheet_${System.currentTimeMillis()}.txt")
            FileWriter(file).use { it.write(text) }
            Toast.makeText(context, "Saved: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "${e.message}", Toast.LENGTH_LONG).show()
        }
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
                                "extractedTextSaved" to false,
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
                                    Toast.makeText(
                                        context,
                                        "Transcribe failed: ${printout.take(200)}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }

                                val studentName = when {
                                    isRosterMode && rosterNames.isNotEmpty() -> {
                                        val n = rosterNames.getOrNull(rosterIndex) ?: "Unknown"
                                        rosterIndex++; n
                                    }
                                    manualStudentName.isNotBlank() -> manualStudentName
                                    else -> "Unknown"
                                }

                                newSheets.add(StudentAnswerSheetData(
                                    studentName = studentName,
                                    extractedText = printout,
                                    image = imgB64
                                ))
                            }
                            val newCollected = collectedStudentAnswerSheets + newSheets
                            val sheetsData = newCollected.map { s ->
                                mapOf("studentName" to s.studentName,
                                    "extractedText" to s.extractedText,
                                    "image" to s.image)
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
        if (isLoggedIn) { loadProjects(); loadSavedRosters() }
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
                                auth.signOut(); isLoggedIn = false; saveLoginState(false)
                            }) { Text("Exit", fontSize = 14.sp) }
                        }
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    when (currentView) {
                        "projects" -> {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("My Exams", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Button(
                                    onClick = { currentView = "create" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                ) { Text("+ New Exam") }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            if (projects.isEmpty()) {
                                Column(modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("No exams yet", color = Color(0xFF94a3b8))
                                }
                            } else {
                                LazyColumn {
                                    items(projects) { project ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                .clickable {
                                                    currentProject = project
                                                    currentProjectId = project.id
                                                    currentProjectTitle = project.title
                                                    currentView = "projectDetail"
                                                    loadExamData(project.id)
                                                },
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text(project.title, fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                Text(project.subject, color = Color(0xFF94a3b8), fontSize = 14.sp)
                                                Text("Created: ${project.createdAt}", color = Color(0xFF64748b), fontSize = 12.sp)
                                                Text("Status: ${project.status}", color = Color(0xFF64748b), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "create" -> {
                            var title by remember { mutableStateOf("") }
                            var subject by remember { mutableStateOf("") }
                            Text("Create Exam", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedTextField(value = title, onValueChange = { title = it },
                                label = { Text("Exam Title") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = subject, onValueChange = { subject = it },
                                label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val doc = db.collection("exams").add(mapOf(
                                                    "teacherId" to auth.currentUser?.uid,
                                                    "title" to title, "subject" to subject,
                                                    "status" to "created",
                                                    "createdAt" to System.currentTimeMillis()
                                                )).await()
                                                currentProjectId = doc.id
                                                currentProjectTitle = title
                                                loadProjects(); currentView = "projects"
                                                Toast.makeText(context, "Created!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                ) { Text("Create") }
                                Button(onClick = { currentView = "projects" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                ) { Text("Back") }
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
                            currentProject?.let { p ->
                                Column(modifier = Modifier.fillMaxSize()) {
                                    val nothingOpen = !sectionState.isDashboardOpen && !sectionState.isMarkedSheetsOpen &&
                                        !sectionState.isQuestionPaperOpen && !sectionState.isAIAnswerSheetOpen &&
                                        !sectionState.isManualAnswerSheetOpen && !sectionState.isCollectedSheetsOpen &&
                                        !sectionState.isPrintableReportOpen && !sectionState.isRosterSetupOpen &&
                                        !sectionState.isSavedRostersOpen && !sectionState.isAnswerSheetGeneratorOpen
                                    if (nothingOpen) {
                                        Row(modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(p.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Button(onClick = { currentView = "projects" },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("Back") }
                                        }
                                        Text(p.subject, color = Color(0xFF94a3b8))
                                        Text("Created: ${p.createdAt}", color = Color(0xFF64748b), fontSize = 12.sp)
                                        Text("Status: ${p.status}", color = Color(0xFF64748b), fontSize = 12.sp)
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
                                                currentView = "analytics"
                                                loadAdvancedAnalytics()
                                                loadPreviousExamTopicPerformance(currentProjectId)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                        ) { Text("View Analytics") }
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
                                                                loadProjects(); currentView = "projects"
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
                                                ) { Text("Back") }
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
                                                    Button(onClick = { saveAnswerSheetTemplate(generatedAnswerSheetText) },
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
                                                ) { Text("Back") }
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
                                                ) { Text("Back") }
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
                                                ) { Text("Back") }
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
                                                ) { Text("Back") }
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
                                                ) { Text("Back") }
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
                                                ) { Text("Back") }
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
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Button(onClick = { saveQuestionPaperText() },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                                ) { Text("Save") }
                                            }
                                        }
                                    } else if (sectionState.isAIAnswerSheetOpen) {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            item {
                                                Button(onClick = { sectionState = sectionState.copy(isAIAnswerSheetOpen = false) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                                ) { Text("Back") }
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
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Button(onClick = { saveAIAnswerSheetText() },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                                ) { Text("Save") }
                                            }
                                        }
                                    } else if (sectionState.isManualAnswerSheetOpen) {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            item {
                                                Button(onClick = { sectionState = sectionState.copy(isManualAnswerSheetOpen = false) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                                ) { Text("Back") }
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
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Button(onClick = { saveManualAnswerSheetText() },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf59e0b))
                                                ) { Text("Save") }
                                            }
                                        }
                                    } else if (sectionState.isCollectedSheetsOpen) {
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            item {
                                                Button(onClick = { sectionState = sectionState.copy(isCollectedSheetsOpen = false) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                                ) { Text("Back") }
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
                                                Button(onClick = { saveCollectedSheets() },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                                ) { Text("Save Collected") }
                                                Spacer(modifier = Modifier.height(8.dp))
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
                        }
                        "analytics" -> {
                            LaunchedEffect(currentProjectId) {
                                loadAdvancedAnalytics()
                                loadPreviousExamTopicPerformance(currentProjectId)
                            }
                            if (selectedStudent != null) {
                                val s = selectedStudent!!
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("Student: ${s.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Button(onClick = { selectedStudent = null },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                        ) { Text("Back") }
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
                                    Row(modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("Analytics", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Button(onClick = { currentView = "projectDetail" },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                        ) { Text("Back") }
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
                                            if (a.studentComparisons.isNotEmpty()) {
                                                item {
                                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                        Column(modifier = Modifier.padding(16.dp)) {
                                                            Text("Comparison", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFf59e0b))
                                                            a.studentComparisons.forEach { c ->
                                                                Text("${c.studentA} is ${"%.1f".format(c.scoreDifference)}% ahead of ${c.studentB}",
                                                                    color = Color.White, fontSize = 12.sp)
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
                                                        Text("Question Analysis", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                        a.questionBreakdown.forEach { q ->
                                                            Text("Q${q.questionNumber} (${q.topic}): ${q.correctCount}/${q.totalCount} [${q.difficulty}]",
                                                                color = when (q.difficulty) {
                                                                    "Easy" -> Color(0xFF10b981); "Moderate" -> Color(0xFFf59e0b); else -> Color(0xFFef4444)
                                                                }, fontSize = 12.sp)
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