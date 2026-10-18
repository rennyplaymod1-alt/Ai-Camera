package com.aicameraultra.settings

import android.content.Context
import com.aicameraultra.imageprocessing.ExportSize
import com.aicameraultra.performance.PerformanceMode

data class AppSettings(
    val jpegQuality: Int = 95,
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED,
    val exportSize: ExportSize = ExportSize.FULL_HD,
    /** Dirección https del backend de IA. Vacía = IA desactivada. */
    val backendUrl: String = ""
)

class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("ai_camera_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        jpegQuality = prefs.getInt(KEY_QUALITY, 95).coerceIn(80, 100),
        performanceMode = enumOr(KEY_PERF, PerformanceMode.BALANCED),
        exportSize = enumOr(KEY_EXPORT, ExportSize.FULL_HD),
        backendUrl = prefs.getString(KEY_BACKEND, "") ?: ""
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putInt(KEY_QUALITY, settings.jpegQuality)
            .putString(KEY_PERF, settings.performanceMode.name)
            .putString(KEY_EXPORT, settings.exportSize.name)
            .putString(KEY_BACKEND, settings.backendUrl)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(key: String, default: T): T =
        runCatching { enumValueOf<T>(prefs.getString(key, null) ?: default.name) }.getOrDefault(default)

    private companion object {
        const val KEY_QUALITY = "jpeg_quality"
        const val KEY_PERF = "performance_mode"
        const val KEY_EXPORT = "export_size"
        const val KEY_BACKEND = "backend_url"
    }
}
