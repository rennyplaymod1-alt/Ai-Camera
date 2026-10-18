package com.aicameraultra.network

import android.util.Base64
import com.aicameraultra.enhance.EnhancePreset
import com.aicameraultra.performance.PerformanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Error del servidor. [retryable] indica si tiene sentido reintentar. */
class BackendException(message: String, val retryable: Boolean = false) : IOException(message)

/**
 * Cliente del backend propio. No contiene ninguna clave: el backend es quien habla con OpenAI.
 * Timeouts y reintentos dependen del modo de rendimiento. La cancelación cancela la llamada HTTP.
 */
object AiBackendClient {

    data class RemoteImage(val bytes: ByteArray, val mimeType: String)

    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
    }

    suspend fun enhance(
        baseUrl: String,
        upload: File,
        preset: EnhancePreset,
        instructions: String,
        mode: PerformanceMode
    ): RemoteImage {
        val client = baseClient.newBuilder()
            .callTimeout(mode.timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(mode.timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(mode.timeoutSeconds, TimeUnit.SECONDS)
            .build()

        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                return callOnce(client, baseUrl, upload, preset, instructions)
            } catch (e: BackendException) {
                if (!e.retryable || attempt >= mode.maxRetries) throw e
            } catch (e: IOException) {
                if (attempt >= mode.maxRetries) throw e
            }
            attempt++
            currentCoroutineContext().ensureActive()   // no reintentar si el usuario canceló
            delay(2000L * attempt)
        }
    }

    private suspend fun callOnce(
        client: OkHttpClient,
        baseUrl: String,
        upload: File,
        preset: EnhancePreset,
        instructions: String
    ): RemoteImage = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "upload.jpg", upload.asRequestBody("image/jpeg".toMediaType()))
            .addFormDataPart("preset", preset.id)
            .addFormDataPart("instructions", instructions)
            .build()
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/enhance")
            .post(body)
            .build()
        val call = client.newCall(request)
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }
                        .getOrNull() ?: "El servidor devolvió un error (${response.code})."
                    throw BackendException(msg, retryable = response.code == 429 || response.code >= 500)
                }
                try {
                    val json = JSONObject(text)
                    RemoteImage(
                        bytes = Base64.decode(json.getString("imageBase64"), Base64.DEFAULT),
                        mimeType = json.optString("mimeType", "image/jpeg")
                    )
                } catch (e: JSONException) {
                    throw BackendException("Respuesta no válida del servidor.")
                }
            }
        } finally {
            handle.dispose()
        }
    }
}
