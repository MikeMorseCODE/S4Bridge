# S4Bridge UI Design — Approved Direction

This document is the implementation specification for the approved S4Bridge Android UI concept.

## Design intent

Build a polished dark Android DJ/control interface inspired by professional stage and DJ hardware. The UI should feel native to S4Bridge rather than like a generic Android utility.

Visual language:
- near-black / deep navy background
- dark elevated cards with thin blue-gray borders
- electric blue as the primary action/navigation accent
- green for connected/ready/capture-success states
- cyan for faders and mixer controls
- orange for loop/load/secondary deck actions
- purple for FX
- red only for stop/destructive/record states
- large touch targets suitable for live use
- compact technical typography for metadata, HID values, VID:PID, report IDs, and event counts
- rounded cards and buttons, but not excessively pill-shaped
- strong contrast and readable at arm's length

Do not copy trademarks/assets from the visual concept directly. Use an original simplified controller representation or functional deck/mixer visualization if an image asset is unavailable.

## Navigation

Use a persistent bottom navigation with five destinations:
1. Home
2. Capture
3. Mapping
4. MIDI
5. Settings

A navigation drawer may expose the same destinations plus Help & Docs and About, but bottom navigation is the primary phone navigation.

## Home / Controller

Header:
- S4Bridge wordmark/text
- settings shortcut

Connection card:
- `Traktor Kontrol S4 MK2`
- Connected / Ready state
- USB icon/status
- tapping opens device details

Primary content:
- controller/deck visualization
- VID:PID `17cc:1310`
- HID mode
- interface/device status

Primary action:
- Start Capture / Stop Capture

Secondary actions:
- Identify/Test LED when implemented
- Reconnect

The existing proven USB permission/open/capture behavior must remain intact. UI work must not change the known hardware interface selection or HID parsing.

## Capture

Top status card while active:
- green live indicator
- `Capturing…`
- elapsed capture time
- event count

Filter chips/tabs:
- All
- Buttons
- Faders
- Jog
- Knobs
- Other

Event rows should show, when applicable:
- timestamp
- semantic control name, e.g. `DECK_A_PLAY`
- PRESS / RELEASE / normalized value / delta
- report ID (`0x01` or `0x02`)
- report size

Continuous controls should update efficiently; do not append uncontrolled log spam or queue a UI update for every HID packet. Throttle/coalesce continuous display updates.

Bottom actions:
- Stop
- Clear
- Save Log (only when functional; otherwise disabled or marked future)

## Mapping

Top deck/category tabs:
- Deck A
- Deck B
- Mixer
- FX
- Global

Group mappings into cards such as:
- Play / Cue
- Transport
- Jog Wheel
- Faders
- EQ / Filter
- Loop
- FX

Rows display:
- semantic control
- known HID source
- current mapped destination
- disclosure affordance for future editing

Initially this screen may be read-only. Do not invent working MIDI mapping/edit functionality before the underlying feature exists.

## MIDI Router

This is a staged/future feature. The screen may be implemented as a clear feature-preview state until routing exists.

Intended modes:
- USB HID -> MIDI
- USB HID -> Direct App

Future flow visualization:
S4 MK2 -> S4Bridge translation -> MIDI output / direct deck engine

Never imply MIDI output is active if the backend is not implemented.

## Settings

Device:
- Auto reconnect
- Request USB permission automatically when supported
- LED feedback (future/disabled until implemented)
- Keep screen on

App:
- Theme (dark initially)
- Log level
- HID buffer/display options if useful
- Clear logs

About:
- S4Bridge version
- `Traktor Kontrol S4 MK2 on Android`
- GPL-2.0-or-later attribution/license link/text

## Standalone DJ direction

The visual concept contains MIDI tooling, but the project goal remains a standalone Android DJ application as well as a hardware bridge. Preserve the engine-facing architecture so Home can evolve into the performance surface:
- Deck A / Deck B track state
- play/cue
- channel volume
- crossfader
- tempo
- EQ/filter
- jog state
- browser/load
- waveform later

Do not let UI restructuring couple the HID parser directly to Android Views. Semantic controller events should continue flowing through the existing mapping/engine boundary.

## Hardware invariants — DO NOT REGRESS

- USB VID:PID: `17cc:1310`
- HID USB interface ID: `4`
- Android interface array index observed on hardware: `6`
- HID interrupt IN endpoint: `0x84`
- report `0x01`: short report, observed 21 bytes
- report `0x02`: long report, observed 64 bytes
- preserve all existing `S4Mk2Mapping` offsets/masks unless tests or authoritative mapping evidence justify a correction
- preserve rapid reconnect/capture protection added by Codex

## Build constraints

The project has two relevant build environments:

1. Conventional Gradle/AGP for desktop/cloud Android development.
2. Native Termux fallback using the repository `./r` script.

The Termux build currently relies on ARM64-native `javac`, `dx`, `aapt2`, `zipalign`, and `apksigner`. Avoid introducing Java language features that break the legacy `dx` path unless the build strategy is intentionally migrated and verified on-device.

Gradle's downloaded Linux AAPT2 may be x86_64 and cannot execute natively in ARM64 Termux; this is an environment limitation, not an app-code failure.

## Implementation sequence

1. Introduce the dark design system and bottom navigation without changing HID/engine behavior.
2. Implement Home using real current USB/controller state.
3. Implement Capture using real semantic events with throttled continuous-control rendering.
4. Implement read-only Mapping from the actual mapping definitions.
5. Implement Settings backed only by real available behavior.
6. Add honest placeholder/preview treatment for MIDI features not yet implemented.
7. Run `./test-jvm` and preserve all mapping/core tests.
8. Keep the project buildable by the native Termux `./r` path.
9. Document physical-hardware validation items rather than guessing.

## Acceptance criteria

- Existing JVM tests pass.
- Existing physical S4 MK2 USB/HID capture behavior is not intentionally altered.
- App launches into the new Home UI.
- USB connection/capture state is visible without reading a raw debug log.
- Capture screen displays semantic events cleanly.
- Continuous controls do not cause unbounded UI/log spam.
- Navigation works on a phone-sized display.
- Unimplemented features are visibly disabled/previewed rather than falsely functional.
- `./r` remains a supported on-device build/install path.
