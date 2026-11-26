package com.samanramezani.dina.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.samanramezani.dina.agent.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DinaAgentScreen(context: Context) {
    val logs = remember { AgentLogStore() }
    val engine = remember { DinaAgentEngine(logs) }
    val queue = remember { AgentTaskQueue(context, engine, logs).also { it.restore() } }
    val tasks by queue.tasks.collectAsState()
    val scope = rememberCoroutineScope()
    var request by remember { mutableStateOf("") }
    var modelPath by remember {
        mutableStateOf(context.getSharedPreferences("dina_agent_test", Context.MODE_PRIVATE)
            .getString("model_path", "").orEmpty())
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("مدل GGUF را انتخاب و بارگذاری کن.") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showLogs by remember { mutableStateOf(false) }
    val selected = tasks.firstOrNull { it.id == selectedId } ?: tasks.lastOrNull()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            logs.info("انتخاب فایل مدل از حافظه گوشی")
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) { importModel(context, uri) }
                    modelPath = file.absolutePath
                    context.getSharedPreferences("dina_agent_test", Context.MODE_PRIVATE).edit()
                        .putString("model_path", modelPath).apply()
                    message = "مدل وارد شد: ${file.name}"
                    logs.info("ورود مدل کامل شد: ${file.name}")
                } catch (t: Throwable) {
                    message = "خطای ورود مدل: ${t.message}"
                    logs.error("خطا در ورود فایل مدل", t)
                } finally { busy = false }
            }
        }
    }

    if (showLogs) {
        AgentLogsScreen(logs = logs, onBack = { showLogs = false })
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Dina — دستیار عامل حسابداری", style = MaterialTheme.typography.h6)
                Text("نمونه آزمایشی آفلاین | llama.cpp | CPU | بدون اتصال واقعی")
            }
            Button(onClick = { showLogs = true }) { Text("لاگ اجرا") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) }, enabled = !busy) {
                Text("انتخاب GGUF")
            }
            Button(onClick = {
                val file = File(modelPath)
                if (!file.isFile) {
                    message = "ابتدا مدل را انتخاب کن."
                    logs.warn("درخواست بارگذاری رد شد؛ فایل مدل موجود نیست")
                    return@Button
                }
                busy = true
                scope.launch {
                    try {
                        engine.loadModel(file, 8192)
                        message = "مدل بارگذاری شد — CPU / 8192 context"
                    } catch (t: Throwable) {
                        message = "خطای بارگذاری: ${t.message}"
                        logs.error("بارگذاری مدل ناموفق بود", t)
                    } finally { busy = false }
                }
            }, enabled = !busy && modelPath.isNotBlank()) { Text("بارگذاری CPU") }
        }
        Text(if (modelPath.isBlank()) "مدلی انتخاب نشده" else "Model: ${File(modelPath).name}")
        Text(message)
        Divider(Modifier.padding(vertical = 8.dp))
        Text("درخواست جدید", style = MaterialTheme.typography.subtitle1)
        OutlinedTextField(request, { request = it }, Modifier.fillMaxWidth(), minLines = 2,
            label = { Text("مثلاً: برای آقای فلانی فاکتور محصول A بساز و ارسال کن") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (request.isNotBlank()) {
                    val task = queue.enqueue(request)
                    selectedId = task.id
                    request = ""
                    message = "درخواست به صف اضافه شد."
                }
            }, enabled = request.isNotBlank()) { Text("افزودن به صف") }
            Button(onClick = {
                if (!engine.isLoaded()) {
                    message = "ابتدا مدل را بارگذاری کن."
                    logs.warn("اجرای صف ممکن نیست؛ مدل بارگذاری نشده")
                    return@Button
                }
                busy = true
                scope.launch {
                    try {
                        queue.runPending()
                        message = "پردازش صف تمام شد."
                    } catch (t: Throwable) {
                        message = "خطای صف: ${t.message}"
                        logs.error("خطای خارج از انتظار هنگام اجرای صف", t)
                    } finally { busy = false }
                }
            }, enabled = !busy && tasks.any { it.status == TaskStatus.PENDING }) { Text("اجرای صف") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        Divider(Modifier.padding(vertical = 8.dp))
        Text("صف وظایف (${tasks.size})", style = MaterialTheme.typography.subtitle1)
        LazyColumn(Modifier.weight(1f)) {
            items(tasks, key = { it.id }) { task ->
                Surface(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { selectedId = task.id },
                    color = if (task.id == selected?.id) MaterialTheme.colors.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colors.surface, elevation = 1.dp
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text(task.request)
                        Text("وضعیت: ${task.status}", style = MaterialTheme.typography.caption)
                    }
                }
            }
        }
        Divider(Modifier.padding(vertical = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("گزارش کامل", style = MaterialTheme.typography.subtitle1)
            Button(
                onClick = {
                    copyToClipboard(context, "گزارش نتیجه Dina", selected?.report.orEmpty())
                    message = "گزارش در کلیپ‌بورد کپی شد."
                    logs.info("گزارش وظیفه انتخاب‌شده کپی شد")
                },
                enabled = !selected?.report.isNullOrBlank()
            ) { Text("کپی گزارش نتیجه") }
        }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(selected?.report?.ifBlank { "هنوز گزارشی برای این کار ثبت نشده." } ?: "صف خالی است.")
        }
    }
}

