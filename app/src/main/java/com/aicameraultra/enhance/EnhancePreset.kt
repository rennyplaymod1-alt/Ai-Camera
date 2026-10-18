package com.aicameraultra.enhance

/** Tipos de mejora. El texto real del prompt vive en el backend, no en la app. */
enum class EnhancePreset(val id: String, val label: String) {
    LIGHT("light", "Iluminación"),
    COLOR("color", "Contraste y color"),
    DENOISE("denoise", "Reducir ruido"),
    SHARPEN("sharpen", "Nitidez"),
    FULL("full", "Mejora general"),
    CUSTOM("custom", "Personalizada")
}
