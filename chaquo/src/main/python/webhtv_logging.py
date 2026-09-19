"""Tee Python's existing text streams into the bounded App diagnostic sink."""

import sys
import threading

_TAG = "python-spider"
_MAX_LINE = 12_000
_install_lock = threading.Lock()
_installed = False


class _LogStream:
    def __init__(self, stream, level, store, logger):
        self._stream = stream
        self._level = level
        self._store = store
        self._logger = logger
        self._local = threading.local()

    def __getattr__(self, name):
        # Keep encoding, errors, fileno, buffer, reconfigure and stream ownership.
        return getattr(self._stream, name)

    def write(self, text):
        result = self._stream.write(text)
        if isinstance(text, str):
            text = str.__str__(text)
            if isinstance(result, int) and 0 <= result < len(text):
                text = text[:result]
            self._capture(text)
        return result

    def writelines(self, lines):
        for line in lines:
            self.write(line)

    def flush(self):
        result = self._stream.flush()
        self._capture("", flush=True)
        return result

    def _reset(self, epoch=None):
        state = self._local
        state.epoch = epoch
        state.pending = ""
        state.truncated = False

    def _emit(self):
        state = self._local
        message = state.pending
        if state.truncated:
            # Never emit arbitrary tail chunks: splitting credentials could evade redaction.
            message += " [line truncated]"
        self._reset(state.epoch)
        if message:
            level = self._level
            if level == "STDERR":
                # Recognize logging's default level prefix, never arbitrary message keywords.
                prefix = message.partition(":")[0]
                if prefix in ("DEBUG", "INFO", "WARNING", "WARN", "ERROR", "CRITICAL"):
                    level = "ERROR" if prefix == "CRITICAL" else prefix
                elif message.startswith("Traceback (most recent call last):"):
                    level = "ERROR"
            self._logger.console(_TAG, level, message)

    def _capture(self, text, flush=False):
        state = self._local
        if getattr(state, "busy", False):
            return
        state.busy = True
        try:
            if not self._store.acceptsTag(_TAG):
                self._reset()
                return
            epoch = (self._store.captureGeneration(), self._store.categories())
            if getattr(state, "epoch", None) != epoch:
                self._reset(epoch)
            start = 0
            while start < len(text):
                newline = text.find("\n", start)
                end = len(text) if newline < 0 else newline
                remaining = _MAX_LINE - len(state.pending)
                state.pending += text[start:min(end, start + remaining)]
                state.truncated |= end - start > remaining
                if newline < 0:
                    break
                self._emit()
                start = newline + 1
            if flush:
                self._emit()
        except Exception:
            # Diagnostic failures must not alter script results or recursively print.
            self._reset()
            try:
                self._store.collectorFailure()
            except Exception:
                pass
        finally:
            state.busy = False


def install():
    global _installed
    with _install_lock:
        if _installed:
            return
        from com.github.catvod.crawler import DebugLogStore, SpiderDebug

        sys.stdout = _LogStream(sys.stdout, "INFO", DebugLogStore, SpiderDebug)
        sys.stderr = _LogStream(sys.stderr, "STDERR", DebugLogStore, SpiderDebug)
        _installed = True
