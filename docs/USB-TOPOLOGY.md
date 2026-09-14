# S4 MK2 USB topology

Observed Android topology:

```text
index=0 id=0 class=1 endpoints=0
index=1 id=1 class=1 endpoints=0
index=2 id=1 class=1: 0x01 OUT ISO, 0x81 IN ISO
index=3 id=2 class=1 endpoints=0
index=4 id=2 class=1: 0x82 IN ISO
index=5 id=3 class=1: 0x83 IN BULK, 0x02 OUT BULK
index=6 id=4 class=3: 0x84 IN INT, 0x03 OUT INT
index=7 id=5 class=254 endpoints=0
index=8 id=6 class=255: 0x04 OUT BULK, 0x85 IN BULK, 0x86 IN INT
```

Working HID path: Android array index `6`, USB interface ID `4`, interrupt IN `0x84`, interrupt OUT `0x03`.

Mixxx identifies interface_number `0x4`; that is USB interface ID, not Android array index.
