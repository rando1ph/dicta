package dev.dicta.prototype.profile

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ProfileEngineTest {
    private val root = File(System.getProperty("dicta.root", ".."))
    private val schema = File(root, "profiles/default/entry.schema.json").readText()
    private val template = File(root, "profiles/default/popup.template.json").readText()
    private val sample = File(root, "profiles/default/example.json").readText()
    private fun engine(s: String = schema, t: String = template) = ProfileEngine(s, t)
    private fun changed(raw: String, key: String, value: JsonElement?): String {
        val map = StrictJson.parse(raw).jsonObject.toMutableMap()
        if (value == null) map.remove(key) else map[key] = value
        return JsonObject(map).toString()
    }
    private fun fails(code: String, block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (e: ProfileFailure) { assertEquals(code, e.code) }
    }
    @Test fun allM0FixturesRender() {
        val e = engine()
        assertEquals("ephemeral", e.decodeAndRender(sample).summary["title"])
        File(root, "docs/m0/examples").listFiles()!!.filter { it.extension == "json" }.forEach {
            val rendered = e.decodeAndRender(it.readText())
            assertTrue(rendered.nodes.isNotEmpty())
            assertEquals(it.nameWithoutExtension, rendered.summary["title"])
        }
    }
    @Test fun duplicateKeysIncludingEscapesRejected() {
        for (raw in listOf("{\"a\":1,\"a\":2}", "{\"a\":1,\"\\u0061\":2}", "{\"n\":{\"a\":1,\"a\":2}}"))
            fails("duplicate_key") { StrictJson.parse(raw) }
    }
    @Test fun invalidJsonRejected() {
        for (raw in listOf("```json\n{}\n```", "{\"a\":}", "[1,]", "{\"x\":1,}", "01", "{\"x\":\"bad\\z\"}", "{}{}", "\"unclosed", "[+2]"))
            fails("invalid_json") { StrictJson.parse(raw) }
    }
    @Test fun blankAndNonfiniteRejected() {
        for (raw in listOf("", " \n")) fails("empty_content") { StrictJson.parse(raw) }
        for (raw in listOf("NaN", "Infinity", "-Infinity")) fails("invalid_number") { StrictJson.parse(raw) }
    }
    @Test fun bytesAndDepthBounded() {
        fails("response_too_large") { StrictJson.parse("中".repeat(44000)) }
        fails("json_too_complex") { StrictJson.parse("[".repeat(34) + "0" + "]".repeat(34)) }
    }
    @Test fun requiredTypeExtraAndBoundsRejected() {
        val e = engine()
        for (raw in listOf(changed(sample, "word", null), changed(sample, "senses", JsonPrimitive("bad")),
            changed(sample, "audioUrl", JsonPrimitive("unexpected")), changed(sample, "senses", JsonArray(emptyList())),
            changed(sample, "word", JsonPrimitive("x".repeat(101))), changed(sample, "synonyms", JsonArray(List(7) { JsonPrimitive("x") }))))
            fails("schema_invalid") { e.decode(raw) }
    }
    @Test fun nullCollapsesEmptyContainer() {
        val raw = changed(sample, "pronunciation", JsonObject(mapOf("uk" to JsonNull, "us" to JsonNull)))
        val result = engine().decodeAndRender(raw)
        assertFalse(result.nodes.any { it.type == "row" && it.children.any { n -> n.style == "ipa" } })
    }
    @Test fun nullRequiredNonnullableRejected() {
        fails("schema_invalid") { engine().decode(changed(sample, "word", JsonNull)) }
    }
    @Test fun typoAndBindingTypeRejected() {
        fails("unmapped_binding") { engine(t = template.replace("/word", "/wrod")) }
        fails("binding_type") { engine(t = template.replace("/word", "/senses")) }
    }
    @Test fun componentsAndActionsWhitelisted() {
        fails("invalid_template") { engine(t = template.replace("\"row\"", "\"webview\"")) }
        fails("invalid_template") { engine(t = template.replace("\"speak\"", "\"execute\"")) }
        fails("invalid_template") { engine(t = template.replace("\"headline\"", "\"script\"")) }
    }
    @Test fun templateShapeRejected() {
        fails("invalid_template") { engine(t = changed(template, "unknown", JsonPrimitive(1))) }
        fails("invalid_template") { engine(t = changed(template, "formatVersion", JsonPrimitive(2))) }
        fails("invalid_template") { engine(t = changed(template, "nodes", JsonArray(emptyList()))) }
        fails("invalid_template") { engine(t = template.replace("\"maxWidthDp\": 480", "\"maxWidthDp\": 900")) }
    }
    @Test fun unsupportedSchemaRejected() {
        fails("unsupported_schema") { engine(s = changed(schema, "\$ref", JsonPrimitive("https://example.invalid"))) }
        fails("unsupported_draft") { engine(s = schema.replace("draft-07", "draft-04")) }
        fails("unsupported_type") { engine(s = schema.replace("\"string\",\n            \"null\"", "\"array\",\n            \"null\"")) }
        fails("unsupported_schema") { engine(s = schema.replace("\"additionalProperties\": false", "\"additionalProperties\": true")) }
    }
    @Test fun optionalBindingRejected() {
        val s = StrictJson.parse(schema).jsonObject
        fails("optional_binding") { engine(s = changed(schema, "required", JsonArray(s.getValue("required").jsonArray.filterNot { it == JsonPrimitive("word") }))) }
        fails("optional_binding") { engine(s = schema.replace("\"minItems\": 1", "\"minItems\": 0")) }
    }
    @Test fun customFieldReorderAndVisibilityRequireNoCodeChange() {
        val s = StrictJson.parse(schema).jsonObject
        val properties = s.getValue("properties").jsonObject + ("note" to buildJsonObject { put("type", "string"); put("maxLength", 100) })
        val customSchema = JsonObject(s + mapOf("properties" to JsonObject(properties), "required" to JsonArray(s.getValue("required").jsonArray + JsonPrimitive("note")))).toString()
        val t = StrictJson.parse(template).jsonObject
        val customNode = buildJsonObject { put("type", "text"); put("bind", "/note") }
        val reversed = t.getValue("nodes").jsonArray.reversed().dropLast(1)
        val customTemplate = changed(template, "nodes", JsonArray(listOf(customNode) + reversed))
        val result = engine(customSchema, customTemplate).decodeAndRender(changed(sample, "note", JsonPrimitive("Custom configured field")))
        assertEquals("Custom configured field", result.nodes.first().value)
        assertFalse(result.nodes.any { n -> n.children.any { it.style == "headline" } })
    }
    @Test fun plainTextNotExecuted() {
        val text = "<script>alert(1)</script>"
        assertEquals(text, engine().decodeAndRender(changed(sample, "word", JsonPrimitive(text))).speech)
    }
    @Test fun unicodeLengthUsesCodePoints() {
        assertEquals(100, engine().decode(changed(sample, "word", JsonPrimitive("😀".repeat(100)))).jsonObject.getValue("word").jsonPrimitive.content.codePointCount(0, 200))
        fails("schema_invalid") { engine().decode(changed(sample, "word", JsonPrimitive("😀".repeat(101)))) }
    }
    @Test fun jsonPointerEscapingAndFailures() {
        assertEquals(JsonPrimitive("ok"), ProfileEngine.resolve(StrictJson.parse("{\"a/b\":{\"~x\":\"ok\"}}"), "/a~1b/~0x"))
        fails("invalid_binding") { ProfileEngine.resolve(JsonObject(emptyMap()), "/bad~2") }
        fails("missing_binding") { ProfileEngine.resolve(JsonArray(listOf(JsonPrimitive(1))), "/01") }
    }
    @Test fun numericAndBooleanSubsetWorks() {
        val s = StrictJson.parse(schema).jsonObject
        val extras = mapOf("count" to StrictJson.parse("{\"type\":\"integer\",\"minimum\":1,\"maximum\":3}"),
            "active" to StrictJson.parse("{\"type\":\"boolean\"}"))
        val custom = JsonObject(s + ("properties" to JsonObject(s.getValue("properties").jsonObject + extras))).toString()
        val e = engine(custom)
        e.decode(changed(changed(sample, "count", JsonPrimitive(2)), "active", JsonPrimitive(true)))
        fails("schema_invalid") { e.decode(changed(sample, "count", JsonPrimitive(2.5))) }
        fails("schema_invalid") { e.decode(changed(sample, "count", JsonPrimitive(4))) }
        fails("schema_invalid") { e.decode(changed(sample, "active", JsonPrimitive("true"))) }
    }
    @Test fun arraysAndStringsBoundedBySchema() {
        fails("unbounded_array") { engine(s = schema.replace("\"maxItems\": 4", "\"maxItems\": 21")) }
        fails("unsupported_schema") { engine(s = schema.replace("\"maxLength\": 100", "\"maxLength\": -1")) }
    }
    @Test fun enumRejected() {
        fails("schema_invalid") { engine().decode(sample.replace("\"established\"", "\"verified\"")) }
    }
    @Test fun nullableEnumStillRejectsNullWhenNotListed() {
        val s = schema.replace("\"minLength\": 1,\n          \"maxLength\": 100", "\"enum\": [\"known\"],\n          \"minLength\": 1,\n          \"maxLength\": 100")
        val raw = changed(sample, "pronunciation", JsonObject(mapOf("uk" to JsonNull, "us" to JsonNull)))
        fails("schema_invalid") { engine(s).decode(raw) }
    }
    @Test fun numericEnumUsesMathematicalEquality() {
        val s = StrictJson.parse(schema).jsonObject
        val props = s.getValue("properties").jsonObject + ("n" to StrictJson.parse("{\"type\":\"number\",\"enum\":[1]}"))
        val e = engine(JsonObject(s + ("properties" to JsonObject(props))).toString())
        e.decode(changed(sample, "n", StrictJson.parse("1.0")))
    }
    @Test fun numericEnumWithEquivalentMembersIsLegalDraft7Schema() {
        val s = StrictJson.parse(schema).jsonObject
        val props = s.getValue("properties").jsonObject + ("n" to StrictJson.parse("{\"type\":\"number\",\"enum\":[1,1.0]}"))
        engine(JsonObject(s + ("properties" to JsonObject(props))).toString()).decode(changed(sample, "n", JsonPrimitive(1)))
    }
    @Test fun templateUnicodeLengthsUseCodePoints() {
        engine(t = changed(template, "id", JsonPrimitive("😀".repeat(100))))
        fails("invalid_template") { engine(t = changed(template, "id", JsonPrimitive("😀".repeat(101)))) }
    }
    @Test fun expandedTreeBudgetPreventsMultiplicativeRendering() {
        val s = StrictJson.parse(schema).jsonObject
        val nested = StrictJson.parse("""{"type":"array","maxItems":20,"items":{"type":"object","properties":{"inner":{"type":"array","maxItems":20,"items":{"type":"object","properties":{"value":{"type":"string"}},"required":["value"],"additionalProperties":false}}},"required":["inner"],"additionalProperties":false}}""")
        val boundedSchema = JsonObject(s + mapOf("properties" to JsonObject(s.getValue("properties").jsonObject + ("custom" to nested)),
            "required" to JsonArray(s.getValue("required").jsonArray + JsonPrimitive("custom")))).toString()
        val largeNodes = StrictJson.parse("""[{"type":"repeat","bind":"/custom","children":[{"type":"repeat","bind":"/inner","children":[{"type":"text","bind":"/value"},{"type":"text","bind":"/value"}]}]}]""")
        val item = buildJsonObject { put("value", "test") }
        val row = buildJsonObject { put("inner", JsonArray(List(20) { item })) }
        val raw = changed(sample, "custom", JsonArray(List(20) { row }))
        fails("render_too_large") { engine(boundedSchema, changed(template, "nodes", largeNodes)).decodeAndRender(raw) }
    }
}
