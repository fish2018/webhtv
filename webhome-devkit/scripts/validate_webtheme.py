#!/usr/bin/env python3
"""Validate a WebHTV WebTheme V2 manifest against the authoring schema."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Mapping, Sequence

try:
    from jsonschema import Draft202012Validator, FormatChecker
    from jsonschema.exceptions import SchemaError
except ImportError as error:  # pragma: no cover - exercised only in an incomplete environment.
    raise SystemExit(
        "Missing dependency 'jsonschema'. Install it with "
        "'py -3 -m pip install -r webhome-devkit/requirements.txt'."
    ) from error


DEVKIT_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SCHEMA = DEVKIT_ROOT / "schemas" / "webtheme-v2.schema.json"
SUPPORTED_TARGETS = frozenset({"mobile", "leanback"})
FORMAT_CHECKER = FormatChecker()
if "uri-reference" not in FORMAT_CHECKER.checkers:  # pragma: no cover - environment guard.
    raise SystemExit(
        "Missing jsonschema format dependencies. Install them with "
        "'py -3 -m pip install -r webhome-devkit/requirements.txt'."
    )


def _json_path(parts: Sequence[Any]) -> str:
    path = "$"
    for part in parts:
        if isinstance(part, int):
            path += f"[{part}]"
        elif isinstance(part, str) and part.isidentifier():
            path += f".{part}"
        else:
            path += f"[{json.dumps(part, ensure_ascii=False)}]"
    return path


def _schema_error_message(error: Any) -> str:
    path = _json_path(list(error.absolute_path))
    if error.validator == "contains":
        required = error.validator_value.get("const") if isinstance(error.validator_value, dict) else None
        if required:
            return f"{path}: must contain required contract permission {required!r}"
    return f"{path}: {error.message}"


def _reserved_warnings(document: Mapping[str, Any], schema: Mapping[str, Any]) -> list[str]:
    warnings: list[str] = []
    properties = schema.get("properties", {})
    if not isinstance(properties, Mapping):
        return warnings
    for field, definition in properties.items():
        if field not in document or not isinstance(definition, Mapping):
            continue
        status = definition.get("x-webhtv-status")
        if status:
            warnings.append(
                f"$.{field}: field is {status}; Host API 3 accepts it for forward compatibility "
                "but ignores its contents"
            )
    return warnings


def _target_errors(document: Mapping[str, Any], target: str | None) -> list[str]:
    if target is None:
        return []
    requested = target.strip().lower()
    if requested not in SUPPORTED_TARGETS:
        return [
            f"$.targets: requested target {target!r} is not supported; "
            f"choose one of {', '.join(sorted(SUPPORTED_TARGETS))}"
        ]
    targets = document.get("targets")
    if isinstance(targets, list) and requested not in {
        value.strip().lower() for value in targets if isinstance(value, str)
    }:
        return [f"$.targets: requested target {requested!r} is not declared by this manifest"]
    return []


def validate_document(
    document: Any,
    schema: Mapping[str, Any],
    target: str | None = None,
    manifest_bytes: int | None = None,
) -> tuple[list[str], list[str]]:
    """Return deterministic ``(errors, warnings)`` for one parsed manifest."""

    if not isinstance(schema, Mapping):
        return (["Invalid WebTheme schema: root must be a JSON object"], [])
    try:
        Draft202012Validator.check_schema(schema)
    except SchemaError as error:
        return ([f"Invalid WebTheme schema: {error.message}"], [])

    validator = Draft202012Validator(schema, format_checker=FORMAT_CHECKER)
    schema_errors = sorted(
        validator.iter_errors(document),
        key=lambda error: (
            tuple(str(part) for part in error.absolute_path),
            tuple(str(part) for part in error.absolute_schema_path),
            error.message,
        ),
    )
    errors = [_schema_error_message(error) for error in schema_errors]
    max_bytes = schema.get("x-webhtv-maxBytes")
    if (
        manifest_bytes is not None
        and isinstance(max_bytes, int)
        and not isinstance(max_bytes, bool)
        and max_bytes > 0
        and manifest_bytes > max_bytes
    ):
        errors.append(
            f"$: manifest is {manifest_bytes} UTF-8 bytes; maximum is {max_bytes}"
        )
    if isinstance(document, Mapping):
        errors.extend(_target_errors(document, target))
        warnings = _reserved_warnings(document, schema)
    else:
        warnings = []
    return errors, warnings


def _load_json(path: Path, label: str) -> tuple[Any, int]:
    try:
        raw = path.read_bytes()
    except FileNotFoundError as error:
        raise ValueError(f"{label} not found: {path}") from error
    except OSError as error:
        raise ValueError(f"Unable to read {label.lower()} {path}: {error}") from error
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as error:
        raise ValueError(
            f"Invalid UTF-8 in {label.lower()} {path}:{error.start + 1}"
        ) from error
    try:
        return json.loads(text), len(raw)
    except json.JSONDecodeError as error:
        raise ValueError(
            f"Invalid JSON in {label.lower()} {path}:{error.lineno}:{error.colno}: {error.msg}"
        ) from error


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate a WebHTV WebTheme V2 theme.json manifest."
    )
    parser.add_argument("manifest", type=Path, help="path to theme.json")
    parser.add_argument(
        "--schema",
        type=Path,
        default=DEFAULT_SCHEMA,
        help=f"schema path (default: {DEFAULT_SCHEMA})",
    )
    parser.add_argument(
        "--target",
        choices=sorted(SUPPORTED_TARGETS),
        help="validate compatibility with this runtime target (omitted targets mean both)",
    )
    parser.add_argument(
        "--warnings-as-errors",
        action="store_true",
        help="return a non-zero status when reserved/experimental fields are present",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        document, manifest_bytes = _load_json(args.manifest, "Manifest")
        schema, _ = _load_json(args.schema, "Schema")
    except ValueError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2

    errors, warnings = validate_document(
        document, schema, target=args.target, manifest_bytes=manifest_bytes
    )
    for warning in warnings:
        print(f"WARNING: {warning}", file=sys.stderr)
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)

    if errors or (warnings and args.warnings_as_errors):
        return 1
    print(f"OK: {args.manifest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
