package com.opentoolbox.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 轻量本地存储：ADB 收藏/执行历史、设置开关等。
 * 不引入第三方依赖，直接基于 SharedPreferences。
 */
object Prefs {

    private const val FILE = "opentoolbox_prefs"
    private const val SEPARATOR = "\u0001"

    // 常用 key
    const val KEY_ADB_FAVORITES = "adb_favorites"
    const val KEY_ADB_HISTORY = "adb_history"
    const val KEY_THEME_MODE = "theme_mode"

    // 主题模式取值
    const val THEME_MODE_SYSTEM = "system"
    const val THEME_MODE_LIGHT = "light"
    const val THEME_MODE_DARK = "dark"

    private fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun stringSet(context: Context, key: String): Set<String> =
        get(context).getStringSet(key, emptySet()).orEmpty()

    fun putStringSet(context: Context, key: String, value: Set<String>) {
        get(context).edit().putStringSet(key, value).apply()
    }

    fun stringList(context: Context, key: String): List<String> {
        val raw = get(context).getString(key, null) ?: return emptyList()
        return if (raw.isEmpty()) emptyList() else raw.split(SEPARATOR)
    }

    /** 头部插入一条记录，超过 cap 则淘汰最旧的。 */
    fun appendStringList(context: Context, key: String, value: String, cap: Int = 100) {
        val list = stringList(context, key).toMutableList()
        list.removeAll { it == value }
        list.add(0, value)
        while (list.size > cap) list.removeAt(list.size - 1)
        get(context).edit().putString(key, list.joinToString(SEPARATOR)).apply()
    }

    fun removeStringList(context: Context, key: String, value: String) {
        val list = stringList(context, key).toMutableList()
        list.removeAll { it == value }
        get(context).edit().putString(key, list.joinToString(SEPARATOR)).apply()
    }

    fun string(context: Context, key: String, default: String = ""): String =
        get(context).getString(key, default) ?: default

    fun putString(context: Context, key: String, value: String) {
        get(context).edit().putString(key, value).apply()
    }

    fun boolean(context: Context, key: String, default: Boolean = false): Boolean =
        get(context).getBoolean(key, default)

    fun putBoolean(context: Context, key: String, value: Boolean) {
        get(context).edit().putBoolean(key, value).apply()
    }

    fun clearKeys(context: Context, vararg keys: String) {
        val editor = get(context).edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
    }
}
