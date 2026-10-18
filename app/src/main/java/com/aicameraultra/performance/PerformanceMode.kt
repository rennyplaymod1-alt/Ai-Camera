package com.aicameraultra.performance

/**
 * Smart Performance Mode: controla latencia de captura, tamaño de la copia temporal enviada a la IA,
 * tiempos de espera y reintentos. Nunca modifica el archivo original.
 */
enum class PerformanceMode(
    val label: String,
    val description: String,
    val uploadLongSide: Int,
    val uploadJpegQuality: Int,
    val timeoutSeconds: Long,
    val maxRetries: Int
) {
    FAST(
        "Rápido",
        "Captura con menor latencia y copia pequeña para la IA. Ideal con conexiones lentas.",
        uploadLongSide = 1024, uploadJpegQuality = 75, timeoutSeconds = 60, maxRetries = 0
    ),
    BALANCED(
        "Equilibrado",
        "Buen compromiso entre calidad, tiempo y datos.",
        uploadLongSide = 1600, uploadJpegQuality = 85, timeoutSeconds = 90, maxRetries = 1
    ),
    MAX_QUALITY(
        "Máxima calidad",
        "Captura con máxima calidad y copia grande para la IA. Más lento y consume más datos.",
        uploadLongSide = 2048, uploadJpegQuality = 92, timeoutSeconds = 150, maxRetries = 1
    )
}
