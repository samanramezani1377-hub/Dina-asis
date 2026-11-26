package com.samanramezani.dina.agent

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SimulatedAccountingToolsTest {
    @Test fun invoiceRequiresFinalConfirmationBeforeSimulatedSend() {
        val tools = SimulatedAccountingTools()

        val customer = tools.execute(PlannedStep(
            "1", "find_customer", JSONObject().put("name", "آقای احمدی")
        ))
        assertTrue(customer.success)

        val product = tools.execute(PlannedStep(
            "2", "find_product",
            JSONObject().put("name", "سرویس خواب اوونتوس")
                .put("quantity", 1).put("unit_price_toman", 61_800_000L)
        ))
        assertTrue(product.success)

        val total = tools.execute(PlannedStep(
            "3", "calculate_total",
            JSONObject().put("quantity", 1).put("unit_price_toman", 61_800_000L)
        ))
        assertTrue(total.success)
        assertEquals(61_800_000L, total.data.getLong("total_toman"))

        val invoice = tools.execute(PlannedStep("4", "create_invoice", JSONObject()))
        assertTrue(invoice.success)
        assertEquals(61_800_000L, invoice.data.getLong("total_toman"))
        assertEquals("DRAFT_SIMULATED", invoice.data.getString("status"))

        val blockedSend = tools.execute(PlannedStep("5", "send_invoice", JSONObject()))
        assertFalse(blockedSend.success)

        val confirmed = tools.execute(PlannedStep(
            "6", "confirm_invoice",
            JSONObject().put("expected_customer_name", "آقای احمدی")
                .put("expected_product_name", "سرویس خواب اوونتوس")
                .put("expected_total_toman", 61_800_000L)
        ))
        assertTrue(confirmed.success)
        assertEquals("CONFIRMED_SIMULATED", confirmed.data.getString("status"))

        val sent = tools.execute(PlannedStep("7", "send_invoice", JSONObject()))
        assertTrue(sent.success)
        assertEquals("SIMULATED_ONLY", sent.data.getString("delivery_status"))
    }

    @Test fun rejectsFinalConfirmationWhenAmountDoesNotMatch() {
        val tools = SimulatedAccountingTools()
        assertTrue(tools.execute(PlannedStep(
            "1", "find_customer", JSONObject().put("name", "آقای احمدی")
        )).success)
        assertTrue(tools.execute(PlannedStep(
            "2", "find_product",
            JSONObject().put("name", "سرویس خواب اوونتوس")
                .put("quantity", 1).put("unit_price_toman", 61_800_000L)
        )).success)
        assertTrue(tools.execute(PlannedStep("3", "create_invoice", JSONObject())).success)

        val result = tools.execute(PlannedStep(
            "4", "confirm_invoice",
            JSONObject().put("expected_customer_name", "آقای احمدی")
                .put("expected_product_name", "سرویس خواب اوونتوس")
                .put("expected_total_toman", 61_000_000L)
        ))
        assertFalse(result.success)
    }

    @Test fun blocksInvoiceWithoutMockRecords() {
        assertFalse(
            SimulatedAccountingTools().execute(
                PlannedStep("1", "create_invoice", JSONObject())
            ).success
        )
    }

    @Test fun rejectsProductWithoutExplicitPositivePrice() {
        val result = SimulatedAccountingTools().execute(PlannedStep(
            "1", "find_product", JSONObject().put("name", "محصول بدون قیمت")
        ))
        assertFalse(result.success)
    }
}
