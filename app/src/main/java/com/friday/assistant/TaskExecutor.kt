package com.friday.assistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.telephony.SmsManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log

class TaskExecutor(private val context: Context) {

    fun execute(response: String, speak: (String) -> Unit) {
        val json = extractJson(response)
        if (json == null) {
            speak(response.take(200))
            return
        }

        when (json.optString("action")) {
            "call"       -> handleCall(json, speak)
            "sms"        -> handleSms(json, speak)
            "alarm"      -> handleAlarm(json, speak)
            "weather"    -> fetchWeather(speak)
            "time"       -> speak("It is " + SimpleDateFormat("h:mm a",
                Locale.getDefault()).format(Date()))
            "date"       -> speak("Today is " + SimpleDateFormat("EEEE, MMMM d",
                Locale.getDefault()).format(Date()))
            "open_app"   -> handleOpenApp(json, speak)
            "play_music" -> handlePlayMusic(json, speak)
            "speak"      -> speak(json.optString("text", "Done."))
            else         -> speak(json.optString("text", "Done."))
        }
    }

    // ── Call ───────────────────────────────────────────────────────────────

    private fun handleCall(json: JSONObject, speak: (String) -> Unit) {
        val name = json.optString("contact", "").trim()

        // First try exact contact lookup
        val number = lookupContact(name)
        if (number != null) {
            speak("Calling $name, Boss.")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                } catch (e: SecurityException) {
                    speak("I do not have permission to make calls, Boss.")
                }
            }, 1800)
        } else {
            // Try fuzzy search — split name and search by first name only
            val firstName = name.split(" ").firstOrNull() ?: name
            val fuzzyNumber = lookupContact(firstName)
            if (fuzzyNumber != null) {
                speak("Calling $firstName, Boss.")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$fuzzyNumber"))
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }, 1800)
            } else {
                speak("I could not find $name in your contacts, Boss.")
            }
        }
    }

    // ── SMS ────────────────────────────────────────────────────────────────

    private fun handleSms(json: JSONObject, speak: (String) -> Unit) {
        val name    = json.optString("contact", "").trim()
        val message = json.optString("message", "")
        val number  = lookupContact(name)
            ?: lookupContact(name.split(" ").firstOrNull() ?: name)

        if (number != null && message.isNotEmpty()) {
            try {
                @Suppress("DEPRECATION")
                SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
                speak("Message sent to $name, Boss.")
            } catch (e: Exception) {
                speak("Failed to send the message, Boss.")
            }
        } else {
            speak("Could not find $name in your contacts, Boss.")
        }
    }

    // ── Alarm ──────────────────────────────────────────────────────────────

    private fun handleAlarm(json: JSONObject, speak: (String) -> Unit) {
        val hour   = json.optInt("hour", 7)
        val minute = json.optInt("minute", 0)
        val label  = json.optString("label", "Alarm")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        val amPm   = if (hour < 12) "AM" else "PM"
        val h12    = if (hour % 12 == 0) 12 else hour % 12
        val minStr = minute.toString().padStart(2, '0')
        speak("Alarm set for $h12:$minStr $amPm, Boss.")
    }

    // ── Weather ────────────────────────────────────────────────────────────

    private fun fetchWeather(speak: (String) -> Unit) {
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${Constants.WEATHER_LAT}" +
                "&longitude=${Constants.WEATHER_LON}" +
                "&current_weather=true"

        OkHttpClient().newCall(Request.Builder().url(url).build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    speak("Cannot get weather right now, Boss.")
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val w    = JSONObject(response.body?.string() ?: "")
                            .getJSONObject("current_weather")
                        val temp = w.getDouble("temperature").toInt()
                        val code = w.getInt("weathercode")
                        speak("Currently ${weatherText(code)}, ${temp} degrees Celsius, Boss.")
                    } catch (e: Exception) {
                        speak("Could not read the weather, Boss.")
                    }
                }
            })
    }

    // ── Open App ───────────────────────────────────────────────────────────

    private fun handleOpenApp(json: JSONObject, speak: (String) -> Unit) {
        val appName = json.optString("app", "").lowercase().trim()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({

            // Step 1 — check hardcoded package map first
            val packages = mapOf(
                "youtube"    to "com.google.android.youtube",
                "whatsapp"   to "com.whatsapp",
                "chrome"     to "com.android.chrome",
                "maps"       to "com.google.android.apps.maps",
                "gmail"      to "com.google.android.gm",
                "instagram"  to "com.instagram.android",
                "telegram"   to "org.telegram.messenger",
                "spotify"    to "com.spotify.music",
                "settings"   to "com.android.settings",
                "calculator" to "com.google.android.calculator",
                "camera"     to "com.android.camera2",
                "netflix"    to "com.netflix.mediaclient",
                "twitter"    to "com.twitter.android",
                "facebook"   to "com.facebook.katana",
                "snapchat"   to "com.snapchat.android",
                "contacts"   to "com.android.contacts",
                "clock"      to "com.android.deskclock",
                "calendar"   to "com.android.calendar",
                "gpay"       to "com.google.android.apps.nbu.paisa.user",
                "phonepe"    to "com.phonepe.app",
                "paytm"      to "net.one97.paytm",
                "flipkart"   to "com.flipkart.android",
                "amazon"     to "com.amazon.mShop.android.shopping",
                "hotstar"    to "in.startv.hotstar",
                "phone"      to "com.google.android.dialer"
            )

            val pkg = packages.entries.find { appName.contains(it.key) }?.value

            if (pkg != null) {
                val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                if (launch != null) {
                    speak("Opening $appName, Boss.")
                    launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(launch)
                    return@postDelayed
                }
            }

            // Step 2 — search through all installed apps by label name
            val pm = context.packageManager
            val installedApps = pm.getInstalledApplications(0)

            val found = installedApps
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .firstOrNull {
                    val label = pm.getApplicationLabel(it).toString().lowercase()
                    label.contains(appName) || appName.contains(label)
                }

            if (found != null) {
                val label = pm.getApplicationLabel(found).toString()
                speak("Opening $label, Boss.")
                val launch = pm.getLaunchIntentForPackage(found.packageName)
                launch?.let {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(it)
                }
            } else {
                speak("I could not find $appName on your phone, Boss.")
            }

        }, 1000)
    }

    private fun handlePlayMusic(json: JSONObject, speak: (String) -> Unit) {
        val query  = json.optString("query", "").trim()
        val action = json.optString("music_action", "play").trim()
        val spotify = SpotifyManager(context)

        Log.d("TaskExecutor", "Music action: $action, query: $query")

        when (action) {
            "pause"    -> spotify.pause    { speak(it) }
            "next"     -> spotify.next     { speak(it) }
            "previous" -> spotify.previous { speak(it) }
            "resume"   -> spotify.resume   { speak(it) }
            else       -> spotify.play(query) { speak(it) }
        }
    }

    // ── Contacts ───────────────────────────────────────────────────────────

    private fun lookupContact(name: String): String? {
        if (name.isBlank()) return null

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )

        // Collect all matches scored by similarity
        val matches = mutableListOf<Pair<String, String>>()

        cursor?.use {
            while (it.moveToNext()) {
                val contactName = it.getString(0) ?: continue
                val number      = it.getString(1) ?: continue
                if (contactName.contains(name, ignoreCase = true) ||
                    name.contains(contactName, ignoreCase = true)) {
                    matches.add(Pair(contactName, number.replace("\\s|-".toRegex(), "")))
                }
            }
        }

        // Return best match — prefer shorter names (more specific)
        return matches.minByOrNull { it.first.length }?.second
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun extractJson(text: String): JSONObject? {
        return try {
            val start = text.indexOf("{")
            val end   = text.lastIndexOf("}") + 1
            if (start >= 0 && end > start) JSONObject(text.substring(start, end)) else null
        } catch (e: Exception) { null }
    }

    private fun weatherText(code: Int): String = when (code) {
        0          -> "clear sky"
        1, 2, 3    -> "partly cloudy"
        45, 48     -> "foggy"
        51, 53, 55 -> "drizzling"
        61, 63, 65 -> "raining"
        80, 81, 82 -> "rain showers"
        95         -> "thunderstorm"
        else       -> "cloudy"
    }
}