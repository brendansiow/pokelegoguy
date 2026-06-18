package com.pokelegoguy.ai

import android.graphics.Bitmap
import com.pokelegoguy.utils.executeAsync
import com.pokelegoguy.utils.toBase64Jpeg
import kotlinx.serialization.json.buildJsonArray
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

class OpenAIProvider(private val model: String, private val apiKey: String) : AIProvider {

    private val client = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun recommendAction(bitmap: Bitmap, gameStateText: String): String {
        val base64 = bitmap.toBase64Jpeg()
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 10)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", "$SYSTEM_PROMPT\n\n$gameStateText")
                        })
                        add(buildJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,$base64")
                            }
                        })
                    }
                })
            }
        }.toString()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.executeAsync(request)
        val responseBody = response.body?.string()
            ?: throw AIProviderException("OpenAI: empty response body")

        if (!response.isSuccessful) {
            if (response.code == 429) throw AIProviderException("OpenAI: rate limited (429)")
            throw AIProviderException("OpenAI: HTTP ${response.code} — $responseBody")
        }

        return runCatching {
            JSONObject(responseBody)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }.getOrElse { throw AIProviderException("OpenAI: failed to parse response", it) }
    }
}
