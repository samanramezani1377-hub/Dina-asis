package com.samanramezani.dina.agent
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
class AgentTaskQueue(context:Context,private val engine:DinaAgentEngine) {
 private val app=context.applicationContext
 private val mutex=Mutex()
 private val mutable=MutableStateFlow<List<AgentTask>>(emptyList())
 val tasks:StateFlow<List<AgentTask>> = mutable
 private val reports=File(app.filesDir,"agent-reports").apply { mkdirs() }
 fun enqueue(request:String):AgentTask {
  require(request.isNotBlank()); val task=AgentTask(request=request.trim())
  mutable.value=mutable.value+task; persist(); return task
 }
 suspend fun runPending()=mutex.withLock {
  val pending=mutable.value.filter { it.status==TaskStatus.PENDING }
  for(task in pending) {
   update(task.id) { it.copy(status=TaskStatus.RUNNING) }
   try {
    val report=engine.executeRequest(task.request)
    val status=when {
     report.contains("\"status\": \"NEEDS_INPUT\"")->TaskStatus.NEEDS_INPUT
     report.contains("\"status\": \"COMPLETED\"")->TaskStatus.COMPLETED
     else->TaskStatus.FAILED
    }
    File(reports,"${task.id}.json").writeText(report)
    update(task.id) { it.copy(status=status,report=report) }
   } catch(t:Throwable) {
    val report=JSONObject().put("status","FAILED").put("request",task.request).put("error",t.message ?: t::class.java.simpleName)
     .put("simulation_only",true).put("real_accounting_system_touched",false).toString(2)
    File(reports,"${task.id}.json").writeText(report)
    update(task.id) { it.copy(status=TaskStatus.FAILED,report=report) }
   }
  }
 }
 fun clearCompleted() { mutable.value=mutable.value.filter { it.status==TaskStatus.PENDING || it.status==TaskStatus.RUNNING }; persist() }
 private fun update(id:String,fn:(AgentTask)->AgentTask) { mutable.value=mutable.value.map { if(it.id==id) fn(it) else it }; persist() }
 private fun persist() {
  runCatching {
   val array=JSONArray()
   mutable.value.forEach { t -> array.put(JSONObject().put("id",t.id).put("request",t.request).put("status",t.status.name).put("createdAt",t.createdAt).put("report",t.report)) }
   app.getSharedPreferences("dina_agent_test",Context.MODE_PRIVATE).edit().putString("task_queue",array.toString()).apply()
  }
 }
 fun restore() {
  runCatching {
   val raw=app.getSharedPreferences("dina_agent_test",Context.MODE_PRIVATE).getString("task_queue",null) ?: return
   val a=JSONArray(raw)
   mutable.value=(0 until a.length()).map { i ->
    val o=a.getJSONObject(i)
    val status=runCatching { TaskStatus.valueOf(o.optString("status")) }.getOrDefault(TaskStatus.PENDING)
    AgentTask(o.getString("id"),o.getString("request"),if(status==TaskStatus.RUNNING) TaskStatus.PENDING else status,o.optString("report"),o.optLong("createdAt"))
   }
  }
 }
}
