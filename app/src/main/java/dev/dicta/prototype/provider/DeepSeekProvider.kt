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

/** Never stringify this object: it contains a transient plaintext credential. */
class ProviderConfig(val endpoint: String = "https://api.deepseek.com", val model: String = "deepseek-flash", val apiKey: String = "") {
    fun validated(): ProviderConfig {
        val url = endpoint.trim().trimEnd('/').toHttpUrlOrNullSafe()
        if (url == null || url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null)
            throw ProviderFailure("请输入有效的 HTTPS 服务地址。")
        if (model.isBlank() || model.length > 100 || model.any { it.isISOControl() }) throw ProviderFailure("请输入有效的模型名称。")
        if (apiKey.length > 512 || apiKey.any { it.isISOControl() }) throw ProviderFailure("密钥格式无效，请重新输入。")
        return ProviderConfig(url.toString().trimEnd('/'), model.trim(), apiKey.trim())
    }
    override fun toString() = "ProviderConfig(redacted)"
}
private fun String.toHttpUrlOrNullSafe() = try { toHttpUrl() } catch (_: IllegalArgumentException) { null }

enum class FailureKind { CONFIGURATION, AUTH, BALANCE, RATE_LIMIT, SERVER, NETWORK, TIMEOUT, RESPONSE, REQUEST }
class ProviderFailure(val userMessage: String, val kind: FailureKind = FailureKind.RESPONSE) : Exception(userMessage)
fun interface Provider { suspend fun generate(query: String): String }

class DeepSeekProvider(
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
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("thinking", buildJsonObject { put("type", "disabled") })
            put("max_tokens", 2200)
            put("stream", false)
        }
        val request = Request.Builder().url(active.endpoint + "/chat/completions")
            .header("Authorization", "Bearer " + active.apiKey)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
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
                            if (!it.isSuccessful) throw httpFailure(it.code)
                            val source = it.body?.source() ?: throw responseFailure()
                            if (source.request(131073)) throw responseFailure()
                            val envelope = StrictJson.parse(source.readUtf8()).jsonObject
                            val choice = envelope["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: throw responseFailure()
                            if (choice["finish_reason"]?.jsonPrimitive?.content != "stop") throw responseFailure()
                            val content = choice["message"]?.jsonObject?.get("content") as? JsonPrimitive ?: throw responseFailure()
                            if (!content.isString || content.content.isBlank()) throw responseFailure()
                            if (continuation.isActive) continuation.resume(content.content)
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
            else -> ProviderFailure("服务拒绝了请求，请检查地址与模型配置。", FailureKind.REQUEST)
        }
    }
}
