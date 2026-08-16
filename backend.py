"""TV Tenderr - Backend API
Connects to Radarr + Sonarr + Plex to serve movies/shows for the swipe interface.
"""
import os
import json
import random
import httpx
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, Response
from fastapi.staticfiles import StaticFiles
from pathlib import Path
from datetime import datetime
from dotenv import load_dotenv

# Load .env file if it exists
load_dotenv(Path(__file__).parent / ".env")

app = FastAPI(title="TV Tenderr")

# Serve web UI
WEB_DIR = Path(__file__).parent / "web"
app.mount("/static", StaticFiles(directory=str(WEB_DIR)), name="static")

@app.get("/")
async def serve_ui():
    # Check if first run (no .env or missing config)
    env_file = Path(__file__).parent / ".env"
    if not env_file.exists() or not RADARR_KEY or RADARR_KEY == "your_radarr_api_key":
        return FileResponse(str(WEB_DIR / "setup.html"))
    return FileResponse(str(WEB_DIR / "index.html"))

# Config - set these in .env or use env vars
RADARR_URL = os.getenv("RADARR_URL", "http://localhost:7878")
RADARR_KEY = os.getenv("RADARR_KEY", "")
SONARR_URL = os.getenv("SONARR_URL", "http://localhost:8989")
SONARR_KEY = os.getenv("SONARR_KEY", "")
PLEX_URL = os.getenv("PLEX_URL", "http://localhost:32400")
PLEX_TOKEN = os.getenv("PLEX_TOKEN", "")

DATA_DIR = Path("/home/roy/projects/movie-swipe/data")
DATA_DIR.mkdir(exist_ok=True)

def is_decision_active(info):
    """Check if a decision is still active (not expired)."""
    action = info.get("action")
    if action == "block":
        return True
    if action == "super_keep":
        return True
    if action == "keep":
        # Regular keeps expire after 6 months
        timestamp = info.get("timestamp")
        if timestamp:
            from dateutil.relativedelta import relativedelta
            decided = datetime.fromisoformat(timestamp)
            expires = decided + relativedelta(months=6)
            return datetime.now() < expires
        return True
    return False

DECISIONS_FILE = DATA_DIR / "decisions.json"
SHOW_DECISIONS_FILE = DATA_DIR / "show_decisions.json"

def load_decisions():
    if DECISIONS_FILE.exists():
        return json.loads(DECISIONS_FILE.read_text())
    return {}

def save_decisions(decisions):
    DECISIONS_FILE.write_text(json.dumps(decisions, indent=2))

def load_show_decisions():
    if SHOW_DECISIONS_FILE.exists():
        return json.loads(SHOW_DECISIONS_FILE.read_text())
    return {}

def save_show_decisions(decisions):
    SHOW_DECISIONS_FILE.write_text(json.dumps(decisions, indent=2))

def get_plex_sections():
    """Get Plex library sections to find the movies library."""
    if not PLEX_TOKEN:
        return None
    try:
        r = httpx.get(f"{PLEX_URL}/library/sections", params={"X-Plex-Token": PLEX_TOKEN}, timeout=10)
        r.raise_for_status()
        import xml.etree.ElementTree as ET
        root = ET.fromstring(r.text)
        for dir_elem in root.findall(".//Directory"):
            if dir_elem.get("type") == "movie":
                return dir_elem.get("key")
    except Exception as e:
        print(f"Plex error: {e}")
    return None

def get_plex_watch_history(section_key):
    """Get watch history from Plex."""
    if not PLEX_TOKEN or not section_key:
        return {}
    try:
        r = httpx.get(
            f"{PLEX_URL}/library/sections/{section_key}/all",
            params={"X-Plex-Token": PLEX_TOKEN, "type": "1", "viewCount": ">>0"},
            timeout=30
        )
        r.raise_for_status()
        import xml.etree.ElementTree as ET
        root = ET.fromstring(r.text)
        history = {}
        for video in root.findall(".//Video"):
            rating_key = video.get("ratingKey")
            view_count = int(video.get("viewCount", 0))
            last_viewed = video.get("lastViewedAt", "")
            history[rating_key] = {
                "viewCount": view_count,
                "lastViewedAt": last_viewed
            }
        return history
    except Exception as e:
        print(f"Plex history error: {e}")
        return {}

def get_poster_url(movie):
    """Get poster URL from Radarr images - prefer remote TMDB URL."""
    for img in movie.get("images", []):
        if img.get("coverType") == "poster":
            remote = img.get("remoteUrl")
            if remote:
                return remote
            return img.get("url")
    return None

@app.get("/api/movies")
async def get_movies(skip: int = 0, limit: int = 50):
    """Get movies from Radarr with metadata."""
    decisions = load_decisions()
    
    async with httpx.AsyncClient() as client:
        # Get all movies from Radarr
        r = await client.get(
            f"{RADARR_URL}/api/v3/movie",
            headers={"X-Api-Key": RADARR_KEY},
            timeout=30
        )
        r.raise_for_status()
        radarr_movies = r.json()
    
    movies = []
    for m in radarr_movies:
        movie_id = str(m["id"])
        decision = decisions.get(movie_id, None)
        
        # Skip already-decided movies
        if decision and is_decision_active(decision):
            continue
        
        # Get file size if available
        size_gb = 0
        if m.get("sizeOnDisk"):
            size_gb = round(m["sizeOnDisk"] / (1024**3), 2)
        
        poster_url = get_poster_url(m)
        
        movies.append({
            "id": m["id"],
            "title": m["title"],
            "year": m.get("year"),
            "overview": m.get("overview", ""),
            "genres": [g if isinstance(g, str) else g["name"] for g in m.get("genres", [])],
            "rating": m.get("ratings", {}).get("value"),
            "runtime": m.get("runtime"),
            "sizeGB": size_gb,
            "posterUrl": poster_url,
            "hasFile": m.get("hasFile", False),
            "monitored": m.get("monitored", False),
            "qualityProfileId": m.get("qualityProfileId"),
            "status": m.get("status"),
        })
    
    random.shuffle(movies)
    return {"movies": movies[skip:skip+limit], "total": len(movies)}

