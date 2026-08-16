"""Movie Swipe - Backend API
Connects to Radarr + Plex to serve movies for the swipe interface.
"""
import os
import json
import random
import httpx
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, Response
from pathlib import Path
from datetime import datetime

app = FastAPI(title="Movie Swipe")

# Config - set these or use env vars
RADARR_URL = os.getenv("RADARR_URL", "http://localhost:7878")
RADARR_KEY = os.getenv("RADARR_KEY", "YOUR_RADARR_API_KEY")
SONARR_URL = os.getenv("SONARR_URL", "http://localhost:8989")
SONARR_KEY = os.getenv("SONARR_KEY", "YOUR_SONARR_API_KEY")
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
        "plexUrl": PLEX_URL,
        "hasPlexToken": bool(PLEX_TOKEN),
    }

@app.post("/api/config")
async def update_config(config: dict):
    """Update configuration."""
    global RADARR_URL, RADARR_KEY, PLEX_URL, PLEX_TOKEN
    if "radarrUrl" in config:
        RADARR_URL = config["radarrUrl"]
    if "radarrKey" in config:
        RADARR_KEY = config["radarrKey"]
    if "plexUrl" in config:
        PLEX_URL = config["plexUrl"]
    if "plexToken" in config:
        PLEX_TOKEN = config["plexToken"]
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


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8899)
