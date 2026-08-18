# TV Tenderr - User Guide

## Overview
TV Tenderr is a Tinder-style media library manager for Plex, Radarr, and Sonarr. Swipe through your movies and TV shows to keep, block, or clean up your collection. Discover new content from all streaming services.

---

## Android App

### Navigation
Tap the **mode label** (top center) to cycle between:
- **Movies** — your Radarr movie library
- **Shows** — your Sonarr TV show library
- **Discover** — new movies/shows from TMDb

### Movie & Show Cards

#### Swipe Gestures
| Gesture | Action | Description |
|---------|--------|-------------|
| **→ Right** | Keep | Keeps the item for 6 months |
| **← Left** | Block | Deletes files and adds to Radarr/Sonarr import list exclusion (prevents re-addition) (10s undo) |
| **↓ Down** | Super Keep | Keeps forever, never expires |
| **↑ Up** | Skip | Skip for now, decide later |

#### Tap Buttons (bottom)
| Button | Action |
|--------|--------|
| **Red circle (✗)** | Block with 10-second undo |
| **Yellow circle (→)** | Skip |
| **Green circle (✓)** | Keep (6 months) |
| **Long-press green** | Super Keep (forever) |
| **Long-press yellow** (Shows only) | Clean files, keep monitoring |

#### Show Status
Cards show **🟢 Continuing** or **🔴 Ended** next to the file size and episode count.

#### Clean Shows
Long-press the **skip button** (yellow) when in Shows mode to:
- Delete all episode files from disk
- Unmonitor all episodes (prevents re-download)
- Keep the show monitored for future new episodes
- 10-second undo window before files are deleted

### Discover Mode
Tap the mode label until it says **Discover** (turns orange).

#### Discover Controls (bottom left)
- **🎬 Movies / 📺 Shows** — tap to switch content type
- **🔥 Popular / 🆕 Newest / ⭐ Top Rated** — tap to cycle sort order

#### Discover Gestures
| Gesture | Action | Description |
|---------|--------|-------------|
| **→ Right** | Add | Adds to Radarr/Sonarr and searches for files |
| **← Left** | Hide | Removes from discover feed permanently |
| **↑ Up** | Skip | Hides from discover |
| **Tap green (+)** | Add to library |
| **Tap red (✗)** | Hide from discover |

Discover automatically filters out movies/shows already in your library.

### History
Tap the **clock icon** (top right) to view history:
- **Movies history** — when in Movies mode
- **Shows history** — when in Shows mode
- **Discover history** — when in Discover mode

History shows all actions with undo buttons:
- **Un-keep** — removes a keep decision
- **Restore** — unblocks a blocked item
- **Remove** — removes an added discover item from Radarr/Sonarr
- **Show Again** — brings back a hidden discover item

### Settings
Tap the **gear icon** (top right) to configure:

#### Server Connection
- **Backend URL** — TV Tenderr server address

#### Service Connections
- **Radarr URL & API Key** — movie management
- **Sonarr URL & API Key** — TV show management
- **Plex URL & Token** — library refresh
- **TMDb API Key** — discover feature

#### Quality Settings
- **Radarr Quality Profile** — Any, SD, HD-720p, HD-1080p, Ultra-HD
- **Sonarr Quality Profile** — same options
- **Radarr Root Folder** — where movies are stored
- **Sonarr Root Folder** — where TV shows are stored

### Plex Refresh
Tap the **refresh icon** (top right) to trigger a Plex library scan after deleting content.

---

## Web GUI

Access at **http://YOUR_SERVER_IP:8899**

### Layout
- **Left sidebar** — navigation (Movies, Shows, Discover, History, Settings)
- **Top bar** — search, view toggle, stats
- **Content area** — cards or grid

### View Modes
Toggle in the top right:
- **🃏 Card view** — one card at a time, drag to swipe or use buttons
- **⊞ Grid view** — all cards in a grid, click for details

### Card View (Desktop)
#### Mouse Drag
| Direction | Action |
|-----------|--------|
| **Drag right** | Keep / Add |
| **Drag left** | Block / Hide |
| **Drag up** | Skip |

#### Action Buttons (right side)
| Button | Action |
|--------|--------|
| **✗ (red)** | Block / Hide |
| **→ (yellow)** | Skip |
| **✓/ + (green)** | Keep / Add |
| **⭐ (blue)** | Super Keep (library only) |

### Grid View
- **Hover** a card to see action buttons
- **Click** a card to open detail panel
- Detail panel shows full info and action buttons

### Discover (Web)
- Switch between Movies/Shows with tabs
- Sort by Popular, Newest, or Top Rated
- Same add/hide gestures as the app

### History (Web)
Shows all three histories combined:
- Movies, Shows, and Discover actions
- Undo buttons for each action type

### Settings (Web)
Same settings as the Android app:
- Server, Radarr, Sonarr, Plex, TMDb connections
- Quality profiles and root folders

---

## Setup (New Installation)

### Prerequisites
- Python 3.10+
- Radarr instance with API key
- Sonarr instance with API key
- Plex server with token
- TMDb API key (free at themoviedb.org)

### Quick Start
```bash
git clone https://github.com/croycrabtree/tv-tenderr.git
cd tv-tenderr
./setup.sh
# Edit .env with your credentials
python3 backend.py
```

### Environment Variables (.env)
```env
RADARR_URL=http://your-radarr:7878
RADARR_KEY=your_radarr_api_key
SONARR_URL=http://your-sonarr:8989
SONARR_KEY=your_sonarr_api_key
PLEX_URL=http://your-plex:32400
PLEX_TOKEN=your_plex_token
TMDB_KEY=your_tmdb_api_key
BACKEND_PORT=8899
```

### Android APK
1. Download the APK from releases
2. Install on your Android device
3. Open Settings and configure your server URL
4. All other settings auto-populate from the backend

### Web Access
Open **http://YOUR_SERVER_IP:8899** in any browser.

---

## Tips
- **Swipe right** on movies you want to keep watching
- **Swipe left** on movies you'll never watch again
- **Super Keep** your all-time favorites
- **Clean shows** after finishing a season to free disk space
- Use **Discover** to find new content from streaming services
- **Refresh Plex** after a delete session to update your library
- Check **History** to undo mistakes