@app.post("/api/movies/{movie_id}/keep")
async def keep_movie(movie_id: int):
    """Mark a movie as keep - expires in 6 months."""
    decisions = load_decisions()
    
    movie_info = {}
    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{RADARR_URL}/api/v3/movie/{movie_id}",
            headers={"X-Api-Key": RADARR_KEY},
            timeout=15
        )
        if r.status_code == 200:
            m = r.json()
            movie_info = {
                "title": m.get("title"),
                "year": m.get("year"),
                "posterUrl": get_poster_url(m),
            }
    
    decisions[str(movie_id)] = {
        "action": "keep",
        "timestamp": datetime.now().isoformat(),
        **movie_info
    }
    save_decisions(decisions)
    return {"ok": True, "action": "keep"}


@app.post("/api/movies/{movie_id}/super_keep")
async def super_keep_movie(movie_id: int):
    """Mark a movie as super_keep - kept forever."""
    decisions = load_decisions()
    
    movie_info = {}
    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{RADARR_URL}/api/v3/movie/{movie_id}",
            headers={"X-Api-Key": RADARR_KEY},
            timeout=15
        )
        if r.status_code == 200:
            m = r.json()
            movie_info = {
                "title": m.get("title"),
                "year": m.get("year"),
                "posterUrl": get_poster_url(m),
            }
    
    decisions[str(movie_id)] = {
        "action": "super_keep",
        "timestamp": datetime.now().isoformat(),
        **movie_info
    }
    save_decisions(decisions)
    return {"ok": True, "action": "super_keep"}

@app.post("/api/movies/{movie_id}/block")
async def block_movie(movie_id: int):
    """Remove movie from Radarr and add to blocklist."""
    decisions = load_decisions()
    
    async with httpx.AsyncClient() as client:
        # Get movie details BEFORE deleting
        r = await client.get(
            f"{RADARR_URL}/api/v3/movie/{movie_id}",
            headers={"X-Api-Key": RADARR_KEY},
            timeout=15
        )
        movie_info = {}
        if r.status_code == 200:
            m = r.json()
            movie_info = {
                "title": m.get("title"),
                "year": m.get("year"),
                "tmdbId": m.get("tmdbId"),
                "posterUrl": get_poster_url(m),
            }
        
        # Delete from Radarr (deleteFiles=true removes the actual files)
        r = await client.delete(
            f"{RADARR_URL}/api/v3/movie/{movie_id}",
            headers={"X-Api-Key": RADARR_KEY},
            params={"deleteFiles": "true", "addImportListExclusion": "true"},
            timeout=30
        )
        if r.status_code not in (200, 204):
            raise HTTPException(status_code=r.status_code, detail=f"Radarr delete failed: {r.text}")
    
    decisions[str(movie_id)] = {
        "action": "block",
        "timestamp": datetime.now().isoformat(),
        **movie_info
    }
    save_decisions(decisions)
    return {"ok": True, "action": "block"}

@app.post("/api/movies/{movie_id}/skip")
async def skip_movie(movie_id: int):
    """Skip - decide later."""
    decisions = load_decisions()
    decisions[str(movie_id)] = {
        "action": "skip",
        "timestamp": datetime.now().isoformat()
    }
    save_decisions(decisions)
    return {"ok": True, "action": "skip"}

@app.get("/api/history")
async def get_history():
    """Get all decisions with movie details."""
    decisions = load_decisions()
    history = []
    
    # For decisions missing titles, look them up from Radarr
    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{RADARR_URL}/api/v3/movie",
            headers={"X-Api-Key": RADARR_KEY},
            timeout=30
        )
        radarr_movies = {str(m["id"]): m for m in r.json()} if r.status_code == 200 else {}
    
    for mid, info in decisions.items():
        title = info.get("title")
        year = info.get("year")
        poster = info.get("posterUrl")
        
        # If no title stored, try to get it from Radarr
        if not title and mid in radarr_movies:
            m = radarr_movies[mid]
            title = m.get("title")
            year = m.get("year")
            poster = get_poster_url(m)
        
        history.append({
            "movieId": int(mid),
            "action": info.get("action"),
            "timestamp": info.get("timestamp"),
            "title": title,
            "year": year,
            "tmdbId": info.get("tmdbId"),
            "posterUrl": poster,
        })
    # Filter out skips
    history = [h for h in history if h.get("action") != "skip"]
    # Sort by timestamp, newest first
    history.sort(key=lambda x: x.get("timestamp", ""), reverse=True)
    return {"history": history}

@app.post("/api/movies/{movie_id}/unkeep")
async def unkeep_movie(movie_id: int):
    """Remove a keep decision - movie goes back to undecided."""
    decisions = load_decisions()
    mid = str(movie_id)
    if mid in decisions and decisions[mid].get("action") == "keep":
        del decisions[mid]
        save_decisions(decisions)
        return {"ok": True, "action": "unkeep"}
    raise HTTPException(status_code=404, detail="No keep decision found")

