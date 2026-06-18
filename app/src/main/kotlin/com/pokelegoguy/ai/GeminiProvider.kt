package com.pokelegoguy.ai

import android.graphics.Bitmap
import com.pokelegoguy.utils.executeAsync
import com.pokelegoguy.utils.toBase64Jpeg
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiProvider(private val model: String, private val apiKey: String) : AIProvider {

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun recommendAction(bitmap: Bitmap, gameStateText: String): String {
        val base64 = bitmap.toBase64Jpeg()
        val body = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    putJsonArray("parts") {
                        add(buildJsonObject {
                            put("text", "$SYSTEM_PROMPT\n\n$gameStateText")
                        })
                        add(buildJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", "image/jpeg")
                                put("data", base64)
                            }
                        })
                    }
                })
            }
            putJsonObject("generationConfig") {
                put("maxOutputTokens", 10)
            }
        }.toString()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.executeAsync(request)
        val responseBody = response.body?.string()
            ?: throw AIProviderException("Gemini: empty response body")

        if (!response.isSuccessful) {
            if (response.code == 429) throw AIProviderException("Gemini: rate limited (429)")
            throw AIProviderException("Gemini: HTTP ${response.code} — $responseBody")
        }

        return runCatching {
            JSONObject(responseBody)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
        }.getOrElse { throw AIProviderException("Gemini: failed to parse response", it) }
    }
}
