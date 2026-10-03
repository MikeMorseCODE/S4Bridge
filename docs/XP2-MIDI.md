# DDJ-XP2 MIDI input milestone

Connect the S4 MK2 and DDJ-XP2 to a powered USB hub attached to the Android device.
The S4 keeps its existing HID connection. Android's MIDI service opens the XP2
independently; S4 USB permissions and capture controls do not control XP2 input.
The Android device must expose `android.software.midi` and recognize the XP2 as
USB MIDI. MIDI devices whose name or product contains `DDJ-XP2` are auto-opened.

## Use

1. Open the **MIDI** tab. It now shows XP2 status and an input monitor.
2. Connect the XP2; use **Rescan XP2** if it was not detected.
3. Choose a dedicated pad mode on the XP2 for transport mapping.
4. Tap **Learn DECK_A_PLAY**, then press your chosen XP2 pad/button.
5. Repeat for A/B PLAY, CUE and LOAD. Assignments persist after app restarts.
6. Cancel learning to leave the old assignment intact, or clear all bindings.

Learning accepts a Note On with nonzero velocity. The learning gesture performs
no deck action. Once learned, PLAY toggles and LOAD loads the library selection
on press; CUE preserves the existing press-and-hold semantics. Note Off and Note
On with velocity zero release a control. Repeated Note On messages while held do
not toggle repeatedly. Disconnecting releases held CUE controls and cancels learn.
A note can have one action and an action can have one note. Assigning a used note
replaces its previous action. Mapping uses XP2 port 0; other exposed ports are
monitored but do not perform actions. Changing XP2 modes may change its note
addresses; return to the mode used during learning. Shifted controls can be
learned separately, but there is no global shift layer in this milestone.

The monitor shows port, channel (1–16), and hexadecimal status/data bytes.
Saved binding labels use zero-based `channel:note`. The monitor retains the last
24 channel messages; continuous messages are coalesced per port to at most 20 Hz.
Every note edge is retained for routing. System messages and SysEx are not shown.
`MidiStreamParser` supports fragmented messages, running status, interleaved
realtime bytes, and one-byte channel messages.

## Scope and next milestone

This is input capture plus learned transport mapping, not a complete factory XP2
performance map. It does not emit LEDs or virtual MIDI, infer relative encoder
formats, implement hot cues/loops/sampler/sync/FX, or route decks C/D. Those engine
features are absent in the current MediaPlayer prototype. The official Pioneer
MIDI table URL returned HTTP 403 during implementation, so no factory addresses,
LED color values, USB VID/PID, or pad mode channel layouts were guessed.

Official reference for completing the semantic map:
https://www.pioneerdj.com/-/media/pioneerdj/software-info/controller/ddj-xp2/ddj-xp2_midi_message_list_e1.pdf

Next: obtain the official table, compare captures in the monitor for each mode,
then add an XP2-specific semantic decoder and device-state LED feedback alongside
actual hot cue/loop/FX support. On-device detection, pad release, reconnect and
simultaneous S4 input still need physical hardware validation.

## Verification

Run `./test-jvm` for stream framing, learned routing, held-note release and the
existing S4/core regressions. The Java 8 / Termux `./r` build includes the new
sources. A full Android API 34 source compile can be performed with:

```sh
javac -source 8 -target 8 -classpath sdk/android-34.jar -d /tmp/s4bridge-classes $(find src -name '*.java')
```
