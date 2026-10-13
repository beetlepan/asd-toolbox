package com.ateliersourcedeck.toolbox

/** 版本字串比對：只比數字段，忽略前綴 v 與尾巴文字（例如 v2.2.0-7854 → 2,2,0,7854）。 */
object Versions {
    private val numberRe = Regex("\\d+")
    private val startsLikeVersion = Regex("^[vV]?\\d+.*")

    fun looksLikeVersion(s: String): Boolean = startsLikeVersion.matches(s.trim())

    fun parts(s: String): List<Long> =
        numberRe.findAll(s).mapNotNull { it.value.toLongOrNull() }.toList()

    /** 回傳 <0：a 較舊；0：相同；>0：a 較新 */
    fun compare(a: String, b: String): Int {
        val x = parts(a)
        val y = parts(b)
        val n = maxOf(x.size, y.size)
        for (i in 0 until n) {
            val p = x.getOrElse(i) { 0L }
            val q = y.getOrElse(i) { 0L }
            if (p != q) return if (p < q) -1 else 1
        }
        return 0
    }
}
