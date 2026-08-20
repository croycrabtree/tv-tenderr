import unittest
from unittest.mock import patch

import backend


class FakeResponse:
    def __init__(self, status_code, json_data=None, text=""):
        self.status_code = status_code
        self._json_data = json_data
        self.text = text

    def json(self):
        return self._json_data


class FakeAsyncClient:
    def __init__(self, responses):
        self.responses = list(responses)
        self.requests = []

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return False

    async def get(self, url, **kwargs):
        self.requests.append(("GET", url, kwargs))
        return self.responses.pop(0)

    async def post(self, url, **kwargs):
        self.requests.append(("POST", url, kwargs))
        return self.responses.pop(0)


class DiscoverDislikeExclusionTests(unittest.IsolatedAsyncioTestCase):
    async def test_movie_dislike_adds_radarr_import_list_exclusion_and_preserves_history_metadata(self):
        client = FakeAsyncClient([FakeResponse(201, {"id": 7})])
        saved = []
        body = {
            "title": "The Test Movie",
            "year": 2024,
            "posterUrl": "https://image.test/poster.jpg",
            "type": "movies",
        }

        with (
            patch.object(backend.httpx, "AsyncClient", return_value=client),
            patch.object(backend, "load_hidden", return_value={}),
            patch.object(backend, "save_hidden", side_effect=lambda data: saved.append(data)),
        ):
            result = await backend.dislike_discover(12345, body)

        self.assertEqual({"ok": True}, result)
        self.assertEqual(
            [
                (
                    "POST",
                    f"{backend.RADARR_URL}/api/v3/exclusions",
                    {
                        "headers": {"X-Api-Key": backend.RADARR_KEY},
                        "json": {
                            "tmdbId": 12345,
                            "movieTitle": "The Test Movie",
                            "movieYear": 2024,
                        },
                        "timeout": 30,
                    },
                )
            ],
            client.requests,
        )
        decision = saved[0]["12345"]
        self.assertEqual("hidden", decision["action"])
        self.assertEqual("The Test Movie", decision["title"])
        self.assertEqual(2024, decision["year"])
        self.assertEqual("https://image.test/poster.jpg", decision["posterUrl"])
        self.assertEqual("movies", decision["type"])
        self.assertTrue(decision["timestamp"])

    async def test_show_dislike_resolves_tvdb_id_and_adds_sonarr_import_list_exclusion(self):
        client = FakeAsyncClient(
            [
                FakeResponse(200, [{"tvdbId": 9876, "title": "Sonarr Canonical Title"}]),
                FakeResponse(201, {"id": 8}),
            ]
        )
        saved = []
        body = {
            "title": "The Test Show",
            "year": 2023,
            "posterUrl": "https://image.test/show.jpg",
            "type": "shows",
        }

        with (
            patch.object(backend.httpx, "AsyncClient", return_value=client),
            patch.object(backend, "load_hidden", return_value={}),
            patch.object(backend, "save_hidden", side_effect=lambda data: saved.append(data)),
        ):
            result = await backend.dislike_discover(54321, body)

        self.assertEqual({"ok": True}, result)
        self.assertEqual(
            [
                (
                    "GET",
                    f"{backend.SONARR_URL}/api/v3/series/lookup",
                    {
                        "headers": {"X-Api-Key": backend.SONARR_KEY},
                        "params": {"term": "tmdb:54321"},
                        "timeout": 15,
                    },
                ),
                (
                    "POST",
                    f"{backend.SONARR_URL}/api/v3/importlistexclusion",
                    {
                        "headers": {"X-Api-Key": backend.SONARR_KEY},
                        "json": {"tvdbId": 9876, "title": "Sonarr Canonical Title"},
                        "timeout": 30,
                    },
                ),
            ],
            client.requests,
        )
        decision = saved[0]["54321"]
        self.assertEqual("hidden", decision["action"])
        self.assertEqual("The Test Show", decision["title"])
        self.assertEqual(2023, decision["year"])
        self.assertEqual("https://image.test/show.jpg", decision["posterUrl"])
        self.assertEqual("shows", decision["type"])
        self.assertTrue(decision["timestamp"])

    async def test_skip_hide_records_metadata_without_contacting_radarr_or_sonarr(self):
        saved = []
        body = {
            "action": "hidden",
            "title": "Skipped Movie",
            "year": 2022,
            "posterUrl": "https://image.test/skipped.jpg",
            "type": "movies",
        }

        with (
            patch.object(backend.httpx, "AsyncClient", side_effect=AssertionError("skip must not call an Arr service")),
            patch.object(backend, "load_hidden", return_value={}),
            patch.object(backend, "save_hidden", side_effect=lambda data: saved.append(data)),
        ):
            result = await backend.hide_discover(111, body)

        self.assertEqual({"ok": True}, result)
        self.assertEqual("hidden", saved[0]["111"]["action"])
        self.assertEqual("Skipped Movie", saved[0]["111"]["title"])

    async def test_failed_import_list_exclusion_does_not_record_dislike(self):
        client = FakeAsyncClient([FakeResponse(500, text="service unavailable")])
        saved = []

        with (
            patch.object(backend.httpx, "AsyncClient", return_value=client),
            patch.object(backend, "load_hidden", return_value={}),
            patch.object(backend, "save_hidden", side_effect=lambda data: saved.append(data)),
        ):
            with self.assertRaises(backend.HTTPException) as raised:
                await backend.dislike_discover(
                    222,
                    {"title": "Failed Movie", "year": 2020, "type": "movies"},
                )

        self.assertEqual(500, raised.exception.status_code)
        self.assertEqual([], saved)


if __name__ == "__main__":
    unittest.main()
