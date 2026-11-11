package dev.dicta.prototype.provider

import dev.dicta.prototype.profile.StrictJson
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ProviderProtocol { AUTO, DEEPSEEK, OPENAI, COMPATIBLE }
enum class JsonOutput { JSON_OBJECT, PROMPT_ONLY }

/** A Profile model overrides the global default, including for daily words and tests. */
fun effectiveModel(global: String, profile: String) = profile.trim().ifBlank { global.trim() }

/** Never stringify this object: it contains a transient plaintext credential. */
class ProviderConfig(
    val endpoint: String = "https://api.deepseek.com", val model: String = "deepseek-flash", val apiKey: String = "",
    val protocol: ProviderProtocol = ProviderProtocol.AUTO, val output: JsonOutput = JsonOutput.JSON_OBJECT,
) {
    fun validated(requireModel: Boolean = true): ProviderConfig {
        val url = endpoint.trim().trimEnd('/').toHttpUrlOrNullSafe()
        if (url == null || url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null)
            throw ProviderFailure("请输入有效的 HTTPS 服务地址。")
        if ((requireModel && model.isBlank()) || model.length > 100 || model.any { it.isISOControl() }) throw ProviderFailure("请输入有效的模型名称。")
        if (apiKey.length > 512 || apiKey.any { it.isISOControl() }) throw ProviderFailure("密钥格式无效，请重新输入。")
        return ProviderConfig(url.toString().trimEnd('/'), model.trim(), apiKey.trim(), protocol, output)
    }
    fun withModel(model: String) = ProviderConfig(endpoint, model, apiKey, protocol, output)
    fun resolvedProtocol(): ProviderProtocol = if (protocol != ProviderProtocol.AUTO) protocol else when (endpoint.toHttpUrl().host) {
        "api.deepseek.com" -> ProviderProtocol.DEEPSEEK
        "api.openai.com" -> ProviderProtocol.OPENAI
        else -> ProviderProtocol.COMPATIBLE
    }
    /** Accept a base URL or an existing chat/models URL without duplicating API paths. */
    fun apiUrl(resource: String): HttpUrl {
        val url = endpoint.toHttpUrl()
        var path = url.encodedPath.trimEnd('/')
        for (suffix in listOf("/chat/completions", "/models")) if (path.endsWith(suffix)) { path = path.removeSuffix(suffix); break }
        if (path.isEmpty() && url.host == "api.openai.com") path = "/v1"
        return url.newBuilder().encodedPath("$path/$resource").build()
    }
    override fun toString() = "ProviderConfig(redacted)"
}
private fun String.toHttpUrlOrNullSafe() = try { toHttpUrl() } catch (_: IllegalArgumentException) { null }

enum class FailureKind { CONFIGURATION, AUTH, BALANCE, RATE_LIMIT, SERVER, NETWORK, TIMEOUT, RESPONSE, REQUEST, DISCOVERY_UNSUPPORTED }
class ProviderFailure(val userMessage: String, val kind: FailureKind = FailureKind.RESPONSE) : Exception(userMessage)
fun interface Provider { suspend fun generate(query: String): String }

