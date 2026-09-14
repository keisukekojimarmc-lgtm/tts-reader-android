package com.example.ttsreader.reading

/**
 * 読み上げ済み（またはキュー投入済み）テキストの履歴。重複読み上げの防止に使う。
 *
 * スクロール後も画面に残っている要素や、ヘッダー・タブなどの固定要素を
 * 二度読みしないために、正規化済みテキストを集合として保持する。
 * 容量を超えた場合は古いものから捨てる（LRU ではなく FIFO で十分）。
 * Android 非依存。
 */
class ReadHistory(private val capacity: Int = DEFAULT_CAPACITY) {

    private val entries = LinkedHashSet<String>()

    val size: Int get() = entries.size

    fun contains(key: String): Boolean = entries.contains(key)

    /**
     * 未登録なら登録して true、既に登録済みなら false を返す。
     */
    fun add(key: String): Boolean {
        if (key.isEmpty() || !entries.add(key)) return false
        if (entries.size > capacity) {
            val iterator = entries.iterator()
            iterator.next()
            iterator.remove()
        }
        return true
    }

    fun clear() = entries.clear()

    companion object {
        const val DEFAULT_CAPACITY = 500
    }
}
