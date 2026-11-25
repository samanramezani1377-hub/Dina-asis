package com.samanramezani.dina.agent
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
/** Local-only mocks. No real accounting system or network is accessed. */
class SimulatedAccountingTools {
 private var customer:JSONObject?=null
 private var product:JSONObject?=null
 private var invoice:JSONObject?=null
 fun execute(step:PlannedStep):ToolResult=when(step.tool) {
  "find_customer"->findCustomer(step.arguments)
  "find_product"->findProduct(step.arguments)
  "calculate_total"->calculateTotal(step.arguments)
  "create_invoice"->createInvoice()
  "send_invoice"->simulateSend(step.arguments)
  else->ToolResult(false,"ابزار ناشناخته")
 }
 private fun findCustomer(args:JSONObject):ToolResult {
  val name=args.optString("name","آقای فلانی").ifBlank { "آقای فلانی" }
  customer=JSONObject().put("id","TEST-CUSTOMER-001").put("name",name).put("record_type","MOCK")
  return ToolResult(true,"مشتری آزمایشی پیدا شد: $name",customer!!)
 }
 private fun findProduct(args:JSONObject):ToolResult {
  val name=args.optString("name","محصول A").ifBlank { "محصول A" }; val quantity=args.optInt("quantity",1).coerceAtLeast(1)
  product=JSONObject().put("id","TEST-PRODUCT-A").put("name",name).put("quantity",quantity).put("unit_price_toman",250000).put("record_type","MOCK")
  return ToolResult(true,"محصول آزمایشی ثبت شد: $name × $quantity (هر واحد ۲۵۰٬۰۰۰ تومان)",product!!)
 }
 private fun calculateTotal(args:JSONObject):ToolResult {
  val quantity=args.optInt("quantity",product?.optInt("quantity",1) ?: 1).coerceAtLeast(1)
  val unit=args.optLong("unit_price_toman",product?.optLong("unit_price_toman",250000L) ?: 250000L)
  return ToolResult(true,"جمع آزمایشی: ${quantity*unit} تومان",JSONObject().put("quantity",quantity).put("unit_price_toman",unit).put("total_toman",quantity*unit))
 }
 private fun createInvoice():ToolResult {
  val c=customer ?: return ToolResult(false,"مشتری آزمایشی پیدا نشده است.")
  val p=product ?: return ToolResult(false,"محصول آزمایشی پیدا نشده است.")
  val total=p.optLong("quantity",1)*p.optLong("unit_price_toman",250000)
  invoice=JSONObject().put("invoice_id","TEST-INV-" + UUID.randomUUID().toString().take(8).uppercase()).put("customer",c).put("items",JSONArray().put(p)).put("total_toman",total).put("status","DRAFT_SIMULATED")
  return ToolResult(true,"پیش‌نویس فاکتور آزمایشی ساخته شد؛ مبلغ $total تومان",invoice!!)
 }
 private fun simulateSend(args:JSONObject):ToolResult {
  val inv=invoice ?: return ToolResult(false,"فاکتور آزمایشی برای ارسال وجود ندارد.")
  val channel=args.optString("channel","ارسال آزمایشی"); inv.put("status","SENT_SIMULATED").put("delivery_channel",channel)
  return ToolResult(true,"ارسال فقط شبیه‌سازی شد؛ هیچ فاکتور واقعی ارسال نشد.",JSONObject().put("invoice_id",inv.getString("invoice_id")).put("delivery_status","SIMULATED_ONLY").put("channel",channel))
 }
}
