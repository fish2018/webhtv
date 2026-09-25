import json
import os
import re
import tempfile
import zipfile
from urllib.parse import urlencode

import requests


API_BASE = "https://api.assrt.net/v1"
REFERER = "https://assrt.net/"
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
FORMATS = ("ass", "ssa", "vtt", "srt")


def _text(value):
    return "" if value is None else str(value).strip()


def _first(item, *keys):
    if not isinstance(item, dict):
        return ""
    for key in keys:
        value = _text(item.get(key))
        if value:
            return value
    return ""


def _number(value, fallback=0):
    try:
        return int(value)
    except (TypeError, ValueError):
        return fallback


def _format(subtype, name):
    value = (_text(subtype) + " " + _text(name)).lower()
    for fmt in FORMATS[:-1]:
        if "." + fmt in value or (" " + fmt) in value:
            return fmt
    return "srt"


def _season_episode(value):
    text = _text(value)
    season = -1
    episode = -1
    match = re.search(r"(?i)\bS(\d{1,2})(?:\s*[-.]?\s*E(\d{1,3}))?\b", text)
    if match:
        season = _number(match.group(1), -1)
        if match.group(2):
            episode = _number(match.group(2), -1)
    if episode < 0:
        match = re.search(r"(?i)\bEP?(?:ISODE)?[ ._-]?(\d{1,3})\b", text)
        if match:
            episode = _number(match.group(1), -1)
    return season, episode


def _year(value):
    match = re.search(r"\b(19\d{2}|20\d{2})\b", _text(value))
    return _number(match.group(1), 0) if match else 0


def _request(url, **kwargs):
    headers = {"User-Agent": USER_AGENT, "Referer": REFERER, "Connection": "close"}
    headers.update(kwargs.pop("headers", {}) or {})
    return requests.get(url, headers=headers, timeout=15, **kwargs)


def _download(url, source_key, candidate_id, filename):
    root = os.path.dirname(os.path.abspath(__file__))
    safe_id = re.sub(r"[^A-Za-z0-9_.-]", "_", _text(candidate_id))[:80] or "subtitle"
    suffix = os.path.splitext(_text(filename))[1].lower()
    if suffix not in (".ass", ".ssa", ".srt", ".vtt", ".zip"):
        suffix = ".srt"
    target = os.path.join(root, "%s_%s%s" % (source_key, safe_id, suffix))
    temporary = target + ".tmp"
    response = _request(url, stream=True)
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


def _extract_archive(path, source_key, candidate_id):
    if not zipfile.is_zipfile(path):
        return path
    root = os.path.join(os.path.dirname(path), "%s_%s" % (source_key, re.sub(r"[^A-Za-z0-9_.-]", "_", _text(candidate_id))[:80]))
    os.makedirs(root, exist_ok=True)
    total = 0
    chosen = []
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            name = os.path.normpath(info.filename)
            if name in (".", "") or name.startswith("../") or name.startswith("/") or ":" in name.split(os.sep)[0]:
                raise ValueError("unsafe subtitle archive path")
            if info.is_dir():
                continue
            if info.file_size > 32 * 1024 * 1024:
                raise ValueError("subtitle archive entry too large")
            total += info.file_size
            if total > 64 * 1024 * 1024:
                raise ValueError("subtitle archive too large")
            destination = os.path.join(root, name)
            if not os.path.realpath(destination).startswith(os.path.realpath(root) + os.sep):
                raise ValueError("unsafe subtitle archive path")
            os.makedirs(os.path.dirname(destination), exist_ok=True)
            with archive.open(info) as source, open(destination, "wb") as output:
                output.write(source.read())
            if destination.lower().endswith(tuple("." + fmt for fmt in FORMATS)):
                chosen.append(destination)
    if not chosen:
        return path
    chosen.sort(key=lambda value: (0 if value.lower().endswith(".ass") else 1, value.lower()))
    return chosen[0]


class Spider:
    def __init__(self):
        self.config = {}

    def init(self, config=None):
        self.config = config if isinstance(config, dict) else {}
        return {"code": 0, "data": {}}

    def search(self, request):
        query = (request or {}).get("query") or {}
        text = _text(query.get("text"))
        token = _text(self.config.get("token"))
        if not text or not token:
            return {"code": 0, "data": {"items": []}}
        params = {"token": token, "q": text, "is_file": 1, "cnt": 15}
        response = _request(API_BASE + "/sub/search?" + urlencode(params))
        if not response.ok:
            return {"code": 0, "data": {"items": []}}
        root = response.json()
        if _number(root.get("status"), 1) != 0:
            return {"code": 0, "data": {"items": []}}
        subs = ((root.get("sub") or {}).get("subs") or [])
        items = []
        for item in subs:
            if not isinstance(item, dict):
                continue
            candidate_id = _first(item, "id", "fileid")
            if not candidate_id:
                continue
            name = _first(item, "name", "sub_name", "m_version", "m_title")
            video_name = _first(item, "videoname", "m_videoname", "video_chinese_name", "m_video_chinese_name")
            language = _first(item.get("lang") if isinstance(item.get("lang"), dict) else {}, "desc") or _first(item, "m_lang")
            fmt = _format(_first(item, "subtype", "m_subtype"), name)
            season, episode = _season_episode(name + " " + video_name)
            items.append({
                "id": candidate_id,
                "name": name,
                "language": language,
                "format": fmt,
                "releaseInfo": video_name,
                "score": 0,
                "year": _year(video_name),
                "season": season,
                "episode": episode,
                "matchType": "METADATA_FUZZY",
                "requiresResolve": True,
                "payload": {"id": candidate_id, "format": fmt},
            })
        return {"code": 0, "data": {"items": items}}

    def resolve(self, request):
        candidate = (request or {}).get("candidate") or {}
        payload = candidate.get("payload") or {}
        candidate_id = _text(payload.get("id")) or _text(candidate.get("id"))
        token = _text(self.config.get("token"))
        if not candidate_id or not token:
            return {"code": 1, "message": "candidate_not_found", "data": {}}
        response = _request(API_BASE + "/sub/detail?" + urlencode({"token": token, "id": candidate_id}))
        if not response.ok:
            return {"code": 1, "message": "detail_http_failed", "data": {}}
        root = response.json()
        if _number(root.get("status"), 1) != 0:
            return {"code": 1, "message": "detail_api_failed", "data": {}}
        subs = ((root.get("sub") or {}).get("subs") or [])
        first = next((item for item in subs if isinstance(item, dict)), None)
        url = _first(first or {}, "url")
        if not url:
            return {"code": 1, "message": "download_url_missing", "data": {}}
        filename = _first(first or {}, "filename")
        path = _download(url, "assrt", candidate_id, filename or (candidate.get("name") or "subtitle.srt"))
        path = _extract_archive(path, "assrt", candidate_id)
        return {"code": 0, "data": {"url": path, "fileName": candidate.get("name", ""), "language": candidate.get("language", ""), "format": candidate.get("format", "srt")}}

    def destroy(self):
        self.config = {}
