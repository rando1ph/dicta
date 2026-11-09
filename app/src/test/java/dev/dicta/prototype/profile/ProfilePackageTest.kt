package dev.dicta.prototype.profile

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlinx.serialization.json.*

class ProfilePackageTest {
    private val root=File(System.getProperty("dicta.root"),"profiles/default")
    private fun draft()=ProfileDraft("Custom",File(root,"prompt.md").readText(),File(root,"entry.schema.json").readText(),File(root,"popup.template.json").readText(),File(root,"example.json").readText())
    @Test fun roundTripKeepsHashAndPreviewWithoutCredentials() {
        val original=draft();val restored=ProfileDraft.import(original.export())
        assertEquals(original.hash(),restored.hash());assertEquals(original.preview(),restored.preview())
        val keys=Json.parseToJsonElement(original.export()).jsonObject.keys
        assertFalse("apiKey" in keys);assertFalse("endpoint" in keys)
    }
    @Test fun unknownFormatAndCredentialFieldsRejected() {
        for(raw in listOf(draft().export().replace("\"formatVersion\":1","\"formatVersion\":99"),draft().export().dropLast(1)+",\"apiKey\":\"test\"}")) {
            assertThrows(ProfileFailure::class.java) {ProfileDraft.import(raw)}
        }
    }
    @Test fun invalidPromptSchemaAndBindingsCannotActivate() {
        val original=draft()
        for(bad in listOf(original.copy(prompt="no variables"),original.copy(schema="{bad"),original.copy(template=original.template.replace("/word","/missing")),original.copy(example="{}"),original.copy(model="bad\nmodel"))) {
            assertThrows(Exception::class.java) {bad.preview()}
        }
    }
    @Test fun customFieldsAndOrderNeedNoKotlinChanges() {
        val original=draft()
        val schema="""{"type":"object","properties":{"term":{"type":"string"},"meaning":{"type":"string"}},"required":["term","meaning"],"additionalProperties":false}"""
        val obj=Json.parseToJsonElement(original.template).jsonObject.toMutableMap()
        obj["summary"]=buildJsonObject {put("title","/term");put("subtitle","/meaning");put("secondary","/term");put("badge","/term")}
        obj["speech"]=JsonPrimitive("/term")
        obj["nodes"]=buildJsonArray {add(buildJsonObject {put("type","text");put("bind","/meaning");put("style","definition")});add(buildJsonObject {put("type","text");put("bind","/term");put("style","headline")})}
        val custom=original.copy(schema=schema,template=JsonObject(obj).toString(),example="""{"term":"verdant","meaning":"青翠的"}""")
        val result=custom.preview()
        assertEquals("verdant",result.summary["title"]);assertEquals(listOf("青翠的","verdant"),result.nodes.map {it.value})
        assertEquals(custom.preview(),ProfileDraft.import(custom.export()).preview())
    }
    @Test fun promptExpansionIsSinglePassAndRenamingKeepsCacheIdentity() {
        val original=draft()
        assertEquals(original.hash(),original.copy(name="Renamed").hash())
        val customized=original.copy(schema=original.schema.replace("Dicta default lexical entry v1", "{{example_json}}"))
        assertTrue(customized.systemPrompt().contains("{{example_json}}"))
    }
    @Test fun malformedOrOversizePackagesFailClosed() {
        assertThrows(ProfileFailure::class.java) {ProfileDraft.import("x".repeat(262145))}
        assertThrows(ProfileFailure::class.java) {ProfileDraft.import("{\"formatVersion\":1,\"formatVersion\":1}")}
    }
}
