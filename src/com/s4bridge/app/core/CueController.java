package com.s4bridge.app.core;

/** Implements press-and-hold CDJ-style cue preview semantics. */
public final class CueController {
    private final Playback playback;
    private int cuePointMs;
    private boolean previewing;

    public CueController(Playback playback) {
        this.playback = playback;
    }

    public void onPress() {
        if (!playback.isLoaded()) return;
        if (playback.isPlaying()) {
            playback.pause();
            playback.seekToMs(cuePointMs);
            previewing = false;
        } else {
            cuePointMs = Math.max(0, playback.getPositionMs());
            playback.play();
            previewing = true;
        }
    }

    public void onRelease() {
        if (!previewing) return;
        playback.pause();
        playback.seekToMs(cuePointMs);
        previewing = false;
    }

    public void onTrackLoaded() {
        cuePointMs = 0;
        previewing = false;
    }

    public int getCuePointMs() { return cuePointMs; }
    public boolean isPreviewing() { return previewing; }
}
