# DDJ-XP2 performance integration

Connect the S4 MK2 and DDJ-XP2 through a powered USB hub. The S4 uses its existing
HID connection; Android's MIDI service owns the XP2 connection independently.
The Android device must recognize the XP2 as USB MIDI. Detection uses the USB
MIDI device name/product containing `DDJ-XP2`. Input and LED output use port 0;
additional input ports are monitored only.

## Start

1. Open **MIDI**, connect the XP2, and use **Rescan XP2** if necessary.
2. Enable **Performance audio**. Changing this setting unloads both decks.
3. Add/select library tracks, then load A and B. Decoding happens in the
   background; an existing track stays available until its replacement succeeds.
4. Set each track's BPM with **Set A BPM** / **Set B BPM**. Defaults are 120 BPM.
5. At the first beat, use Transport pad 9 to set the grid origin. SYNC then matches
   the other deck's effective BPM and fractional beat position. The last deck
   synced becomes the follower; the other deck is the master.

The original MediaPlayer path remains the default. The performance engine is an
opt-in Java PCM prototype; neither XP2 operation nor this audio path has yet been
validated on the physical Android/S4/XP2 setup.

## Factory messages and S4Bridge layers

Pioneer documents eight pad banks. Normal pad channels are 8/10 for A/B and
shifted channels are 9/11 (shown here as human channels 1–16). Each bank has 16
notes with reversed physical row order: pad 1 starts at note 12, pad 5 at 8, pad 9
at 4, and pad 13 at 0; subsequent banks add 16. The decoder also handles the
normal/shifted mode selector messages. The functions below are **S4Bridge's
custom layers**, rather than an emulation of every Serato or rekordbox mode.
Pad-bank behavior must be checked in the XP2's general MIDI controller mode.

| Bank | Pads | Function | SHIFT |
|---|---|---|---|
| 1 | 1–16 | Set/trigger hot cues 1–16 for that deck | Clear cue |
| 2 | 1–8 | Auto loops: 1/8, 1/4, 1/2, 1, 2, 4, 8, 16 beats | Same |
| 2 | 9–16 | Beat jumps: −1,+1,−2,+2,−4,+4,−8,+8 | Same |
| 3 | 1–8 | Trigger shared sampler slots 1–8 | Load selected library track into slot |
| 3 | 9–16 | Stop corresponding sampler slot | Same |
| 4 / 8 | 1–16 | Transport and grid controls, below | Rate reset on pads 3/4; sync off on pad 6 |
| 5 | 1–8 | Hold loop rolls at the bank-2 lengths; release resumes the moving timeline | Beat jumps |
| 5 | 9–16 | Beat jumps at the bank-2 amounts | Same |
| 6 | 1–3 | Hold filter, echo, tremolo | Same |
| 6 | 4 | Toggle FX hold | Same |
| 6 | 5–7 | Set corresponding FX amount to 75% | Set amount to 25% |
| 6 | 8–16 | Unassigned | Unassigned |
| 7 | 1–8 | Store current loop if slot empty, otherwise recall stored loop | Clear saved loop |
| 7 | 9–16 | Loop in, out, toggle, exit, halve, double, move −1 beat, move +1 beat | Same |

Transport/grid pads 1–16: jump −1 beat; jump +1 beat; rate −1%; rate +1%;
quantize toggle; sync; CUE hold; PLAY toggle; set grid origin; shift grid −0.01
beat; shift grid +0.01 beat; load library selection; loop in; loop out; loop
toggle; loop exit. Quantize affects new cue/loop anchors using the manually
supplied grid. It does not queue events until a future beat.

Dedicated 4 BEAT LOOP, 1/2X, 2X, shifted loop-in/out, QUANTIZE, BEAT SYNC,
shifted sync-off, SILENT CUE, PARAMETER loop resize/shift, browser encoder, and
A/B LOAD messages are routed. Deck C/D messages are ignored or show an unsupported-deck notice
and never alias to A/B. Dedicated KEYLOCK, key shifting, slicer, and external
input switching are not implemented.

Hot cues and saved loops belong to the currently loaded deck track. They reset
on load and are not yet written to a persistent track database. Eight sampler
slots are shared between the decks; loading replaces a slot, and triggering
restarts it. Samples play up to the first 15 seconds, possibly less if the
memory cap is reached. There is no independent sampler bank or PFL bus.

## Slide FX and feedback

