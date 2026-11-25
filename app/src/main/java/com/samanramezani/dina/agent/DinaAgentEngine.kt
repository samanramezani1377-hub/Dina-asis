package com.samanramezani.dina.agent
import com.woogit.aicore.runtime.android.NativeLlamaCpp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
class DinaAgentEngine {
 @Volatile private var loaded=false
 @Volatile private var loadedPath:String?=null
 fun isLoaded()=loaded
 fun loadedModelPath()=loadedPath
 suspend fun loadModel(file:File,contextLength:Int=8192)=withContext(Dispatchers.IO) {
  require(file.isFile && file.canRead()) { "فایل GGUF قابل خواندن نیست." }
  val result=NativeLlamaCpp.load(file.absolutePath,contextLength,0,null)
  check(result==0) { "بارگذاری مدل ناموفق بود؛ کد Native: $result" }
  loadedPath=file.absolutePath; loaded=true
 }
 suspend fun unloadModel()=withContext(Dispatchers.IO) { NativeLlamaCpp.unload(); loaded=false; loadedPath=null }
 suspend fun executeRequest(request:String):String {
  check(loaded) { "ابتدا مدل GGUF را بارگذاری کن." }; require(request.isNotBlank())
  val raw=withContext(Dispatchers.IO) {
   val output=StringBuilder()
   NativeLlamaCpp.generate(buildPrompt(request),640,0.1f,40,0.9f,0.05f).collect { output.append(it) }
   output.toString()
  }
  val plan=AgentPlanParser.parse(raw)
  if(plan.needsClarification) return report(request,raw,plan,emptyList(),"NEEDS_INPUT")
  val tools=SimulatedAccountingTools(); val results=mutableListOf<JSONObject>(); var failed=false
  for(step in plan.steps) {
   val result=tools.execute(step)
   results += JSONObject().put("step_id",step.id).put("tool",step.tool).put("success",result.success).put("summary",result.summary).put("data",result.data)
   if(!result.success) { failed=true; break }
  }
  return report(request,raw,plan,results,if(failed) "FAILED" else "COMPLETED")
 }
 private fun buildPrompt(request:String)=buildString {
  append("<|im_start|>system\n")
  append("""تو موتور برنامه‌ریز یک دستیار حسابداری آزمایشی هستی، نه چت‌بات.
فقط یک شیء JSON معتبر برگردان؛ هیچ متن یا Markdown بیرون JSON ننویس.
ساختار نمونه:
{"summary":"خلاصه فارسی","needs_clarification":false,"clarification_questions":[],"steps":[{"id":"1","tool":"find_customer","arguments":{"name":"آقای فلانی"}},{"id":"2","tool":"find_product","arguments":{"name":"محصول A","quantity":1}},{"id":"3","tool":"create_invoice","arguments":{}},{"id":"4","tool":"send_invoice","arguments":{"channel":"ارسال آزمایشی"}}]}
ابزارهای مجاز فقط: find_customer, find_product, calculate_total, create_invoice, send_invoice.
برای فاکتور ترتیب معمول: find_customer سپس find_product سپس create_invoice سپس send_invoice.
اگر نام مشتری یا محصول مشخص نیست، needs_clarification=true و steps=[] قرار بده و سؤال را در clarification_questions بنویس.
ابزار دیگری اختراع نکن. هر عملیات فقط شبیه‌سازی است و نباید ادعای اجرای واقعی کنی.""".trimIndent())
  append("<|im_end|>\n<|im_start|>user\n"); append(request.trim())
  append("<|im_end|>\n<|im_start|>assistant\n<|channel|>final\n")
 }
 private fun report(request:String,raw:String,plan:AgentPlan,steps:List<JSONObject>,status:String)=JSONObject()
  .put("report_type","DINA_AGENT_TEST_REPORT").put("status",status).put("request",request)
  .put("plan_summary",plan.summary).put("clarification_questions",JSONArray(plan.questions))
  .put("steps",JSONArray(steps)).put("raw_model_output",raw).put("simulation_only",true)
  .put("real_accounting_system_touched",false).put("generated_at_epoch_ms",System.currentTimeMillis()).toString(2)
}
