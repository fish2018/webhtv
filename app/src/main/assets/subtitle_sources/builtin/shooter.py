import hashlib
import os
import re

import requests


API = "https://www.shooter.cn/api/subapi.php"
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"


def _text(value):
    return "" if value is None else str(value).strip()


def _number(value, fallback=0):
    try:
        return int(value)
    except (TypeError, ValueError):
        return fallback


def _language(value):
    return "eng" if _text(value).lower().startswith(("en", "eng")) else "zh"


def _format(ext, url):
    value = (_text(ext) or os.path.splitext(_text(url))[1]).lower().replace(".", "")
    return value if value in ("ass", "ssa", "srt", "vtt") else "srt"


def _hash(path):
    if not path or not os.path.isfile(path):
        return ""
    size = os.path.getsize(path)
    if size < 12 * 1024:
        return ""
    offsets = (4 * 1024, size // 3 * 2, size // 3, size - 8 * 1024)
    result = []
    with open(path, "rb") as source:
        for offset in offsets:
            source.seek(offset)
            result.append(hashlib.md5(source.read(4 * 1024)).hexdigest())
    return ";".join(result)


def _download(url, candidate_id, fmt):
    safe = re.sub(r"[^A-Za-z0-9_.-]", "_", _text(candidate_id))[:80] or "subtitle"
    target = os.path.join(os.path.dirname(os.path.abspath(__file__)), "shooter_" + safe + "." + (fmt or "srt"))
    temporary = target + ".tmp"
    response = requests.get(url, headers={"User-Agent": USER_AGENT}, timeout=15, stream=True)
    response.raise_for_status()
    try:
        with open(temporary, "wb") as output:
            for chunk in response.iter_content(16384):
                if chunk:
                    output.write(chunk)
        os.replace(temporary, target)
    finally:
        response.close()
        if os.path.exists(temporary):
            os.remove(temporary)
    return target


class Spider:
    def __init__(self):
        self.config = {}

    def init(self, config=None):
        self.config = config if isinstance(config, dict) else {}
        return {"code": 0, "data": {}}

    def search(self, request):
        request = request or {}
        query = request.get("query") or {}
        context = request.get("context") or {}
        media_path = _text(context.get("mediaPath"))
        file_hash = _hash(media_path)
        if not file_hash:
            return {"code": 0, "data": {"items": []}}
        language = _language(query.get("language") or context.get("preferredLanguage"))
        body = {"filehash": file_hash, "pathinfo": media_path, "format": "json", "lang": "eng" if language == "eng" else "chn"}
        response = requests.post(API, data=body, headers={"User-Agent": USER_AGENT}, timeout=15)
        if not response.ok:
            return {"code": 0, "data": {"items": []}}
        try:
            root = response.json()
        except ValueError:
            return {"code": 0, "data": {"items": []}}
        file_name = os.path.basename(media_path)
        items = []
        for group in root if isinstance(root, list) else []:
            for item in (group.get("Files") or []) if isinstance(group, dict) else []:
                if not isinstance(item, dict):
                    continue
                url = _text(item.get("Link"))
                if not url:
                    continue
                fmt = _format(item.get("Ext"), url)
                name = (file_name or _text(context.get("canonicalTitle")) or "subtitle") + " | 射手 | " + fmt
                items.append({
                    "id": url,
                    "name": name,
                    "language": language,
                    "format": fmt,
                    "releaseInfo": "hash",
                    "score": 120,
                    "year": _number(query.get("year"), _number(context.get("year"))),
                    "season": _number(query.get("season"), _number(context.get("season"), -1)),
                    "episode": _number(query.get("episode"), _number(context.get("episode"), -1)),
                    "matchType": "METADATA_STRICT",
                    "requiresResolve": True,
                    "payload": {"url": url, "format": fmt},
                })
        return {"code": 0, "data": {"items": items}}

    def resolve(self, request):
        candidate = (request or {}).get("candidate") or {}
        payload = candidate.get("payload") or {}
        url = _text(payload.get("url"))
        fmt = _text(payload.get("format")) or _text(candidate.get("format")) or "srt"
        if not url:
            return {"code": 1, "message": "download_url_missing", "data": {}}
        path = _download(url, _text(candidate.get("id")) or url, fmt)
        return {"code": 0, "data": {"url": path, "fileName": candidate.get("name", ""), "language": candidate.get("language", ""), "format": fmt}}

    def destroy(self):
        self.config = {}
