package dev.koshik.lrcembedder.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("lrc_embedder", Context.MODE_PRIVATE)

    var alsoSaveLrc: Boolean
        get() = sp.getBoolean("also_lrc", false)
        set(v) = sp.edit().putBoolean("also_lrc", v).apply()

    var animated: Boolean
        get() = sp.getBoolean("animated", true)
        set(v) = sp.edit().putBoolean("animated", v).apply()

    var ascending: Boolean
        get() = sp.getBoolean("ascending", true)
        set(v) = sp.edit().putBoolean("ascending", v).apply()

    var themeName: String
        get() = sp.getString("theme", "SYSTEM") ?: "SYSTEM"
        set(v) = sp.edit().putString("theme", v).apply()

    var paletteName: String
        get() = sp.getString("palette", "AURORA") ?: "AURORA"
        set(v) = sp.edit().putString("palette", v).apply()

    var sortName: String
        get() = sp.getString("sort", "TITLE") ?: "TITLE"
        set(v) = sp.edit().putString("sort", v).apply()

    var rapidApiKey: String
        get() = sp.getString("rapidapi_key", "") ?: ""
        set(v) = sp.edit().putString("rapidapi_key", v.trim()).apply()

    fun done(): Set<String> = sp.getStringSet("done", emptySet())?.toSet() ?: emptySet()

    fun saveDone(paths: Set<String>) {
        sp.edit().putStringSet("done", HashSet(paths)).apply()
    }
}
