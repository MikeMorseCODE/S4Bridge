# Roadmap

## Reconstructed baseline
- [x] USB scan/permission
- [x] HID interface discovery and `0x84` capture
- [x] Reports 01/02 parsing
- [x] Deck A/B PLAY
- [x] Two MediaPlayer decks
- [x] Channel A/B volume
- [x] Equal-power crossfader
- [x] Pitch/EQ/filter state routing
- [x] Jog and relative encoders decoded

## Usable prototype
- [ ] Android media picker/library
- [ ] CUE semantics
- [ ] Controller browser/load
- [ ] USB reconnect handling
- [ ] HID parser tests

## Controller feedback
- [ ] S4 output reports
- [ ] transport/hotcue/loop LEDs

## Native audio
- [ ] Gradle + NDK
- [ ] Oboe/AAudio
- [ ] PCM decoder/ring buffers
- [ ] master + cue buses
- [ ] deterministic transport

## DJ DSP/workflow
- [ ] EQ/filter
- [ ] tempo/time stretch
- [ ] jog nudge/scratch
- [ ] waveforms/BPM/beatgrid/sync
- [ ] loops/hotcues/FX/recording
- [ ] four decks
