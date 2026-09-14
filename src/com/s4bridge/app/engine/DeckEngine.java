package com.s4bridge.app.engine;

import android.media.MediaPlayer;

public class DeckEngine {
    private final String name;
    private MediaPlayer player;
    private String loadedPath;
    private float channelVolume = 1.0f;
    private float crossfaderGain = 1.0f;
    private float tempo = 0.0f;
    private float eqLow = 0.5f;
    private float eqMid = 0.5f;
    private float eqHigh = 0.5f;
    private float filter = 0.5f;
    private boolean jogTouched = false;

    public DeckEngine(String name) { this.name = name; }

    public boolean loadFile(String path) {
        releasePlayer();
        try {
            MediaPlayer next = new MediaPlayer();
            next.setDataSource(path);
            next.prepare();
            player = next;
            loadedPath = path;
            applyGain();
            return true;
        } catch (Exception e) {
            releasePlayer();
            loadedPath = null;
            return false;
        }
    }

    public String getName() { return name; }
    public boolean isLoaded() { return player != null; }
    public String getLoadedPath() { return loadedPath; }

    public boolean isPlaying() {
        if (player == null) return false;
        try { return player.isPlaying(); } catch (IllegalStateException e) { return false; }
    }

    public void play() {
        if (player == null) return;
        try { player.start(); } catch (IllegalStateException ignored) {}
    }

    public void pause() {
        if (player == null) return;
        try { if (player.isPlaying()) player.pause(); } catch (IllegalStateException ignored) {}
    }

    public void togglePlay() {
        if (player == null) return;
        try {
            if (player.isPlaying()) player.pause(); else player.start();
        } catch (IllegalStateException ignored) {}
    }

    public void setChannelVolume(float value) { channelVolume = clamp(value); applyGain(); }
    public float getChannelVolume() { return channelVolume; }
    public void setCrossfaderGain(float value) { crossfaderGain = clamp(value); applyGain(); }
    public float getCrossfaderGain() { return crossfaderGain; }

    private void applyGain() {
        if (player == null) return;
        float outputGain = channelVolume * crossfaderGain;
        try { player.setVolume(outputGain, outputGain); } catch (IllegalStateException ignored) {}
    }

    public void setTempo(float normalized) { tempo = (clamp(normalized) - 0.5f) * 2.0f; }
    public float getTempo() { return tempo; }
    public void setEqLow(float value) { eqLow = clamp(value); }
    public float getEqLow() { return eqLow; }
    public void setEqMid(float value) { eqMid = clamp(value); }
    public float getEqMid() { return eqMid; }
    public void setEqHigh(float value) { eqHigh = clamp(value); }
    public float getEqHigh() { return eqHigh; }
    public void setFilter(float value) { filter = clamp(value); }
    public float getFilter() { return filter; }
    public void setJogTouched(boolean touched) { jogTouched = touched; }
    public boolean isJogTouched() { return jogTouched; }
    public void jog(int delta) { /* Native audio engine milestone. */ }

    public void release() { releasePlayer(); loadedPath = null; }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    private float clamp(float value) {
        if (value < 0.0f) return 0.0f;
        if (value > 1.0f) return 1.0f;
        return value;
    }
}
