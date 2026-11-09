package dev.dicta.prototype.profile

import kotlinx.serialization.json.*
import java.security.MessageDigest

/** Portable v1 package. No endpoint or credential association can be imported. */
data class ProfileDraft(val name:String, val prompt:String, val schema:String, val template:String, val example:String, val model:String="") {
    fun preview():RenderedEntry {
        if(name.isBlank() || name.length>100 || prompt.isBlank() || prompt.length>32000 || model.length>100 || model.any {it.isISOControl()}) throw ProfileFailure("invalid_profile")
        if(!prompt.contains("{{schema_json}}") || !prompt.contains("{{example_json}}")) throw ProfileFailure("prompt_variables")
        val result=ProfileEngine(schema,template).decodeAndRender(example)
        if(result.summary["title"].isNullOrBlank()) throw ProfileFailure("empty_title")
        if(export().toByteArray().size>262144) throw ProfileFailure("profile_too_large")
        return result
    }
    fun systemPrompt()=Regex("\\{\\{(schema_json|example_json)\\}\\}").replace(prompt) {if(it.groupValues[1]=="schema_json") schema else example}
    fun export():String=buildJsonObject {
        put("formatVersion",1);put("name",name);put("prompt",prompt)
        put("schema",StrictJson.parse(schema));put("template",StrictJson.parse(template));put("example",StrictJson.parse(example));put("model",model)
    }.toString()
    fun hash()=digest(copy(name="").export())
    companion object {
        fun import(raw:String):ProfileDraft {
            val obj=StrictJson.parse(raw,262144) as? JsonObject ?: throw ProfileFailure("invalid_profile")
            if(obj["formatVersion"]!=JsonPrimitive(1)) throw ProfileFailure("profile_version")
            if(obj.keys!=setOf("formatVersion","name","prompt","schema","template","example","model")) throw ProfileFailure("invalid_profile")
            fun string(key:String)=(obj[key] as? JsonPrimitive)?.takeIf {it.isString}?.content ?: throw ProfileFailure("invalid_profile")
            return ProfileDraft(string("name"),string("prompt"),obj.getValue("schema").toString(),obj.getValue("template").toString(),obj.getValue("example").toString(),string("model")).also {it.preview()}
        }
        fun digest(raw:String)=MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") {"%02x".format(it)}
    }
}
fun profileMessage(error:Throwable):String=when((error as? ProfileFailure)?.code) {
    "prompt_variables" -> "Prompt 须包含 {{schema_json}} 和 {{example_json}}。"
    "unsupported_schema","unsupported_type","unsupported_draft","unbounded_array","unbounded_object" -> "Schema 使用了不支持的格式；请使用受控 Draft-07 子集。"
    "invalid_binding","binding_type","binding_optional","binding_missing" -> "模板字段映射与 Schema 不符，请检查 bind 路径和类型。"
    "schema_invalid" -> "预览样例与 Schema 不符，请同步编辑。"
    "profile_version" -> "不支持此 Profile 文件版本，请使用 formatVersion 1。"
    "profile_too_large","response_too_large" -> "配置超出大小限制。"
    else -> "配置无效，请检查 JSON、模板、名称及样例；当前生效版本已保留。"
}
