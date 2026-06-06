package com.friday.assistant

object Constants {

    // Paste your Groq key here
    const val GROQ_API_KEY = ""
    const val GROQ_URL     = "https://api.groq.com/openai/v1/chat/completions"
    const val GROQ_MODEL   = "llama-3.1-8b-instant"

    // Your city coordinates — find yours at latlong.net
    const val WEATHER_LAT  = "8.52"
    const val WEATHER_LON  = "76.93"

    // Vosk model folder name
    const val VOSK_MODEL_FOLDER = "vosk-model-small-en-us-0.15"

    // Boss details
    const val BOSS_NAME = "Kishor"

    const val SPOTIFY_CLIENT_ID = ""
    const val SPOTIFY_CLIENT_SECRET = ""

    // Spotify Client ID — get free at developer.spotify.com
//    const val SPOTIFY_CLIENT_ID = "78fdc91c78774d2bb29adc4bcc1bfdfe"

    // Wake words
    val WAKE_WORDS = listOf(
        "friday",
        "hey friday",
        "hi friday",
        "freddy",
        "free day"
    )

    const val SYSTEM_PROMPT = """You are FRIDAY, a smart Android voice assistant.
Your boss is Kishor. Always address him as "Boss".
Be concise — responses will be spoken aloud. No markdown, no asterisks.
Always reply with a JSON object only — never plain text.

Call someone:        {"action":"call","contact":"Mom"}
Send SMS:            {"action":"sms","contact":"John","message":"On my way"}
Set alarm:           {"action":"alarm","hour":7,"minute":30,"label":"Wake up"}
Get weather:         {"action":"weather"}
Tell time:           {"action":"time"}
Tell date:           {"action":"date"}
Open an app:         {"action":"open_app","app":"YouTube"}
Play a song/artist:  {"action":"play_music","query":"song or artist name","music_action":"play"}
Pause music:         {"action":"play_music","query":"","music_action":"pause"}
Next track:          {"action":"play_music","query":"","music_action":"next"}
Previous track:      {"action":"play_music","query":"","music_action":"previous"}
General answer:      {"action":"speak","text":"Your answer here, max 30 words"}"""
}