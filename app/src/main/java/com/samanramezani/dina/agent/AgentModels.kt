package com.samanramezani.dina.agent
import java.util.UUID
enum class TaskStatus { PENDING, RUNNING, COMPLETED, NEEDS_INPUT, FAILED }
data class AgentTask(val id:String=UUID.randomUUID().toString(),val request:String,val status:TaskStatus=TaskStatus.PENDING,val report:String="",val createdAt:Long=System.currentTimeMillis())
data class PlannedStep(val id:String,val tool:String,val arguments:org.json.JSONObject)
data class AgentPlan(val summary:String,val needsClarification:Boolean,val questions:List<String>,val steps:List<PlannedStep>,val rawJson:String)
data class ToolResult(val success:Boolean,val summary:String,val data:org.json.JSONObject=org.json.JSONObject())
