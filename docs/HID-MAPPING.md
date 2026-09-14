# S4 MK2 HID mapping

Source baseline: Mixxx S4 MK2 HID mapping, ported into `S4Mk2Mapping.java`.

Report `0x01` carries buttons and jog movement. Report `0x02` carries continuous controls and 4-bit relative encoders.

Key long-report offsets: Deck A/B pitch `0x09/0x0B`; crossfader `0x07`; headphone mix `0x0D`; channel volumes A-D `0x37/39/3B/3D`; A EQ/filter `0x17/19/1B/1D`; B `0x1F/21/23/25`; C `0x27/29/2B/2D`; D `0x2F/31/33/35`; FX1 `0x3F/41/43/45`; FX2 `0x47/49/4B/4D`.

4-bit encoders: byte `0x01` low/high = Deck A loop move/size; `0x02` low/high = browser/Deck B loop move; `0x03` low/high = Deck B loop size/Channel A pregain; `0x04` low/high = B/C pregain; `0x05` low = D pregain.

See the Java mapping for all exact short-report button masks.
