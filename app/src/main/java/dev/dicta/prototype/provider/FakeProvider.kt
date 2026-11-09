package dev.dicta.prototype.provider

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.Locale

/** M1 fixture source. No endpoint, credentials, network, or invented entries. */
fun interface Provider {
    suspend fun generate(query: String): String
}

class ProviderFailure(val userMessage: String) : Exception(userMessage)

class FakeProvider(private val readFixture: (String) -> String) : Provider {
    override suspend fun generate(query: String): String {
        val normalized = query.lowercase(Locale.ROOT)
        val fixture = when (normalized) {
            ":error" -> {
                delay(350)
                throw ProviderFailure("演示失败：请重试，或查询 luminous / ephemeral。")
            }
            ":timeout" -> withTimeout(1_200) { delay(10_000); "luminous" }
            ":slow" -> { delay(8_000); "luminous" }
            "luminous", "ephemeral", "qzxvplmnonce" -> { delay(350); normalized }
            else -> {
                delay(350)
                throw ProviderFailure("M1 暂无此词的演示样例。请试 luminous 或 ephemeral；真实 AI 查词将在后续阶段提供。")
            }
        }
        return readFixture("$fixture.json")
    }
}