@app.post("/api/movies/{movie_id}/unblock")
async def unblock_movie(movie_id: int):
    """Re-add a blocked movie to Radarr using stored tmdbId."""
    decisions = load_decisions()
    mid = str(movie_id)
    
    if mid not in decisions or decisions[mid].get("action") != "block":
        raise HTTPException(status_code=404, detail="No block decision found")
    
    tmdb_id = decisions[mid].get("tmdbId")
    if not tmdb_id:
        raise HTTPException(status_code=400, detail="No tmdbId stored - cannot re-add")
    
    async with httpx.AsyncClient() as client:
        # Look up the movie on Radarr by tmdbId
        r = await client.get(
            f"{RADARR_URL}/api/v3/movie/lookup",
            headers={"X-Api-Key": RADARR_KEY},
            params={"term": f"tmdb:{tmdb_id}"},
            timeout=15
        )
        if r.status_code != 200 or not r.json():
            raise HTTPException(status_code=404, detail="Movie not found on TMDB")
        
        movie_data = r.json()[0]
        
        # Add movie back to Radarr
        add_payload = {
            "title": movie_data["title"],
            "tmdbId": tmdb_id,
            "qualityProfileId": movie_data.get("qualityProfileId") or 4,
            "rootFolderPath": "H:\\",
            "monitored": True,
            "addOptions": {"searchForMovie": False},
            "images": movie_data.get("images", []),
        }
        
        r = await client.post(
            f"{RADARR_URL}/api/v3/movie",
            headers={"X-Api-Key": RADARR_KEY},
            json=add_payload,
            timeout=30
        )
        if r.status_code not in (200, 201):
            raise HTTPException(status_code=r.status_code, detail=f"Radarr add failed: {r.text}")
    
    # Remove from decisions
    del decisions[mid]
    save_decisions(decisions)
    return {"ok": True, "action": "unblock", "title": movie_data["title"]}

@app.get("/api/stats")
async def get_stats():
    """Get summary stats."""
    decisions = load_decisions()
    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{RADARR_URL}/api/v3/movie",
            headers={"X-Api-Key": RADARR_KEY},
            timeout=30
        )
        r.raise_for_status()
        movies = r.json()
    
    kept = sum(1 for d in decisions.values() if d.get("action") == "keep")
    blocked = sum(1 for d in decisions.values() if d.get("action") == "block")
    skipped = sum(1 for d in decisions.values() if d.get("action") == "skip")
    decided = kept + blocked + skipped
    
    return {
        "totalMovies": len(movies),
        "kept": kept,
        "blocked": blocked,
        "skipped": skipped,
        "undecided": len(movies) - decided,
        "decisions": decisions
    }

@app.get("/api/blocklist")
async def get_blocklist():
    """Get current Radarr blocklist."""
    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{RADARR_URL}/api/v3/blocklist",
            headers={"X-Api-Key": RADARR_KEY},
            timeout=30
        )
        r.raise_for_status()
        data = r.json()
    
    items = data.get("records", data) if isinstance(data, dict) else data
    return {"blocklist": items, "count": len(items)}

@app.get("/api/poster/{movie_id}")
async def get_poster(movie_id: int):
    """Proxy poster image from Radarr to avoid CORS issues."""
    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{RADARR_URL}/api/v3/movie/{movie_id}",
            headers={"X-Api-Key": RADARR_KEY},
            timeout=15
        )
        r.raise_for_status()
        movie = r.json()
    
    poster_path = get_poster_url(movie)
    if not poster_path:
        raise HTTPException(status_code=404, detail="No poster")
    
    # Radarr returns relative paths like /MediaCover/7/poster.jpg
    full_url = f"{RADARR_URL}{poster_path}" if poster_path.startswith("/") else poster_path
    
    async with httpx.AsyncClient() as client:
        r = await client.get(full_url, headers={"X-Api-Key": RADARR_KEY}, timeout=15)
        r.raise_for_status()
    
    return Response(content=r.content, media_type="image/jpeg")

@app.get("/api/config")
async def get_config():
    """Get app configuration."""
    return {
        "radarrUrl": RADARR_URL,
        "radarrKey": RADARR_KEY,
        "sonarrUrl": SONARR_URL,
        "sonarrKey": SONARR_KEY,
        "plexUrl": PLEX_URL,
        "plexToken": PLEX_TOKEN,
        "tmdbKey": TMDB_KEY,
    }

@app.post("/api/config")
async def update_config(config: dict):
    """Update configuration."""
    global RADARR_URL, RADARR_KEY, SONARR_URL, SONARR_KEY, PLEX_URL, PLEX_TOKEN
    global RADARR_QUALITY_ID, SONARR_QUALITY_ID, RADARR_ROOT_FOLDER, SONARR_ROOT_FOLDER, TMDB_KEY
    if "radarrUrl" in config and config["radarrUrl"]:
        RADARR_URL = config["radarrUrl"]
    if "radarrKey" in config and config["radarrKey"]:
        RADARR_KEY = config["radarrKey"]
    if "sonarrUrl" in config and config["sonarrUrl"]:
        SONARR_URL = config["sonarrUrl"]
    if "sonarrKey" in config and config["sonarrKey"]:
        SONARR_KEY = config["sonarrKey"]
    if "plexUrl" in config and config["plexUrl"]:
        PLEX_URL = config["plexUrl"]
    if "plexToken" in config and config["plexToken"]:
        PLEX_TOKEN = config["plexToken"]
    if "radarrQualityId" in config:
        RADARR_QUALITY_ID = int(config["radarrQualityId"])
    if "sonarrQualityId" in config:
        SONARR_QUALITY_ID = int(config["sonarrQualityId"])
    if "radarrRootFolder" in config:
        RADARR_ROOT_FOLDER = config["radarrRootFolder"]
    if "sonarrRootFolder" in config:
        SONARR_ROOT_FOLDER = config["sonarrRootFolder"]
    if "tmdbKey" in config and config["tmdbKey"]:
        TMDB_KEY = config["tmdbKey"]
    return {"ok": True}

