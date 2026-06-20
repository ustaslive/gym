# Unified Exercise Data Model

## Status

Phase 4 Android client adapted.

This document defines the first canonical data model for shared exercise data.
The model is intended for both the Android app in `andriod/` and the browser app in `docs/gym.html`.

## Problem

Exercise-related data is currently split across code and data files:

- `docs/gym.html` reads flat JSON files from `docs/`.
- The Android app keeps important exercise and session definitions in Kotlin code.

Because of this, small changes to exercises, parameters, ordering, or session composition require code changes and often require reinstalling the Android app.

The new model moves exercise and session definitions into shared data files.

## Goals

- Store exercise and session definitions in data files.
- Use one shared model for Android and browser clients.
- Support multiple reusable sessions.
- Support logical sections inside each session.
- Keep user runtime state separate from shared definitions.
- Enable strict validation before data is loaded or saved.
- Prepare the project for a browser-based editor.

## Non-goals

- Full Android/browser feature parity in the first migration.
- Replacing Android user-state persistence in this phase.
- Defining the final editor UI.

## Final Terminology

The model uses these terms:

- `bundle`
  The top-level JSON document containing all shared exercise data.
- `exerciseCatalog`
  A reusable catalog of exercise definitions keyed by exercise ID.
- `session`
  A reusable workout unit. It is not tied to a calendar day. A user can perform one or more sessions in one day.
- `section`
  A logical part of a session. Examples: `warmup`, `main`, `cardio`, `cooldown`, `mobility`, `rehab`.
- `exercise`
  A concrete exercise entry inside a session section.
- `exerciseId`
  A reference from a session exercise entry to a reusable catalog exercise.
- `parameters`
  Structured values that affect how an exercise is performed or tracked.
- `settings`
  Machine setup, seat positions, setup notes, and similar preparation data.
- `instructions`
  User-facing execution notes and detail sections.

Primary hierarchy:

```text
session -> sections -> exercises
```

Reusable catalog relationship:

```text
exerciseCatalog -> referenced by session section exercises through exerciseId
```

## Canonical Format

The canonical storage format is JSON.

JSON is used because it is directly consumable by the browser, straightforward to load on Android, and compatible with strict JSON Schema validation.

Manual editing is allowed, but the expected long-term workflow is editing through a local browser UI that validates before saving.

## Canonical File Layout

Phase 1 chooses a single bundle file.

Canonical data file:

```text
docs/data/exercise-bundle.json
```

Canonical schema file:

```text
docs/schema/exercise-data.schema.json
```

Reasoning:

- `docs/gym.html` can load `docs/data/exercise-bundle.json` in static browser deployments.
- Android can import the same file or bundle a copy as an app asset.
- A single file keeps the first migration simple.
- The model can later be split into multiple files without changing the core entities.

## Schema Versioning

The current schema version is:

```json
{
  "schemaVersion": 1
}
```

Versioning rules:

- `schemaVersion` is a required positive integer.
- Version `1` is the first supported shared exercise data format.
- Any incompatible structural change must increment `schemaVersion`.
- Clients must reject a bundle with a higher unsupported `schemaVersion`.
- Clients should reject a bundle with a lower unsupported `schemaVersion`.

The top-level `metadata.revision` field is for content revisions and does not replace `schemaVersion`.

Example:

```json
{
  "schemaVersion": 1,
  "metadata": {
    "id": "lemon_exercises",
    "title": "Lemon Exercise Data",
    "revision": 1
  }
}
```

## Canonical JSON Structure

Top-level bundle shape:

```json
{
  "$schema": "../schema/exercise-data.schema.json",
  "schemaVersion": 1,
  "metadata": {
    "id": "lemon_exercises",
    "title": "Lemon Exercise Data",
    "revision": 1
  },
  "exerciseCatalog": {},
  "sessions": []
}
```

### Top-level fields

- `$schema`
  Optional JSON Schema reference.
- `schemaVersion`
  Required schema contract version.
- `metadata`
  Required descriptive bundle metadata.
- `exerciseCatalog`
  Required object keyed by reusable exercise IDs.
- `sessions`
  Required ordered list of reusable sessions.

### IDs

IDs use lowercase snake case.

Examples:

- `leg_press`
- `hands_a`
- `warmup`
- `main_leg_press`

ID rule:

