"""Offline fake httpx client for plugin contract tests."""


class FakeResponse:
    def __init__(self, body: str, status: int = 200, encoding: str = "utf-8"):
        # fetch_html treats bodies under 300 bytes as failures; the padding comment covers that.
        self.content = (body + "<!--" + "-" * 320 + "-->").encode(encoding)
        self.status_code = status
        self.text = self.content.decode(encoding, "ignore")


class FakeClient:
    """Returns the first (url-substring, body) match, else 404. `params` are appended as ?k=v."""

    def __init__(self, routes: list[tuple[str, str]], encoding: str = "utf-8"):
        self.routes, self.encoding = routes, encoding

    async def get(self, url, params=None, **kw):
        if params:
            url += "?" + "&".join(f"{k}={v}" for k, v in params.items())
        for part, body in self.routes:
            if part in url:
                return FakeResponse(body, encoding=self.encoding)
        return FakeResponse("", 404)
