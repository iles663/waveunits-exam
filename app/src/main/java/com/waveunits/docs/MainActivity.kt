package com.waveunits.docs

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.layout.ContentScale
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
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    val matched: Boolean = true,
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

data class TopicStat(
    val topic: String,
    val passedCount: Int,
    val failedCount: Int,
    val examsAppeared: Int,
    val averageScore: Double
)

data class ClassPathStudentPortfolio(
    val studentName: String,
    val combinedAverage: Double,
    val overallGrade: String,
    val rank: Int,
    val examsTaken: Int,
    val highestExam: Pair<String, Double>,
    val lowestExam: Pair<String, Double>,
    val trend: String,
    val trendChange: Double,
    val mostFailedTopics: List<TopicStat>,
    val mostPassedTopics: List<TopicStat>,
    val examScores: List<Pair<String, Double>>
)

data class SeriesAnalyticsBundle(
    val grade: String,
    val subjectKey: String,
    val examPoints: List<ExamPoint>,
    val classAverage: Double,
    val classTrend: Double,
    val topicSeries: List<TopicSeriesPoint>,
    val studentSeries: List<StudentSeriesPoint>,
    val clusters: List<ClusterPoint>,
    val examCount: Int,
    val studentPortfolios: List<ClassPathStudentPortfolio>
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
        trends.add(TrendData(c.topic, c.averageScore, c.averageScore, c.averageScore - p.averageScore))
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

// ===== PDF HELPERS =====
private const val PT_PER_MM = 2.83465f
private fun mm(v: Float): Float = v * PT_PER_MM

// ===== PDF ANSWER SHEET GENERATOR =====
fun buildAnswerSheetPdf(
    context: Context,
    schoolName: String,
    grade: String,
    subject: String,
    examTitle: String,
    term: String,
    dateText: String,
    questionCount: Int,
    studentNames: List<String>,
    filenameBase: String
): String? {
    val names = if (studentNames.isEmpty()) listOf("") else studentNames
    val cappedCount = if (questionCount > 50) 50 else questionCount
    val leftCount = if (cappedCount <= 25) cappedCount else 25
    val rightCount = if (cappedCount <= 25) 0 else cappedCount - 25

    val pdf = PdfDocument()
    val pageWidthPt = mm(297f)
    val pageHeightPt = mm(210f)
    val marginPt = mm(3f)
    val cutGapPt = mm(4f)
    val sliceWidthPt = (pageWidthPt - 2f * marginPt - cutGapPt) / 2f
    val sliceHeightPt = pageHeightPt - 2f * marginPt

    val paintBorder = Paint().apply {
        color = AndroidColor.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        isAntiAlias = true
    }
    val paintThin = Paint().apply {
        color = AndroidColor.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 0.3f
        isAntiAlias = true
    }
    val paintDashed = Paint().apply {
        color = AndroidColor.parseColor("#888888")
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
        isAntiAlias = true
    }

    val chunks = names.chunked(2)
    for (chunk in chunks) {
        val pageInfo = PdfDocument.PageInfo.Builder(
            pageWidthPt.toInt(),
            pageHeightPt.toInt(),
            pdf.pages.size + 1
        ).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        drawAnswerSheetSlice(
            canvas = canvas,
            originX = marginPt,
            originY = marginPt,
            sliceWidth = sliceWidthPt,
            sliceHeight = sliceHeightPt,
            studentName = chunk.getOrNull(0) ?: "",
            schoolName = schoolName,
            grade = grade,
            subject = subject,
            examTitle = examTitle,
            term = term,
            dateText = dateText,
            leftCount = leftCount,
            rightCount = rightCount,
            paintBorder = paintBorder,
            paintThin = paintThin
        )

        val cutX = marginPt + sliceWidthPt + cutGapPt / 2f
        canvas.drawLine(cutX, marginPt, cutX, marginPt + sliceHeightPt, paintDashed)

        drawAnswerSheetSlice(
            canvas = canvas,
            originX = marginPt + sliceWidthPt + cutGapPt,
            originY = marginPt,
            sliceWidth = sliceWidthPt,
            sliceHeight = sliceHeightPt,
            studentName = chunk.getOrNull(1) ?: "",
            schoolName = schoolName,
            grade = grade,
            subject = subject,
            examTitle = examTitle,
            term = term,
            dateText = dateText,
            leftCount = leftCount,
            rightCount = rightCount,
            paintBorder = paintBorder,
            paintThin = paintThin
        )

        pdf.finishPage(page)
    }

    return try {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val safeBase = filenameBase.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        val file = File(dir, "${safeBase}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out -> pdf.writeTo(out) }
        pdf.close()
        file.absolutePath
    } catch (e: Exception) {
        pdf.close()
        null
    }
}

private fun drawAnswerSheetSlice(
    canvas: Canvas,
    originX: Float,
    originY: Float,
    sliceWidth: Float,
    sliceHeight: Float,
    studentName: String,
    schoolName: String,
    grade: String,
    subject: String,
    examTitle: String,
    term: String,
    dateText: String,
    leftCount: Int,
    rightCount: Int,
    paintBorder: Paint,
    paintThin: Paint
) {
    canvas.drawRect(originX, originY, originX + sliceWidth, originY + sliceHeight, paintBorder)

    val topPad = mm(1f)
    val schoolBandH = mm(5f)
    val dividerH = mm(0.5f)
    val nameBandH1Line = mm(16f)
    val nameBandH2Line = mm(18f)
    val detailsH = mm(5f)
    val gridHeaderH = mm(5f)
    val instrH = mm(5f)
    val bottomPad = mm(1f)
    val padX = mm(6f)
    val rowH = mm(6.5f)

    val schoolPaint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = mm(3.5f)
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    val nameFillPaint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = mm(7.8f)
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        style = Paint.Style.FILL
    }
    val nameStrokePaint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = mm(7.8f)
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeWidth = mm(0.25f)
    }
    val detailPaint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = mm(2.5f)
        isAntiAlias = true
    }
    val gridHeaderPaint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = mm(2.8f)
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    val rowNumPaint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = mm(2.6f)
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    val bracketPaint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = mm(2.6f)
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
    }
    val instrPaint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = mm(2.2f)
        isAntiAlias = true
    }

    var y = originY + topPad

    if (schoolName.isNotBlank()) {
        val baseline = y + schoolBandH * 0.72f
        val w = schoolPaint.measureText(schoolName)
        canvas.drawText(schoolName, originX + (sliceWidth - w) / 2f, baseline, schoolPaint)
    }
    y += schoolBandH
    canvas.drawLine(originX, y, originX + sliceWidth, y, paintThin)
    y += dividerH

    val nameLabel = "NAME:  "
    val nameValue = if (studentName.isBlank()) "________________________" else studentName.uppercase()

    val availWidth = sliceWidth - 2f * padX
    val labelWidth = nameFillPaint.measureText(nameLabel) * 1.15f
    val availForValue = availWidth - labelWidth

    val nameLines = wrapNameToWidth(nameValue, availForValue, nameFillPaint, 1.15f)
    val linesUsed = nameLines.size.coerceAtMost(2)
    val nameBandH = if (linesUsed <= 1) nameBandH1Line else nameBandH2Line

    val nameBandTop = y
    val nameBandBottom = nameBandTop + nameBandH
    val nameTextSize = nameFillPaint.textSize
    val lineGap = if (linesUsed <= 1) 0f else nameTextSize * 0.95f
    val firstBaseline = nameBandTop + nameBandH * 0.5f - ((linesUsed - 1) * lineGap) / 2f + nameTextSize * 0.35f

    for (i in 0 until linesUsed) {
        val prefix = if (i == 0) nameLabel else "       "
        val fullLine = prefix + nameLines[i]
        val baselineY = firstBaseline + i * lineGap
        canvas.save()
        canvas.scale(1.15f, 1.0f, originX + padX, baselineY)
        canvas.drawText(fullLine, originX + padX, baselineY, nameFillPaint)
        canvas.drawText(fullLine, originX + padX, baselineY, nameStrokePaint)
        canvas.restore()
    }
    canvas.drawLine(originX, nameBandBottom, originX + sliceWidth, nameBandBottom, paintThin)
    y = nameBandBottom + dividerH

    val detailsBaseline = y + detailsH * 0.72f
    val line1 = "GRADE: ${grade.ifBlank { "-" }}   SUBJ: ${subject.ifBlank { "-" }}   EXAM: ${examTitle.ifBlank { "-" }}"
    canvas.drawText(line1, originX + padX, detailsBaseline, detailPaint)
    y += detailsH
    canvas.drawLine(originX, y, originX + sliceWidth, y, paintThin)
    y += dividerH

    val gridHeaderTop = y
    val gridHeaderBottom = gridHeaderTop + gridHeaderH

    val gutterW = mm(4f)
    val halfWidth = (sliceWidth - gutterW) / 2f
    val noColW = mm(9f)
    val bracketColW = (halfWidth - noColW) / 4f

    for (half in 0..1) {
        val halfStartX = originX + (halfWidth + gutterW) * half
        val hdrBaseline = gridHeaderTop + gridHeaderH * 0.72f
        drawCenteredText(canvas, "NO", halfStartX, halfStartX + noColW, hdrBaseline, gridHeaderPaint)
        val letters = listOf("A", "B", "C", "D")
        for (i in 0..3) {
            val xStart = halfStartX + noColW + bracketColW * i
            drawCenteredText(canvas, letters[i], xStart, xStart + bracketColW, hdrBaseline, gridHeaderPaint)
        }
    }
    canvas.drawLine(originX, gridHeaderBottom, originX + sliceWidth, gridHeaderBottom, paintThin)

    val gridTop = gridHeaderBottom
    val gridBottomLimit = originY + sliceHeight - instrH - bottomPad

    val dividerX = originX + halfWidth + gutterW / 2f
    canvas.drawLine(dividerX, gridHeaderTop, dividerX, gridBottomLimit, paintThin)

    for (half in 0..1) {
        val halfStartX = originX + (halfWidth + gutterW) * half
        val start = if (half == 0) 1 else 26
        val count = if (half == 0) leftCount else rightCount
        for (i in 0 until count) {
            val q = start + i
            val rowTop = gridTop + rowH * i
            val rowBottom = rowTop + rowH
            val baseline = rowTop + rowH * 0.70f

            val numText = q.toString()
            val numWidth = rowNumPaint.measureText(numText)
            canvas.drawText(numText, halfStartX + noColW - mm(1.5f) - numWidth, baseline, rowNumPaint)

            for (bi in 0..3) {
                val xStart = halfStartX + noColW + bracketColW * bi
                val xCenter = xStart + bracketColW / 2f
                val bracketText = "[   ]"
                val bw = bracketPaint.measureText(bracketText)
                canvas.drawText(bracketText, xCenter - bw / 2f, baseline, bracketPaint)
            }

            canvas.drawLine(halfStartX, rowBottom, halfStartX + halfWidth, rowBottom, paintThin)
        }
        canvas.drawLine(halfStartX + noColW, gridTop, halfStartX + noColW, gridBottomLimit, paintThin)
        for (bi in 1..3) {
            val x = halfStartX + noColW + bracketColW * bi
            canvas.drawLine(x, gridTop, x, gridBottomLimit, paintThin)
        }
    }

    val instrTop = originY + sliceHeight - instrH - bottomPad
    canvas.drawLine(originX, instrTop, originX + sliceWidth, instrTop, paintThin)
    val instrBaseline = instrTop + instrH * 0.72f
    canvas.drawText("Write A/B/C/D inside the bracket of your choice.", originX + padX, instrBaseline, instrPaint)
}

