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

class AnthropicProvider(private val model: String, private val apiKey: String) : AIProvider {

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun recommendAction(bitmap: Bitmap, gameStateText: String): String {
        val base64 = bitmap.toBase64Jpeg()
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 10)
            put("system", SYSTEM_PROMPT)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", base64)
                            }
                        })
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", gameStateText)
                        })
                    }
                })
            }
        }.toString()

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.executeAsync(request)
        val responseBody = response.body?.string()
            ?: throw AIProviderException("Anthropic: empty response body")

        if (!response.isSuccessful) {
            if (response.code == 429) throw AIProviderException("Anthropic: rate limited (429)")
            throw AIProviderException("Anthropic: HTTP ${response.code} — $responseBody")
        }

        return runCatching {
            JSONObject(responseBody)
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text")
                .trim()
        }.getOrElse { throw AIProviderException("Anthropic: failed to parse response", it) }
    }
}