```text
^[a-z][a-z0-9_]*$
```

## Exercise Catalog

`exerciseCatalog` is an object keyed by exercise ID.

Catalog keys are the canonical reusable exercise IDs. The exercise object does not repeat its own ID.

Example:

```json
{
  "exerciseCatalog": {
    "leg_press": {
      "title": "Leg Press",
      "kind": "weights",
      "parameters": {
        "weightOptions": [23, 30, 37, 44, 51],
        "defaultWeight": 51,
        "totalSets": 3,
        "restBetweenSeconds": 45,
        "restFinalSeconds": 120
      },
      "settings": {
        "setupNote": "Set sled length to position 8."
      }
    }
  }
}
```

### Exercise kinds

Initial supported exercise kinds:

- `weights`
- `activity`
- `guided`
- `repetitions`
- `timer`
- `intervals`
- `sequence`

Each kind has explicit parameter rules in `docs/schema/exercise-data.schema.json`.

### Common catalog fields

- `title`
  Required display title.
- `kind`
  Required exercise kind.
- `parameters`
  Kind-specific structured parameters.
- `settings`
  Optional setup notes and machine settings.
- `instructions`
  Optional detailed user-facing instructions.
- `presentation`
  Optional UI hints.
- `tags`
  Optional classification tags.
- `muscleGroups`
  Optional list of the main muscle groups loaded by the exercise. Android can use this to recommend or highlight remaining exercises after the last completed exercise.
- `equipment`
  Optional equipment labels.

## Sessions

`sessions` is an ordered array. Its order is the default display order in selectors.

Each session contains ordered sections.

Example:

```json
{
  "sessions": [
    {
      "id": "legs_a",
      "title": "Legs A",
      "sections": [
        {
          "id": "warmup",
          "title": "Warmup",
          "exercises": [
            {
              "id": "warmup_elliptical",
              "exerciseId": "elliptical"
            }
          ]
        },
        {
          "id": "main",
          "title": "Main",
          "exercises": [
            {
              "id": "main_leg_press",
              "exerciseId": "leg_press"
            }
          ]
        }
      ]
    }
  ]
}
```

### Session fields

- `id`
  Required session ID.
- `title`
  Required display title.
- `description`
  Optional user-facing description.
- `sections`
  Required ordered list of sections.
- `tags`
  Optional classification tags.

### Section fields

- `id`
  Required section ID.
- `title`
  Required display title.
- `description`
  Optional user-facing description.
- `exercises`
  Required ordered list of concrete exercise entries.

Section IDs are user-defined. The recommended starting vocabulary is:

- `warmup`
- `main`
- `cardio`
- `cooldown`
- `mobility`
- `rehab`

## Session Exercise Entries

An exercise entry inside a section has its own stable `id` and references the catalog through `exerciseId`.

Example:

```json
{
  "id": "main_leg_press",
  "exerciseId": "leg_press"
}
```

The separate entry ID is needed because:

- the same catalog exercise may appear in multiple sessions
- the same catalog exercise may appear more than once in one session
- Android progress state needs stable per-session exercise IDs

### Overrides

Session exercise entries may define local overrides.

Example:

```json
{
  "id": "cardio_bike",
  "exerciseId": "bike",
  "overrides": {
    "parameters": {
      "durationMinutes": 15,
      "level": 18
    }
  }
}
```

Allowed override areas:

- `title`
- `parameters`
- `settings`
- `instructions`
- `presentation`

## User State Separation

Shared bundle data must not contain runtime user state.

Examples of data that must stay outside the shared bundle:

- selected weight chosen by the user during use
- personal notes
- completed sets
- active exercise
- current timer values
- unlock state
- session progress

Android may keep that state in its existing persistence layer. The browser may keep temporary state in memory or browser storage.

## Validation Contract

JSON Schema validates structural rules:

- required fields
- known field names
- field types
- ID format
- exercise kind values
- kind-specific parameter requirements

Additional semantic validation is still required by the editor and clients:

- unique session IDs
- unique section IDs within a session
- unique exercise entry IDs within a session
- valid `exerciseId` references into `exerciseCatalog`
- `defaultWeight` is present in `weightOptions`
- recommended next-exercise logic uses `muscleGroups` from catalog exercises

These checks are intentionally listed outside JSON Schema because they require cross-object validation.

## Minimal Complete Example

