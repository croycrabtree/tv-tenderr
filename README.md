# TV Tenderr

A Tinder-style app for managing your Plex media library. Swipe through movies and TV shows to keep, block, or clean up your collection.

![TV Tenderr](https://img.shields.io/badge/Platform-Android-green) ![TV Tenderr](https://img.shields.io/badge/Backend-Python-blue)

## Features

- **Swipe gestures**: Right = Keep, Left = Block, Down = Super Keep, Up = Skip
- **Movies & Shows**: Toggle between Radarr movies and Sonarr shows
- **Smart keeps**: Regular keep (6 months) vs Super Keep (forever)
- **Undo protection**: 10-second undo on blocks and cleans
- **Clean shows**: Remove episode files but keep monitoring for new episodes
- **Plex refresh**: Trigger library scan after cleanup sessions
- **History**: Search, filter, and undo decisions
- **Settings**: Configure all URLs and API keys from the app
- **Random order**: Never see the same sequence twice

## Quick Start

### 1. Clone the repo
```bash
git clone https://github.com/croycrabtree/tv-tenderr.git
cd tv-tenderr
```

### 2. Run setup
```bash
chmod +x setup.sh
./setup.sh
```

### 3. Configure
Edit `.env` with your actual API keys:
```bash
nano .env
```

### 4. Start the backend
```bash
python3 backend.py
```

### 5. Install the APK
Transfer the APK to your phone and install via ADB:
```bash
adb install app-debug.apk
```

### 6. Configure the app
Open TV Tenderr → Settings → Enter your server URL and API keys

## API Keys

### Radarr
1. Open Radarr web UI
2. Settings → General → API Key

### Sonarr
1. Open Sonarr web UI
2. Settings → General → API Key

### Plex Token
1. Open Plex web UI
2. Press F12 → Network tab
3. Click around in Plex
4. Find `X-Plex-Token` in any request header

## Gesture Guide

| Gesture | Action |
|---------|--------|
| Swipe Right | Keep (6 months) |
| Swipe Left | Block & Delete (10s undo) |
| Swipe Down | Super Keep (forever) |
| Swipe Up | Skip |
| Long-press Keep | Super Keep |
| Long-press Skip (shows) | Clean files, keep monitoring |

## Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Android    │────▶│   Backend    │────▶│   Radarr     │
│   TV Tenderr │     │   FastAPI    │     │   Sonarr     │
│              │◀────│   Port 8899  │◀────│   Plex       │
└──────────────┘     └──────────────┘     └──────────────┘
```

- **Backend**: Python FastAPI server connecting to Radarr, Sonarr, and Plex APIs
- **Android**: Native Kotlin app with card swipe UI
- **Runs on**: Any Linux server with Python 3.8+

## Install as Service

```bash
./setup.sh --service
```

This enables the backend to start automatically on boot.

## Project Structure

```
tv-tenderr/
├── backend.py          # FastAPI backend server
├── setup.sh            # Setup script
├── .env.example        # Environment template
├── .gitignore          # Git ignore rules
├── movie-swipe.service # Systemd service file
├── android/            # Android app source
│   └── app/
│       └── src/
│           └── main/
│               ├── java/com/movieswipe/
│               │   ├── MainActivity.kt
│               │   ├── HistoryActivity.kt
│               │   ├── SettingsActivity.kt
│               │   ├── ApiClient.kt
│               │   └── Models.kt
│               └── res/
│                   └── layout/
└── README.md
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

MIT License

## Acknowledgments

- Built with [FastAPI](https://fastapi.tiangolo.com/)
- Android UI with [Material Design](https://material.io/)
- Inspired by the need to manage massive Plex libraries
