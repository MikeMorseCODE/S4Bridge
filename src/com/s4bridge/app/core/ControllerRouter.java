package com.s4bridge.app.core;

/** Routes the Phase 1 transport and browser controls emitted by S4Mk2Mapping. */
public final class ControllerRouter {
    public interface Actions {
        void togglePlay(boolean deckA);
        void cue(boolean deckA, boolean pressed);
        void load(boolean deckA);
        void browse(int delta);
        void jog(boolean deckA, int delta);
    }

    private final Actions actions;

    public ControllerRouter(Actions actions) { this.actions = actions; }

    public boolean onButton(String control, boolean pressed) {
        if ("DECK_A_PLAY".equals(control) || "DECK_B_PLAY".equals(control)) {
            if (pressed) actions.togglePlay(control.charAt(5) == 'A');
            return true;
        }
        if ("DECK_A_CUE".equals(control) || "DECK_B_CUE".equals(control)) {
            actions.cue(control.charAt(5) == 'A', pressed);
            return true;
        }
        if ("DECK_A_LOAD".equals(control) || "DECK_B_LOAD".equals(control)) {
            if (pressed) actions.load(control.charAt(5) == 'A');
            return true;
        }
        return false;
    }

    public boolean onRelative(String control, int delta) {
        if ("BROWSER".equals(control)) {
            actions.browse(delta);
            return true;
        }
        if ("DECK_A_JOG".equals(control) || "DECK_B_JOG".equals(control)) {
            actions.jog(control.charAt(5) == 'A', delta);
            return true;
        }
        return false;
    }
}
