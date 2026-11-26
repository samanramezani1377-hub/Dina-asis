package com.samanramezani.dina.agent

import com.woogit.aicore.runtime.android.NativeLlamaCpp
import com.woogit.aicore.runtime.android.NativeGenerationStats
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
        val prompt = buildPrompt(request)
        val promptTokens = runCatching { NativeLlamaCpp.countTokens(prompt) }.getOrDefault(-1)
        logs.info("توکن‌سازی prompt؛ توکن ورودی=${if (promptTokens >= 0) promptTokens else "نامشخص"}")
        var generationStats: NativeGenerationStats? = null
        val raw = try {
            withContext(Dispatchers.IO) {
                val output = StringBuilder()
                logs.info("ارسال prompt به موتور llama.cpp؛ حداکثر خروجی=640 توکن")
                NativeLlamaCpp.generate(prompt, 640, 0.1f, 40, 0.9f, 0.05f,
                    onStats = { generationStats = it }).collect { output.append(it) }
                val stats = generationStats
                if (stats != null) {
                    val totalTps = if (stats.generationMs > 0) stats.generatedTokens * 1000.0 / stats.generationMs else 0.0
                    val decodeTps = if (stats.decodeMs > 0) stats.generatedTokens * 1000.0 / stats.decodeMs else 0.0
                    logs.info("آمار تولید مدل؛ ورودی=${promptTokens} توکن | خروجی=${stats.generatedTokens} توکن | زمان کل=${stats.generationMs}ms | prefill=${stats.prefillMs}ms | decode=${stats.decodeMs}ms | سرعت کل=${"%.2f".format(java.util.Locale.US, totalTps)} توکن/ثانیه | سرعت decode=${"%.2f".format(java.util.Locale.US, decodeTps)} توکن/ثانیه")
                } else {
                    logs.warn("آمار دقیق توکن خروجی از موتور دریافت نشد؛ مقدار ساختگی ثبت نشد.")
                }
                logs.info("تولید خروجی مدل تمام شد؛ زمان=${System.currentTimeMillis() - started}ms | خروجی=${output.length} نویسه")
                output.toString()
            }
        } catch (t: Throwable) {
            logs.error("خطا هنگام اجرای inference مدل", t)
            throw t
        }

        val stats = generationStats
        val totalTps = stats?.let { if (it.generationMs > 0) it.generatedTokens * 1000.0 / it.generationMs else 0.0 }
        val decodeTps = stats?.let { if (it.decodeMs > 0) it.generatedTokens * 1000.0 / it.decodeMs else 0.0 }
        val performance = JSONObject()
            .put("input_tokens", if (promptTokens >= 0) promptTokens else JSONObject.NULL)
            .put("output_tokens", stats?.generatedTokens ?: JSONObject.NULL)
            .put("generation_ms", stats?.generationMs ?: JSONObject.NULL)
            .put("prefill_ms", stats?.prefillMs ?: JSONObject.NULL)
            .put("decode_ms", stats?.decodeMs ?: JSONObject.NULL)
            .put("generation_tokens_per_second", totalTps ?: JSONObject.NULL)
            .put("decode_tokens_per_second", decodeTps ?: JSONObject.NULL)

        val plan = try {
            AgentPlanParser.parse(raw).also {
                logs.info("برنامه مدل تجزیه شد؛ تعداد مراحل=${it.steps.size} | نیاز به اطلاعات بیشتر=${it.needsClarification}")
            }
        } catch (t: Throwable) {
            logs.error("خروجی مدل JSON معتبر نبود", t)
            return JSONObject().put("report_type", "DINA_AGENT_TEST_REPORT").put("status", "FAILED")
                .put("request", request).put("plan_parse_error", t.message ?: "invalid model output")
                .put("raw_model_output", raw).put("performance", performance).put("simulation_only", true)
                .put("real_accounting_system_touched", false).put("generated_at_epoch_ms", System.currentTimeMillis()).toString(2)
        }

        if (plan.needsClarification) {
            logs.warn("مدل برای ادامه اطلاعات بیشتری درخواست کرده است")
            return report(request, raw, plan, emptyList(), "NEEDS_INPUT", performance)
        }

        if (request.contains("فاکتور") || request.contains("صورتحساب")) {
            val required = listOf("find_customer", "find_product", "calculate_total", "create_invoice", "confirm_invoice")
            val actual = plan.steps.map { it.tool }
            if (actual != required) {
                val reason = "مراحل فاکتور ناقص یا نامرتب است؛ مراحل لازم: ${required.joinToString(" -> ")}"
                logs.error(reason + " | برنامه مدل: ${actual.joinToString(" -> ")}")
                return JSONObject(report(request, raw, plan, emptyList(), "FAILED", performance))
                    .put("plan_validation_error", reason).toString(2)
            }
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
        val finalConfirmed = results.lastOrNull()?.optString("tool") == "confirm_invoice" &&
            results.lastOrNull()?.optBoolean("success", false) == true
        val status = if (failed || ((request.contains("فاکتور") || request.contains("صورتحساب")) && !finalConfirmed)) "FAILED" else "COMPLETED"
        logs.info("پردازش درخواست پایان یافت؛ status=$status")
        return report(request, raw, plan, results, status, performance)
    }

    private fun buildPrompt(request: String) = buildString {
        append("<|im_start|>system\n")
        append("""تو موتور برنامه‌ریز یک دستیار حسابداری آزمایشی هستی، نه چت‌بات.
فقط یک شیء JSON معتبر برگردان؛ هیچ متن یا Markdown بیرون JSON ننویس.
ابزارهای مجاز: find_customer, find_product, calculate_total, create_invoice, confirm_invoice, send_invoice.
برای هر درخواست ساخت فاکتور دقیقاً پنج مرحله زیر را به همین ترتیب تولید کن:
1) find_customer با name مشتری.
2) find_product با name محصول، quantity و unit_price_toman به عدد صحیح تومان.
3) calculate_total با quantity و unit_price_toman.
4) create_invoice با arguments خالی.
5) confirm_invoice به عنوان آخرین مرحله با expected_customer_name، expected_product_name و expected_total_toman.
تأیید نهایی باید آخرین مرحله باشد و مشتری، محصول و مبلغ فاکتور را با درخواست تطبیق دهد.
مثال برای «فاکتور آقای احمدی، سرویس خواب اوونتوس، ۶۱ میلیون و ۸۰۰ هزار تومان»:
{"summary":"فاکتور فروش سرویس خواب اوونتوس برای آقای احمدی","needs_clarification":false,"clarification_questions":[],"steps":[{"id":"1","tool":"find_customer","arguments":{"name":"آقای احمدی"}},{"id":"2","tool":"find_product","arguments":{"name":"سرویس خواب اوونتوس","quantity":1,"unit_price_toman":61800000}},{"id":"3","tool":"calculate_total","arguments":{"quantity":1,"unit_price_toman":61800000}},{"id":"4","tool":"create_invoice","arguments":{}},{"id":"5","tool":"confirm_invoice","arguments":{"expected_customer_name":"آقای احمدی","expected_product_name":"سرویس خواب اوونتوس","expected_total_toman":61800000}}]}
اگر نام مشتری، محصول یا مبلغ مشخص نیست، needs_clarification=true و steps=[] قرار بده و سؤال لازم را در clarification_questions بنویس.
send_invoice را فقط وقتی اضافه کن که کاربر صریحاً درخواست ارسال کرده باشد؛ برای درخواست صرفاً ساخت فاکتور ارسال نکن.
همه عملیات فقط شبیه‌سازی هستند و هیچ سیستم واقعی یا پیام‌رسانی فراخوانی نمی‌شود.""".trimIndent())
        append("<|im_end|>\n<|im_start|>user\n")
        append(request.trim())
        append("<|im_end|>\n<|im_start|>assistant\n<|channel|>final\n")
    }

    private fun report(request: String, raw: String, plan: AgentPlan, steps: List<JSONObject>, status: String, performance: JSONObject) =
        JSONObject().put("report_type", "DINA_AGENT_TEST_REPORT").put("status", status).put("request", request)
            .put("plan_summary", plan.summary).put("clarification_questions", JSONArray(plan.questions))
            .put("planned_steps", JSONArray(plan.steps.map {
                JSONObject().put("id", it.id).put("tool", it.tool).put("arguments", it.arguments)
            }))
            .put("executed_steps", JSONArray(steps)).put("raw_model_output", raw)
            .put("performance", performance)
            .put("model", "Qwen 3.8 2B GGUF").put("runtime", "llama.cpp Android")
            .put("execution_backend", "CPU").put("simulation_only", true)
            .put("real_accounting_system_touched", false)
            .put("generated_at_epoch_ms", System.currentTimeMillis()).toString(2)
}
