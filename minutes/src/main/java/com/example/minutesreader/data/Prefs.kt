package com.example.minutesreader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 表示設定・既読状態の保存。 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _root = MutableStateFlow(sp.getString(K_ROOT, DEFAULT_ROOT) ?: DEFAULT_ROOT)
    val rootPath: StateFlow<String> = _root

    private val _zoom = MutableStateFlow(sp.getInt(K_ZOOM, 100))
    val textZoom: StateFlow<Int> = _zoom

    private val _rate = MutableStateFlow(sp.getFloat(K_RATE, 1.0f))
    val speechRate: StateFlow<Float> = _rate

    private val _read = MutableStateFlow(sp.getStringSet(K_READ, emptySet())!!.toSet())
    val readKeys: StateFlow<Set<String>> = _read

    private val _sortAsc = MutableStateFlow(sp.getBoolean(K_SORT_ASC, false))
    /** true = 古い順 */
    val sortAsc: StateFlow<Boolean> = _sortAsc

    private val _collapsed = MutableStateFlow(sp.getStringSet(K_COLLAPSED, emptySet())!!.toSet())
    /** 折りたたんでいる月（「2026年11月」など） */
    val collapsed: StateFlow<Set<String>> = _collapsed

    fun setSortAsc(v: Boolean) { _sortAsc.value = v; sp.edit().putBoolean(K_SORT_ASC, v).apply() }

    fun setCollapsed(v: Set<String>) { _collapsed.value = v; sp.edit().putStringSet(K_COLLAPSED, v).apply() }

    fun setRoot(path: String) {
        val p = normalizeRoot(path)
        _root.value = p
        sp.edit().putString(K_ROOT, p).apply()
    }

    fun setZoom(v: Int) { _zoom.value = v; sp.edit().putInt(K_ZOOM, v).apply() }
    fun setRate(v: Float) { _rate.value = v; sp.edit().putFloat(K_RATE, v).apply() }

    fun markRead(m: Minute) {
        if (m.readKey in _read.value) return
        // 同じファイルの古い rev の既読は消す
        val next = _read.value.filterNot { it.startsWith(m.id + "@") }.toSet() + m.readKey
        _read.value = next
        sp.edit().putStringSet(K_READ, next).apply()
    }

    fun retainRead(validKeys: Set<String>) {
        val next = _read.value intersect validKeys
        if (next.size == _read.value.size) return
        _read.value = next
        sp.edit().putStringSet(K_READ, next).apply()
    }

    /** 初回起動時、まだ既読情報がなければ既存の議事録はすべて既読扱いにする。 */
    fun markAllReadIfFirst(keys: Set<String>) {
        if (sp.getBoolean(K_INIT, false)) return
        _read.value = keys
        sp.edit().putStringSet(K_READ, keys).putBoolean(K_INIT, true).apply()
    }

    companion object {
        const val DEFAULT_ROOT = "/Kojima_unshared/議事録"
        private const val K_ROOT = "root"
        private const val K_ZOOM = "zoom"
        private const val K_RATE = "rate"
        private const val K_READ = "read"
        private const val K_INIT = "read_init"
        private const val K_SORT_ASC = "sort_asc"
        private const val K_COLLAPSED = "collapsed"

        fun normalizeRoot(path: String): String {
            val t = path.trim().trimEnd('/')
            if (t.isEmpty()) return ""
            return if (t.startsWith("/")) t else "/$t"
        }
    }
}
