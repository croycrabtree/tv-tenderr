import unittest
from unittest.mock import patch

import backend


class FakeResponse:
    def __init__(self, status_code, json_data):
        self.status_code = status_code
        self._json_data = json_data

    def json(self):
        return self._json_data


class FakeAsyncClient:
    def __init__(self, response):
        self.response = response

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc, traceback):
        return False

    async def get(self, url, **kwargs):
        return self.response


class ShowHistoryTests(unittest.IsolatedAsyncioTestCase):
    async def test_show_history_uses_shared_history_item_id(self):
        decisions = {
            "356": {
                "action": "super_keep",
                "timestamp": "2026-08-20T18:36:49",
                "title": "Ghosts (US)",
                "year": 2021,
            }
        }
        client = FakeAsyncClient(FakeResponse(200, []))

        with (
            patch.object(backend, "load_show_decisions", return_value=decisions),
            patch.object(backend.httpx, "AsyncClient", return_value=client),
        ):
            result = await backend.get_show_history()

        item = result["history"][0]
        self.assertEqual(356, item["movieId"])
        self.assertEqual(356, item["showId"])


if __name__ == "__main__":
    unittest.main()