# ==================== SONARR SHOW ENDPOINTS ====================

@app.get("/api/shows")
async def get_shows(skip: int = 0, limit: int = 50):
    """Get TV shows from Sonarr with metadata."""
    decisions = load_show_decisions()

    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{SONARR_URL}/api/v3/series",
            headers={"X-Api-Key": SONARR_KEY},
            timeout=30
        )
        r.raise_for_status()
        sonarr_shows = r.json()

    shows = []
    for s in sonarr_shows:
        show_id = str(s["id"])
        decision = decisions.get(show_id, None)

        # Skip already-decided shows
        if decision and is_decision_active(decision):
            continue

        # Calculate total size and episode count
        total_size = 0
        episode_count = 0
        for season in s.get("seasons", []):
            stats = season.get("statistics", {})
            total_size += stats.get("sizeOnDisk", 0)
            episode_count += stats.get("episodeFileCount", 0)

        size_gb = round(total_size / (1024**3), 2) if total_size else 0

        # Get poster URL
        poster_url = None
        for img in s.get("images", []):
            if img.get("coverType") == "poster":
                poster_url = img.get("remoteUrl") or img.get("url")
                break

        shows.append({
            "id": s["id"],
            "title": s["title"],
            "year": s.get("year"),
            "overview": s.get("overview", ""),
            "genres": s.get("genres", []),
            "rating": s.get("ratings", {}).get("value"),
            "seasonCount": len(s.get("seasons", [])),
            "episodeCount": episode_count,
            "sizeGB": size_gb,
            "posterUrl": poster_url,
            "status": s.get("status"),
            "monitored": s.get("monitored", False),
            "network": s.get("network"),
        })

    random.shuffle(shows)
    return {"shows": shows[skip:skip+limit], "total": len(shows)}


@app.post("/api/shows/{show_id}/keep")
async def keep_show(show_id: int):
    """Mark a show as keep - expires in 6 months."""
    decisions = load_show_decisions()

    show_info = {}
    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{SONARR_URL}/api/v3/series/{show_id}",
            headers={"X-Api-Key": SONARR_KEY},
            timeout=15
        )
        if r.status_code == 200:
            s = r.json()
            poster_url = None
            for img in s.get("images", []):
                if img.get("coverType") == "poster":
                    poster_url = img.get("remoteUrl") or img.get("url")
                    break
            show_info = {
                "title": s.get("title"),
                "year": s.get("year"),
                "posterUrl": poster_url,
            }

    decisions[str(show_id)] = {
        "action": "keep",
        "timestamp": datetime.now().isoformat(),
        **show_info
    }
    save_show_decisions(decisions)
    return {"ok": True, "action": "keep"}


@app.post("/api/shows/{show_id}/super_keep")
async def super_keep_show(show_id: int):
    """Mark a show as super_keep - kept forever."""
    decisions = load_show_decisions()

    show_info = {}
    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{SONARR_URL}/api/v3/series/{show_id}",
            headers={"X-Api-Key": SONARR_KEY},
            timeout=15
        )
        if r.status_code == 200:
            s = r.json()
            poster_url = None
            for img in s.get("images", []):
                if img.get("coverType") == "poster":
                    poster_url = img.get("remoteUrl") or img.get("url")
                    break
            show_info = {
                "title": s.get("title"),
                "year": s.get("year"),
                "posterUrl": poster_url,
            }

    decisions[str(show_id)] = {
        "action": "super_keep",
        "timestamp": datetime.now().isoformat(),
        **show_info
    }
    save_show_decisions(decisions)
    return {"ok": True, "action": "super_keep"}


@app.post("/api/shows/{show_id}/block")
async def block_show(show_id: int):
    """Remove show from Sonarr (delete files + blocklist)."""
    decisions = load_show_decisions()

    async with httpx.AsyncClient() as client:
        # Get show details BEFORE deleting
        r = await client.get(
            f"{SONARR_URL}/api/v3/series/{show_id}",
            headers={"X-Api-Key": SONARR_KEY},
            timeout=15
        )
        show_info = {}
        if r.status_code == 200:
            s = r.json()
            poster_url = None
            for img in s.get("images", []):
                if img.get("coverType") == "poster":
                    poster_url = img.get("remoteUrl") or img.get("url")
                    break
            show_info = {
                "title": s.get("title"),
                "year": s.get("year"),
                "tvdbId": s.get("tvdbId"),
                "posterUrl": poster_url,
            }

        # Delete from Sonarr
        r = await client.delete(
            f"{SONARR_URL}/api/v3/series/{show_id}",
            headers={"X-Api-Key": SONARR_KEY},
            params={"deleteFiles": "true", "addImportListExclusion": "true"},
            timeout=30
        )
        if r.status_code not in (200, 204):
            raise HTTPException(status_code=r.status_code, detail=f"Sonarr delete failed: {r.text}")

    decisions[str(show_id)] = {
        "action": "block",
        "timestamp": datetime.now().isoformat(),
        **show_info
    }
    save_show_decisions(decisions)
    return {"ok": True, "action": "block"}


@app.post("/api/shows/{show_id}/skip")
async def skip_show(show_id: int):
    """Skip a show - decide later."""
    decisions = load_show_decisions()
    decisions[str(show_id)] = {
        "action": "skip",
        "timestamp": datetime.now().isoformat()
    }
    save_show_decisions(decisions)
    return {"ok": True, "action": "skip"}


