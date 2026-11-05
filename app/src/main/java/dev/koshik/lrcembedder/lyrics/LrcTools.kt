package dev.koshik.lrcembedder.lyrics

object LrcTools {
    private val ms3 = Regex("""\[(\d{1,3}):(\d{2})\.(\d{3})]""")
    private val timeTag = Regex("""\[\d+:\d+(\.\d+)?]""")
    private val timeLine = Regex("""^\s*\[\d+:\d+(\.\d+)?]""")
    private val metaLine = Regex("""^\s*\[(ti|ar|al|au|by|offset|re|ve|length|tool):[^\]]*]\s*$""", RegexOption.IGNORE_CASE)
    private val credit = Regex(
        """^\s*\[\d+:\d+\.\d+](作词|作曲|编曲|制作人?|混音|录音|母带|词|曲)\s*[:：].*$"""
    )

    /** Netease style [mm:ss.mmm] -> [mm:ss.xx], drops credit lines like "作词 : ...". */
    fun normalize(raw: String): String {
        return raw.replace("\r\n", "\n").lines()
            .map { line ->
                ms3.replace(line) { m ->
                    val cs = (m.groupValues[3].toInt() / 10).toString().padStart(2, '0')
                    "[${m.groupValues[1]}:${m.groupValues[2]}.$cs]"
                }
            }
            .filterNot { credit.containsMatchIn(it) || metaLine.containsMatchIn(it) }
            .joinToString("\n")
            .trim()
    }

    fun isSynced(text: String): Boolean = text.lineSequence().count { timeLine.containsMatchIn(it) } >= 3

    /** For previews / plain fallbacks: timestamps removed. */
    fun toPlain(text: String): String =
        text.lineSequence()
            .filterNot { metaLine.containsMatchIn(it) }
            .joinToString("\n") { timeTag.replace(it, "").trim() }
            .trim()
}
