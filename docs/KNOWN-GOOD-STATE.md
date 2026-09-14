# Known-good state before phone reset

Last confirmed live test included both 64-byte report 02 and 21-byte report 01 capture, Channel A/B volume near full scale, crossfader at normalized 0.463, both Deck A and Deck B loading successfully, and independent physical PLAY events setting each engine playing=true.

Channel A was swept smoothly through approximately raw `4084 -> 2042 -> 2`, corresponding to normalized `0.997 -> 0.499 -> 0.000`.

This proved the path:

```text
S4 MK2 -> Android USB host -> HID capture -> semantic mapping -> DeckEngine/MixerEngine -> two MediaPlayers
```

Control routing was confirmed. Do not claim every audible fader/crossfader result was explicitly verbally confirmed in the historical test log.
