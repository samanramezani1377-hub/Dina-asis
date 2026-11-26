package com.samanramezani.dina.agent

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Local-only mocks. No real accounting system or network is accessed. */
class SimulatedAccountingTools {
    private var customer: JSONObject? = null
    private var product: JSONObject? = null
    private var invoice: JSONObject? = null

    fun execute(step: PlannedStep): ToolResult = when (step.tool) {
        "find_customer" -> findCustomer(step.arguments)
        "find_product" -> findProduct(step.arguments)
        "calculate_total" -> calculateTotal(step.arguments)
        "create_invoice" -> createInvoice()
        "confirm_invoice" -> confirmInvoice(step.arguments)
        "send_invoice" -> simulateSend(step.arguments)
        else -> ToolResult(false, "ابزار ناشناخته")
    }

    private fun findCustomer(args: JSONObject): ToolResult {
        val name = args.optString("name").trim()
        if (name.isBlank()) return ToolResult(false, "نام مشتری مشخص نشده است.")
        customer = JSONObject().put("id", "TEST-CUSTOMER-001").put("name", name).put("record_type", "MOCK")
        return ToolResult(true, "مشتری آزمایشی پیدا شد: $name", customer!!)
    }

    private fun findProduct(args: JSONObject): ToolResult {
        val name = args.optString("name").trim()
        val quantity = args.optInt("quantity", 1)
        val unitPrice = args.optLong("unit_price_toman", 0L)
        if (name.isBlank()) return ToolResult(false, "نام محصول مشخص نشده است.")
        if (quantity <= 0 || unitPrice <= 0L) return ToolResult(false, "تعداد یا قیمت واحد محصول معتبر نیست.")
        product = JSONObject().put("id", "TEST-PRODUCT-A").put("name", name)
            .put("quantity", quantity).put("unit_price_toman", unitPrice).put("record_type", "MOCK")
        return ToolResult(true, "محصول آزمایشی ثبت شد: $name × $quantity؛ هر واحد $unitPrice تومان", product!!)
    }

    private fun calculateTotal(args: JSONObject): ToolResult {
        val current = product ?: return ToolResult(false, "ابتدا محصول باید پیدا شود.")
        val quantity = args.optInt("quantity", current.optInt("quantity", 1))
        val unit = args.optLong("unit_price_toman", current.optLong("unit_price_toman", 0L))
        if (quantity <= 0 || unit <= 0L) return ToolResult(false, "تعداد یا قیمت برای محاسبه معتبر نیست.")
        current.put("quantity", quantity).put("unit_price_toman", unit)
        val total = quantity.toLong() * unit
        return ToolResult(true, "جمع آزمایشی: $total تومان", JSONObject()
            .put("quantity", quantity).put("unit_price_toman", unit).put("total_toman", total))
    }

    private fun createInvoice(): ToolResult {
        val c = customer ?: return ToolResult(false, "مشتری آزمایشی پیدا نشده است.")
        val p = product ?: return ToolResult(false, "محصول آزمایشی پیدا نشده است.")
        val total = p.optLong("quantity", 1) * p.optLong("unit_price_toman", 0L)
        if (total <= 0L) return ToolResult(false, "مبلغ فاکتور معتبر نیست.")
        invoice = JSONObject()
            .put("invoice_id", "TEST-INV-" + UUID.randomUUID().toString().take(8).uppercase())
            .put("customer", c).put("items", JSONArray().put(p))
            .put("total_toman", total).put("status", "DRAFT_SIMULATED")
        return ToolResult(true, "پیش‌نویس فاکتور آزمایشی ساخته شد؛ مبلغ $total تومان", invoice!!)
    }

    private fun confirmInvoice(args: JSONObject): ToolResult {
        val inv = invoice ?: return ToolResult(false, "پیش‌نویس فاکتور برای تأیید وجود ندارد.")
        val c = inv.optJSONObject("customer") ?: return ToolResult(false, "مشتری فاکتور موجود نیست.")
        val items = inv.optJSONArray("items") ?: return ToolResult(false, "اقلام فاکتور موجود نیست.")
        val item = items.optJSONObject(0) ?: return ToolResult(false, "محصول فاکتور موجود نیست.")
        val expectedCustomer = args.optString("expected_customer_name")
        val expectedProduct = args.optString("expected_product_name")
        val expectedTotal = args.optLong("expected_total_toman", -1L)
        val actualTotal = inv.optLong("total_toman", -1L)
        if (expectedCustomer.isBlank() || expectedProduct.isBlank() || expectedTotal < 0L) {
            return ToolResult(false, "مدل اطلاعات مورد انتظار برای تأیید نهایی را کامل ارائه نکرد.")
        }
        if (c.optString("name") != expectedCustomer || item.optString("name") != expectedProduct || actualTotal != expectedTotal) {
            return ToolResult(false, "تأیید نهایی رد شد؛ اطلاعات فاکتور با درخواست مدل مطابقت ندارد.",
                JSONObject().put("expected_total_toman", expectedTotal).put("actual_total_toman", actualTotal)
                    .put("customer_match", c.optString("name") == expectedCustomer)
                    .put("product_match", item.optString("name") == expectedProduct))
        }
        inv.put("status", "CONFIRMED_SIMULATED")
            .put("confirmation", "MODEL_PLANNED_FINAL_VALIDATION")
            .put("confirmed_total_toman", actualTotal)
        return ToolResult(true, "تأیید نهایی مدل موفق بود؛ فاکتور آزمایشی با مبلغ $actualTotal تومان تأیید شد.", inv)
    }

    private fun simulateSend(args: JSONObject): ToolResult {
        val inv = invoice ?: return ToolResult(false, "فاکتور آزمایشی برای ارسال وجود ندارد.")
        if (inv.optString("status") != "CONFIRMED_SIMULATED") {
            return ToolResult(false, "فاکتور قبل از ارسال باید تأیید نهایی شود.")
        }
        val channel = args.optString("channel", "ارسال آزمایشی")
        inv.put("status", "SENT_SIMULATED").put("delivery_channel", channel)
        return ToolResult(true, "ارسال فقط شبیه‌سازی شد؛ هیچ فاکتور واقعی ارسال نشد.",
            JSONObject().put("invoice_id", inv.getString("invoice_id"))
                .put("delivery_status", "SIMULATED_ONLY").put("channel", channel))
    }
}
