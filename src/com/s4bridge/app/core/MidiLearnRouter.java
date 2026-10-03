package com.s4bridge.app.core;

import java.util.HashMap;
import java.util.Map;

/** Explicit note learning; no unverified DDJ-XP2 address assumptions. */
public final class MidiLearnRouter {
    private final ControllerRouter router;
    private final Map<Integer, String> bindings = new HashMap<Integer, String>();
    private final Map<Integer, String> held = new HashMap<Integer, String>();
    private String learning;
    public MidiLearnRouter(ControllerRouter router) { this.router = router; }
    public void learn(String control) {
        if (!valid(control)) throw new IllegalArgumentException("Unknown action");
        releaseAll(); learning = control;
    }
    public void cancelLearn() { learning = null; }
    public String getLearning() { return learning; }
    public void bind(int channel, int note, String control) {
        if (channel < 0 || channel > 15 || note < 0 || note > 127 || !valid(control))
            throw new IllegalArgumentException("Invalid note binding");
        // A control gets one binding; a note gets one action.
        Integer previous = null;
        for (Map.Entry<Integer, String> entry : bindings.entrySet())
            if (entry.getValue().equals(control)) previous = entry.getKey();
        if (previous != null) bindings.remove(previous);
        bindings.put(channel * 128 + note, control);
    }
    public boolean hasBinding(int status,int note) {
        int kind=status&0xf0;
        return (kind==0x80||kind==0x90)&&bindings.containsKey((status&15)*128+note);
    }
    public String binding(String control) {
        for (Map.Entry<Integer, String> entry : bindings.entrySet())
            if (entry.getValue().equals(control)) return (entry.getKey() / 128) + ":" + (entry.getKey() % 128);
        return null;
    }
    /** Returns learned action, if this message completed learning. */
    public String onMessage(int status, int note, int velocity) {
        int kind = status & 0xf0;
        if (kind != 0x80 && kind != 0x90) return null;
        int key = (status & 15) * 128 + note;
        boolean pressed = kind == 0x90 && velocity > 0;
        if (learning != null) {
            if (!pressed) return null;
            String learned = learning;
            bind(status & 15, note, learned);
            learning = null;
            return learned; // Learning must never trigger playback.
        }
        if (pressed) {
            String control = bindings.get(key);
            if (control != null && !held.containsKey(key)) {
                held.put(key, control);
                router.onButton(control, true);
            }
        } else {
            String control = held.remove(key);
            if (control != null) router.onButton(control, false);
        }
        return null;
    }
    public void releaseAll() {
        for (String control : held.values()) router.onButton(control, false);
        held.clear();
    }
    public void clear() { releaseAll(); bindings.clear(); learning = null; }
    private static boolean valid(String control) {
        return "DECK_A_PLAY".equals(control) || "DECK_B_PLAY".equals(control)
            || "DECK_A_CUE".equals(control) || "DECK_B_CUE".equals(control)
            || "DECK_A_LOAD".equals(control) || "DECK_B_LOAD".equals(control);
    }
}