The left/right strips control their corresponding decks. Buttons 1–3 select
low-pass filter, feedback echo, and beat tremolo. Touch/release pulses gate the
selected FX; HOLD latches the gate until HOLD is disabled. Pioneer emits an
immediate Note On/Off pair on both touch and release, so the decoder does not
mistake the immediate Note Off for finger release. HOLD changes are explicitly
handled. Strip amount uses the full 14-bit MSB/LSB pairs: CC 2/34, 3/35, 4/36.
Both halves reach the mapping; only UI telemetry is coalesced.

XP2 feedback sends changed-state Note On (127) / explicit Note Off (0) messages.
It reflects loaded hot cues, selected auto-loop/roll lengths, sampler occupancy
and playback, saved loops, transport PLAY, quantize, sync, silent cue, selected
FX and HOLD. Only the selected bank is painted. Reconnect and bank changes
invalidate the output cache so state is replayed. Custom RGB color palettes,
mode-button output, load-animation triggers, and S4 HID LED reports are not
implemented. Learned-note overrides are excluded from factory pad LED painting.

## Learn and monitor

Saved note learning for A/B PLAY, CUE and LOAD remains available. A learned note
supersedes the factory action at that channel/note. Learning first releases
performance gestures and consumes the assignment press without triggering a
transport action. Note Off and Note On velocity zero release learned controls;
held duplicate Note On messages do not toggle repeatedly. Disconnect releases
held CUE and performance gates, exits loops/rolls, and cancels learning.

The monitor retains the last 24 channel messages and updates at most 10 times
per second. All channel messages still reach routing. The monitor shows port,
channel (1–16), and hex status/data bytes; saved assignments use zero-based
`channel:note`. System/SysEx messages are excluded. Changing pad bank can change
note addresses, so learned assignments apply to the bank where they were made.

## Audio implementation and limits

MediaExtractor/MediaCodec decode mono/stereo PCM16 or float output into immutable
stereo clips. Tracks are decoded with a cap of the smaller of 96 MiB or 1/8 of
Android's maximum heap. Oversized tracks fail with a visible message, preserving
the previously loaded track; they are not silently truncated. Sampler decoding
uses a smaller per-slot cap and intentionally truncates. A bounded single-worker
queue, cancellation and generation checks prevent obsolete loads from replacing
newer selections or restoring audio after switching modes/destroying the app.
The manifest requests a large heap; Android decides the actual allocation limit.

One stereo AudioTrack mixes both decks and eight samples at 48 kHz, in 256-frame
render blocks. Loop/roll wrapping and transport positions happen in the sample
renderer, not UI polling. Linear resampling supplies rate and sample-rate
conversion. SYNC follows master tempo changes at a 100 ms control interval.
Rate is clamped to 0.25–4x. **Tempo changes also change pitch**; key-preserving time
stretch is absent. BPM analysis is absent. This is not the future native
Oboe/AAudio engine. Output uses Android's normal audio route; automatic S4 audio
routing, headphone PFL, scratch, EQ DSP, waveform rendering, audio-focus behavior,
and background-service playback remain later milestones.

## References

Factory protocol reference: Pioneer DDJ-XP2 MIDI Message List e1/j1 (2019).
The direct English download returned 403; indexed official Japanese/English
reference rows were recovered for channel layouts, pad/selector/control notes,
relative encoder encoding, strip CC pairs, and LED Note On/Off behavior.

- https://www.pioneerdj.com/-/media/pioneerdj/software-info/controller/ddj-xp2/ddj-xp2_midi_message_list_e1.pdf
- https://www.pioneerdj.com/-/media/pioneerdj/software-info/controller/ddj-xp2/ddj-xp2_midi_message_list_j1.pdf

## Verification and hardware checklist

`./test-jvm` checks the original core/S4 behavior plus all 512 A/B normal/shifted
pad-bank addresses, mode selectors, browser deltas, 14-bit FX input, duplicate
note suppression, explicit LED messages/cache retry/replay, PCM rendering and
resampling, loop boundaries, slip recovery, hot cue clear/quantize/silent cue,
saved loop recall, tempo/phase sync, sampler routing and audible DSP effects.
All Android Java sources are separately compiled against API 34.

Physical validation still required:

- General MIDI mode: confirm all banks/shifted notes against the monitor.
- Independent S4/XP2 connect, disconnect, reattach and MIDI permission behavior.
- Longest supported tracks, decoder cancellation and mode switching under load.
- Stereo output route, clipping, sustained playback, measured latency/underruns.
- Strip touch/release pulses and HOLD, plus lit/dim pad behavior per bank.
- Learned overrides while switching banks, and releasing CUE/roll during detach.

The code is published as a draft for review; on-device success is not claimed.
