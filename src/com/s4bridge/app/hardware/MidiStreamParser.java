package com.s4bridge.app.hardware;

/** MIDI 1.0 byte stream framing, independent of Android and USB packet boundaries. */
public final class MidiStreamParser {
    public interface Listener { void onMessage(int status, int data1, int data2); }
    private final Listener listener;
    private int status, needed, count, first;
    public MidiStreamParser(Listener listener) { this.listener = listener; }
    public void reset() { status = needed = count = first = 0; }
    public void accept(byte[] bytes, int offset, int length) {
        if (offset < 0 || length < 0 || offset > bytes.length - length)
            throw new IndexOutOfBoundsException();
        for (int i = offset; i < offset + length; i++) {
            int value = bytes[i] & 255;
            if (value >= 0xf8) continue; // Realtime may interrupt any message.
            if ((value & 128) != 0) {
                count = 0;
                if (value >= 0xf0) { status = needed = 0; continue; }
                status = value;
                int kind = value & 0xf0;
                needed = kind == 0xc0 || kind == 0xd0 ? 1 : 2;
            } else if (status != 0) {
                if (count++ == 0) first = value;
                if (count == needed) {
                    listener.onMessage(status, first, needed == 1 ? 0 : value);
                    count = 0; // Keep channel running status.
                }
            }
        }
    }
}