private fun wrapNameToWidth(text: String, availWidth: Float, paint: Paint, stretchFactor: Float): List<String> {
    if (text.isBlank()) return listOf("")
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var current = ""
    for (word in words) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        val w = paint.measureText(candidate) * stretchFactor
        if (w <= availWidth || current.isEmpty()) {
            current = candidate
        } else {
            lines.add(current)
            current = word
            if (lines.size >= 2) break
        }
    }
    if (current.isNotEmpty() && lines.size < 2) lines.add(current)
    return lines
}

private fun drawCenteredText(canvas: Canvas, text: String, left: Float, right: Float, baselineY: Float, paint: Paint) {
    val w = paint.measureText(text)
    val centerX = (left + right) / 2f
    canvas.drawText(text, centerX - w / 2f, baselineY, paint)
}

// ===== PDF MARKED SHEETS REPORT =====
fun buildMarkedSheetsPdf(
    context: Context,
    sheets: List<MarkedAnswerSheetData>,
    examTitle: String,
    grade: String,
    subject: String,
    filenameBase: String
): String? {
    if (sheets.isEmpty()) return null
    val pdf = PdfDocument()
    val pageWidthPt = mm(297f)
    val pageHeightPt = mm(210f)
    val marginPt = mm(3f)
    val cutGapPt = mm(4f)
    val sliceWidthPt = (pageWidthPt - 2f * marginPt - cutGapPt) / 2f
    val sliceHeightPt = pageHeightPt - 2f * marginPt

    val paintBorder = Paint().apply {
        color = AndroidColor.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        isAntiAlias = true
    }
    val paintThin = Paint().apply {
        color = AndroidColor.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 0.3f
        isAntiAlias = true
    }
    val paintDashed = Paint().apply {
        color = AndroidColor.parseColor("#888888")
        style = Paint.Style.STROKE
        strokeWidth = 0.5f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 3f), 0f)
        isAntiAlias = true
    }

    val chunks = sheets.chunked(2)
    for (chunk in chunks) {
        val pageInfo = PdfDocument.PageInfo.Builder(
            pageWidthPt.toInt(),
            pageHeightPt.toInt(),
            pdf.pages.size + 1
        ).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        drawMarkedSlice(canvas, marginPt, marginPt, sliceWidthPt, sliceHeightPt,
            chunk.getOrNull(0), examTitle, grade, subject, paintBorder, paintThin)

        val cutX = marginPt + sliceWidthPt + cutGapPt / 2f
        canvas.drawLine(cutX, marginPt, cutX, marginPt + sliceHeightPt, paintDashed)

        drawMarkedSlice(canvas, marginPt + sliceWidthPt + cutGapPt, marginPt, sliceWidthPt, sliceHeightPt,
            chunk.getOrNull(1), examTitle, grade, subject, paintBorder, paintThin)

        pdf.finishPage(page)
    }

    return try {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val safeBase = filenameBase.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        val file = File(dir, "${safeBase}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { out -> pdf.writeTo(out) }
        pdf.close()
        file.absolutePath
    } catch (e: Exception) {
        pdf.close()
        null
    }
}

private fun drawMarkedSlice(
    canvas: Canvas,
    originX: Float,
    originY: Float,
    sliceWidth: Float,
    sliceHeight: Float,
    sheet: MarkedAnswerSheetData?,
    examTitle: String,
    grade: String,
    subject: String,
    paintBorder: Paint,
    paintThin: Paint
) {
    canvas.drawRect(originX, originY, originX + sliceWidth, originY + sliceHeight, paintBorder)
    if (sheet == null) return

    val padX = mm(4f)
    val yTopPad = mm(2f)
    val yMetaBaseline = mm(5f)
    val yMetaDivider = mm(6.5f)
    val yNameBaseline = mm(10.5f)
    val yNameDivider = mm(12f)
    val yScoreBaseline = mm(15.5f)
    val yScoreDivider = mm(17.5f)
    val yTextTop = mm(18f)
    val yTextBottom = mm(119f)
    val yTextDivider = mm(119.5f)
    val yImageTop = mm(120f)
    val yImageBottom = mm(196f)

    val metaPaint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = mm(2.8f)
        isAntiAlias = true
    }
    val namePaint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = mm(4.2f)
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    val scorePaint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = mm(3.5f)
        isAntiAlias = true
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    val bodyPaint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = mm(2.1f)
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
    }

    val metaLine = "${if (examTitle.isBlank()) "WaveUnits" else examTitle}   •   ${grade.ifBlank { "-" }}   •   ${subject.ifBlank { "-" }}"
    val metaW = metaPaint.measureText(metaLine)
    canvas.drawText(metaLine, originX + (sliceWidth - metaW) / 2f, originY + yMetaBaseline, metaPaint)
    canvas.drawLine(originX, originY + yMetaDivider, originX + sliceWidth, originY + yMetaDivider, paintThin)

    val nameText = sheet.studentName.uppercase()
    val nameAvail = sliceWidth - 2f * padX
    var nameToDraw = nameText
    if (namePaint.measureText(nameText) > nameAvail) {
        var cut = nameText
        while (cut.length > 3 && namePaint.measureText("$cut...") > nameAvail) {
            cut = cut.dropLast(1)
        }
        nameToDraw = "$cut..."
    }
    canvas.drawText(nameToDraw, originX + padX, originY + yNameBaseline, namePaint)
    canvas.drawLine(originX, originY + yNameDivider, originX + sliceWidth, originY + yNameDivider, paintThin)

    val scoreLine = "Score: ${sheet.score}/${sheet.total}    Percentage: ${"%.1f".format(sheet.percentage)}%    Grade: ${getKenyanGrade(sheet.percentage)}"
    canvas.drawText(scoreLine, originX + padX, originY + yScoreBaseline, scorePaint)
    canvas.drawLine(originX, originY + yScoreDivider, originX + sliceWidth, originY + yScoreDivider, paintThin)

    val lineH = bodyPaint.textSize * 1.15f
    val textAreaH = yTextBottom - yTextTop
    val maxLines = (textAreaH / lineH).toInt().coerceAtLeast(1)
    val lines = sheet.markedText.split("\n")
    val truncated = lines.size > maxLines
    val shownCount = if (truncated) maxLines - 1 else lines.size
    var lineY = originY + yTextTop + bodyPaint.textSize
    for (i in 0 until shownCount) {
        canvas.drawText(lines[i], originX + padX, lineY, bodyPaint)
        lineY += lineH
    }
    if (truncated) {
        val remaining = lines.size - shownCount
        canvas.drawText("… $remaining more lines truncated", originX + padX, lineY, bodyPaint)
    }
    canvas.drawLine(originX, originY + yTextDivider, originX + sliceWidth, originY + yTextDivider, paintThin)

    val bmp = decodeBase64(sheet.image)
    if (bmp != null) {
        val imgAreaH = yImageBottom - yImageTop
        val availW = sliceWidth - 2f * padX
        val availH = imgAreaH

        val srcW = bmp.width.toFloat()
        val srcH = bmp.height.toFloat()
        val aspect = srcW / srcH

        var drawW = availW
        var drawH = drawW / aspect
        if (drawH > availH) {
            drawH = availH
            drawW = drawH * aspect
        }

        val drawX = originX + (sliceWidth - drawW) / 2f
        val drawY = originY + yImageTop
        val dst = android.graphics.RectF(drawX, drawY, drawX + drawW, drawY + drawH)
        canvas.drawBitmap(bmp, null, dst, Paint().apply { isFilterBitmap = true })
    }
}

