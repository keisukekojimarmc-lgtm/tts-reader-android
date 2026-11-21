package com.example.ttsreader.reading

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReaderState { IDLE, READING, PAUSED }

sealed interface TtsStatus {
    data object Initializing : TtsStatus

    /** @param englishAvailable 端末に英語音声があるか（無ければ英語の文も日本語音声で読む） */
    data class Ready(val englishAvailable: Boolean) : TtsStatus

    data class Error(val message: String) : TtsStatus
}

/**
 * サービス・オーバーレイ・アプリ画面の間で共有する読み上げ状態。
 * プロセス内シングルトンなので、3 者は同じ StateFlow を購読するだけでよい。
 */
object ReaderStateHolder {
    private val _state = MutableStateFlow(ReaderState.IDLE)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _ttsStatus = MutableStateFlow<TtsStatus>(TtsStatus.Initializing)
    val ttsStatus: StateFlow<TtsStatus> = _ttsStatus.asStateFlow()

    fun setState(state: ReaderState) {
        _state.value = state
    }

    fun setServiceConnected(connected: Boolean) {
        _serviceConnected.value = connected
        if (!connected) {
            _state.value = ReaderState.IDLE
            _ttsStatus.value = TtsStatus.Initializing
        }
    }

    fun setTtsStatus(status: TtsStatus) {
        _ttsStatus.value = status
    }
}
