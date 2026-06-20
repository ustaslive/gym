#!/usr/bin/env python3
"""Local exercise bundle editor server."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import tempfile
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[2]
EDITOR_ROOT = Path(__file__).resolve().parent
DATA_PATH = REPO_ROOT / "docs" / "data" / "exercise-bundle.json"
SCHEMA_PATH = REPO_ROOT / "docs" / "schema" / "exercise-data.schema.json"
BACKUP_DIR = REPO_ROOT / "docs" / "data" / "backups"

ID_RE = re.compile(r"^[a-z][a-z0-9_]*$")
KINDS = {"weights", "activity", "guided", "repetitions", "timer", "intervals", "sequence"}


class ValidationError(ValueError):
    pass


def require_object(value: Any, path: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValidationError(f"{path} must be an object")
    return value


def require_array(value: Any, path: str) -> list[Any]:
    if not isinstance(value, list):
        raise ValidationError(f"{path} must be an array")
    return value


def require_id(value: Any, path: str) -> str:
    if not isinstance(value, str) or not ID_RE.fullmatch(value):
        raise ValidationError(f"{path} must be a lowercase snake_case id")
    return value


def require_string(value: Any, path: str) -> str:
    if not isinstance(value, str) or not value:
        raise ValidationError(f"{path} must be a non-empty string")
    return value


def require_int(value: Any, path: str, minimum: int = 0) -> int:
    if not isinstance(value, int) or value < minimum:
        raise ValidationError(f"{path} must be an integer >= {minimum}")
    return value


def validate_parameters(kind: str, parameters: Any, path: str) -> None:
    params = require_object(parameters, path)

    if kind == "weights":
        weight_options = require_array(params.get("weightOptions"), f"{path}.weightOptions")
        if not weight_options:
            raise ValidationError(f"{path}.weightOptions must not be empty")
        for index, weight in enumerate(weight_options):
            require_int(weight, f"{path}.weightOptions[{index}]")
        default_weight = require_int(params.get("defaultWeight"), f"{path}.defaultWeight")
        if default_weight not in weight_options:
            raise ValidationError(f"{path}.defaultWeight must exist in weightOptions")
        require_int(params.get("totalSets"), f"{path}.totalSets", minimum=1)
        return

    if kind == "activity":
        require_int(params.get("durationMinutes"), f"{path}.durationMinutes", minimum=1)
        return

    if kind == "timer":
        require_int(params.get("durationSeconds"), f"{path}.durationSeconds", minimum=1)
        return

    if kind == "repetitions":
        repetitions = params.get("repetitions")
        if not isinstance(repetitions, (int, str)) or repetitions == "":
            raise ValidationError(f"{path}.repetitions must be a positive integer or non-empty string")
        if isinstance(repetitions, int) and repetitions < 1:
            raise ValidationError(f"{path}.repetitions must be >= 1")
        return

    if kind == "intervals":
        require_int(params.get("workSeconds"), f"{path}.workSeconds", minimum=1)
        require_int(params.get("restSeconds"), f"{path}.restSeconds")
        require_int(params.get("rounds"), f"{path}.rounds", minimum=1)
        return

    if kind == "sequence":
        steps = require_array(params.get("steps"), f"{path}.steps")
        if not steps:
            raise ValidationError(f"{path}.steps must not be empty")
        for index, step in enumerate(steps):
            step_obj = require_object(step, f"{path}.steps[{index}]")
            require_string(step_obj.get("label"), f"{path}.steps[{index}].label")
            require_int(step_obj.get("durationSeconds"), f"{path}.steps[{index}].durationSeconds", minimum=1)


def validate_bundle(bundle: Any) -> list[str]:
    root = require_object(bundle, "bundle")
    if root.get("schemaVersion") != 1:
        raise ValidationError("schemaVersion must be 1")

    metadata = require_object(root.get("metadata"), "metadata")
    require_id(metadata.get("id"), "metadata.id")
    require_string(metadata.get("title"), "metadata.title")
    require_int(metadata.get("revision"), "metadata.revision", minimum=1)

    catalog = require_object(root.get("exerciseCatalog"), "exerciseCatalog")
    sessions = require_array(root.get("sessions"), "sessions")

    warnings: list[str] = []
    for exercise_id, exercise in catalog.items():
        require_id(exercise_id, f"exerciseCatalog key {exercise_id!r}")
        exercise_obj = require_object(exercise, f"exerciseCatalog.{exercise_id}")
        require_string(exercise_obj.get("title"), f"exerciseCatalog.{exercise_id}.title")
        kind = exercise_obj.get("kind")
        if kind not in KINDS:
            raise ValidationError(f"exerciseCatalog.{exercise_id}.kind is not supported")
        if "description" in exercise_obj:
            raise ValidationError(f"exerciseCatalog.{exercise_id}.description is no longer supported")
        if kind != "guided" or "parameters" in exercise_obj:
            validate_parameters(kind, exercise_obj.get("parameters", {}), f"exerciseCatalog.{exercise_id}.parameters")
        if kind == "guided" and "instructions" not in exercise_obj:
            warnings.append(f"exerciseCatalog.{exercise_id} is guided but has no instructions")

    session_ids: set[str] = set()
    for session_index, session in enumerate(sessions):
        session_obj = require_object(session, f"sessions[{session_index}]")
        session_id = require_id(session_obj.get("id"), f"sessions[{session_index}].id")
        if session_id in session_ids:
            raise ValidationError(f"Duplicate session id: {session_id}")
        session_ids.add(session_id)
        require_string(session_obj.get("title"), f"sessions[{session_index}].title")
        sections = require_array(session_obj.get("sections"), f"sessions[{session_index}].sections")

        section_ids: set[str] = set()
        session_entry_ids: set[str] = set()

        for section_index, section in enumerate(sections):
            section_obj = require_object(section, f"sessions[{session_index}].sections[{section_index}]")
            section_id = require_id(section_obj.get("id"), f"sessions[{session_index}].sections[{section_index}].id")
            if section_id in section_ids:
                raise ValidationError(f"Duplicate section id in {session_id}: {section_id}")
            section_ids.add(section_id)
            require_string(section_obj.get("title"), f"sessions[{session_index}].sections[{section_index}].title")
            exercises = require_array(section_obj.get("exercises"), f"sessions[{session_index}].sections[{section_index}].exercises")

            for entry_index, entry in enumerate(exercises):
                entry_path = f"sessions[{session_index}].sections[{section_index}].exercises[{entry_index}]"
                entry_obj = require_object(entry, entry_path)
                entry_id = require_id(entry_obj.get("id"), f"{entry_path}.id")
                if entry_id in session_entry_ids:
                    raise ValidationError(f"Duplicate exercise entry id in {session_id}: {entry_id}")
                session_entry_ids.add(entry_id)
                exercise_id = require_id(entry_obj.get("exerciseId"), f"{entry_path}.exerciseId")
                if exercise_id not in catalog:
                    raise ValidationError(f"{entry_path}.exerciseId references missing catalog item: {exercise_id}")
                overrides = entry_obj.get("overrides")
                if isinstance(overrides, dict) and "description" in overrides:
                    raise ValidationError(f"{entry_path}.overrides.description is no longer supported")

    return warnings


def read_json_file(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json_file(path: Path, data: Any) -> Path:
    warnings = validate_bundle(data)
    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    backup_path = BACKUP_DIR / f"exercise-bundle.{timestamp}.json"
    if path.exists():
        backup_path.write_bytes(path.read_bytes())

    serialized = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    fd, temp_name = tempfile.mkstemp(prefix=path.name, suffix=".tmp", dir=str(path.parent))
    with os.fdopen(fd, "w", encoding="utf-8") as temp_file:
        temp_file.write(serialized)
    os.replace(temp_name, path)
    return backup_path if path.exists() else Path()


class EditorHandler(BaseHTTPRequestHandler):
    server_version = "ExerciseEditor/1.0"

    def log_message(self, format: str, *args: Any) -> None:
        print(f"{self.address_string()} - {format % args}")

    def send_json(self, payload: Any, status: HTTPStatus = HTTPStatus.OK) -> None:
        body = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_file(self, path: Path, content_type: str) -> None:
        body = path.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        if self.path in {"/", "/index.html"}:
            self.send_file(EDITOR_ROOT / "index.html", "text/html; charset=utf-8")
            return
        if self.path == "/api/bundle":
            self.send_json(read_json_file(DATA_PATH))
            return
        if self.path == "/api/schema":
            self.send_json(read_json_file(SCHEMA_PATH))
            return
        self.send_error(HTTPStatus.NOT_FOUND)

    def do_PUT(self) -> None:
        if self.path != "/api/bundle":
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
            warnings = validate_bundle(payload)
            backup_path = write_json_file(DATA_PATH, payload)
            self.send_json({
                "ok": True,
                "backup": str(backup_path.relative_to(REPO_ROOT)) if backup_path else None,
                "warnings": warnings,
            })
        except json.JSONDecodeError as exc:
            self.send_json({"ok": False, "error": f"Invalid JSON: {exc}"}, HTTPStatus.BAD_REQUEST)
        except ValidationError as exc:
            self.send_json({"ok": False, "error": str(exc)}, HTTPStatus.BAD_REQUEST)
        except Exception as exc:
            self.send_json({"ok": False, "error": str(exc)}, HTTPStatus.INTERNAL_SERVER_ERROR)


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the local exercise bundle editor.")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8787)
    args = parser.parse_args()

    validate_bundle(read_json_file(DATA_PATH))
    server = ThreadingHTTPServer((args.host, args.port), EditorHandler)
    print(f"Exercise editor: http://{args.host}:{args.port}/")
    print(f"Editing: {DATA_PATH}")
    server.serve_forever()


if __name__ == "__main__":
    main()