@app.post("/api/shows/{show_id}/clean")
async def clean_show(show_id: int):
    """Remove all episode files but keep show monitored for future episodes."""
    decisions = load_show_decisions()

    async with httpx.AsyncClient() as client:
        # Get show info before cleaning
        r = await client.get(
            f"{SONARR_URL}/api/v3/series/{show_id}",
            headers={"X-Api-Key": SONARR_KEY},
            timeout=15
        )
        show_info = {}
        if r.status_code == 200:
            s = r.json()
            poster_url = None
            for img in s.get("images", []):
                if img.get("coverType") == "poster":
                    poster_url = img.get("remoteUrl") or img.get("url")
                    break
            show_info = {
                "title": s.get("title"),
                "year": s.get("year"),
                "posterUrl": poster_url,
            }

        # Get episode files
        r = await client.get(
            f"{SONARR_URL}/api/v3/episodefile",
            headers={"X-Api-Key": SONARR_KEY},
            params={"seriesId": show_id},
            timeout=30
        )
        if r.status_code != 200:
            raise HTTPException(status_code=r.status_code, detail="Failed to get episode files")

        files = r.json()
        deleted = 0
        total_size = 0
        for f in files:
            total_size += f.get("size", 0)
            dr = await client.delete(
                f"{SONARR_URL}/api/v3/episodefile/{f['id']}",
                headers={"X-Api-Key": SONARR_KEY},
                timeout=15
            )
            if dr.status_code in (200, 204):
                deleted += 1

        # Unmonitor all episodes so they don't re-download
        er = await client.get(
            f"{SONARR_URL}/api/v3/episode",
            headers={"X-Api-Key": SONARR_KEY},
            params={"seriesId": show_id},
            timeout=30
        )
        if er.status_code == 200:
            episodes = er.json()
            for ep in episodes:
                if ep.get("monitored"):
                    ep["monitored"] = False
                    await client.put(
                        f"{SONARR_URL}/api/v3/episode/{ep['id']}",
                        headers={"X-Api-Key": SONARR_KEY},
                        json=ep,
                        timeout=15
                    )

        # Make sure show stays monitored (for future episodes)
        r = await client.get(
            f"{SONARR_URL}/api/v3/series/{show_id}",
            headers={"X-Api-Key": SONARR_KEY},
            timeout=15
        )
        if r.status_code == 200:
            show = r.json()
            show["monitored"] = True
            await client.put(
                f"{SONARR_URL}/api/v3/series/{show_id}",
                headers={"X-Api-Key": SONARR_KEY},
                json=show,
                timeout=15
            )

    # Log the clean action
    decisions[str(show_id)] = {
        "action": "clean",
        "timestamp": datetime.now().isoformat(),
        "deletedFiles": deleted,
        "freedGB": round(total_size / (1024**3), 2),
        **show_info
    }
    save_show_decisions(decisions)

    return {"ok": True, "deleted": deleted, "total": len(files), "freedGB": round(total_size / (1024**3), 2)}


@app.post("/api/shows/{show_id}/unclean")
async def unclean_show(show_id: int):
    """Re-monitor all episodes for a show (undo a clean)."""
    decisions = load_show_decisions()

    async with httpx.AsyncClient() as client:
        # Re-monitor all episodes
        er = await client.get(
            f"{SONARR_URL}/api/v3/episode",
            headers={"X-Api-Key": SONARR_KEY},
            params={"seriesId": show_id},
            timeout=30
        )
        re_monitored = 0
        if er.status_code == 200:
            episodes = er.json()
            for ep in episodes:
                if not ep.get("monitored"):
                    ep["monitored"] = True
                    await client.put(
                        f"{SONARR_URL}/api/v3/episode/{ep['id']}",
                        headers={"X-Api-Key": SONARR_KEY},
                        json=ep,
                        timeout=15
                    )
                    re_monitored += 1

    # Remove clean decision
    sid = str(show_id)
    if sid in decisions and decisions[sid].get("action") == "clean":
        del decisions[sid]
        save_show_decisions(decisions)

    return {"ok": True, "reMonitored": re_monitored}


@app.get("/api/shows/history")
async def get_show_history():
    """Get all show decisions with details."""
    decisions = load_show_decisions()
    history = []

    # Look up missing titles from Sonarr
    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{SONARR_URL}/api/v3/series",
            headers={"X-Api-Key": SONARR_KEY},
            timeout=30
        )
        sonarr_shows = {str(s["id"]): s for s in r.json()} if r.status_code == 200 else {}

    for sid, info in decisions.items():
        title = info.get("title")
        year = info.get("year")
        poster = info.get("posterUrl")

        if not title and sid in sonarr_shows:
            s = sonarr_shows[sid]
            title = s.get("title")
            year = s.get("year")
            for img in s.get("images", []):
                if img.get("coverType") == "poster":
                    poster = img.get("remoteUrl") or img.get("url")
                    break

        history.append({
            "showId": int(sid),
            "action": info.get("action"),
            "timestamp": info.get("timestamp"),
            "title": title,
            "year": year,
            "posterUrl": poster,
            "deletedFiles": info.get("deletedFiles"),
            "freedGB": info.get("freedGB"),
        })

    # Filter out skips
    history = [h for h in history if h.get("action") != "skip"]
    history.sort(key=lambda x: x.get("timestamp", ""), reverse=True)
    return {"history": history}


@app.post("/api/plex/refresh")
async def plex_refresh():
    """Trigger Plex library refresh for all sections."""
    if not PLEX_TOKEN:
        raise HTTPException(status_code=400, detail="No Plex token configured")

    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{PLEX_URL}/library/sections/all/refresh",
            headers={"X-Plex-Token": PLEX_TOKEN},
            timeout=15
        )

    return {"ok": True, "status": r.status_code}



