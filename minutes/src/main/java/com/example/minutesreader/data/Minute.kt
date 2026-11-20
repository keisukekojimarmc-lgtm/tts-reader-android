package com.example.minutesreader.data

/** 一覧に表示する議事録 1 件分の情報（本文はキャッシュファイルに別保存）。 */
data class Minute(
    val id: String,
    val path: String,
    val name: String,
    val rev: String,
    val serverModified: String,
    /** yyyy-MM-dd。ファイル名に日付がない資料（用語リストなど）は null */
    val date: String?,
    /** HH:mm */
    val time: String?,
    val category: String,
    val title: String,
    val summary: String,
    val participants: String,
) {
    val readKey: String get() = "$id@$rev"
}
