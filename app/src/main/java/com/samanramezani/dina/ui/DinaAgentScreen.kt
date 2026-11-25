package com.samanramezani.dina.ui
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.samanramezani.dina.agent.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
@Composable
fun DinaAgentScreen(context:Context) {
 val engine=remember { DinaAgentEngine() }
 val queue=remember { AgentTaskQueue(context,engine).also { it.restore() } }
 val tasks by queue.tasks.collectAsState()
 val scope=rememberCoroutineScope()
 var request by remember { mutableStateOf("") }
 var modelPath by remember { mutableStateOf(context.getSharedPreferences("dina_agent_test",Context.MODE_PRIVATE).getString("model_path","").orEmpty()) }
 var busy by remember { mutableStateOf(false) }
 var message by remember { mutableStateOf("مدل GGUF را انتخاب و بارگذاری کن.") }
 var selectedId by remember { mutableStateOf<String?>(null) }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri:Uri? ->
  if(uri!=null) { busy=true; scope.launch {
   try {
    val file=withContext(Dispatchers.IO) { importModel(context,uri) }
    modelPath=file.absolutePath
    context.getSharedPreferences("dina_agent_test",Context.MODE_PRIVATE).edit().putString("model_path",modelPath).apply()
    message="مدل وارد شد: ${file.name}"
   } catch(t:Throwable) { message="خطای ورود مدل: ${t.message}" } finally { busy=false }
  } }
 }
 val selected=tasks.firstOrNull { it.id==selectedId } ?: tasks.lastOrNull()
 Column(Modifier.fillMaxSize().padding(16.dp)) {
  Text("Dina — دستیار عامل حسابداری",style=MaterialTheme.typography.h6)
  Text("نمونه آزمایشی آفلاین | llama.cpp | CPU | بدون اتصال واقعی")
  Spacer(Modifier.height(10.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   Button(onClick={picker.launch(arrayOf("application/octet-stream","*/*"))},enabled=!busy) { Text("انتخاب GGUF") }
   Button(onClick={
    val f=File(modelPath); if(!f.isFile) { message="ابتدا مدل را انتخاب کن."; return@Button }
    busy=true; scope.launch {
     try { engine.loadModel(f,8192); message="مدل بارگذاری شد — CPU / 8192 context" }
     catch(t:Throwable) { message="خطای بارگذاری: ${t.message}" } finally { busy=false }
    }
   },enabled=!busy && modelPath.isNotBlank()) { Text("بارگذاری CPU") }
  }
  Text(if(modelPath.isBlank()) "مدلی انتخاب نشده" else "Model: ${File(modelPath).name}")
  Text(message)
  Divider(Modifier.padding(vertical=8.dp))
  Text("درخواست جدید",style=MaterialTheme.typography.subtitle1)
  OutlinedTextField(request,{request=it},Modifier.fillMaxWidth(),minLines=2,label={Text("مثلاً: برای آقای فلانی فاکتور محصول A بساز و ارسال کن")})
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   Button(onClick={ if(request.isNotBlank()) { val t=queue.enqueue(request); selectedId=t.id; request=""; message="درخواست به صف اضافه شد." } },enabled=request.isNotBlank()) { Text("افزودن به صف") }
   Button(onClick={
    if(!engine.isLoaded()) { message="ابتدا مدل را بارگذاری کن."; return@Button }
    busy=true; scope.launch { try { queue.runPending(); message="پردازش صف تمام شد." } catch(t:Throwable) { message="خطای صف: ${t.message}" } finally { busy=false } }
   },enabled=!busy && tasks.any { it.status==TaskStatus.PENDING }) { Text("اجرای صف") }
  }
  if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=6.dp))
  Divider(Modifier.padding(vertical=8.dp))
  Text("صف وظایف (${tasks.size})",style=MaterialTheme.typography.subtitle1)
  LazyColumn(Modifier.weight(1f)) {
   items(tasks,key={it.id}) { task ->
    Surface(Modifier.fillMaxWidth().padding(vertical=3.dp).clickable { selectedId=task.id },
     color=if(task.id==selected?.id) MaterialTheme.colors.primary.copy(alpha=0.1f) else MaterialTheme.colors.surface,elevation=1.dp) {
     Column(Modifier.padding(8.dp)) { Text(task.request); Text("وضعیت: ${task.status}",style=MaterialTheme.typography.caption) }
    }
   }
  }
  Divider(Modifier.padding(vertical=6.dp))
  Text("گزارش کامل",style=MaterialTheme.typography.subtitle1)
  Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
   Text(selected?.report?.ifBlank { "هنوز گزارشی برای این کار ثبت نشده." } ?: "صف خالی است.")
  }
 }
}
private fun importModel(context:Context,uri:Uri):File {
 val dir=File(context.filesDir,"models").apply { mkdirs() }
 val temp=File(dir,"qwen3.8-2b.gguf.part"); val target=File(dir,"qwen3.8-2b.gguf")
 context.contentResolver.openInputStream(uri).use { input ->
  requireNotNull(input) { "فایل قابل خواندن نیست." }
  temp.outputStream().use { output->input.copyTo(output,1024*1024) }
 }
 require(temp.length()>1024*1024) { "فایل خیلی کوچک است." }
 temp.inputStream().use { s -> val magic=ByteArray(4); require(s.read(magic)==4 && magic.decodeToString()=="GGUF") { "فایل GGUF معتبر نیست." } }
 if(target.exists()) target.delete()
 check(temp.renameTo(target)) { "انتقال مدل ناموفق بود." }
 return target
}