@Composable
private fun AgentLogsScreen(logs: AgentLogStore, onBack: () -> Unit) {
    val entries by logs.entries.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(entries.firstOrNull()?.id) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("لاگ زنده اجرا", style = MaterialTheme.typography.h6)
            TextButton(onClick = onBack) { Text("بازگشت") }
        }
        Text("جدیدترین رویداد در ابتدای فهرست نمایش داده می‌شود.", style = MaterialTheme.typography.caption)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                copyToClipboard(context, "Dina execution logs", logs.copyText())
                copied = true
            }, enabled = entries.isNotEmpty()) { Text("کپی همه لاگ‌ها") }
            TextButton(onClick = { copied = false }) { Text("تعداد: ${entries.size}") }
        }
        if (copied) Text("لاگ‌ها کپی شدند.", color = MaterialTheme.colors.primary)
        Divider(Modifier.padding(vertical = 6.dp))
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("هنوز لاگی ثبت نشده است.")
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(entries, key = { it.id }) { entry ->
                    val time = remember(entry.timestamp) {
                        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(entry.timestamp))
                    }
                    val color = when (entry.level) {
                        LogLevel.ERROR -> MaterialTheme.colors.error
                        LogLevel.WARN -> MaterialTheme.colors.secondary
                        LogLevel.INFO -> MaterialTheme.colors.onSurface
                    }
                    Surface(Modifier.fillMaxWidth(), elevation = 1.dp) {
                        Column(Modifier.padding(8.dp)) {
                            Text("$time  [${entry.level}]", color = color, style = MaterialTheme.typography.caption)
                            Text(entry.message, color = color, style = MaterialTheme.typography.body2)
                        }
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun importModel(context: Context, uri: Uri): File {
    val dir = File(context.filesDir, "models").apply { mkdirs() }
    val temp = File(dir, "qwen3.8-2b.gguf.part")
    val target = File(dir, "qwen3.8-2b.gguf")
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "فایل قابل خواندن نیست." }
        temp.outputStream().use { output -> input.copyTo(output, 1024 * 1024) }
    }
    require(temp.length() > 1024 * 1024) { "فایل خیلی کوچک است." }
    temp.inputStream().use { stream ->
        val magic = ByteArray(4)
        require(stream.read(magic) == 4 && magic.decodeToString() == "GGUF") { "فایل GGUF معتبر نیست." }
    }
    if (target.exists()) target.delete()
    check(temp.renameTo(target)) { "انتقال مدل ناموفق بود." }
    return target
}
