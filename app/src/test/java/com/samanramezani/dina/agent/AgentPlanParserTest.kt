package com.samanramezani.dina.agent
import org.junit.Assert.*
import org.junit.Test
class AgentPlanParserTest {
 @Test fun parsesJsonWrappedInModelText() {
  val p=AgentPlanParser.parse("""<|channel|>final
{"summary":"ساخت فاکتور","needs_clarification":false,"clarification_questions":[],"steps":[{"id":"1","tool":"find_customer","arguments":{"name":"آقای فلانی"}}]}""")
  assertEquals("ساخت فاکتور",p.summary); assertEquals("find_customer",p.steps.single().tool)
 }
 @Test(expected=IllegalArgumentException::class) fun rejectsUnknownTools() {
  AgentPlanParser.parse("""{"summary":"x","needs_clarification":false,"steps":[{"tool":"send_real_email","arguments":{}}]}""")
 }
 @Test fun acceptsClarification() {
  val p=AgentPlanParser.parse("""{"summary":"اطلاعات ناقص","needs_clarification":true,"clarification_questions":["نام مشتری چیست؟"],"steps":[]}""")
  assertTrue(p.needsClarification); assertEquals(1,p.questions.size)
 }
}
