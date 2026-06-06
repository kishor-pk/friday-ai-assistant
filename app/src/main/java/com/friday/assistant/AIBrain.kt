package com.friday.assistant

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AIBrain {

    private val TAG = "AIBrain"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val history = mutableListOf<JSONObject>()

    fun ask(
        userInput: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Log.d(TAG, "Sending to Groq: $userInput")

        history.add(
            JSONObject().apply {
                put("role", "user")
                put("content", userInput)
            }
        )

        val messages = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "system")
                    put("content", Constants.SYSTEM_PROMPT)
                }
            )
            history.takeLast(10).forEach { put(it) }
        }

        val body = JSONObject().apply {
            put("model", Constants.GROQ_MODEL)
            put("messages", messages)
            put("max_tokens", 150)
            put("temperature", 0.3)
        }

        Log.d(TAG, "Request body: ${body}")

        val request = Request.Builder()
            .url(Constants.GROQ_URL)
            .addHeader("Authorization", "Bearer ${Constants.GROQ_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network failure: ${e.message}", e)
                onError("""{"action":"speak","text":"Network error. Check your internet connection."}""")
            }

            override fun onResponse(call: Call, response: Response) {
                val rawBody = response.body?.string() ?: ""
                Log.d(TAG, "Response code: ${response.code}")
                Log.d(TAG, "Response body: $rawBody")

                if (!response.isSuccessful) {
                    Log.e(TAG, "API error ${response.code}: $rawBody")
                    onError("""{"action":"speak","text":"API error ${response.code}. Check your Groq key."}""")
                    return
                }

                try {
                    val reply = JSONObject(rawBody)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim()

                    Log.d(TAG, "AI reply: $reply")

                    history.add(
                        JSONObject().apply {
                            put("role", "assistant")
                            put("content", reply)
                        }
                    )

                    onResult(reply)

                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message} — raw: $rawBody", e)
                    onError("""{"action":"speak","text":"I could not parse the response."}""")
                }
            }
        })
    }
}