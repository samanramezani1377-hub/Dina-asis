package com.samanramezani.dina.agent

import com.woogit.aicore.runtime.android.NativeLlamaCpp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DinaAgentEngine(private val logs: AgentLogStore) {
    @Volatile private var loaded = false
    @Volatile private var loadedPath: String? = null

    fun isLoaded() = loaded
    fun loadedModelPath() = loadedPath

    suspend fun loadModel(file: File, contextLength: Int = 8192) = withContext(Dispatchers.IO) {
        logs.info("بررسی فایل مدل: ${file.name} | حجم=${file.length()} بایت")
        require(file.isFile && file.canRead()) { "فایل GGUF قابل خواندن نیست." }
        logs.info("شروع بارگذاری مدل با CPU-only و GPU Layers=0")
        val started = System.currentTimeMillis()
        try {
            val result = NativeLlamaCpp.load(file.absolutePath, contextLength, 0, null)
            check(result == 0) { "بارگذاری مدل ناموفق بود؛ کد Native: $result" }
            loadedPath = file.absolutePath
            loaded = true
            logs.info("بارگذاری مدل موفق بود؛ زمان=${System.currentTimeMillis() - started}ms | context=$contextLength")
        } catch (t: Throwable) {
            loaded = false
            logs.error("خطا در بارگذاری مدل", t)
            throw t
        }
    }

    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        logs.warn("درخواست آزادسازی مدل")
        NativeLlamaCpp.unload()
        loaded = false
        loadedPath = null
        logs.info("مدل آزاد شد")
    }

    suspend fun executeRequest(request: String): String {
        check(loaded) { "ابتدا مدل GGUF را بارگذاری کن." }
        require(request.isNotBlank())
        logs.info("شروع پردازش درخواست؛ طول متن=${request.length} نویسه")
        val started = System.currentTimeMillis()
        val raw = try {
            withContext(Dispatchers.IO) {
                val output = StringBuilder()
                var chunks = 0
                logs.info("ارسال prompt به موتور llama.cpp؛ حداکثر خروجی=640 توکن")
                NativeLlamaCpp.generate(buildPrompt(request), 640, 0.1f, 40, 0.9f, 0.05f).collect {
                    output.append(it)
                    chunks++
                    if (chunks == 1 || chunks % 16 == 0) {
                        logs.info("مدل در حال تولید پاسخ است؛ قطعه‌ها=$chunks | خروجی=${output.length} نویسه")
                    }
                }
                logs.info("تولید خروجی مدل تمام شد؛ زمان=${System.currentTimeMillis() - started}ms | قطعه‌ها=$chunks | خروجی=${output.length} نویسه")
                output.toString()
            }
        } catch (t: Throwable) {
            logs.error("خطا هنگام اجرای inference مدل", t)
            throw t
        }

        val plan = try {
            AgentPlanParser.parse(raw).also {
                logs.info("برنامه مدل تجزیه شد؛ تعداد مراحل=${it.steps.size} | نیاز به اطلاعات بیشتر=${it.needsClarification}")
            }
        } catch (t: Throwable) {
            logs.error("خروجی مدل JSON معتبر نبود", t)
            return JSONObject().put("report_type", "DINA_AGENT_TEST_REPORT").put("status", "FAILED")
                .put("request", request).put("plan_parse_error", t.message ?: "invalid model output")
                .put("raw_model_output", raw).put("simulation_only", true)
                .put("real_accounting_system_touched", false).put("generated_at_epoch_ms", System.currentTimeMillis()).toString(2)
        }

        if (plan.needsClarification) {
            logs.warn("مدل برای ادامه اطلاعات بیشتری درخواست کرده است")
            return report(request, raw, plan, emptyList(), "NEEDS_INPUT")
        }

        val tools = SimulatedAccountingTools()
        val results = mutableListOf<JSONObject>()
        var failed = false
        for ((index, step) in plan.steps.withIndex()) {
            logs.info("اجرای مرحله ${index + 1}/${plan.steps.size}: tool=${step.tool}")
            val result = try {
                tools.execute(step)
            } catch (t: Throwable) {
                logs.error("استثنا در ابزار ${step.tool}", t)
                throw t
            }
            if (result.success) logs.info("مرحله ${step.id} موفق: ${result.summary}")
            else logs.error("مرحله ${step.id} ناموفق: ${result.summary}")
            results += JSONObject().put("step_id", step.id).put("tool", step.tool)
                .put("success", result.success).put("summary", result.summary).put("data", result.data)
            if (!result.success) { failed = true; break }
        }
        val status = if (failed) "FAILED" else "COMPLETED"
        logs.info("پردازش درخواست پایان یافت؛ status=$status")
        return report(request, raw, plan, results, status)
    }

    private fun buildPrompt(request: String) = buildString {
        append("<|im_start|>system\n")
        append("""تو موتور برنامه‌ریز یک دستیار حسابداری آزمایشی هستی، نه چت‌بات.
فقط یک شیء JSON معتبر برگردان؛ هیچ متن یا Markdown بیرون JSON ننویس.
ساختار نمونه:
{"summary":"خلاصه فارسی","needs_clarification":false,"clarification_questions":[],"steps":[{"id":"1","tool":"find_customer","arguments":{"name":"آقای فلانی"}},{"id":"2","tool":"find_product","arguments":{"name":"محصول A","quantity":1}},{"id":"3","tool":"create_invoice","arguments":{}},{"id":"4","tool":"send_invoice","arguments":{"channel":"ارسال آزمایشی"}}]}
ابزارهای مجاز فقط: find_customer, find_product, calculate_total, create_invoice, send_invoice.
برای فاکتور ترتیب معمول: find_customer سپس find_product سپس create_invoice سپس send_invoice.
اگر نام مشتری یا محصول مشخص نیست، needs_clarification=true و steps=[] قرار بده و سؤال را در clarification_questions بنویس.
ابزار دیگری اختراع نکن. هر عملیات فقط شبیه‌سازی است و نباید ادعای اجرای واقعی کنی.""".trimIndent())
        append("<|im_end|>\n<|im_start|>user\n")
        append(request.trim())
        append("<|im_end|>\n<|im_start|>assistant\n<|channel|>final\n")
    }

    private fun report(request: String, raw: String, plan: AgentPlan, steps: List<JSONObject>, status: String) =
        JSONObject().put("report_type", "DINA_AGENT_TEST_REPORT").put("status", status).put("request", request)
            .put("plan_summary", plan.summary).put("clarification_questions", JSONArray(plan.questions))
            .put("planned_steps", JSONArray(plan.steps.map {
                JSONObject().put("id", it.id).put("tool", it.tool).put("arguments", it.arguments)
            }))
            .put("executed_steps", JSONArray(steps)).put("raw_model_output", raw)
            .put("model", "Qwen 3.8 2B GGUF").put("runtime", "llama.cpp Android")
            .put("execution_backend", "CPU").put("simulation_only", true)
            .put("real_accounting_system_touched", false)
            .put("generated_at_epoch_ms", System.currentTimeMillis()).toString(2)
}
