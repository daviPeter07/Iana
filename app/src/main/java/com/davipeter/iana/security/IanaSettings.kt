package com.davipeter.iana.security

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import com.davipeter.iana.ai.CounterStore
import java.time.LocalDate

/** Preferências da IA, guardadas no celular. */
class IanaSettings(context: Context) {
    private val prefs = context.getSharedPreferences("iana_settings", Context.MODE_PRIVATE)

    /** Usar a IA quando houver chave. Desligar volta para os comandos por regras. */
    var aiEnabled: Boolean
        get() = prefs.getBoolean("ai_enabled", true)
        set(value) = prefs.edit().putBoolean("ai_enabled", value).apply()

    /** Falar agenda e relatórios com o celular bloqueado. Desligado por padrão. */
    var personalDataWhenLocked: Boolean
        get() = prefs.getBoolean("personal_when_locked", false)
        set(value) = prefs.edit().putBoolean("personal_when_locked", value).apply()

    /** Contador do teto diário de pedidos à IA. */
    val counter: CounterStore = object : CounterStore {
        override fun read(): Pair<LocalDate, Int>? {
            val day = prefs.getString("usage_day", null) ?: return null
            return runCatching { LocalDate.parse(day) to prefs.getInt("usage_count", 0) }.getOrNull()
        }

        override fun write(day: LocalDate, count: Int) {
            prefs.edit().putString("usage_day", day.toString()).putInt("usage_count", count).apply()
        }
    }
}

/**
 * O celular está bloqueado agora?
 *
 * Tela apagada conta como bloqueado. O `isDeviceLocked` sozinho dá falso nos segundos de carência
 * depois de apagar a tela, com Smart Lock, ou em celular sem bloqueio de tela, e a palavra "IANA"
 * funciona com a tela apagada.
 */
class DeviceLock(context: Context) {
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    fun isLocked(): Boolean = (keyguard?.isDeviceLocked ?: false) || !(power?.isInteractive ?: true)

    /** O celular tem PIN, padrão, senha ou biometria. Sem isso, "bloqueado" não protege nada. */
    fun isSecure(): Boolean = keyguard?.isDeviceSecure ?: false
}
