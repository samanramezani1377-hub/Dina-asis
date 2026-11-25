package com.samanramezani.dina.agent
import org.json.JSONObject
object AgentPlanParser {
 private val allowedTools=setOf("find_customer","find_product","calculate_total","create_invoice","send_invoice")
 fun parse(modelOutput:String):AgentPlan {
  val jsonText=extractJson(modelOutput); val root=JSONObject(jsonText)
  val questions=root.optJSONArray("clarification_questions")?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } }.orEmpty()
  val array=root.optJSONArray("steps")
  val steps=(0 until (array?.length() ?: 0)).map { i ->
   val item=array!!.getJSONObject(i); val tool=item.getString("tool")
   require(tool in allowedTools) { "ابزار غیرمجاز در برنامه مدل: $tool" }
   PlannedStep(item.optString("id","step-" + (i+1)),tool,item.optJSONObject("arguments") ?: JSONObject())
  }
  val needs=root.optBoolean("needs_clarification",false)
  require(needs || steps.isNotEmpty()) { "مدل هیچ مرحله اجرایی معتبری تولید نکرد." }
  return AgentPlan(root.optString("summary","برنامه اجرایی تولید شد."),needs,questions,steps,jsonText)
 }
 private fun extractJson(text:String):String {
  val start=text.indexOf('{'); require(start>=0) { "خروجی مدل JSON ندارد." }
  var depth=0; var inString=false; var escaped=false
  for(i in start until text.length) {
   val c=text[i]
   if(inString) { if(escaped) escaped=false else if(c=='\\') escaped=true else if(c=='"') inString=false }
   else when(c) { '"'->inString=true; '{'->depth++; '}'->{ depth--; if(depth==0) return text.substring(start,i+1) } }
  }
  error("JSON خروجی مدل ناقص است.")
 }
}