// ===== PDF PREVIEW RENDER =====
fun renderPdfFirstPage(pdfPath: String): Bitmap? {
    return try {
        val file = File(pdfPath)
        if (!file.exists()) return null
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        if (renderer.pageCount <= 0) { renderer.close(); pfd.close(); return null }
        val page = renderer.openPage(0)
        val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(AndroidColor.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        pfd.close()
        bmp
    } catch (e: Exception) { null }
}

// ===== AI FUNCTIONS =====
private suspend fun transcribeAnswerSheet(context: Context, uri: Uri): String {
    return withContext(Dispatchers.IO) {
        try {
            val isr = context.contentResolver.openInputStream(uri) ?: return@withContext "ERR: cannot open image"
            val bmp = BitmapFactory.decodeStream(isr)
            isr.close()
            val scaled = Bitmap.createScaledBitmap(bmp, 1000, 1400, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            val prompt = """
                FIRST PRIORITY: Read the student's name.

                Every sheet has a prominent BOLD ALL CAPS name near the top,
                on a line that starts with "NAME:". This is the largest text
                on the page. Read it first and copy it into the NAME field
                below exactly as printed.

                This is a scanned student answer sheet. Report what is
                actually on the page. Do not invent anything that is not
                there.

                STEP 1 — Output the student header block exactly:

                ---STUDENT---
                NAME: <name as printed, exactly>
                GRADE: <grade as printed>
                SUBJ: <subject as printed>
                ---SHEET---

                If there is no printed NAME line but there IS a handwritten
                name at the top, output it after NAME: as your best reading.

                If there is no name at all, output:

                ---STUDENT---
                NAME:
                ---SHEET---

                STEP 2 — Transcribe the answer section below the header.
                Preserve whatever format the student used.

                FORMAT A — Printed grid with brackets. Each row:
                  1 | [ ] [B] [ ] [ ]
                Rules:
                  - Student writes exactly ONE letter in one bracket per row.
                  - If two brackets have letters, take the LEFTMOST one.
                  - If a bracket has a mark but the letter is unreadable,
                    print the column header letter inside that bracket.
                  - If a row has no letters, keep all four empty: [ ] [ ] [ ] [ ]
                  - Preserve row order.

                FORMAT B — Plain handwritten list:
                  1B  2C  3B  4A
                Output:
                  1 | B
                  2 | C
                  3 | B
                Rules:
                  - Only the letter the student wrote. No added brackets.
                  - If a number has no letter, output it blank: 14 |
                  - Preserve number order.

                FORMAT C — Bubble sheet. Output the letter of the filled oval.

                FORMAT D — Any other layout. Infer the answer per question
                number and output: <number> | <letter>
                Do not add brackets unless the page has brackets.

                STEP 3 — Output everything below the ---SHEET--- line in
                the format that matches what is on the page.
                Never invent brackets. Never invent answers. Never summarise.
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

private fun extractPrintedStudentName(transcription: String): String? {
    if (transcription.isBlank()) return null
    val lines = transcription.lines()
    var insideHeader = false
    for (line in lines) {
        val t = line.trim()
        if (t.equals("---STUDENT---", ignoreCase = true)) { insideHeader = true; continue }
        if (t.equals("---SHEET---", ignoreCase = true)) { insideHeader = false; continue }
        if (insideHeader && t.startsWith("NAME:", ignoreCase = true)) {
            val value = t.substringAfter(":").trim()
            if (value.isNotBlank() && !value.contains("____")) return value
            return null
        }
    }
    return null
}

private suspend fun gradeWithAI(
    printout: String,
    aiAnswerSheetText: String,
    studentName: String
): StudentResult? {
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        val prompt = """
            TASK: Grade a student's answer sheet.

            The printout below is the student's answers. It may be in one
            of two formats:

            FORMAT A — grid with brackets:
              1 | [ ] [B] [ ] [ ]
              2 | [ ] [ ] [C] [ ]
            The student writes exactly ONE letter inside one bracket per
            row. The letter inside a bracket is the answer.
            If TWO brackets have letters, take the LEFTMOST one.
            If all four brackets are empty, the answer is blank -> WRONG.

            FORMAT B — simple list:
              1 | B
              2 | C
            The letter after the number is the answer.
            Missing number or blank after the pipe -> WRONG.

            In either format, compare each student answer to the correct
            answer from the ANSWER KEY below by question number.
            Blank or missing = WRONG.

            ============ PART 1 - HUMAN-READABLE SHEET ============
            Write one line per graded question:

            Q1: Student chose A, correct answer is B -> WRONG
            Q2: Student chose C, correct answer is C -> CORRECT
            Q3: Student chose nothing, correct answer is D -> WRONG

            Then:
            SCORE: <number correct> / <total>
            PERCENTAGE: <number>%
            GRADE: <letter>

            ============ PART 2 - MACHINE-READABLE BLOCK ============
            After the score, print this line on its own:

            ---ANALYTICS---

            Then ONE line per question, EXACTLY this format:

            Q<number>|<studentLetter>|<correctLetter>|<C or W>|<topic>

            Topic = the strand and sub-strand copied from the ANSWER KEY
            for that question, in the form "Strand -> Sub-strand".

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
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
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

private suspend fun generateAIAnswerSheetWithTopics(questions: List<QuestionData>): String {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
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
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
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
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
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

    var treeGrades by remember { mutableStateOf<List<String>>(emptyList()) }
    var treeSubjects by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    var selectedGrade by remember { mutableStateOf<String?>(null) }
    var selectedSubject by remember { mutableStateOf<String?>(null) }

    var showAddGradeDialog by remember { mutableStateOf(false) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var showNewExamDialog by remember { mutableStateOf(false) }

    var pendingGradeDelete by remember { mutableStateOf<String?>(null) }
    var pendingExamDelete by remember { mutableStateOf<ExamProject?>(null) }
    var pendingSheetRename by remember { mutableStateOf<StudentAnswerSheetData?>(null) }

    var initialLoadComplete by remember { mutableStateOf(false) }

    var scanPhase by remember { mutableStateOf("") }
    var progressText by remember { mutableStateOf("") }
    var extractedQuestions by remember { mutableStateOf<List<QuestionData>>(emptyList()) }
    var aiAnswerSheet by remember { mutableStateOf("") }
    var simplifiedAnswerKey by remember { mutableStateOf("") }
    var questionPaperText by remember { mutableStateOf("") }
    var allQuestionPaperTexts by remember { mutableStateOf<List<String>>(emptyList()) }
    var allQuestionPaperImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var answerKey by remember { mutableStateOf("") }
    var isGrading by remember { mutableStateOf(false) }

    var savedRosters by remember { mutableStateOf<List<RosterTemplate>>(emptyList()) }
    var rosterInput by remember { mutableStateOf("") }
    var rosterTemplateName by remember { mutableStateOf("") }
    var editingRosterId by remember { mutableStateOf<String?>(null) }
    var rosterNames by remember { mutableStateOf<List<String>>(emptyList()) }

    var genSchool by remember { mutableStateOf("") }
    var genGrade by remember { mutableStateOf("") }
    var genSubject by remember { mutableStateOf("") }
    var genExamTitle by remember { mutableStateOf("") }
    var genTerm by remember { mutableStateOf("") }
    var genDate by remember {
        mutableStateOf(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()))
    }
    var genQuestionCount by remember { mutableStateOf("50") }
    var lastGeneratedPdfPath by remember { mutableStateOf("") }
    var pdfPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var manualStudentName by remember { mutableStateOf("") }
    var printableReportText by remember { mutableStateOf("") }
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
    var selectedPortfolioStudent by remember { mutableStateOf<ClassPathStudentPortfolio?>(null) }

    var lastSeenPrintedName by remember { mutableStateOf<String?>(null) }

    var isEditingAnswerKey by remember { mutableStateOf(false) }
    var editableKeyText by remember { mutableStateOf("") }
    var showRegradePromptAfterSave by remember { mutableStateOf(false) }
    var showRegradeConfirm by remember { mutableStateOf(false) }

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

    fun clearAllState() {
        projects = emptyList()
        treeGrades = emptyList()
        treeSubjects = emptyMap()
        selectedGrade = null
        selectedSubject = null
        currentProject = null
        currentProjectId = ""
        currentProjectTitle = ""
        collectedStudentAnswerSheets = emptyList()
        markedAnswerSheets = emptyList()
        examAnalytics = null
        seriesAnalytics = null
        selectedPortfolioStudent = null
        allResults = emptyList()
        initialLoadComplete = false
        lastSeenPrintedName = null
        isEditingAnswerKey = false
        editableKeyText = ""
        currentView = "home"
    }

    fun loadTreeAndProjects() {
        scope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            initialLoadComplete = false
            try {
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
            try {
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
            initialLoadComplete = true
        }
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

    fun deleteGradeAndItsExams(grade: String) {
        scope.launch {
            try {
                val examsToDelete = projects.filter { it.grade == grade }
                for (exam in examsToDelete) {
                    try {
                        val ms = db.collection("exams").document(exam.id).collection("markedSheets").get().await()
                        for (d in ms.documents) d.reference.delete().await()
                        val ar = db.collection("exams").document(exam.id).collection("aiResponses").get().await()
                        for (d in ar.documents) d.reference.delete().await()
                        db.collection("exams").document(exam.id).delete().await()
                        val rs = db.collection("results").whereEqualTo("examId", exam.id).get().await()
                        for (d in rs.documents) d.reference.delete().await()
                    } catch (_: Exception) {}
                }
                deleteGradeFromTree(grade)
                loadProjects()
                Toast.makeText(context, "Deleted $grade and ${examsToDelete.size} exam(s)", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Delete error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun deleteExamCascade(exam: ExamProject) {
        scope.launch {
            try {
                try {
                    val ms = db.collection("exams").document(exam.id).collection("markedSheets").get().await()
                    for (d in ms.documents) d.reference.delete().await()
                } catch (_: Exception) {}
                try {
                    val ar = db.collection("exams").document(exam.id).collection("aiResponses").get().await()
                    for (d in ar.documents) d.reference.delete().await()
                } catch (_: Exception) {}
                try {
                    val rs = db.collection("results").whereEqualTo("examId", exam.id).get().await()
                    for (d in rs.documents) d.reference.delete().await()
                } catch (_: Exception) {}
                db.collection("exams").document(exam.id).delete().await()
                sectionState = SectionViewState()
                currentProject = null
                currentProjectId = ""
                currentProjectTitle = ""
                collectedStudentAnswerSheets = emptyList()
                markedAnswerSheets = emptyList()
                examAnalytics = null
                allResults = emptyList()
                loadProjects()
                currentView = "subject"
                Toast.makeText(context, "Exam deleted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Delete error: ${e.message}", Toast.LENGTH_LONG).show()
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
                    if (answerKey.isNotBlank()) extractedQuestions = parseAnswerKeyToQuestions(answerKey)

                    val sheetsData = (doc.get("studentAnswerSheets") as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()
                    collectedStudentAnswerSheets = sheetsData.map { sheet ->
                        StudentAnswerSheetData(
                            studentName = sheet["studentName"] as? String ?: "",
                            extractedText = sheet["extractedText"] as? String ?: "",
                            image = sheet["image"] as? String ?: "",
                            matched = sheet["matched"] as? Boolean ?: true,
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
                    "createdAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                ))
                Toast.makeText(context, "Saved '$templateName'", Toast.LENGTH_SHORT).show()
                loadSavedRosters()
            } catch (e: Exception) {
                Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun updateRosterTemplate(templateId: String, newName: String, newNames: List<String>) {
        if (newName.isBlank() || newNames.isEmpty()) {
            Toast.makeText(context, "Enter name and names", Toast.LENGTH_SHORT).show(); return
        }
        scope.launch {
            try {
                db.collection("rosterTemplates").document(templateId).update(mapOf(
                    "name" to newName,
                    "names" to newNames,
                    "updatedAt" to System.currentTimeMillis()
                )).await()
                Toast.makeText(context, "Updated '$newName'", Toast.LENGTH_SHORT).show()
                loadSavedRosters()
            } catch (e: Exception) {
                Toast.makeText(context, "Update error: ${e.message}", Toast.LENGTH_SHORT).show()
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

    fun shareFile(path: String, mime: String) {
        try {
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show(); return
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val i = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(i, "Share"))
        } catch (e: Exception) {
            Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    .sortedBy { it.createdAtMillis }

                if (seriesExams.isEmpty()) {
                    seriesAnalytics = SeriesAnalyticsBundle(
                        grade, subjectKey, emptyList(), 0.0, 0.0,
                        emptyList(), emptyList(), emptyList(), 0, emptyList()
                    )
                    return@launch
                }

                val examIds = seriesExams.map { it.id }
                val perExamResults = mutableMapOf<String, MutableList<StudentResult>>()

                val chunks = examIds.chunked(30)
                for (chunk in chunks) {
                    val q = db.collection("results").whereIn("examId", chunk).get().await()
                    for (doc in q.documents) {
                        val examId = doc.getString("examId") ?: continue
                        val questionsData = doc.get("questions") as? List<Map<String, Any>> ?: emptyList()
                        val r = StudentResult(
                            studentName = doc.getString("studentName") ?: "Unknown",
                            score = (doc.get("score") as? Number)?.toInt() ?: 0,
                            totalMarks = (doc.get("totalMarks") as? Number)?.toInt() ?: 0,
                            percentage = (doc.get("percentage") as? Number)?.toDouble() ?: 0.0,
                            questions = questionsData.map { qq ->
                                QuestionResultData(
                                    (qq["questionNumber"] as? Number)?.toInt() ?: 0,
                                    qq["topic"] as? String ?: "General",
                                    qq["studentAnswer"] as? String ?: "",
                                    qq["correctAnswer"] as? String ?: "",
                                    qq["isCorrect"] as? Boolean ?: false
                                )
                            }
                        )
                        perExamResults.getOrPut(examId) { mutableListOf() }.add(r)
                    }
                }

                val examPoints = mutableListOf<ExamPoint>()
                for (exam in seriesExams) {
                    val results = perExamResults[exam.id] ?: emptyList()
                    val avg = if (results.isNotEmpty()) results.map { it.percentage }.average() else 0.0
                    examPoints.add(ExamPoint(exam.id, exam.title, exam.createdAtMillis, avg, results.size))
                }

                val classAverage = if (examPoints.isNotEmpty()) examPoints.map { it.classAverage }.average() else 0.0
                val classTrend = if (examPoints.size >= 2) examPoints.last().classAverage - examPoints.first().classAverage else 0.0

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

                val allStudentNames = mutableSetOf<String>()
                for (exam in seriesExams) {
                    perExamResults[exam.id]?.forEach { r ->
                        if (r.studentName.isNotBlank() && r.studentName != "Unknown") {
                            allStudentNames.add(r.studentName)
                        }
                    }
                }

                val rawPortfolios = mutableListOf<ClassPathStudentPortfolio>()
                for (studentName in allStudentNames) {
                    val examScores = mutableListOf<Pair<String, Double>>()
                    val percentages = mutableListOf<Double>()
                    val topicScoresByExam = mutableMapOf<String, MutableList<Double>>()
                    val topicPassed = mutableMapOf<String, Int>()
                    val topicFailed = mutableMapOf<String, Int>()
                    val topicAppeared = mutableMapOf<String, Int>()

                    for (exam in seriesExams) {
                        val results = perExamResults[exam.id] ?: continue
                        val r = results.find { it.studentName == studentName } ?: continue
                        examScores.add(Pair(exam.title, r.percentage))
                        percentages.add(r.percentage)

                        val perTopicForExam = mutableMapOf<String, MutableList<Boolean>>()
                        for (q in r.questions) {
                            if (q.questionNumber <= 0) continue
                            if (q.topic.isBlank()) continue
                            perTopicForExam.getOrPut(q.topic) { mutableListOf() }.add(q.isCorrect)
                        }
                        for ((topic, correctList) in perTopicForExam) {
                            val total = correctList.size
                            val correct = correctList.count { it }
                            val pctOnTopic = if (total > 0) correct * 100.0 / total else 0.0
                            topicScoresByExam.getOrPut(topic) { mutableListOf() }.add(pctOnTopic)
                            topicPassed[topic] = (topicPassed[topic] ?: 0) + correct
                            topicFailed[topic] = (topicFailed[topic] ?: 0) + (total - correct)
                            topicAppeared[topic] = (topicAppeared[topic] ?: 0) + 1
                        }
                    }

                    if (percentages.isEmpty()) continue

                    val combinedAverage = percentages.average()
                    val overallGrade = getKenyanGrade(combinedAverage)
                    val examsTaken = percentages.size

                    val highestExam = examScores.maxByOrNull { it.second } ?: Pair("", 0.0)
                    val lowestExam = examScores.minByOrNull { it.second } ?: Pair("", 0.0)

                    val trendChange = if (percentages.size >= 2) percentages.last() - percentages.first() else 0.0
                    val trend = when {
                        percentages.size < 2 -> "insufficient data"
                        trendChange > 5.0 -> "improving"
                        trendChange < -5.0 -> "declining"
                        else -> "steady"
                    }

                    val topicStats = topicScoresByExam.map { (topic, scores) ->
                        TopicStat(
                            topic = topic,
                            passedCount = topicPassed[topic] ?: 0,
                            failedCount = topicFailed[topic] ?: 0,
                            examsAppeared = topicAppeared[topic] ?: 0,
                            averageScore = if (scores.isNotEmpty()) scores.average() else 0.0
                        )
                    }

                    val mostFailed = topicStats
                        .filter { it.failedCount > 0 }
                        .sortedWith(compareByDescending<TopicStat> { it.failedCount }.thenBy { it.averageScore })
                        .take(5)

                    val mostPassed = topicStats
                        .filter { it.passedCount > 0 }
                        .sortedWith(compareByDescending<TopicStat> { it.passedCount }.thenByDescending { it.averageScore })
                        .take(5)

                    rawPortfolios.add(ClassPathStudentPortfolio(
                        studentName = studentName,
                        combinedAverage = combinedAverage,
                        overallGrade = overallGrade,
                        rank = 0,
                        examsTaken = examsTaken,
                        highestExam = highestExam,
                        lowestExam = lowestExam,
                        trend = trend,
                        trendChange = trendChange,
                        mostFailedTopics = mostFailed,
                        mostPassedTopics = mostPassed,
                        examScores = examScores
                    ))
                }

                val rankedPortfolios = rawPortfolios
                    .sortedByDescending { it.combinedAverage }
                    .mapIndexed { i, p -> p.copy(rank = i + 1) }

                seriesAnalytics = SeriesAnalyticsBundle(
                    grade = grade,
                    subjectKey = subjectKey,
                    examPoints = examPoints,
                    classAverage = classAverage,
                    classTrend = classTrend,
                    topicSeries = topicSeries,
                    studentSeries = studentSeries,
                    clusters = clusters,
                    examCount = examPoints.size,
                    studentPortfolios = rankedPortfolios
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
                    it.studentName.isNotBlank() &&
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
                        "gradedBy" to "ai-printed-header",
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

    fun regradeCollectedSheets() {
        scope.launch {
            try {
                isGrading = true
                progressText = "Clearing old results..."
                try {
                    val ms = db.collection("exams").document(currentProjectId).collection("markedSheets").get().await()
                    for (d in ms.documents) d.reference.delete().await()
                } catch (_: Exception) {}
                try {
                    val rs = db.collection("results").whereEqualTo("examId", currentProjectId).get().await()
                    for (d in rs.documents) d.reference.delete().await()
                } catch (_: Exception) {}
                markedAnswerSheets = emptyList()
                examAnalytics = null
                isGrading = false
                Toast.makeText(context, "Old results cleared. Re-grading...", Toast.LENGTH_SHORT).show()
                gradeCollectedSheets()
            } catch (e: Exception) {
                Toast.makeText(context, "Re-grade error: ${e.message}", Toast.LENGTH_LONG).show()
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

    fun saveEditedAnswerKey(newKey: String) {
        scope.launch {
            try {
                db.collection("exams").document(currentProjectId).update(mapOf(
                    "simplifiedAnswerKey" to newKey,
                    "answerKey" to newKey
                )).await()
                simplifiedAnswerKey = newKey
                answerKey = newKey
                extractedQuestions = parseAnswerKeyToQuestions(newKey)
                Toast.makeText(context, "Answer key updated", Toast.LENGTH_SHORT).show()
                showRegradePromptAfterSave = true
            } catch (e: Exception) {
                Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_SHORT).show()
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

    fun persistScannedSheet(sheet: StudentAnswerSheetData) {
        val newList = collectedStudentAnswerSheets + sheet
        collectedStudentAnswerSheets = newList
        val sheetsData = newList.map { s ->
            mapOf(
                "studentName" to s.studentName,
                "extractedText" to s.extractedText,
                "image" to s.image,
                "matched" to s.matched
            )
        }
        scope.launch {
            try {
                db.collection("exams").document(currentProjectId).update(mapOf(
                    "studentAnswerSheets" to sheetsData,
                    "status" to "collected"
                ))
            } catch (_: Exception) {}
        }
    }

    fun renameSheet(oldName: String, newName: String) {
        val newList = collectedStudentAnswerSheets.map {
            if (it.studentName == oldName && !it.matched) it.copy(studentName = newName, matched = true) else it
        }
        collectedStudentAnswerSheets = newList
        val sheetsData = newList.map { s ->
            mapOf(
                "studentName" to s.studentName,
                "extractedText" to s.extractedText,
                "image" to s.image,
                "matched" to s.matched
            )
        }
        scope.launch {
            try {
                db.collection("exams").document(currentProjectId).update(mapOf(
                    "studentAnswerSheets" to sheetsData
                ))
                Toast.makeText(context, "Renamed to $newName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Rename error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
                                "extractedTextSaved" to true,
                                "answerSheetGenerated" to true,
                                "markingMode" to "ai"))
                            progressText = "Extracted ${allQs.size}. Total: ${newQs.size}."
                            isExtracting = false
                            loadProjects(); loadExamData(currentProjectId); scanPhase = ""
                        }
                        "answer_sheets" -> {
                            isExtracting = true
                            for ((idx, uri) in uris.withIndex()) {
                                progressText = "Transcribing sheet ${idx + 1} of ${uris.size}..."
                                val printout = transcribeAnswerSheet(context, uri)
                                val imgB64 = imageToBase64(context, uri)
                                val printedName = extractPrintedStudentName(printout)
                                val resolvedName: String
                                val matched: Boolean
                                if (printedName != null && printedName.isNotBlank()) {
                                    resolvedName = printedName
                                    matched = true
                                    lastSeenPrintedName = printedName
                                } else if (lastSeenPrintedName != null) {
                                    resolvedName = lastSeenPrintedName!!
                                    matched = true
                                } else {
                                    resolvedName = "Unknown"
                                    matched = false
                                }
                                if (printout.isBlank() || printout.startsWith("ERR")) {
                                    Toast.makeText(context, "Transcribe failed: ${printout.take(200)}", Toast.LENGTH_LONG).show()
                                }
                                persistScannedSheet(
                                    StudentAnswerSheetData(
                                        studentName = resolvedName,
                                        extractedText = printout,
                                        image = imgB64,
                                        matched = matched
                                    )
                                )
                            }
                            isExtracting = false
                            progressText = "Transcribed ${uris.size} sheet(s)."
                            scanPhase = ""
                            val unmatched = collectedStudentAnswerSheets.filter { !it.matched }
                            if (unmatched.isNotEmpty()) {
                                pendingSheetRename = unmatched.first()
                            }
                        }
                    }
                }
            }
        }
    }

    fun launchScan(phase: String) {
        scanPhase = phase
        if (phase == "answer_sheets") {
            lastSeenPrintedName = null
        }
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

    LaunchedEffect(isLoggedIn, auth.currentUser?.uid) {
        if (isLoggedIn) {
            clearAllState()
            loadTreeAndProjects()
            loadSavedRosters()
        }
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
                    Text(gradeForDialog, color = Color(0xFF60a5fa), fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                    Text("${selectedGrade ?: ""}  -  ${selectedSubject ?: ""}",
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

    val gradeToConfirm = pendingGradeDelete
    if (gradeToConfirm != null) {
        val examCount = projects.count { it.grade == gradeToConfirm }
        val subjCount = treeSubjects[gradeToConfirm]?.size ?: 0
        AlertDialog(
            onDismissRequest = { pendingGradeDelete = null },
            title = { Text("Delete $gradeToConfirm?") },
            text = {
                Text(
                    if (examCount == 0)
                        "This removes the grade and $subjCount subject(s) from your tree."
                    else
                        "This will permanently delete the grade, $subjCount subject(s), and $examCount exam(s) with all their results. This cannot be undone.",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val g = gradeToConfirm
                        pendingGradeDelete = null
                        deleteGradeAndItsExams(g)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFef4444))
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingGradeDelete = null }) { Text("Cancel") }
            }
        )
    }

    val examToConfirm = pendingExamDelete
    if (examToConfirm != null) {
        AlertDialog(
            onDismissRequest = { pendingExamDelete = null },
            title = { Text("Delete exam?") },
            text = {
                Text(
                    "\"${examToConfirm.title}\" and all its marked sheets and results will be permanently deleted. This cannot be undone.",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val e = examToConfirm
                        pendingExamDelete = null
                        deleteExamCascade(e)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFef4444))
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingExamDelete = null }) { Text("Cancel") }
            }
        )
    }

    val renameTarget = pendingSheetRename
    if (renameTarget != null) {
        var newName by remember(renameTarget) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingSheetRename = null },
            title = { Text("Unreadable name") },
            text = {
                Column {
                    Text("This sheet had no readable name. Type the student's name:",
                        color = Color.White, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Student Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val n = newName.trim()
                        if (n.isNotBlank()) {
                            renameSheet(renameTarget.studentName, n)
                            pendingSheetRename = null
                            val remaining = collectedStudentAnswerSheets.filter { !it.matched }
                            if (remaining.isNotEmpty()) pendingSheetRename = remaining.first()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSheetRename = null }) { Text("Skip") }
            }
        )
    }

    if (showRegradePromptAfterSave) {
        AlertDialog(
            onDismissRequest = { showRegradePromptAfterSave = false },
            title = { Text("Answer key updated") },
            text = {
                Text(
                    "Re-grade all collected sheets with the new answer key now?",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRegradePromptAfterSave = false
                        if (collectedStudentAnswerSheets.isEmpty()) {
                            Toast.makeText(context, "No collected sheets to re-grade", Toast.LENGTH_SHORT).show()
                        } else {
                            showRegradeConfirm = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                ) { Text("Re-grade now") }
            },
            dismissButton = {
                TextButton(onClick = { showRegradePromptAfterSave = false }) { Text("Not now") }
            }
        )
    }

    if (showRegradeConfirm) {
        AlertDialog(
            onDismissRequest = { showRegradeConfirm = false },
            title = { Text("Re-grade this exam?") },
            text = {
                Text(
                    "This will delete ${markedAnswerSheets.size} marked sheet(s) and all current results for this exam, then re-grade ${collectedStudentAnswerSheets.size} collected sheet(s) with the current answer key. Continue?",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRegradeConfirm = false
                        regradeCollectedSheets()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                ) { Text("Re-grade") }
            },
            dismissButton = {
                TextButton(onClick = { showRegradeConfirm = false }) { Text("Cancel") }
            }
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
                    Text("- or -", color = Color(0xFF94a3b8), fontSize = 12.sp)
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
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1e293b))
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    when (currentView) {
                        "home" -> {
                            if (!initialLoadComplete) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(color = Color(0xFF60a5fa))
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Loading your classes...", color = Color(0xFF94a3b8), fontSize = 14.sp)
                                    }
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("My Classes", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Button(
                                        onClick = {
                                            loadTreeAndProjects()
                                            Toast.makeText(context, "Reloading...", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) { Text("Reload", fontSize = 12.sp, maxLines = 1) }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = { showAddGradeDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) { Text("+ Grade", fontSize = 12.sp, maxLines = 1) }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                try { googleSignInClient.signOut().await() } catch (_: Exception) {}
                                                auth.signOut()
                                                isLoggedIn = false
                                                saveLoginState(false)
                                                clearAllState()
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94a3b8)),
                                        border = BorderStroke(1.dp, Color(0xFF475569)),
                                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                                    ) { Text("Log out", fontSize = 13.sp, maxLines = 1) }
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                val effectiveGrades = if (treeGrades.isNotEmpty()) treeGrades
                                                      else projects.map { it.grade }.filter { it.isNotBlank() }.distinct().sorted()

                                if (effectiveGrades.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Welcome to WaveUnits", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Add the grades and subjects you teach.", color = Color(0xFF94a3b8), fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(32.dp))
                                        Button(
                                            onClick = { showAddGradeDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981)),
                                            modifier = Modifier.fillMaxWidth(0.7f)
                                        ) { Text("+ Add Subjects I Teach") }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                loadTreeAndProjects()
                                                Toast.makeText(context, "Loading...", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa)),
                                            modifier = Modifier.fillMaxWidth(0.7f)
                                        ) { Text("Load My Classes") }
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(effectiveGrades) { grade ->
                                            val treeSubs = treeSubjects[grade] ?: emptyList()
                                            val examSubs = projects.filter { it.grade == grade }.map { it.subjectKey }.filter { it.isNotBlank() }.distinct()
                                            val subjects = (treeSubs + examSubs).distinct().sorted()

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
                                                        Text(grade, fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa), fontSize = 17.sp)
                                                        Row {
                                                            Button(
                                                                onClick = {
                                                                    selectedGrade = grade
                                                                    showAddSubjectDialog = true
                                                                },
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                                            ) { Text("+ Subject", fontSize = 13.sp, maxLines = 1) }
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Button(
                                                                onClick = { pendingGradeDelete = grade },
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFef4444))
                                                            ) { Text("Delete", fontSize = 12.sp, maxLines = 1) }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    if (subjects.isEmpty()) {
                                                        Text("No subjects yet. Tap + Subject.", color = Color(0xFF64748b), fontSize = 12.sp)
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
                                                                        Text(subject, color = Color.White, fontSize = 14.sp)
                                                                        Text("$count exams", color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                                    }
                                                                    Button(
                                                                        onClick = { deleteSubjectFromTree(grade, subject) },
                                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7f1d1d))
                                                                    ) { Text("Delete", fontSize = 11.sp, maxLines = 1) }
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
                        "subject" -> {
                            val grade = selectedGrade ?: ""
                            val subject = selectedSubject ?: ""

                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { currentView = "home" },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                ) { Text("Back", maxLines = 1) }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(grade, color = Color(0xFF94a3b8), fontSize = 11.sp)
                                    Text(subject, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

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
                            ) { Text("Class Path Analytics") }
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
                                Text("No exams in this class path yet. Tap + New Exam.", color = Color(0xFF94a3b8), fontSize = 13.sp)
                            } else {
                                LazyColumn {
                                    items(exams) { exam ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f).clickable {
                                                        currentProject = exam
                                                        currentProjectId = exam.id
                                                        currentProjectTitle = exam.title
                                                        currentView = "projectDetail"
                                                        loadExamData(exam.id)
                                                        loadAIResponses(exam.id)
                                                    }
                                                ) {
                                                    Text(exam.title, fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                    Text("Created: ${exam.createdAt}", color = Color(0xFF64748b), fontSize = 12.sp)
                                                    Text("Status: ${exam.status}", color = Color(0xFF64748b), fontSize = 12.sp)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Button(
                                                    onClick = { pendingExamDelete = exam },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFef4444)),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                ) { Text("Delete", fontSize = 11.sp, maxLines = 1) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "seriesAnalytics" -> {
                            val s = seriesAnalytics
                            if (selectedPortfolioStudent != null) {
                                val sp = selectedPortfolioStudent!!
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    item {
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Button(
                                                onClick = { selectedPortfolioStudent = null },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("Back", maxLines = 1) }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Student Portfolio", color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                Text(sp.studentName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                    item {
                                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("Overview", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa), fontSize = 16.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("Path: ${s?.grade ?: ""} - ${s?.subjectKey ?: ""}", color = Color(0xFF94a3b8), fontSize = 12.sp)
                                                Text("Combined Average: ${"%.1f".format(sp.combinedAverage)}%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                Text("Overall Grade: ${sp.overallGrade}", color = Color.White, fontSize = 14.sp)
                                                Text("Rank in Class Path: #${sp.rank}", color = Color(0xFFf59e0b), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                Text("Exams Taken: ${sp.examsTaken}", color = Color.White, fontSize = 14.sp)
                                            }
                                        }
                                    }
                                    item {
                                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("Highs and Lows", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa), fontSize = 16.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("Highest: ${sp.highestExam.first} - ${"%.1f".format(sp.highestExam.second)}%", color = Color(0xFF10b981), fontSize = 13.sp)
                                                Text("Lowest: ${sp.lowestExam.first} - ${"%.1f".format(sp.lowestExam.second)}%", color = Color(0xFFef4444), fontSize = 13.sp)
                                                Text(
                                                    when {
                                                        sp.trend == "improving" -> "Trend: improving (+${"%.1f".format(sp.trendChange)})"
                                                        sp.trend == "declining" -> "Trend: declining (${"%.1f".format(sp.trendChange)})"
                                                        sp.trend == "steady" -> "Trend: steady"
                                                        else -> "Trend: insufficient data"
                                                    },
                                                    color = when (sp.trend) {
                                                        "improving" -> Color(0xFF10b981)
                                                        "declining" -> Color(0xFFef4444)
                                                        else -> Color(0xFF94a3b8)
                                                    },
                                                    fontSize = 13.sp, fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    item {
                                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("Score for Every Exam", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa), fontSize = 16.sp)
                                                Text("Oldest first", color = Color(0xFF64748b), fontSize = 11.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                sp.examScores.forEach { (title, pct) ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(title, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                                        Text("${"%.1f".format(pct)}%  (${getKenyanGrade(pct)})",
                                                            color = if (pct >= 50) Color(0xFF10b981) else Color(0xFFef4444),
                                                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    item {
                                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text("Most Failed Topics", fontWeight = FontWeight.Bold, color = Color(0xFFef4444), fontSize = 16.sp)
                                                Text("Ranked by total failures across the class path", color = Color(0xFF64748b), fontSize = 11.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                if (sp.mostFailedTopics.isEmpty()) {
                                                    Text("No failures recorded.", color = Color(0xFF94a3b8), fontSize = 12.sp)
                                                } else {
                                                    sp.mostFailedTopics.forEach { t ->
                                                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))) {
                                                            Column(modifier = Modifier.padding(12.dp)) {
                                                                Text(t.topic, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                                Text("Failed ${t.failedCount} time(s) - passed ${t.passedCount} - appeared in ${t.examsAppeared} exam(s)",
                                                                    color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                                Text("Average score on this topic: ${"%.1f".format(t.averageScore)}%",
                                                                    color = if (t.averageScore >= 50) Color(0xFF10b981) else Color(0xFFef4444),
                                                                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                                Text("Most Passed Topics", fontWeight = FontWeight.Bold, color = Color(0xFF10b981), fontSize = 16.sp)
                                                Text("Ranked by total correct answers across the class path", color = Color(0xFF64748b), fontSize = 11.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                if (sp.mostPassedTopics.isEmpty()) {
                                                    Text("No passes recorded.", color = Color(0xFF94a3b8), fontSize = 12.sp)
                                                } else {
                                                    sp.mostPassedTopics.forEach { t ->
                                                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))) {
                                                            Column(modifier = Modifier.padding(12.dp)) {
                                                                Text(t.topic, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                                Text("Passed ${t.passedCount} time(s) - failed ${t.failedCount} - appeared in ${t.examsAppeared} exam(s)",
                                                                    color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                                Text("Average score on this topic: ${"%.1f".format(t.averageScore)}%",
                                                                    color = if (t.averageScore >= 50) Color(0xFF10b981) else Color(0xFFef4444),
                                                                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    item {
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Button(
                                                onClick = { currentView = "subject" },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("Back", maxLines = 1) }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text("Class Path Analytics", color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                Text("${s?.grade ?: ""} - ${s?.subjectKey ?: ""}",
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
                                                    Text("Student Portfolios", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa), fontSize = 16.sp)
                                                    Text("Tap a student to see combined stats across every exam in this path.",
                                                        color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    s.studentPortfolios.forEach { sp ->
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                                                .clickable { selectedPortfolioStudent = sp },
                                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text("#${sp.rank}", color = Color(0xFFf59e0b), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                                Spacer(modifier = Modifier.width(12.dp))
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(sp.studentName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                                    Text("${sp.examsTaken} exam(s) - Avg ${"%.1f".format(sp.combinedAverage)}% - ${sp.overallGrade}",
                                                                        color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                                    Text(
                                                                        when {
                                                                            sp.trend == "improving" -> "Improving"
                                                                            sp.trend == "declining" -> "Declining"
                                                                            sp.trend == "steady" -> "Steady"
                                                                            else -> "Insufficient data"
                                                                        },
                                                                        color = when (sp.trend) {
                                                                            "improving" -> Color(0xFF10b981)
                                                                            "declining" -> Color(0xFFef4444)
                                                                            else -> Color(0xFF94a3b8)
                                                                        },
                                                                        fontSize = 11.sp
                                                                    )
                                                                }
                                                                Text(">", color = Color(0xFF60a5fa), fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                                                    Text("Timeline", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa), fontSize = 16.sp)
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
                                                        Text("Persistent Weaknesses", fontWeight = FontWeight.Bold, color = Color(0xFFef4444), fontSize = 16.sp)
                                                        Text("Below 50% in every exam. Re-teach these.", color = Color(0xFF94a3b8), fontSize = 12.sp)
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        weakTopics.forEach { t ->
                                                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))) {
                                                                Column(modifier = Modifier.padding(12.dp)) {
                                                                    Text(t.topic, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
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
                                                    Text("Student Progress", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa), fontSize = 16.sp)
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
                                                                            sp.trend == "improving" -> "Up +${"%.1f".format(sp.change)}"
                                                                            sp.trend == "declining" -> "Down ${"%.1f".format(sp.change)}"
                                                                            sp.trend == "steady" -> "Steady"
                                                                            else -> "-"
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
                                                        Text("Small-Group Focus", fontWeight = FontWeight.Bold, color = Color(0xFFf59e0b), fontSize = 16.sp)
                                                        Text("These students fail the same topic in every exam.", color = Color(0xFF94a3b8), fontSize = 12.sp)
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        s.clusters.forEach { c ->
                                                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))) {
                                                                Column(modifier = Modifier.padding(12.dp)) {
                                                                    Text(c.topic, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                                    Text("Failed in all ${c.examCount} exams:", color = Color(0xFF94a3b8), fontSize = 11.sp)
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
                                    !sectionState.isCollectedSheetsOpen &&
                                    !sectionState.isPrintableReportOpen && !sectionState.isRosterSetupOpen &&
                                    !sectionState.isSavedRostersOpen && !sectionState.isAnswerSheetGeneratorOpen
                                if (nothingOpen) {
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = { currentView = "subject" },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                        ) { Text("Back", maxLines = 1) }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${p?.grade ?: ""} - ${p?.subjectKey ?: ""}", color = Color(0xFF94a3b8), fontSize = 11.sp)
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
                                    if (collectedStudentAnswerSheets.isNotEmpty() && markedAnswerSheets.isNotEmpty()) {
                                        Button(
                                            onClick = { showRegradeConfirm = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7f1d1d))
                                        ) { Text("Re-grade with current key (${collectedStudentAnswerSheets.size})") }
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
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf59e0b))
                                            ) { Text("Generate Answer Sheet (PDF)") }
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
                                            Button(onClick = { sectionState = sectionState.copy(isCollectedSheetsOpen = true) },
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                            ) { Text("Collected Sheets (${collectedStudentAnswerSheets.size})") }
                                        }
                                        item {
                                            Button(
                                                onClick = { pendingExamDelete = p },
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
                                            ) { Text("Back", maxLines = 1) }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Generate Answer Sheets (PDF)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFf59e0b))
                                            Text("A4 landscape, 2 sheets per page. Each sheet has 2 columns of questions.", color = Color(0xFF94a3b8), fontSize = 11.sp)
                                            Spacer(modifier = Modifier.height(12.dp))

                                            OutlinedTextField(value = genSchool, onValueChange = { genSchool = it },
                                                label = { Text("School Name (optional)") }, modifier = Modifier.fillMaxWidth())
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedTextField(value = genGrade, onValueChange = { genGrade = it },
                                                label = { Text("Grade") }, modifier = Modifier.fillMaxWidth())
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedTextField(value = genSubject, onValueChange = { genSubject = it },
                                                label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedTextField(value = genExamTitle, onValueChange = { genExamTitle = it },
                                                label = { Text("Exam Title") }, modifier = Modifier.fillMaxWidth())
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedTextField(value = genTerm, onValueChange = { genTerm = it },
                                                label = { Text("Term") }, modifier = Modifier.fillMaxWidth())
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedTextField(value = genDate, onValueChange = { genDate = it },
                                                label = { Text("Date") }, modifier = Modifier.fillMaxWidth())
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedTextField(value = genQuestionCount, onValueChange = { genQuestionCount = it },
                                                label = { Text("Number of Questions (max 50)") }, modifier = Modifier.fillMaxWidth())
                                            Spacer(modifier = Modifier.height(10.dp))

                                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text("Roster / Names", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa), fontSize = 14.sp)
                                                    Text("Pick a saved roster, or paste names below (comma-separated).",
                                                        color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                    if (savedRosters.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        savedRosters.forEach { r ->
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text(r.name, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                                                Text("${r.names.size}", color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Button(
                                                                    onClick = {
                                                                        rosterInput = r.names.joinToString(", ")
                                                                        rosterNames = r.names
                                                                        Toast.makeText(context, "Loaded ${r.names.size} names", Toast.LENGTH_SHORT).show()
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981)),
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                                ) { Text("Use", fontSize = 11.sp) }
                                                            }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    OutlinedTextField(
                                                        value = rosterInput,
                                                        onValueChange = {
                                                            rosterInput = it
                                                            rosterNames = it.split(",").map { n -> n.trim() }.filter { n -> n.isNotBlank() }
                                                        },
                                                        label = { Text("Names separated by commas") },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        minLines = 2
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("${rosterNames.size} name(s)", color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))

                                            Button(
                                                onClick = {
                                                    val q = genQuestionCount.toIntOrNull() ?: 50
                                                    val path = try {
                                                        buildAnswerSheetPdf(
                                                            context = context,
                                                            schoolName = genSchool,
                                                            grade = genGrade.ifBlank { p?.grade ?: "" },
                                                            subject = genSubject.ifBlank { p?.subjectKey ?: "" },
                                                            examTitle = genExamTitle.ifBlank { currentProjectTitle },
                                                            term = genTerm,
                                                            dateText = genDate,
                                                            questionCount = q,
                                                            studentNames = emptyList(),
                                                            filenameBase = "WaveUnits_AnswerSheet_Blank"
                                                        )
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "PDF error: ${e.message}", Toast.LENGTH_LONG).show()
                                                        null
                                                    }
                                                    if (path != null) {
                                                        lastGeneratedPdfPath = path
                                                        pdfPreviewBitmap = renderPdfFirstPage(path)
                                                        Toast.makeText(context, "Saved: $path", Toast.LENGTH_LONG).show()
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa))
                                            ) { Text("Generate Blank PDF (1 sheet)") }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    if (rosterNames.isEmpty()) {
                                                        Toast.makeText(context, "Pick a roster or paste names first", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        val q = genQuestionCount.toIntOrNull() ?: 50
                                                        val baseName = "WaveUnits_AnswerSheets_${genGrade.ifBlank { p?.grade ?: "" }}_${genSubject.ifBlank { p?.subjectKey ?: "" }}"
                                                        val path = try {
                                                            buildAnswerSheetPdf(
                                                                context = context,
                                                                schoolName = genSchool,
                                                                grade = genGrade.ifBlank { p?.grade ?: "" },
                                                                subject = genSubject.ifBlank { p?.subjectKey ?: "" },
                                                                examTitle = genExamTitle.ifBlank { currentProjectTitle },
                                                                term = genTerm,
                                                                dateText = genDate,
                                                                questionCount = q,
                                                                studentNames = rosterNames,
                                                                filenameBase = baseName
                                                            )
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "PDF error: ${e.message}", Toast.LENGTH_LONG).show()
                                                            null
                                                        }
                                                        if (path != null) {
                                                            lastGeneratedPdfPath = path
                                                            pdfPreviewBitmap = renderPdfFirstPage(path)
                                                            val pages = (rosterNames.size + 1) / 2
                                                            Toast.makeText(context, "Saved ${rosterNames.size} sheets ($pages pages) to: $path", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                            ) { Text("Generate PDF for All in Roster (${rosterNames.size})") }
                                            Spacer(modifier = Modifier.height(10.dp))
                                            if (lastGeneratedPdfPath.isNotBlank()) {
                                                Button(
                                                    onClick = { shareFile(lastGeneratedPdfPath, "application/pdf") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf59e0b))
                                                ) { Text("Open / Share PDF") }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("File: $lastGeneratedPdfPath", color = Color(0xFF94a3b8), fontSize = 10.sp)
                                                Spacer(modifier = Modifier.height(10.dp))
                                            }
                                            pdfPreviewBitmap?.let { bmp ->
                                                Text("Preview (page 1):", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa), fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                                ) {
                                                    Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = "PDF preview",
                                                        modifier = Modifier.fillMaxWidth(),
                                                        contentScale = ContentScale.FillWidth
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (sectionState.isRosterSetupOpen) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Button(onClick = {
                                                sectionState = sectionState.copy(isRosterSetupOpen = false)
                                                editingRosterId = null
                                            },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("Back", maxLines = 1) }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                if (editingRosterId != null) "Edit Roster" else "Class Roster",
                                                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa)
                                            )
                                            Text("Roster is used only to generate answer sheets.",
                                                color = Color(0xFF94a3b8), fontSize = 11.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(value = rosterInput, onValueChange = { rosterInput = it },
                                                label = { Text("Names separated by commas") },
                                                modifier = Modifier.fillMaxWidth(), minLines = 3)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(value = rosterTemplateName, onValueChange = { rosterTemplateName = it },
                                                label = { Text("Template Name") }, modifier = Modifier.fillMaxWidth())
                                            Spacer(modifier = Modifier.height(8.dp))
                                            if (editingRosterId != null) {
                                                Button(
                                                    onClick = {
                                                        val names = rosterInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                                        updateRosterTemplate(editingRosterId!!, rosterTemplateName, names)
                                                        editingRosterId = null
                                                        sectionState = sectionState.copy(isRosterSetupOpen = false)
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                                ) { Text("Save Changes") }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = {
                                                        val names = rosterInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                                        saveRosterTemplate(rosterTemplateName + " (copy)", names)
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa))
                                                ) { Text("Save as New Copy") }
                                            } else {
                                                Button(
                                                    onClick = {
                                                        val names = rosterInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                                        rosterNames = names
                                                        Toast.makeText(context, "Loaded ${names.size} names", Toast.LENGTH_SHORT).show()
                                                        sectionState = sectionState.copy(isRosterSetupOpen = false)
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                                ) { Text("Use This Roster Now") }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = {
                                                        val names = rosterInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                                        saveRosterTemplate(rosterTemplateName, names)
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa))
                                                ) { Text("Save as Template") }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            val previewNames = rosterInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                            if (previewNames.isNotEmpty()) {
                                                Text("Names (${previewNames.size}):", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                previewNames.forEachIndexed { i, n -> Text("${i + 1}. $n", color = Color.White, fontSize = 12.sp) }
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
                                            ) { Text("Back", maxLines = 1) }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Saved Rosters", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10b981))
                                            Text("Used for generating answer sheets only.", color = Color(0xFF94a3b8), fontSize = 11.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            if (savedRosters.isEmpty()) {
                                                Text("None yet.", color = Color(0xFF94a3b8))
                                            } else {
                                                savedRosters.forEach { r ->
                                                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1e293b))) {
                                                        Column(modifier = Modifier.padding(16.dp)) {
                                                            Text(r.name, fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa))
                                                            Text("${r.names.size} students", color = Color(0xFF94a3b8), fontSize = 12.sp)
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            Row {
                                                                Button(
                                                                    onClick = {
                                                                        rosterInput = r.names.joinToString(", ")
                                                                        rosterNames = r.names
                                                                        sectionState = sectionState.copy(isSavedRostersOpen = false)
                                                                        Toast.makeText(context, "Loaded '${r.name}'", Toast.LENGTH_SHORT).show()
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                                                ) { Text("Use", fontSize = 12.sp) }
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Button(
                                                                    onClick = {
                                                                        editingRosterId = r.id
                                                                        rosterTemplateName = r.name
                                                                        rosterInput = r.names.joinToString(", ")
                                                                        sectionState = sectionState.copy(
                                                                            isSavedRostersOpen = false,
                                                                            isRosterSetupOpen = true
                                                                        )
                                                                    },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60a5fa))
                                                                ) { Text("Edit", fontSize = 12.sp) }
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Button(onClick = { deleteRosterTemplate(r.id) },
                                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7f1d1d))
                                                                ) { Text("Delete", fontSize = 12.sp) }
                                                            }
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
                                            ) { Text("Back", maxLines = 1) }
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
                                            ) { Text("Back", maxLines = 1) }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Marked Sheets PDF", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFf59e0b))
                                            Text("Landscape A4, 2 students per page.", color = Color(0xFF94a3b8), fontSize = 11.sp)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = {
                                                    val p2 = projects.find { it.id == currentProjectId }
                                                    val path = try {
                                                        buildMarkedSheetsPdf(
                                                            context = context,
                                                            sheets = markedAnswerSheets,
                                                            examTitle = currentProjectTitle,
                                                            grade = p2?.grade ?: "",
                                                            subject = p2?.subjectKey ?: "",
                                                            filenameBase = "WaveUnits_MarkedSheets"
                                                        )
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "PDF error: ${e.message}", Toast.LENGTH_LONG).show()
                                                        null
                                                    }
                                                    if (path != null) {
                                                        lastGeneratedPdfPath = path
                                                        Toast.makeText(context, "Saved: $path", Toast.LENGTH_LONG).show()
                                                        shareFile(path, "application/pdf")
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981))
                                            ) { Text("Save Marked Sheets as PDF & Share") }
                                        }
                                    }
                                } else if (sectionState.isMarkedSheetsOpen) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isMarkedSheetsOpen = false) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("Back", maxLines = 1) }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Marked Sheets", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10b981))
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    val p2 = projects.find { it.id == currentProjectId }
                                                    val path = try {
                                                        buildMarkedSheetsPdf(
                                                            context = context,
                                                            sheets = markedAnswerSheets,
                                                            examTitle = currentProjectTitle,
                                                            grade = p2?.grade ?: "",
                                                            subject = p2?.subjectKey ?: "",
                                                            filenameBase = "WaveUnits_MarkedSheets"
                                                        )
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "PDF error: ${e.message}", Toast.LENGTH_LONG).show()
                                                        null
                                                    }
                                                    if (path != null) {
                                                        lastGeneratedPdfPath = path
                                                        Toast.makeText(context, "Saved: $path", Toast.LENGTH_LONG).show()
                                                        shareFile(path, "application/pdf")
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf59e0b))
                                            ) { Text("Export as PDF (landscape, 2 per page)") }
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
                                            ) { Text("Back", maxLines = 1) }
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
                                            Button(onClick = {
                                                sectionState = sectionState.copy(isAIAnswerSheetOpen = false)
                                                isEditingAnswerKey = false
                                                editableKeyText = ""
                                            },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("Back", maxLines = 1) }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("AI Answer Sheet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10b981))
                                                Spacer(modifier = Modifier.weight(1f))
                                                if (!isEditingAnswerKey) {
                                                    Button(
                                                        onClick = {
                                                            editableKeyText = simplifiedAnswerKey
                                                            isEditingAnswerKey = true
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf59e0b)),
                                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                    ) { Text("Edit", fontSize = 12.sp, maxLines = 1) }
                                                } else {
                                                    Button(
                                                        onClick = {
                                                            saveEditedAnswerKey(editableKeyText)
                                                            isEditingAnswerKey = false
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10b981)),
                                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                    ) { Text("Save", fontSize = 12.sp, maxLines = 1) }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Button(
                                                        onClick = {
                                                            isEditingAnswerKey = false
                                                            editableKeyText = ""
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7f1d1d)),
                                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                                    ) { Text("Cancel", fontSize = 12.sp, maxLines = 1) }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))

                                            if (isEditingAnswerKey) {
                                                Text("Edit the answer key below. Format:", fontWeight = FontWeight.Bold, color = Color(0xFFf59e0b), fontSize = 13.sp)
                                                Text("Q1: Answer: B | Topic: ... | Sub-topic: ...", color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                Text("Or the short form: 1:B,2:C,3:A", color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                Text("To change an answer without losing its topic, edit only the letter after 'Answer:' on that line.", color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                OutlinedTextField(
                                                    value = editableKeyText,
                                                    onValueChange = { editableKeyText = it },
                                                    label = { Text("Answer Key") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    minLines = 6,
                                                    maxLines = 20
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Card(modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0f172a))) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text("Preview (parsed):", fontWeight = FontWeight.Bold, color = Color(0xFF60a5fa), fontSize = 13.sp)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        val previewQs = parseAnswerKeyToQuestions(editableKeyText)
                                                        if (previewQs.isEmpty()) {
                                                            Text("No questions parsed. Check the format.", color = Color(0xFFef4444), fontSize = 12.sp)
                                                        } else {
                                                            Text("${previewQs.size} question(s) parsed", color = Color(0xFF10b981), fontSize = 12.sp)
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            previewQs.take(15).forEach { q ->
                                                                Text("Q${q.number}: ${q.correctAnswer}  (${q.topic})", color = Color.White, fontSize = 11.sp)
                                                            }
                                                            if (previewQs.size > 15) {
                                                                Text("… ${previewQs.size - 15} more", color = Color(0xFF94a3b8), fontSize = 11.sp)
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
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
                                    }
                                } else if (sectionState.isCollectedSheetsOpen) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Button(onClick = { sectionState = sectionState.copy(isCollectedSheetsOpen = false) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                            ) { Text("Back", maxLines = 1) }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Collected Sheets (${collectedStudentAnswerSheets.size})",
                                                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10b981))
                                            Text("Names are read from the printed header on each sheet.",
                                                color = Color(0xFF94a3b8), fontSize = 11.sp)
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
                                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                        Text("Student: ${sheet.studentName}", fontWeight = FontWeight.Bold,
                                                            color = if (sheet.matched) Color(0xFF60a5fa) else Color(0xFFf59e0b),
                                                            fontSize = 16.sp, modifier = Modifier.weight(1f))
                                                        if (!sheet.matched) {
                                                            Button(
                                                                onClick = { pendingSheetRename = sheet },
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFf59e0b)),
                                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                            ) { Text("Rename", fontSize = 11.sp) }
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    val bmp = remember(sheet.image) { decodeBase64(sheet.image) }
                                                    bmp?.let {
                                                        Image(it.asImageBitmap(), null,
                                                            modifier = Modifier.fillMaxWidth().height(250.dp))
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text("Printout:", fontWeight = FontWeight.Bold, color = Color(0xFFf59e0b), fontSize = 12.sp)
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
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Button(onClick = { selectedStudent = null },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                        ) { Text("Back", maxLines = 1) }
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
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Button(onClick = { currentView = "projectDetail" },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
                                        ) { Text("Back", maxLines = 1) }
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