package com.example.minutesreader.dropbox

/** Dropbox 上のファイル 1 件のメタデータ。 */
data class RemoteFile(
    val id: String,
    val name: String,
    val pathDisplay: String,
    val rev: String,
    val serverModified: String,
)
