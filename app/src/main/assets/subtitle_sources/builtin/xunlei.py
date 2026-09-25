import hashlib
import json
import os
import re
from urllib.parse import quote

import requests


API = "https://api-shoulei-ssl.xunlei.com/oracle/subtitle?name="
REFERER = "https://sl-m-ssl.xunlei.com/"
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"


def _text(value):
    return "" if value is None else str(value).strip()


def _first(item, *keys):
    for key in keys:
        value = _text((item or {}).get(key))
        if value:
            return value
    return ""


def _number(value, fallback=0):
    try:
        return int(value)
    except (TypeError, ValueError):
        return fallback


def _long(value, fallback=0):
    try:
        return int(value)
    except (TypeError, ValueError):
        return fallback


def _format(ext, name, url):
    value = _text(ext) or os.path.splitext(_text(name) or _text(url))[1]
    value = value.lower().replace(".", "")
    return value if value in ("ass", "ssa", "srt", "vtt") else "srt"


def _language(value):
    value = _text(value)
    lower = value.lower()
    if not value or lower == "default" or value == "默认":
        return "zh"
    if any(token in lower for token in ("中文", "中字", "简", "繁", "chs", "cht", "zh", "english", "eng", "en", "日", "ja", "jp", "韩", "韓", "ko", "kr")):
        return value
    return "zh"


def _release(extra, duration):
    result = _text(extra)
    if duration > 0:
        result = (result + " " if result else "") + str(duration // 60000) + "m"
    return result


def _content_id(path):
    if not path or not os.path.isfile(path):
        return ""
    size = os.path.getsize(path)
    with open(path, "rb") as source:
        if size < 0xF000:
            data = source.read()
        else:
            data = source.read(0x5000)
            source.seek(size // 3)
            data += source.read(0x5000)
            source.seek(max(0, size - 0x5000))
            data += source.read(0x5000)
    return hashlib.sha1(data).hexdigest().upper()


def _download(url, candidate_id, filename, fmt):
    safe = re.sub(r"[^A-Za-z0-9_.-]", "_", _text(candidate_id))[:80] or "subtitle"
    suffix = os.path.splitext(_text(filename))[1].lower() or "." + (fmt or "srt")
    if suffix not in (".ass", ".ssa", ".srt", ".vtt"):
        suffix = "." + (fmt or "srt")
    target = os.path.join(os.path.dirname(os.path.abspath(__file__)), "xunlei_" + safe + suffix)
    temporary = target + ".tmp"
    response = requests.get(url, headers={"User-Agent": USER_AGENT, "Referer": REFERER, "Connection": "close"}, timeout=15, stream=True)
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
        text = _text(query.get("text"))
        if not text:
            return {"code": 0, "data": {"items": []}}
        content_id = _content_id(_text(context.get("mediaPath")))
        response = requests.get(API + quote(text), headers={"User-Agent": USER_AGENT, "Referer": REFERER, "Connection": "close"}, timeout=15)
        if not response.ok:
            return {"code": 0, "data": {"items": []}}
        root = response.json()
        if _number(root.get("code"), 1) != 0 or (_text(root.get("result")) and _text(root.get("result")).lower() != "ok"):
            return {"code": 0, "data": {"items": []}}
        items = []
        for item in root.get("data") or []:
            if not isinstance(item, dict):
                continue
            url = _text(item.get("url"))
            candidate_id = _first(item, "cid", "gcid") or url
            if not url or not candidate_id:
                continue
            name = _text(item.get("name")) or candidate_id
            fmt = _format(item.get("ext"), name, url)
            language_values = item.get("languages") if isinstance(item.get("languages"), list) else []
            language = _language(next((_text(value) for value in language_values if _text(value)), ""))
            extra = _text(item.get("extra_name"))
            duration = _long(item.get("duration"))
            score = max(_number(item.get("score")), _number(item.get("fingerprintf_score")))
            strict = bool(content_id and content_id.lower() == _text(item.get("cid")).lower())
            if strict:
                score += 120
            items.append({
                "id": candidate_id,
                "name": name,
                "language": language,
                "format": fmt,
                "releaseInfo": _release(extra, duration),
                "score": score,
                "year": 0,
                "season": -1,
                "episode": -1,
                "matchType": "METADATA_STRICT" if strict else "METADATA_FUZZY",
                "requiresResolve": True,
                "payload": {"url": url, "name": name, "format": fmt, "duration": duration, "gcid": _text(item.get("gcid")), "cid": _text(item.get("cid"))},
            })
        return {"code": 0, "data": {"items": items}}

    def resolve(self, request):
        candidate = (request or {}).get("candidate") or {}
        payload = candidate.get("payload") or {}
        url = _text(payload.get("url"))
        if not url:
            return {"code": 1, "message": "download_url_missing", "data": {}}
        fmt = _text(payload.get("format")) or _text(candidate.get("format")) or "srt"
        path = _download(url, _text(candidate.get("id")) or url, payload.get("name"), fmt)
        return {"code": 0, "data": {"url": path, "fileName": candidate.get("name", ""), "language": candidate.get("language", ""), "format": fmt}}

    def destroy(self):
        self.config = {}
