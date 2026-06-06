# 🤖 Friday AI Assistant

Friday AI Assistant is an Android voice assistant application inspired by intelligent virtual assistants. It uses offline speech recognition, text-to-speech, and AI-powered responses to help users perform tasks through voice commands.

## 🚀 Features

### 🎙️ Voice Recognition
- Wake word detection ("Hey Friday")
- Offline speech recognition using Vosk
- Continuous listening mode
- Voice command processing

### 🗣️ Text-to-Speech
- Natural voice responses
- Spoken confirmations
- Interactive conversations

### 🤖 AI Integration
- AI-powered responses
- Conversational interactions
- Command interpretation
- Smart task execution

### 📱 Device Automation
- Open applications
- Make phone calls
- Send SMS messages
- Set alarms
- Check date and time
- Play music
- Weather information

### 🔄 Background Service
- Always-listening wake word service
- Foreground service support
- Automatic startup capabilities

## 🛠️ Technologies Used

- Kotlin
- Android SDK
- Vosk Speech Recognition
- Text-to-Speech (TTS)
- OkHttp
- JSON Processing

## 📂 Project Architecture

- **MainActivity** – User interface and voice interaction
- **WakeWordService** – Background wake-word detection
- **AIBrain** – AI communication and response generation
- **TaskExecutor** – Executes user commands
- **SpotifyManager** – Music playback integration
- **BootReceiver** – Startup initialization

## 🎯 Supported Commands

Examples:

- "Call John"
- "Send a message to Alex"
- "Set an alarm for 7 AM"
- "What's the weather?"
- "What time is it?"
- "Open Spotify"
- "Play music"

## 📱 Requirements

- Android 8.0 (API 26) or later
- Microphone permission
- Internet connection (for AI features)

## 🔐 Permissions Used

- Record Audio
- Internet Access
- Call Phone
- Send SMS
- Read Contacts
- Foreground Service

## 🚀 Future Improvements

- Local LLM Integration
- Smart Home Control
- WhatsApp Commands
- Calendar Integration
- Multi-language Support
- Custom Wake Words