def get_existing_movie_tmdb_ids():
    """Get TMDb IDs of all movies in Radarr."""
    try:
        import httpx as _httpx
        r = _httpx.get(f"{RADARR_URL}/api/v3/movie", headers={"X-Api-Key": RADARR_KEY}, timeout=30)
        if r.status_code == 200:
            return {str(m.get("tmdbId")) for m in r.json() if m.get("tmdbId")}
    except:
        pass
    return set()

def get_existing_show_titles():
    """Get normalized titles of all shows in Sonarr."""
    try:
        import httpx as _httpx
        r = _httpx.get(f"{SONARR_URL}/api/v3/series", headers={"X-Api-Key": SONARR_KEY}, timeout=30)
        if r.status_code == 200:
            titles = set()
            for s in r.json():
                title = s.get("title", "").lower().strip()
                year = s.get("year", "")
                titles.add(f"{title}|{year}")
            return titles
    except:
        pass
    return set()

# ==================== TMDB DISCOVER ENDPOINTS ====================

TMDB_KEY = os.getenv("TMDB_KEY", "")
RADARR_QUALITY_ID = int(os.getenv("RADARR_QUALITY_ID", "4"))
SONARR_QUALITY_ID = int(os.getenv("SONARR_QUALITY_ID", "4"))
RADARR_ROOT_FOLDER = os.getenv("RADARR_ROOT_FOLDER", "H:\\")
SONARR_ROOT_FOLDER = os.getenv("SONARR_ROOT_FOLDER", "I:\\TV")
HIDDEN_FILE = DATA_DIR / "hidden_discover.json"

def load_hidden():
    if HIDDEN_FILE.exists():
        return json.loads(HIDDEN_FILE.read_text())
    return {}

def save_hidden(hidden):
    HIDDEN_FILE.write_text(json.dumps(hidden, indent=2))

@app.get("/api/providers")
async def get_providers():
    """Get available streaming providers from TMDb."""
    async with httpx.AsyncClient() as client:
        movie_r = await client.get(
            f"https://api.themoviedb.org/3/watch/providers/movie?api_key={TMDB_KEY}&region=US&watch_region=US",
            timeout=15
        )
        tv_r = await client.get(
            f"https://api.themoviedb.org/3/watch/providers/tv?api_key={TMDB_KEY}&region=US&watch_region=US",
            timeout=15
        )

    movie_providers = []
    if movie_r.status_code == 200:
        for p in movie_r.json().get("results", []):
            movie_providers.append({
                "id": p["provider_id"],
                "name": p["provider_name"],
                "logo": f"https://image.tmdb.org/t/p/original{p['logo_path']}" if p.get("logo_path") else None,
            })

    tv_providers = []
    if tv_r.status_code == 200:
        for p in tv_r.json().get("results", []):
            tv_providers.append({
                "id": p["provider_id"],
                "name": p["provider_name"],
                "logo": f"https://image.tmdb.org/t/p/original{p['logo_path']}" if p.get("logo_path") else None,
            })

    return {"movie_providers": movie_providers, "tv_providers": tv_providers}


@app.get("/api/discover/movies")
async def discover_movies(page: int = 1, limit: int = 20, providers: str = "", sort_by: str = "popularity.desc"):
    """Discover movies from TMDb, optionally filtered by streaming providers."""
    hidden = load_hidden()
    existing_ids = get_existing_movie_tmdb_ids()

    movies = []
    tmdb_page = page
    max_attempts = 5  # Fetch up to 5 pages to fill the limit

    async with httpx.AsyncClient() as client:
        while len(movies) < limit and tmdb_page <= page + max_attempts:
            params = {
                "api_key": TMDB_KEY,
                "sort_by": sort_by,
                "watch_region": "US",
                "language": "en-US",
                "page": tmdb_page,
            }
            if providers:
                params["with_watch_providers"] = providers
                params["with_watch_monetization_types"] = "flatrate|free|ads"

            r = await client.get("https://api.themoviedb.org/3/discover/movie", params=params, timeout=15)
            if r.status_code != 200:
                break
            data = r.json()

            for m in data.get("results", []):
                tmdb_id = m["id"]
                if str(tmdb_id) in hidden or str(tmdb_id) in existing_ids:
                    continue

                poster_url = f"https://image.tmdb.org/t/p/w500{m['poster_path']}" if m.get("poster_path") else None
                backdrop_url = f"https://image.tmdb.org/t/p/w780{m['backdrop_path']}" if m.get("backdrop_path") else None

                movies.append({
                    "tmdbId": tmdb_id,
                    "title": m["title"],
                    "year": m.get("release_date", "")[:4] or None,
                    "overview": m.get("overview", ""),
                    "rating": m.get("vote_average"),
                    "posterUrl": poster_url,
                    "backdropUrl": backdrop_url,
                    "releaseDate": m.get("release_date"),
                })

                if len(movies) >= limit:
                    break

            tmdb_page += 1

    return {"movies": movies[:limit], "total": 10000, "page": page}


