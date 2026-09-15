package com.s4bridge.app.core;

/** Minimal transport surface shared by the Android player and JVM tests. */
public interface Playback {
    boolean isLoaded();
    boolean isPlaying();
    int getPositionMs();
    void play();
    void pause();
    void seekToMs(int positionMs);
}
