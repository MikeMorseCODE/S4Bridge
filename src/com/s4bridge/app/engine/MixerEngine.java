package com.s4bridge.app.engine;

public class MixerEngine {
    private final DeckEngine deckA;
    private final DeckEngine deckB;
    private float crossfader = 0.5f;

    public MixerEngine(DeckEngine deckA, DeckEngine deckB) {
        this.deckA = deckA;
        this.deckB = deckB;
        applyMix();
    }

    public void setCrossfader(float value) {
        if (value < 0.0f) value = 0.0f;
        if (value > 1.0f) value = 1.0f;
        crossfader = value;
        applyMix();
    }

    public float getCrossfader() { return crossfader; }

    private void applyMix() {
        double angle = crossfader * Math.PI * 0.5;
        float gainA = (float) Math.cos(angle);
        float gainB = (float) Math.sin(angle);
        deckA.setCrossfaderGain(gainA);
        deckB.setCrossfaderGain(gainB);
    }
}