@app.get("/api/discover/shows")
async def discover_shows(page: int = 1, limit: int = 20, providers: str = "", sort_by: str = "popularity.desc"):
    """Discover TV shows from TMDb, optionally filtered by streaming providers."""
    hidden = load_hidden()
    existing_titles = get_existing_show_titles()

    shows = []
    tmdb_page = page
    max_attempts = 5

    async with httpx.AsyncClient() as client:
        while len(shows) < limit and tmdb_page <= page + max_attempts:
            params = {
                "api_key": TMDB_KEY,
                "sort_by": sort_by,
                "watch_region": "US",
                "language": "en-US",
                "page": tmdb_page,
            }
            if providers:
                params["with_watch_providers"] = providers
                params["with_watch_monetization_types"] = "flatrate|free|ads"

            r = await client.get("https://api.themoviedb.org/3/discover/tv", params=params, timeout=15)
            if r.status_code != 200:
                break
            data = r.json()

            for s in data.get("results", []):
                tmdb_id = s["id"]
                if str(tmdb_id) in hidden:
                    continue
                show_key = f"{s.get('name','').lower().strip()}|{s.get('first_air_date','')[:4]}"
                if show_key in existing_titles:
                    continue

                poster_url = f"https://image.tmdb.org/t/p/w500{s['poster_path']}" if s.get("poster_path") else None
                backdrop_url = f"https://image.tmdb.org/t/p/w780{s['backdrop_path']}" if s.get("backdrop_path") else None

                shows.append({
                    "tmdbId": tmdb_id,
                    "title": s["name"],
                    "year": s.get("first_air_date", "")[:4] or None,
                    "overview": s.get("overview", ""),
                    "rating": s.get("vote_average"),
                    "posterUrl": poster_url,
                    "backdropUrl": backdrop_url,
                    "firstAirDate": s.get("first_air_date"),
                })

                if len(shows) >= limit:
                    break

            tmdb_page += 1

    return {"shows": shows[:limit], "total": 10000, "page": page}


@app.post("/api/discover/{tmdb_id}/hide")
async def hide_discover(tmdb_id: int, body: dict = {}):
    """Hide a discover item so it doesn't show again."""
    hidden = load_hidden()
    hidden[str(tmdb_id)] = {
        "action": body.get("action", "hidden"),
        "timestamp": datetime.now().isoformat(),
        "title": body.get("title"),
        "year": body.get("year"),
        "posterUrl": body.get("posterUrl"),
        "type": body.get("type", "movie"),
    }
    save_hidden(hidden)
    return {"ok": True}


@app.post("/api/discover/{tmdb_id}/add_movie")
async def add_movie_from_discover(tmdb_id: int):
    """Add a discovered movie to Radarr."""
    async with httpx.AsyncClient() as client:
        # Lookup movie in Radarr by tmdbId
        r = await client.get(
            f"{RADARR_URL}/api/v3/movie/lookup",
            headers={"X-Api-Key": RADARR_KEY},
            params={"term": f"tmdb:{tmdb_id}"},
            timeout=15
        )
        if r.status_code != 200 or not r.json():
            raise HTTPException(status_code=404, detail="Movie not found")

        movie_data = r.json()[0]

        add_payload = {
            "title": movie_data["title"],
            "tmdbId": tmdb_id,
            "qualityProfileId": RADARR_QUALITY_ID,
            "rootFolderPath": RADARR_ROOT_FOLDER,
            "monitored": True,
            "addOptions": {"searchForMovie": True},
            "images": movie_data.get("images", []),
        }

        r = await client.post(
            f"{RADARR_URL}/api/v3/movie",
            headers={"X-Api-Key": RADARR_KEY},
            json=add_payload,
            timeout=30
        )
        if r.status_code not in (200, 201):
            raise HTTPException(status_code=r.status_code, detail=f"Radarr add failed: {r.text}")

    # Hide from discover
    hidden = load_hidden()
    hidden[str(tmdb_id)] = {
        "action": "added",
        "timestamp": datetime.now().isoformat(),
        "title": movie_data.get("title"),
        "year": movie_data.get("year"),
        "posterUrl": f"https://image.tmdb.org/t/p/w500{movie_data.get('images', [{}])[0].get('coverUrl', '').split('?')[0].replace('/MediaCover/', '')}" if movie_data.get("images") else None,
        "type": "movie",
    }
    save_hidden(hidden)

    return {"ok": True, "title": movie_data["title"]}


@app.post("/api/discover/{tmdb_id}/add_show")
async def add_show_from_discover(tmdb_id: int):
    """Add a discovered show to Sonarr."""
    async with httpx.AsyncClient() as client:
        # Lookup show by tmdbId
        r = await client.get(
            f"{SONARR_URL}/api/v3/series/lookup",
            headers={"X-Api-Key": SONARR_KEY},
            params={"term": f"tmdb:{tmdb_id}"},
            timeout=15
        )
        if r.status_code != 200 or not r.json():
            raise HTTPException(status_code=404, detail="Show not found")

        show_data = r.json()[0]

        add_payload = {
            "title": show_data["title"],
            "tmdbId": tmdb_id,
            "tvdbId": show_data.get("tvdbId"),
            "qualityProfileId": SONARR_QUALITY_ID,
            "rootFolderPath": SONARR_ROOT_FOLDER,
            "monitored": True,
            "addOptions": {"searchForMissingEpisodes": True},
            "images": show_data.get("images", []),
            "seasons": show_data.get("seasons", []),
        }

        r = await client.post(
            f"{SONARR_URL}/api/v3/series",
            headers={"X-Api-Key": SONARR_KEY},
            json=add_payload,
            timeout=30
        )
        if r.status_code not in (200, 201):
            raise HTTPException(status_code=r.status_code, detail=f"Sonarr add failed: {r.text}")

    # Hide from discover
    hidden = load_hidden()
    hidden[str(tmdb_id)] = {
        "action": "added",
        "timestamp": datetime.now().isoformat(),
        "title": show_data.get("title"),
        "year": show_data.get("year"),
        "type": "show",
    }
    save_hidden(hidden)

    return {"ok": True, "title": show_data["title"]}


