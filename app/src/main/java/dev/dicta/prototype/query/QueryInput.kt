package dev.dicta.prototype.query

class InvalidQuery : Exception("请输入词或短语，最多 100 个字符。")

/** Count Unicode code points, rather than UTF-16 code units. */
fun normalizeQuery(raw: String): String {
    val query = raw.trim()
    if (query.isEmpty() || query.codePointCount(0, query.length) > 100) throw InvalidQuery()
    return query
}
