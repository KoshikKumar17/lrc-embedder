package dev.koshik.lrcembedder.lyrics

import kotlin.math.max

/** Token-set similarity (0..100), same idea as rapidfuzz's token_set_ratio. */
object FuzzyMatch {
    private val splitRegex = Regex("""[^\p{L}\p{N}]+""")

    private fun ratio(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 100.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var prev = IntArray(b.length + 1)
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                cur[j] = if (a[i - 1] == b[j - 1]) prev[j - 1] + 1 else max(prev[j], cur[j - 1])
            }
            val t = prev
            prev = cur
            cur = t
        }
        return 200.0 * prev[b.length] / (a.length + b.length)
    }

    private fun tokens(s: String) = s.lowercase().split(splitRegex).filter { it.isNotEmpty() }.toSet()

    fun score(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val inter = ta.intersect(tb).sorted()
        val diffA = (ta - tb).sorted()
        val diffB = (tb - ta).sorted()
        val sect = inter.joinToString(" ")
        if (inter.isNotEmpty() && (diffA.isEmpty() || diffB.isEmpty())) return 100.0
        val combA = (sect + " " + diffA.joinToString(" ")).trim()
        val combB = (sect + " " + diffB.joinToString(" ")).trim()
        return if (inter.isEmpty()) {
            ratio(combA, combB)
        } else {
            maxOf(ratio(sect, combA), ratio(sect, combB), ratio(combA, combB))
        }
    }
}