@app.get("/api/discover/history")
async def get_discover_history():
    """Get all discover actions (added, hidden)."""
    hidden = load_hidden()
    history = []

    async with httpx.AsyncClient() as client:
        for tmdb_id, info in hidden.items():
            title = info.get("title")
            year = info.get("year")
            poster = info.get("posterUrl")
            item_type = info.get("type", "movie")

            # Look up title from TMDb if missing
            if not title:
                try:
                    endpoint = "movie" if item_type == "movie" else "tv"
                    r = await client.get(
                        f"https://api.themoviedb.org/3/{endpoint}/{tmdb_id}?api_key={TMDB_KEY}",
                        timeout=10
                    )
                    if r.status_code == 200:
                        data = r.json()
                        title = data.get("title") or data.get("name")
                        year = (data.get("release_date") or data.get("first_air_date", ""))[:4] or None
                        poster_path = data.get("poster_path")
                        poster = f"https://image.tmdb.org/t/p/w500{poster_path}" if poster_path else None
                except:
                    pass

            history.append({
                "tmdbId": int(tmdb_id),
                "action": info.get("action", "hidden"),
                "timestamp": info.get("timestamp"),
                "title": title,
                "year": year,
                "posterUrl": poster,
                "type": item_type,
            })

    history.sort(key=lambda x: x.get("timestamp", ""), reverse=True)
    return {"history": history}


@app.post("/api/discover/{tmdb_id}/unhide")
async def unhide_discover(tmdb_id: int):
    """Remove a hide decision so the item shows up in discover again."""
    hidden = load_hidden()
    mid = str(tmdb_id)
    if mid in hidden:
        del hidden[mid]
        save_hidden(hidden)
    return {"ok": True}


@app.post("/api/discover/{tmdb_id}/remove_movie")
async def remove_movie_from_discover(tmdb_id: int):
    """Remove a movie that was added via discover from Radarr."""
    async with httpx.AsyncClient() as client:
        # Find the movie in Radarr by tmdbId
        r = await client.get(
            f"{RADARR_URL}/api/v3/movie",
            headers={"X-Api-Key": RADARR_KEY},
            timeout=30
        )
        if r.status_code == 200:
            for m in r.json():
                if m.get("tmdbId") == tmdb_id:
                    await client.delete(
                        f"{RADARR_URL}/api/v3/movie/{m['id']}",
                        headers={"X-Api-Key": RADARR_KEY},
                        params={"deleteFiles": "true"},
                        timeout=15
                    )
                    break

    # Remove from hidden so it shows in discover again
    hidden = load_hidden()
    mid = str(tmdb_id)
    if mid in hidden:
        del hidden[mid]
        save_hidden(hidden)

    return {"ok": True}


@app.post("/api/discover/{tmdb_id}/remove_show")
async def remove_show_from_discover(tmdb_id: int):
    """Remove a show that was added via discover from Sonarr."""
    async with httpx.AsyncClient() as client:
        # Find the show by looking up via TMDb
        r = await client.get(
            f"{SONARR_URL}/api/v3/series/lookup",
            headers={"X-Api-Key": SONARR_KEY},
            params={"term": f"tmdb:{tmdb_id}"},
            timeout=15
        )
        if r.status_code == 200 and r.json():
            show_data = r.json()[0]
            tvdb_id = show_data.get("tvdbId")
            if tvdb_id:
                # Find in Sonarr by tvdbId
                sr = await client.get(
                    f"{SONARR_URL}/api/v3/series",
                    headers={"X-Api-Key": SONARR_KEY},
                    timeout=30
                )
                if sr.status_code == 200:
                    for s in sr.json():
                        if s.get("tvdbId") == tvdb_id:
                            await client.delete(
                                f"{SONARR_URL}/api/v3/series/{s['id']}",
                                headers={"X-Api-Key": SONARR_KEY},
                                params={"deleteFiles": "true"},
                                timeout=15
                            )
                            break

    # Remove from hidden
    hidden = load_hidden()
    mid = str(tmdb_id)
    if mid in hidden:
        del hidden[mid]
        save_hidden(hidden)

    return {"ok": True}


@app.post("/api/save-env")
async def save_env(config: dict):
    """Save configuration to .env file."""
    env_file = Path(__file__).parent / ".env"
    radarr_root = config.get('radarrRootFolder', 'H:\\')
    sonarr_root = config.get('sonarrRootFolder', 'I:\\TV')

    lines = [
        "# TV Tenderr Configuration",
        "# Auto-generated by setup wizard",
        "",
        "# Backend",
        f"BACKEND_HOST=0.0.0.0",
        f"BACKEND_PORT={config.get('port', 8899)}",
        "",
        "# Radarr (movies)",
        f"RADARR_URL={config.get('radarrUrl', '')}",
        f"RADARR_KEY={config.get('radarrKey', '')}",
        "",
        "# Sonarr (TV shows)",
        f"SONARR_URL={config.get('sonarrUrl', '')}",
        f"SONARR_KEY={config.get('sonarrKey', '')}",
        "",
        "# Plex",
        f"PLEX_URL={config.get('plexUrl', '')}",
        f"PLEX_TOKEN={config.get('plexToken', '')}",
        "",
        "# TMDb",
        f"TMDB_KEY={config.get('tmdbKey', '')}",
        "",
        "# Quality defaults",
        f"RADARR_QUALITY_ID={config.get('radarrQualityId', 4)}",
        f"SONARR_QUALITY_ID={config.get('sonarrQualityId', 4)}",
        f"RADARR_ROOT_FOLDER={radarr_root}",
        f"SONARR_ROOT_FOLDER={sonarr_root}",
        "",
    ]

    env_file.write_text("\n".join(lines))
    return {"ok": True}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8899)
