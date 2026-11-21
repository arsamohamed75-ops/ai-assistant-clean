package com.arsa.cleanassistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TrainedRule(
    val phrase: String,
    val type: String,
    val target: String
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF0A0F24),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF04131A),
    background = Color(0xFF090D1A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF11182E),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1A2442),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFF87171)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = DarkScheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AssistantApp()
                }
            }
        }
    }
}

@Composable
fun AssistantApp() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var commandText by remember { mutableStateOf("") }
    var replyText by remember { mutableStateOf("جاهز للأوامر الصوتية والنصية.") }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    var selectedLang by remember { mutableStateOf("ary") }
    var autoSpeak by remember { mutableStateOf(true) }

    val rules = remember {
        mutableStateListOf(
            TrainedRule("افتح واتساب", "APP", "com.whatsapp"),
            TrainedRule("فتح الإعدادات", "SETTING", Settings.ACTION_SETTINGS),
            TrainedRule("شعل الواي فاي", "SETTING", Settings.ACTION_WIFI_SETTINGS),
            TrainedRule("الوقت", "TIME", "clock")
        )
    }
    val logs = remember { mutableStateListOf<String>() }

    fun addLog(entry: String) {
        val stamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        logs.add(0, "[$stamp] $entry")
    }

    fun executeVoiceOrTextCommand(raw: String) {
        val clean = raw.trim()
        if (clean.isEmpty()) {
            replyText = "اكتب أمراً أو استخدم الميكروفون."
            return
        }
        val match = rules.firstOrNull { rule ->
            clean.contains(rule.phrase, ignoreCase = true) || rule.phrase.contains(clean, ignoreCase = true)
        }
        if (match == null) {
            replyText = "لم أتعرف على الأمر: «$clean». يمكنك إضافته من صفحة الإصلاحات والتعليم."
            addLog("أمر غير معروف: $clean")
            return
        }

        when (match.type) {
            "APP" -> {
                val launch = context.packageManager.getLaunchIntentForPackage(match.target)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    replyText = "تم تنفيذ الأمر الصوتي: فتح ${match.target}"
                } else {
                    replyText = "التطبيق المستهدف غير مثبت على الهاتف (${match.target})."
                    addLog("تعذر فتح التطبيق: ${match.target}")
                }
            }
            "SETTING" -> {
                try {
                    val intent = Intent(match.target).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                    replyText = "تم تنفيذ الأمر الصوتي: فتح إعدادات النظام."
                } catch (_: Exception) {
                    replyText = "تعذر فتح شاشة الإعدادات المطلوبة."
                    addLog("فشل فتح الإعداد: ${match.target}")
                }
            }
            "TIME" -> {
                val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                replyText = "الساعة الآن: $now"
            }
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            commandText = spoken
            executeVoiceOrTextCommand(spoken)
        } else {
            replyText = "لم يكتمل الالتقاط الصوتي؛ جرّب مجدداً أو أعد تهيئة الصوت."
            addLog("لم يكتمل الالتقاط الصوتي.")
        }
    }

    fun launchSpeechRecognizer() {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            statusBanner = "صلاحية الميكروفون مطلوبة للتحدث."
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            statusBanner = "محرك التعرف الصوتي غير متوفر حالياً على الجهاز."
            addLog("محرك التعرف الصوتي غير متوفر.")
            return
        }
        val langTag = when (selectedLang) {
            "en" -> "en-US"
            "ar" -> "ar-SA"
            else -> "ar-MA"
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن...")
        }
        statusBanner = null
        voiceLauncher.launch(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchSpeechRecognizer()
        } else {
            statusBanner = "تم رفض صلاحية الميكروفون."
            addLog("رفض صلاحية RECORD_AUDIO.")
        }
    }

    fun triggerMic() {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            launchSpeechRecognizer()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.Mic, contentDescription = null) },
                    label = { Text("الواجهة") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.Build, contentDescription = null) },
                    label = { Text("الإصلاحات والتعليم") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.Language, contentDescription = null) },
                    label = { Text("اللغات") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF131C3A), MaterialTheme.colorScheme.background)
                    )
                )
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> MainScreen(
                    commandText = commandText,
                    onCommandChange = { commandText = it },
                    replyText = replyText,
                    statusBanner = statusBanner,
                    onExecute = { executeVoiceOrTextCommand(commandText) },
                    onMicClick = { triggerMic() }
                )
                1 -> RepairAndTrainScreen(
                    rules = rules,
                    logs = logs,
                    onRepairVoice = {
                        statusBanner = null
                        replyText = "تمت إعادة تهيئة محرك الصوت."
                        addLog("إعادة تهيئة محرك الصوت.")
                    },
                    onClearLogs = {
                        logs.clear()
                        statusBanner = "تم مسح سجل الأخطاء."
                    },
                    onAddRule = { phrase, type, target ->
                        rules.add(0, TrainedRule(phrase, type, target))
                        addLog("تمت إضافة عبارة: $phrase")
                    }
                )
                2 -> LanguageScreen(
                    selectedLang = selectedLang,
                    onSelectLang = { selectedLang = it },
                    autoSpeak = autoSpeak,
                    onToggleAutoSpeak = { autoSpeak = it }
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    commandText: String,
    onCommandChange: (String) -> Unit,
    replyText: String,
    statusBanner: String?,
    onExecute: () -> Unit,
    onMicClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "المساعد الذكي",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "فتح التطبيقات والإعدادات يتم عبر الأوامر الصوتية فقط.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (statusBanner != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = statusBanner,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = commandText,
                    onValueChange = onCommandChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("اكتب أمراً أو تحدّث بالصوت...") },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onExecute,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("تنفيذ الأمر", fontWeight = FontWeight.SemiBold)
                    }
                    FilledTonalButton(
                        onClick = onMicClick,
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = "ميكروفون")
                        Spacer(Modifier.width(6.dp))
                        Text("صوت")
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "النتيجة",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = replyText,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun RepairAndTrainScreen(
    rules: List<TrainedRule>,
    logs: List<String>,
    onRepairVoice: () -> Unit,
    onClearLogs: () -> Unit,
    onAddRule: (String, String, String) -> Unit
) {
    var newPhrase by remember { mutableStateOf("") }
    var newTarget by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf("APP") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "الإصلاحات والتعليم",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("أزرار إصلاح التطبيق", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onRepairVoice,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("إصلاح الصوت")
                        }
                        OutlinedButton(
                            onClick = onClearLogs,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("تصفير السجل")
                        }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("تعليم عبارة صوتية", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = newPhrase,
                        onValueChange = { newPhrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("العبارة (مثال: افتح الكاميرا)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = newType == "APP",
                            onClick = { newType = "APP" },
                            label = { Text("تطبيق") }
                        )
                        FilterChip(
                            selected = newType == "SETTING",
                            onClick = { newType = "SETTING" },
                            label = { Text("إعداد") }
                        )
                    }
                    OutlinedTextField(
                        value = newTarget,
                        onValueChange = { newTarget = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("الهدف (مثال: com.android.camera)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    Button(
                        onClick = {
                            if (newPhrase.isNotBlank() && newTarget.isNotBlank()) {
                                onAddRule(newPhrase.trim(), newType, newTarget.trim())
                                newPhrase = ""
                                newTarget = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("حفظ التعليم")
                    }
                }
            }
        }
        items(rules) { rule ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(rule.phrase, fontWeight = FontWeight.SemiBold)
                    Text(rule.target, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (logs.isNotEmpty()) {
            item {
                Text("سجل التطبيق", fontWeight = FontWeight.SemiBold)
            }
            items(logs) { log ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(log, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun LanguageScreen(
    selectedLang: String,
    onSelectLang: (String) -> Unit,
    autoSpeak: Boolean,
    onToggleAutoSpeak: (Boolean) -> Unit
) {
    val options = listOf(
        "ary" to "الدارجة / العربية (ar-MA)",
        "ar" to "العربية الفصحى (ar-SA)",
        "en" to "الإنجليزية (en-US)"
    )
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "اللغات",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { (code, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedLang == code,
                            onClick = { onSelectLang(code) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("استجابة صوتية تلقائية")
                    Switch(checked = autoSpeak, onCheckedChange = onToggleAutoSpeak)
                }
            }
        }
    }
}
