package com.aicameraultra.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Valida la dirección del backend. Solo https; http únicamente para emulador/localhost en desarrollo. */
object BackendUrl {
    private val localHosts = setOf("10.0.2.2", "localhost")

    fun normalize(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val url = trimmed.toHttpUrlOrNull() ?: return null
        val allowed = url.scheme == "https" || (url.scheme == "http" && url.host in localHosts)
        return if (allowed) trimmed else null
    }
}
