# Phase 1 on-device validation

Phase 1 is implemented but has not been exercised against physical hardware in
the cloud environment. Use this checklist on an Android API 24+ device and a
Traktor Kontrol S4 MK2 before recording a new known-good checkpoint.

## Media library and transport

1. Add multiple MP3/Opus audio documents in one picker operation and confirm
   each appears once, including after force-stopping and reopening S4Bridge.
2. Select each track with both the screen buttons and browser encoder. Confirm
   selection clamps at the first and last item rather than wrapping.
3. Load the selected track onto Deck A and Deck B using both screen and hardware
   LOAD controls; verify that both decks can play simultaneously.
4. On each paused deck, seek or pause at a nonzero position, hold CUE, and verify
   preview playback. Release CUE and verify playback pauses at the stored point.
5. During normal playback press CUE and verify the deck pauses at its stored cue
   point. Load another track and confirm its cue resets to zero.
6. Verify channel volume and the equal-power crossfader still affect both decks.

## USB lifecycle

1. Start capture and confirm interface ID 4 and endpoint `0x84` are reported.
2. Unplug the controller while capture is active. Confirm capture stops, the
   connection closes, and the status changes to USB OFF without an app crash.
3. Reconnect it. Confirm S4Bridge detects VID:PID `17cc:1310`, requests permission
   if necessary, claims the HID interface, and restarts capture automatically.
4. Background and foreground the app with the controller connected; confirm a
   permitted idle connection resumes without duplicate capture threads.
5. Repeat disconnect/reconnect during playback and confirm both MediaPlayer decks
   continue playing while only controller input is interrupted.

## Evidence to retain

Record the Android version/device, interface count/index and ID, endpoint address,
report lengths, log excerpt for both reconnect directions, tested audio formats,
and any provider-specific URI failures. No firmware blobs, proprietary files, or
USB dumps are required unless observed hardware behavior differs from the mapping.
