package com.gyeongroital.gallaemallae.consent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 개인정보·위치정보 수집·이용 동의 기록(Pleos 심사: 명시적 동의 절차, 동의 버튼 외 행동을 동의로 보지 않음).
 * 동의하기 전에는 위치를 읽지 않고 서버에 아무것도 보내지 않는다.
 * 고지 내용(수집 항목·목적·보유 기간)이 바뀌면 [VERSION]을 올려 다시 동의를 받는다.
 */
class ConsentStore(context: Context) {
    private val prefs = context.getSharedPreferences("consent", Context.MODE_PRIVATE)

    private val _agreed = MutableStateFlow(prefs.getInt(KEY_VERSION, 0) >= VERSION)
    val agreed: StateFlow<Boolean> = _agreed.asStateFlow()

    fun agree(now: Long = System.currentTimeMillis()) {
        prefs.edit().putInt(KEY_VERSION, VERSION).putLong(KEY_AGREED_AT, now).apply()
        _agreed.value = true
    }

    fun withdraw() {
        prefs.edit().clear().apply()
        _agreed.value = false
    }

    companion object {
        const val VERSION = 1
        private const val KEY_VERSION = "agreed_version"
        private const val KEY_AGREED_AT = "agreed_at"
    }
}
