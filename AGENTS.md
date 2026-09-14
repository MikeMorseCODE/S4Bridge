# AGENTS.md — S4Bridge

## Mission
Turn the reconstructed S4Bridge proof of concept into a functional standalone Android DJ application for the Native Instruments Traktor Kontrol S4 MK2. Preserve every known-good HID and USB behavior while improving incrementally.

## Non-negotiable hardware facts
- USB VID:PID `17cc:1310`.
- Working HID interface: Android array index 6, USB interface ID 4.
- Interrupt IN endpoint `0x84`.
- Report `0x01` is short/buttons/jog; report `0x02` is long/continuous.
- Interface ID 4 is not Android array index 4.
- Preserve `S4Mk2Mapping` as the known-good baseline.

## Proven controls
Continuous controls are 12-bit, approximately 0..4095, normalized with `raw / 4095.0f`. Jog relative8 uses wrap correction and `abs(delta) <= 32` reconnect/startup gate. Four-bit encoders use nibble wrap correction. Mapping derives from Mixxx GPL code; preserve attribution and GPL-2.0-or-later compatibility.

## Known-good behavior
USB enumeration and permission, HID discovery/capture, semantic mapping, two MediaPlayer decks, PLAY A/B, Channel A/B volume, equal-power crossfader, and live status UI were proven before the reset.

## Development strategy
Work in small buildable milestones. Keep the legacy `./r` path working until a modern build is proven on-device. Do not spam the UI thread with continuous-control logs.

## Priority roadmap
1. Android media/document browser.
2. Proper CUE semantics.
3. Controller browser/load.
4. USB lifecycle/reconnect.
5. HID parser tests.
6. S4 LED/output reports.
7. Native low-latency audio.
8. Actual tempo/pitch.
9. EQ/filter DSP.
10. Jog nudge/scratch.
11. Headphone PFL/cue bus.
12. Waveforms/position.
13. Loops/hotcues.
14. FX.
15. Four decks.

## Audio direction
MediaPlayer is temporary. Prefer Android NDK + CMake + Oboe/AAudio, decoded PCM/ring buffers, sample-accurate positions, native mixer DSP, GPL-compatible time stretch/pitch, and separate master/cue buses.

## Build compatibility
Historical Termux build: Java 8 source/target + `dx`. Avoid lambdas/method references in the legacy path because invokedynamic broke the Termux dx flow. A Gradle/NDK migration is encouraged only after preserving the working hardware baseline.
