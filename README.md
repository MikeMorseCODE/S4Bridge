# S4Bridge

S4Bridge is an Android proof-of-concept for using a Native Instruments **Traktor Kontrol S4 MK2** directly as a standalone DJ controller.

This repository was reconstructed after the original development phone was reset. The reconstruction is based on the known-good source structure, live HID captures, confirmed mappings, and working two-deck tests from the development conversation.

## Confirmed hardware

- Controller: Traktor Kontrol S4 MK2
- USB VID:PID: `17cc:1310`
- Android interface array index observed: `6`
- USB interface ID: `4`
- HID interrupt IN endpoint: `0x84`
- Report `0x01`: 21 bytes
- Report `0x02`: 64 bytes

## Confirmed proof-of-concept behavior

- S4 detection and Android USB permission
- HID interface claim and interrupt input capture
- Report 01 / Report 02 semantic parsing
- Deck A and Deck B PLAY events
- Two simultaneous MediaPlayer decks
- Channel A and B volume faders
- Equal-power crossfader
- 12-bit continuous controls normalized to `0..1`
- Jog touch and relative jog packet decoding
- Pitch, EQ and filter controls decoded and stored
- Loop/browser/pregain relative encoders decoded

See `AGENTS.md`, `ROADMAP.md`, and `docs/` for the reconstruction details and Codex development plan.

## Attribution

The S4 MK2 HID mapping was ported from the open-source Mixxx controller mapping. This project is intended to remain GPL-2.0-or-later compatible.
