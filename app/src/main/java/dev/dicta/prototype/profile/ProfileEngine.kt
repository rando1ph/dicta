package dev.dicta.prototype.profile

import kotlinx.serialization.json.*
import java.math.BigDecimal

/** Error codes contain no input, response body, or credential data. */
class ProfileFailure(val code: String) : IllegalArgumentException(code)
private fun fail(code: String): Nothing = throw ProfileFailure(code)
private fun ensure(ok: Boolean, code: String) { if (!ok) fail(code) }
private fun JsonElement.obj(code: String = "invalid_template") = this as? JsonObject ?: fail(code)
private fun JsonElement.arr(code: String = "invalid_template") = this as? JsonArray ?: fail(code)
private fun JsonElement.str(code: String = "invalid_template"): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fail(code)
private fun JsonObject.text(key: String, code: String = "invalid_template") = get(key)?.str(code) ?: fail(code)
private fun JsonObject.integer(key: String, default: Int): Int =
    get(key)?.let { (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.intOrNull ?: fail("invalid_template") } ?: default

data class RenderNode(
    val type: String,
    val style: String = "body",
    val label: String? = null,
    val value: String? = null,
    val values: List<String> = emptyList(),
    val children: List<RenderNode> = emptyList(),
    val items: List<List<RenderNode>> = emptyList(),
)
data class RenderedEntry(val summary: Map<String, String>, val nodes: List<RenderNode>, val speech: String)

/** A bounded strict JSON parser: kotlinx.serialization alone silently accepts duplicate keys. */
object StrictJson {
    fun parse(raw: String, maxBytes: Int = 131072): JsonElement {
        ensure(raw.toByteArray(Charsets.UTF_8).size <= maxBytes, "response_too_large")
        if (raw.isBlank()) fail("empty_content")
        return Parser(raw).parse()
    }
    private class Parser(val raw: String) {
        var at = 0
        var nodes = 0
        fun ws() { while (at < raw.length && raw[at] in " \r\n\t") at++ }
        fun take(c: Char): Boolean { ws(); return if (at < raw.length && raw[at] == c) { at++; true } else false }
        fun require(c: Char) { ensure(take(c), "invalid_json") }
        fun parse(): JsonElement { val result = value(0); ws(); ensure(at == raw.length, "invalid_json"); return result }
        fun string(): String {
            ws(); val start = at; require('"')
            while (at < raw.length) {
                val c = raw[at++]
                if (c == '"') {
                    return try { Json.parseToJsonElement(raw.substring(start, at)).jsonPrimitive.content }
                    catch (_: Exception) { fail("invalid_json") }
                }
                ensure(c.code >= 32, "invalid_json")
                if (c == '\\') {
                    ensure(at < raw.length, "invalid_json")
                    val escape = raw[at++]
                    ensure(escape in "\"\\/bfnrtu", "invalid_json")
                    if (escape == 'u') {
                        ensure(at + 4 <= raw.length && raw.substring(at, at + 4).all { it in "0123456789abcdefABCDEF" }, "invalid_json")
                        at += 4
                    }
                }
            }
            fail("invalid_json")
        }
        fun value(depth: Int): JsonElement {
            ensure(depth <= 32 && ++nodes <= 20000, "json_too_complex")
            ws(); ensure(at < raw.length, "invalid_json")
            return when (raw[at]) {
                '{' -> {
                    at++; val map = linkedMapOf<String, JsonElement>()
                    if (!take('}')) do {
                        val key = string(); ensure(key !in map, "duplicate_key"); require(':')
                        map[key] = value(depth + 1)
                        if (take('}')) break
                        require(',')
                    } while (true)
                    JsonObject(map)
                }
                '[' -> {
                    at++; val list = mutableListOf<JsonElement>()
                    if (!take(']')) do {
                        list += value(depth + 1)
                        if (take(']')) break
                        require(',')
                    } while (true)
                    JsonArray(list)
                }
                '"' -> JsonPrimitive(string())
                else -> {
                    val start = at
                    while (at < raw.length && raw[at] !in ",]} \r\n\t") at++
                    val token = raw.substring(start, at)
                    when (token) {
                        "null" -> JsonNull
                        "true" -> JsonPrimitive(true)
                        "false" -> JsonPrimitive(false)
                        "NaN", "Infinity", "-Infinity" -> fail("invalid_number")
                        else -> {
                            ensure(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?").matches(token), "invalid_json")
                            try { token.toBigDecimal() } catch (_: NumberFormatException) { fail("invalid_number") }
                            Json.parseToJsonElement(token)
                        }
                    }
                }
            }
        }
    }
}

/** Implements only the documented M0 Draft-07 subset; unsupported keywords fail closed. */
class ProfileEngine(schemaRaw: String, templateRaw: String) {
    val schema = StrictJson.parse(schemaRaw).obj("unsupported_schema")
    val template = StrictJson.parse(templateRaw).obj()
    init { checkSchema(schema); checkTemplate() }

    fun decode(raw: String): JsonElement = StrictJson.parse(raw).also { validate(it, schema) }
    fun decodeAndRender(raw: String): RenderedEntry = render(decode(raw))
    fun render(data: JsonElement): RenderedEntry {
        validate(data, schema)
        var expanded = 0
        fun walk(nodes: JsonArray, scope: JsonElement): List<RenderNode> = nodes.mapNotNull { element ->
            ensure(++expanded <= 500, "render_too_large")
            val node = element.jsonObject
            val type = node.text("type")
            val value = node["bind"]?.let { resolve(scope, it.str()) }
            val style = node["style"]?.str() ?: "body"
            val label = node["label"]?.str()
            if (node["hideIfEmpty"] == JsonPrimitive(true) && (value == JsonNull || value == JsonPrimitive("") || value == JsonArray(emptyList()))) null
            else when (type) {
                "row", "column" -> {
                    val children = walk(node.getValue("children").jsonArray, scope)
                    if (children.isEmpty()) null else RenderNode(type, style, label, children = children)
                }
                "repeat" -> {
                    val items = value!!.jsonArray.map { walk(node.getValue("children").jsonArray, it) }.filter { it.isNotEmpty() }
                    if (items.isEmpty()) null else RenderNode(type, style, label, items = items)
                }
                "chips" -> RenderNode(type, style, label, values = value!!.jsonArray.mapNotNull { if (it == JsonNull) null else it.str() })
                else -> RenderNode(type, style, label, value = value?.takeUnless { it == JsonNull }?.str())
            }
        }
        fun plain(pointer: JsonElement): String = resolve(data, pointer.str()).let { if (it == JsonNull) "" else it.str() }
        return RenderedEntry(template.getValue("summary").jsonObject.mapValues { plain(it.value) },
            walk(template.getValue("nodes").jsonArray, data), plain(template.getValue("speech")))
    }

    private fun checkSchema(s: JsonObject, depth: Int = 0) {
        val allowed = setOf("\$schema", "title", "description", "type", "properties", "required", "additionalProperties", "items", "enum", "minLength", "maxLength", "minItems", "maxItems", "minimum", "maximum")
        ensure(depth <= 12 && s.keys.all { it in allowed }, "unsupported_schema")
        ensure(s["\$schema"]?.str("unsupported_schema")?.let { it == "http://json-schema.org/draft-07/schema#" } != false, "unsupported_draft")
        val type = s["type"] ?: fail("unsupported_type")
        ensure(type == nullableText || (type is JsonPrimitive && type.isString && type.content in types), "unsupported_type")
        for (key in listOf("title", "description")) s[key]?.str("unsupported_schema")
        for (key in listOf("minLength", "maxLength", "minItems", "maxItems")) s[key]?.let {
            ensure(it is JsonPrimitive && !it.isString && it.intOrNull != null && it.int >= 0, "unsupported_schema")
        }
        for (key in listOf("minimum", "maximum")) s[key]?.let { ensure(number(it) != null, "unsupported_schema") }
        for ((low, high) in listOf("minLength" to "maxLength", "minItems" to "maxItems", "minimum" to "maximum")) {
            if (low in s && high in s) ensure(number(s.getValue(low))!! <= number(s.getValue(high))!!, "unsupported_schema")
        }
        s["enum"]?.let { ensure(it is JsonArray, "unsupported_schema") }
        s["properties"]?.let { props -> props.obj("unsupported_schema").values.forEach { checkSchema(it.obj("unsupported_schema"), depth + 1) } }
        s["required"]?.let { req ->
            val required = req.arr("unsupported_schema").map { it.str("unsupported_schema") }
            ensure(required.distinct().size == required.size, "unsupported_schema")
        }
        s["additionalProperties"]?.let { ensure(it == JsonPrimitive(false), "unsupported_schema") }
        if (type == JsonPrimitive("object")) ensure(s["additionalProperties"] == JsonPrimitive(false), "unbounded_object")
        if (type == JsonPrimitive("array")) {
            ensure((s["maxItems"] as? JsonPrimitive)?.intOrNull?.let { it <= 20 } == true, "unbounded_array")
            ensure("items" in s, "unsupported_schema")
        }
        s["items"]?.let { checkSchema(it.obj("unsupported_schema"), depth + 1) }
    }
    private fun validate(data: JsonElement, s: JsonObject) {
        val type = s.getValue("type")
        val kind = if (type == nullableText) { if (data == JsonNull) "null" else "string" } else type.str()
        val validType = when (kind) {
            "object" -> data is JsonObject
            "array" -> data is JsonArray
            "string" -> data is JsonPrimitive && data.isString
            "boolean" -> data is JsonPrimitive && !data.isString && data.booleanOrNull != null
            "null" -> data == JsonNull
            "number" -> number(data) != null
            "integer" -> number(data)?.stripTrailingZeros()?.scale()?.let { it <= 0 } == true
            else -> false
        }
        ensure(validType, "schema_invalid")
        s["enum"]?.let { ensure(it.jsonArray.any { candidate -> equivalent(data, candidate) }, "schema_invalid") }
        when (kind) {
            "object" -> {
                val obj = data.jsonObject; val properties = s["properties"]?.jsonObject ?: JsonObject(emptyMap())
                ensure(obj.keys.all { it in properties }, "schema_invalid")
                ensure(s["required"]?.jsonArray?.all { it.str() in obj } != false, "schema_invalid")
                obj.forEach { (key, value) -> validate(value, properties.getValue(key).jsonObject) }
            }
            "array" -> {
                val list = data.jsonArray
                bounds(list.size.toBigDecimal(), s, "minItems", "maxItems")
                list.forEach { validate(it, s.getValue("items").jsonObject) }
            }
            "string" -> bounds(data.str().let { it.codePointCount(0, it.length) }.toBigDecimal(), s, "minLength", "maxLength")
            "number", "integer" -> bounds(number(data)!!, s, "minimum", "maximum")
        }
    }
    private fun bounds(value: BigDecimal, s: JsonObject, min: String, max: String) {
        ensure(s[min]?.let { value >= number(it)!! } != false && s[max]?.let { value <= number(it)!! } != false, "schema_invalid")
    }
    private fun checkTemplate() {
        keys(template, setOf("formatVersion", "id", "summary", "containers", "nodes", "actions", "speech"))
        ensure(template["formatVersion"] == JsonPrimitive(1), "invalid_template")
        ensure(codePoints(template.text("id")) in 1..100, "invalid_template")
        val summary = template.getValue("summary").obj()
        keys(summary, setOf("title", "subtitle", "secondary", "badge"))
        (summary.values + template.getValue("speech")).forEach { ensure(isText(atSchema(schema, it.str())), "binding_type") }
        val containers = template.getValue("containers").obj()
        keys(containers, setOf("popup", "home"))
        for (name in listOf("popup", "home")) {
            val c = containers.getValue(name).obj()
            keys(c, if (name == "popup") setOf("maxWidthDp", "marginDp", "cornerDp", "scroll") else setOf("cornerDp", "scroll"))
            ensure(c["scroll"] == JsonPrimitive(true) && c.integer("cornerDp", -1) in 0..48, "invalid_template")
            if (name == "popup") ensure(c.integer("maxWidthDp", 0) in 240..800 && c.integer("marginDp", 0) in 8..48, "invalid_template")
        }
        val actions = template.getValue("actions").obj()
        keys(actions, setOf("popup", "home"))
        for (name in listOf("popup", "home")) {
            val list = actions.getValue(name).arr().map { it.str() }
            val allowed = if (name == "popup") setOf("speak", "save", "details", "close") else setOf("speak", "save", "shuffle")
            ensure(list.distinct().size == list.size && list.all { it in allowed }, "invalid_template")
        }
        var count = 0
        fun walk(nodes: JsonArray, scope: JsonObject, depth: Int) {
            ensure(depth <= 12, "template_too_deep")
            ensure(nodes.size in 1..30, "invalid_template")
            nodes.forEach { element ->
                ensure(++count <= 128, "template_too_large")
                val n = element.obj(); val kind = n.text("type")
                ensure(n.keys.all { it in setOf("type", "bind", "label", "style", "hideIfEmpty", "children") }, "invalid_template")
                ensure(kind in setOf("row", "column", "repeat", "text", "badge", "chips"), "invalid_template")
                n["label"]?.let { ensure(codePoints(it.str()) in 1..120, "invalid_template") }
                n["style"]?.let { ensure(it.str() in setOf("body", "headline", "grammar", "ipa", "definition", "quote", "muted", "etymology"), "invalid_template") }
                n["hideIfEmpty"]?.let { ensure(it == JsonPrimitive(true) || it == JsonPrimitive(false), "invalid_template") }
                if (kind == "row" || kind == "column") {
                    ensure("bind" !in n && "children" in n, "invalid_template")
                    walk(n.getValue("children").arr(), scope, depth + 1)
                } else {
                    val target = atSchema(scope, n.text("bind"))
                    if (kind == "repeat" || kind == "chips") ensure(target["type"] == JsonPrimitive("array"), "binding_type")
                    else ensure(isText(target), "binding_type")
                    if (kind == "repeat") walk(n["children"]?.arr() ?: fail("invalid_template"), target.getValue("items").jsonObject, depth + 1)
                    else {
                        ensure("children" !in n, "invalid_template")
                        if (kind == "chips") ensure(isText(target.getValue("items").jsonObject), "binding_type")
                    }
                }
            }
        }
        walk(template.getValue("nodes").arr(), schema, 0)
    }

    companion object {
        private val types = setOf("object", "array", "string", "number", "integer", "boolean", "null")
        private val nullableText = JsonArray(listOf(JsonPrimitive("string"), JsonPrimitive("null")))
        private fun isText(s: JsonObject) = s["type"] == JsonPrimitive("string") || s["type"] == nullableText
        private fun number(data: JsonElement): BigDecimal? = (data as? JsonPrimitive)?.takeUnless { it.isString || it == JsonNull }?.content?.toBigDecimalOrNull()
        private fun codePoints(value: String) = value.codePointCount(0, value.length)
        // Draft-07 enum compares JSON numbers mathematically, including nested values.
        private fun equivalent(a: JsonElement, b: JsonElement): Boolean {
            val left = number(a); val right = number(b)
            if (left != null && right != null) return left.compareTo(right) == 0
            if (a is JsonObject && b is JsonObject)
                return a.keys == b.keys && a.all { (key, value) -> equivalent(value, b.getValue(key)) }
            if (a is JsonArray && b is JsonArray)
                return a.size == b.size && a.indices.all { equivalent(a[it], b[it]) }
            return a == b
        }
        private fun keys(obj: JsonObject, required: Set<String>) { ensure(obj.keys == required, "invalid_template") }
        private fun tokens(pointer: String): List<String> {
            ensure(pointer.startsWith('/') && !Regex("~(?![01])").containsMatchIn(pointer), "invalid_binding")
            return pointer.substring(1).split('/').map { it.replace("~1", "/").replace("~0", "~") }
        }
        private fun index(token: String): Int? = token.takeIf { it == "0" || it.matches(Regex("[1-9][0-9]*")) }?.toIntOrNull()
        private fun atSchema(root: JsonObject, pointer: String): JsonObject {
            var s = root
            for (key in tokens(pointer)) {
                s = when (s["type"]) {
                    JsonPrimitive("array") -> {
                        val i = index(key) ?: fail("unmapped_binding")
                        ensure(i < s.integer("minItems", 0), "optional_binding")
                        s.getValue("items").jsonObject
                    }
                    JsonPrimitive("object") -> {
                        val props = s["properties"]?.jsonObject ?: fail("unmapped_binding")
                        ensure(key in props, "unmapped_binding")
                        ensure(s["required"]?.jsonArray?.contains(JsonPrimitive(key)) == true, "optional_binding")
                        props.getValue(key).jsonObject
                    }
                    else -> fail("unmapped_binding")
                }
            }
            return s
        }
        fun resolve(root: JsonElement, pointer: String): JsonElement {
            var value = root
            for (key in tokens(pointer)) value = when (val current = value) {
                is JsonObject -> current[key] ?: fail("missing_binding")
                is JsonArray -> current.getOrNull(index(key) ?: fail("missing_binding")) ?: fail("missing_binding")
                else -> fail("missing_binding")
            }
            return value
        }
    }
}