```json
{
  "$schema": "../schema/exercise-data.schema.json",
  "schemaVersion": 1,
  "metadata": {
    "id": "lemon_exercises",
    "title": "Lemon Exercise Data",
    "revision": 1
  },
  "exerciseCatalog": {
    "elliptical": {
      "title": "Elliptical",
      "kind": "activity",
      "parameters": {
        "mode": "elliptical",
        "durationMinutes": 5,
        "level": 10,
        "totalSets": 1,
        "restFinalSeconds": 120
      }
    },
    "leg_press": {
      "title": "Leg Press",
      "kind": "weights",
      "parameters": {
        "weightOptions": [23, 30, 37, 44, 51],
        "defaultWeight": 51,
        "totalSets": 3,
        "restBetweenSeconds": 45,
        "restFinalSeconds": 120
      },
      "settings": {
        "setupNote": "Set sled length to position 8."
      }
    }
  },
  "sessions": [
    {
      "id": "legs_a",
      "title": "Legs A",
      "sections": [
        {
          "id": "warmup",
          "title": "Warmup",
          "exercises": [
            {
              "id": "warmup_elliptical",
              "exerciseId": "elliptical"
            }
          ]
        },
        {
          "id": "main",
          "title": "Main",
          "exercises": [
            {
              "id": "main_leg_press",
              "exerciseId": "leg_press"
            }
          ]
        }
      ]
    }
  ]
}
```

## Migration Phases

### Phase 1: Define the model

Completed by this document and `docs/schema/exercise-data.schema.json`.

### Phase 2: Build the shared data bundle

Completed by `docs/data/exercise-bundle.json`.

Included sources:

- current Android general exercise definitions from Kotlin
- current Android hands session definitions from Kotlin
- current browser plans from `docs/gym.json`, `docs/palm.json`, `docs/nerv.json`, and `docs/examples_gym.json`

The current Android legs placeholder is not represented as a catalog exercise because it is UI placeholder state, not a real exercise definition.

### Phase 3: Adapt browser client

Completed by updating `docs/gym.html`.

Implemented behavior:

- loads `docs/data/exercise-bundle.json`
- supports `session` query parameter
- keeps legacy `type` and `file` query parameters mapped to browser sessions
- populates the selector from bundle `sessions`
- renders section headers before exercise cards
- preserves browser-specific `repetitions`, `timer`, `intervals`, and `sequence` behavior
- renders Android-origin `weights`, `activity`, and `guided` entries as completable cards

### Phase 4: Adapt Android client

Completed by the Android bundle parser and repository wiring.

Implemented behavior:

- Gradle copies the canonical `docs/data/exercise-bundle.json` into generated Android assets during build.
- The APK contains `assets/exercise-bundle.json`.
- Android loads `android_general` and `android_hands` session definitions from the shared bundle.
- JSON catalog/session data is mapped into `ExerciseUiState`.
- Existing `WorkoutDayType` UI remains in place for this phase.
- Existing user-state persistence for notes, weights, progress, active exercise, and timers remains separate.
- Kotlin hardcoded templates remain as fallback if the bundled data cannot be loaded.

### Phase 5: Support updates without reinstall

- Keep a built-in default bundle in app assets.
- Support importing or reloading updated external data.
- Cache the last valid imported bundle.
- Fall back to bundled defaults when external data is invalid.

### Phase 6: Build the editor

Completed by `tools/exercise_editor/`.

Implemented behavior:

- local Python HTTP server using only the standard library
- browser-based session editor
- browser-based catalog editor
- raw JSON editor for advanced changes
- server-side validation before save
- atomic write flow
- backup snapshots in `docs/data/backups/`

### Phase 7: Harden the workflow

- Add migration support for future schema versions.
- Add automated tests for data loading and semantic validation.
- Add editor-level checks for references and duplicate IDs.

## Phase 1 Decisions

- Canonical data format: JSON.
- Initial file layout: single bundle.
- Canonical data path: `docs/data/exercise-bundle.json`.
- Canonical schema path: `docs/schema/exercise-data.schema.json`.
- Top-level model: `bundle`.
- Workout unit term: `session`.
- Session hierarchy: `session -> sections -> exercises`.
- Reusable exercise definitions: `exerciseCatalog`.
- Schema version: integer `schemaVersion`, starting at `1`.