class OpenAiCompatibleProvider(
    private val config: () -> ProviderConfig,
    private val prompt: () -> String,
    private val client: OkHttpClient = defaultClient(),
) : Provider {
    override suspend fun generate(query: String): String = request(query, "lookup", emptyList())
    suspend fun daily(exclude: List<String>): String = request("", "daily", exclude)
    private suspend fun request(query:String, mode:String, exclude:List<String>):String {
        val active = config().validated()
        if (active.apiKey.isBlank()) throw ProviderFailure("请先在设置中添加 API Key。", FailureKind.CONFIGURATION)
        val body = buildJsonObject {
            put("model", active.model)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", prompt()) })
                add(buildJsonObject { put("role", "user"); put("content", buildJsonObject { put("mode", mode); put("query", query); put("exclude", JsonArray(exclude.map { JsonPrimitive(it) })) }.toString()) })
            })
            if (active.output == JsonOutput.JSON_OBJECT) put("response_format", buildJsonObject { put("type", "json_object") })
            if (active.resolvedProtocol() == ProviderProtocol.DEEPSEEK) put("thinking", buildJsonObject { put("type", "disabled") })
            put(if (active.resolvedProtocol() == ProviderProtocol.OPENAI) "max_completion_tokens" else "max_tokens", 2200)
            put("stream", false)
        }
        val request = Request.Builder().url(active.apiUrl("chat/completions"))
            .header("Authorization", "Bearer " + active.apiKey)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        val raw = exchange(request)
        try {
            val envelope = StrictJson.parse(raw).jsonObject
            val choice = envelope["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: throw responseFailure()
            if (choice["finish_reason"]?.jsonPrimitive?.content != "stop") throw responseFailure()
            val content = choice["message"]?.jsonObject?.get("content") as? JsonPrimitive ?: throw responseFailure()
            if (!content.isString || content.content.isBlank()) throw responseFailure()
            return content.content
        } catch (failure: ProviderFailure) { throw failure }
        catch (_: Exception) { throw responseFailure() }
    }

    /** GET only: model discovery never generates a paid dictionary entry. */
    suspend fun models(): List<String> {
        val active = config().validated(requireModel=false)
        if (active.apiKey.isBlank()) throw ProviderFailure("请先保存 API Key，再获取模型。", FailureKind.CONFIGURATION)
        val request = Request.Builder().url(active.apiUrl("models"))
            .header("Authorization", "Bearer " + active.apiKey).get().build()
        val raw = exchange(request, discovery = true, limit = 1048576)
        try {
            val data = StrictJson.parse(raw, 1048576).jsonObject["data"] as? JsonArray ?: throw responseFailure()
            val ids = data.mapNotNull { item -> (item as? JsonObject)?.get("id")?.let { it as? JsonPrimitive }
                ?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotBlank() && it.length <= 100 && it.none(Char::isISOControl) } }
                .distinct().sorted()
            if (ids.isEmpty()) throw ProviderFailure("服务未返回可用模型，请手动输入 Model ID。")
            return ids
        } catch (failure: ProviderFailure) { throw failure }
        catch (_: Exception) { throw ProviderFailure("模型列表格式不符，请手动输入 Model ID。") }
    }

    private suspend fun exchange(request: Request, discovery: Boolean = false, limit: Int = 131072): String =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(ProviderFailure(
                        if (e is InterruptedIOException) "查询超时，请重试。" else "无法连接服务，请检查网络后重试。",
                        if (e is InterruptedIOException) FailureKind.TIMEOUT else FailureKind.NETWORK))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!it.isSuccessful) {
                                if (discovery && it.code in listOf(404, 405, 501)) throw ProviderFailure("服务不提供模型发现，请手动输入 Model ID。", FailureKind.DISCOVERY_UNSUPPORTED)
                                throw httpFailure(it.code)
                            }
                            val source = it.body?.source() ?: throw responseFailure()
                            if (source.request(limit.toLong() + 1)) throw responseFailure()
                            val raw = source.readUtf8()
                            if (continuation.isActive) continuation.resume(raw)
                        }
                    } catch (e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(ProviderFailure(
                            if (e is InterruptedIOException) "查询超时，请重试。" else "网络中断，请重试。",
                            if (e is InterruptedIOException) FailureKind.TIMEOUT else FailureKind.NETWORK))
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(if (e is ProviderFailure) e else responseFailure())
                    }
                }
            })
        }
    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                // OkHttp otherwise repeats a 503 with Retry-After: 0 even when connection retries are off.
                // Paid dictionary queries require an explicit user retry.
                if (response.code == 503) response.newBuilder().removeHeader("Retry-After").build() else response
            }.build()
        private fun responseFailure() = ProviderFailure("服务返回的内容不完整或格式不符，请重试。", FailureKind.RESPONSE)
        fun httpFailure(code: Int) = when (code) {
            401, 403 -> ProviderFailure("密钥无效或无访问权限，请检查设置。", FailureKind.AUTH)
            402 -> ProviderFailure("账户余额不足，请检查服务账户。", FailureKind.BALANCE)
            429 -> ProviderFailure("请求过于频繁，请稍后重试。", FailureKind.RATE_LIMIT)
            in 500..599 -> ProviderFailure("AI 服务暂不可用，请稍后重试。", FailureKind.SERVER)
            400, 422 -> ProviderFailure("服务不接受当前模型或参数。请检查 Model ID、请求协议，或将 JSON 输出改为「仅提示词」后手动重试；不会自动重发。", FailureKind.REQUEST)
            else -> ProviderFailure("服务拒绝了请求，请检查地址与模型配置。", FailureKind.REQUEST)
        }
    }
}
