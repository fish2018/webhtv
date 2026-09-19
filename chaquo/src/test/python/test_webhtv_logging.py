import ast
import contextlib
import importlib.util
import io
import json
import logging
from pathlib import Path
import sys
import threading
import types
import unittest
from unittest import mock

SOURCE = Path(__file__).resolve().parents[2] / "main/python"
spec = importlib.util.spec_from_file_location("webhtv_logging", SOURCE / "webhtv_logging.py")
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)


class Store:
    def __init__(self):
        self.enabled = True
        self.generation = 1
        self.mask = 63
        self.failures = 0

    def acceptsTag(self, tag):
        return self.enabled and bool(self.mask & 2)

    def captureGeneration(self):
        return self.generation

    def categories(self):
        return self.mask

    def collectorFailure(self):
        self.failures += 1


class Logger:
    def __init__(self):
        self.records = []

    def console(self, tag, level, message):
        self.records.append((tag, level, message))


class StreamTest(unittest.TestCase):
    def setUp(self):
        self.original = io.StringIO()
        self.store = Store()
        self.logger = Logger()
        self.stream = bridge._LogStream(self.original, "INFO", self.store, self.logger)

    def messages(self):
        return [r[2] for r in self.logger.records]

    def test_actual_spider_log_print_and_stream_contract(self):
        tree = ast.parse((SOURCE / "base/spider.py").read_text())
        spider = next(n for n in tree.body if isinstance(n, ast.ClassDef) and n.name == "Spider")
        method = next(n for n in spider.body if isinstance(n, ast.FunctionDef) and n.name == "log")
        namespace = {"json": json}
        exec(compile(ast.Module(body=[method], type_ignores=[]), "base/spider.py", "exec"), namespace)
        with contextlib.redirect_stdout(self.stream):
            namespace["log"](None, {"中文": ["值", 1]})
            print("plain", "print")
        self.assertEqual(self.messages(), ['{"中文": ["值", 1]}', "plain print"])
        self.assertEqual(self.original.getvalue(), "\n".join(self.messages()) + "\n")
        self.assertEqual(self.stream.write("tail"), 4)
        self.stream.flush()
        self.assertEqual(self.messages()[-1], "tail")
        self.stream.writelines(["a\n", "b\n"])
        self.assertEqual(self.messages()[-2:], ["a", "b"])
        self.assertEqual(self.stream.encoding, self.original.encoding)
        with self.assertRaises(TypeError):
            self.stream.write(b"bytes")

    def test_disabled_and_new_generation_discard_partial_text(self):
        self.stream.write("old-")
        self.store.generation += 1
        self.stream.write("new\n")
        self.assertEqual(self.messages(), ["new"])
        self.store.enabled = False
        self.stream.write("private-disabled")
        self.store.enabled = True
        self.stream.write("visible\n")
        self.assertEqual(self.messages(), ["new", "visible"])
        self.stream.write("category-old")
        self.store.mask &= ~2
        self.stream.write("category-disabled")
        self.store.mask |= 2
        self.stream.write("category-visible\n")
        self.assertEqual(self.messages()[-1], "category-visible")
        self.assertNotIn("private-disabled", str(self.logger.records))

    def test_interleaved_threads_do_not_join_each_others_lines(self):
        barrier = threading.Barrier(2)

        def worker(prefix):
            self.stream.write(prefix + "-")
            barrier.wait(timeout=2)
            self.stream.write("done\n")

        threads = [threading.Thread(target=worker, args=(prefix,)) for prefix in ("A", "B")]
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join(timeout=3)
            self.assertFalse(thread.is_alive())
        self.assertCountEqual(self.messages(), ["A-done", "B-done"])

    def test_huge_line_is_bounded_and_never_emits_secret_tail_chunks(self):
        self.stream.write("token=" + "s" * 100_000)
        self.assertLessEqual(len(self.stream._local.pending), bridge._MAX_LINE)
        self.assertEqual(self.messages(), [])
        self.stream.write("UNREDACTABLE_TAIL\nnext\n")
        self.assertEqual(len(self.messages()), 2)
        self.assertTrue(self.messages()[0].endswith("[line truncated]"))
        self.assertLess(len(self.messages()[0]), bridge._MAX_LINE + 30)
        self.assertNotIn("UNREDACTABLE_TAIL", self.messages()[0])
        self.assertEqual(self.messages()[1], "next")

    def test_capture_failure_preserves_original_output_and_return(self):
        self.logger.console = mock.Mock(side_effect=RuntimeError("sink unavailable"))
        self.assertEqual(self.stream.write("still printed\n"), 14)
        self.assertEqual(self.original.getvalue(), "still printed\n")
        self.assertEqual(self.store.failures, 1)
        self.assertEqual(self.stream._local.pending, "")
        self.original.close()
        with self.assertRaises(ValueError):
            self.stream.write("closed")

    def test_stderr_is_not_error_but_standard_logging_retains_level(self):
        stream = bridge._LogStream(self.original, "STDERR", self.store, self.logger)
        stream.write("ordinary stderr\nINFO:test:no error occurred\nWARNING:test:careful\nERROR:test:broken\n")
        stream.write("Traceback (most recent call last):\n")
        self.assertEqual([r[1] for r in self.logger.records], ["STDERR", "INFO", "WARNING", "ERROR", "ERROR"])

    def test_install_once_before_import_and_existing_logging_still_outputs_once(self):
        module = types.ModuleType("com.github.catvod.crawler")
        module.DebugLogStore, module.SpiderDebug = self.store, self.logger
        original_err = io.StringIO()
        with mock.patch.dict(sys.modules, {"com.github.catvod.crawler": module}), \
                mock.patch.object(bridge, "_installed", False), \
                mock.patch.object(sys, "stdout", self.original), \
                mock.patch.object(sys, "stderr", original_err):
            bridge.install()
            stdout, stderr = sys.stdout, sys.stderr
            bridge.install()
            self.assertIs(sys.stdout, stdout)
            self.assertIs(sys.stderr, stderr)
            print("module-import-output")
            handler = logging.StreamHandler()
            handler.setFormatter(logging.Formatter("%(levelname)s:%(message)s"))
            handler.handle(logging.LogRecord("test", logging.ERROR, "", 1, "broken", (), None))
            handler.close()
        self.assertEqual(self.messages(), ["module-import-output", "ERROR:broken"])
        self.assertEqual(self.original.getvalue(), "module-import-output\n")
        self.assertEqual(original_err.getvalue(), "ERROR:broken\n")
        self.assertFalse(self.original.closed)
        self.assertFalse(original_err.closed)


if __name__ == "__main__":
    unittest.main()
