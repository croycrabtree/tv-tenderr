# TV Tenderr

A Tinder-style app for managing your Plex media library. Swipe through movies and TV shows to keep, block, or clean up your collection.

## Features

- **Swipe gestures**: Right = Keep, Left = Block, Down = Super Keep, Up = Skip
- **Movies & Shows**: Toggle between Radarr movies and Sonarr shows
- **Smart keeps**: Regular keep (6 months) vs Super Keep (forever)
- **Undo protection**: 10-second undo on blocks and cleans
- **Clean shows**: Remove episode files but keep monitoring for new episodes
- **History**: Search, filter, and undo decisions
- **Random order**: Never see the same sequence twice

## Architecture

- **Backend**: Python FastAPI server connecting to Radarr + Sonarr APIs
- **Android**: Native Kotlin app with card swipe UI
- **Runs on**: Hermes VM (Ubuntu) with systemd service

## Setup

1. Start the backend: `python3 backend.py`
2. Install the APK on your phone via ADB
3. Enter the server URL when prompted

## API Endpoints

- `GET /api/movies` - List movies
- `GET /api/shows` - List TV shows
- `POST /api/movies/{id}/keep` - Keep movie (6 months)
- `POST /api/movies/{id}/super_keep` - Keep movie forever
- `POST /api/movies/{id}/block` - Block and delete
- `POST /api/shows/{id}/clean` - Remove files, keep monitoring
- `GET /api/history` - Movie history
- `GET /api/shows/history` - Show history
