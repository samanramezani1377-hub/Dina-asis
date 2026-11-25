package com.samanramezani.dina.agent
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class SimulatedAccountingToolsTest {
 @Test fun createsAndSendsOnlyMockInvoice() {
  val tools=SimulatedAccountingTools()
  assertTrue(tools.execute(PlannedStep("1","find_customer",JSONObject().put("name","آقای فلانی"))).success)
  assertTrue(tools.execute(PlannedStep("2","find_product",JSONObject().put("name","محصول A").put("quantity",2))).success)
  val invoice=tools.execute(PlannedStep("3","create_invoice",JSONObject()))
  assertTrue(invoice.success); assertEquals(500000L,invoice.data.getLong("total_toman"))
  val sent=tools.execute(PlannedStep("4","send_invoice",JSONObject()))
  assertTrue(sent.success); assertEquals("SIMULATED_ONLY",sent.data.getString("delivery_status"))
 }
 @Test fun blocksInvoiceWithoutMockRecords() {
  assertFalse(SimulatedAccountingTools().execute(PlannedStep("1","create_invoice",JSONObject())).success)
 }
}
