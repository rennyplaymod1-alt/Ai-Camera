# AI Camera Ultra

App Android (Kotlin, Jetpack Compose, CameraX, MVVM) para Xiaomi REDMI A5 con mejora de fotos mediante OpenAI Images, a través de un backend propio.

**Estado:** código completo de las etapas 1 a 4, **sin compilar ni probar en dispositivo** (el entorno donde se generó no tiene Android SDK ni un teléfono). Es posible que la primera sincronización muestre errores menores.

## Estructura
```
AICameraUltra/
├── app/                  Cliente Android
│   └── src/main/java/com/aicameraultra/
│       ├── camera/           CameraX, estado y capacidades de resolución
│       ├── performance/      Smart Performance Mode (rápido / equilibrado / máxima calidad)
│       ├── imageprocessing/  Exportación FHD/4K/original, copias temporales, lectura EXIF
│       ├── gallery/          MediaStore
│       ├── network/          Cliente del backend (OkHttp) y validación de URL
│       ├── enhance/          Flujo de mejora con IA (ViewModel, presets)
│       ├── settings/         Preferencias
│       ├── permissions/      (la gestión de permisos está en ui/PermissionGate.kt)
│       └── ui/               Pantallas y componentes Compose
└── backend/              Servidor Node.js: guarda la clave y llama a OpenAI
```

## Qué hace
**Cámara (etapa 1)**: vista previa, frontal y trasera, enfoque al tocar, flash, temporizador, zoom, exposición y selector de resolución según lo que declara la cámara. Guardado en MediaStore (`Pictures/AICameraUltra`) con verificación de dimensiones y tamaño. Funciona sin Internet.

**Rendimiento y exportación (etapa 2)**: Smart Performance Mode controla la latencia de captura, el tamaño de la copia enviada a la IA, los tiempos de espera y los reintentos. Exporta copias Full HD (1920×1080), 4K (3840×2160) o la original sin re-codificar. Nunca amplía: si la foto es menor, se mantiene su tamaño y se avisa. Las copias 4K se marcan como **escaladas** si la captura original no es 4K nativa. Solo se decodifica una imagen grande a la vez.

**Backend seguro (etapa 3)**: ver `backend/README.md`. La clave de OpenAI solo existe en el servidor. Validación de tipo, tamaño, límites por IP y límite diario.

**Mejora con IA (etapa 4)**: bajo demanda, nunca automática. Presets (iluminación, contraste y color, ruido, nitidez, mejora general, personalizada) más instrucciones de texto. Progreso, cancelación real (cancela la llamada HTTP), comparador antes/después con deslizador, y el resultado se guarda como archivo nuevo. El original no se modifica.

## Compilar
Requisitos: Android Studio Koala (2024.1.1) o superior, JDK 17, Android SDK 34.
Versiones: AGP 8.5.2, Gradle 8.7, Kotlin 1.9.24, Compose BOM 2024.06.00, CameraX 1.3.4, OkHttp 4.12.0.

1. Android Studio → *Open* → carpeta `AICameraUltra`. Espera a que sincronice Gradle.
   Si falta el wrapper: `gradle wrapper --gradle-version 8.7` en la raíz.
2. APK de depuración: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
3. Pruebas: `./gradlew testDebugUnitTest`.

## Backend
```bash
cd backend && npm install && cp .env.example .env   # pon tu OPENAI_API_KEY
npm test && npm start
```
Después despliégalo con HTTPS y pon su dirección en **Ajustes → Servidor de IA** de la app. Sin esa dirección, la app funciona como cámara normal.

## Instalar en el REDMI A5
1. Ajustes → Acerca del teléfono → pulsa 7 veces "Versión de MIUI/HyperOS".
2. Ajustes adicionales → Opciones de desarrollador → activa *Depuración USB*.
3. `adb install -r app/build/outputs/apk/debug/app-debug.apk`

### Lista de pruebas en el teléfono
- **Cámara**: permisos (concederlos y denegarlos), vista previa, frontal/trasera, enfoque, flash, temporizador (3 s, 10 s y cancelar tocando el obturador), zoom, exposición.
- **Resoluciones**: anota qué tamaños declara tu cámara en el menú. Full HD o 4K solo aparecen si la cámara los declara.
- **Guardado**: la foto aparece en la galería y las dimensiones del aviso coinciden con las del archivo.
- **Sin Internet**: activa el modo avión, toma fotos y compruébalas. Todo debe funcionar.
- **Exportación**: exporta FHD, 4K y Original desde la pantalla de IA. Comprueba que el original no cambia y que la copia 4K de una foto pequeña no se amplía.
- **IA**: sin servidor configurado, debe aparecer el aviso. Con servidor de prueba (túnel HTTPS): mejora una foto, observa el progreso, prueba a cancelar y compara con el deslizador.
- **Errores**: apaga el servidor y comprueba el mensaje de error; la cámara debe seguir funcionando.
- **Rendimiento**: mide cuánto tarda cada modo (Rápido, Equilibrado, Máxima calidad) en tu teléfono. No hay cifras medidas todavía.

## Limitaciones conocidas
- Nada de esto ha sido compilado ni probado en un teléfono todavía. Las cifras de rendimiento deben medirse.
- Los tamaños se leen de Camera2; CameraX puede elegir uno cercano si el declarado no es combinable con la vista previa. Por eso la app muestra las dimensiones reales después de guardar.
- Las copias re-codificadas (Full HD, 4K) no conservan los metadatos EXIF; la copia "Original" sí.
- El resultado de la IA puede alterar detalles y su resolución la decide el servicio. No es superresolución.
- Con 2 GB de RAM, una captura de 12 MP puede no poder exportarse en 4K; la app lo indica con un mensaje de memoria insuficiente.
- La clave de la API nunca va en la app, pero el backend todavía no tiene autenticación de usuarios (ver `backend/README.md`).
