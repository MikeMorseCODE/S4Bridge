import com.s4bridge.app.core.CueController;
import com.s4bridge.app.core.ControllerRouter;
import com.s4bridge.app.core.Playback;
import com.s4bridge.app.core.TrackLibrary;

public final class CoreBehaviorTest {
    private static final class FakePlayback implements Playback {
        boolean loaded = true;
        boolean playing;
        int position;
        public boolean isLoaded() { return loaded; }
        public boolean isPlaying() { return playing; }
        public int getPositionMs() { return position; }
        public void play() { playing = true; }
        public void pause() { playing = false; }
        public void seekToMs(int value) { position = value; }
    }

    public static void main(String[] args) {
        cuePreviewReturnsToCuePoint();
        cueWhilePlayingReturnsToStoredPoint();
        unloadedCueIsIgnored();
        libraryDeduplicatesAndClampsSelection();
        controllerRoutesBrowserCueAndLoad();
        System.out.println("Core behavior tests passed");
    }

    private static void cuePreviewReturnsToCuePoint() {
        FakePlayback player = new FakePlayback();
        player.position = 1234;
        CueController cue = new CueController(player);
        cue.onPress();
        check(player.playing && cue.isPreviewing(), "cue press must start preview");
        player.position = 2000;
        cue.onRelease();
        check(!player.playing && player.position == 1234, "cue release must return to cue point");
    }

    private static void cueWhilePlayingReturnsToStoredPoint() {
        FakePlayback player = new FakePlayback();
        CueController cue = new CueController(player);
        player.position = 500;
        cue.onPress();
        cue.onRelease();
        player.position = 900;
        player.playing = true;
        cue.onPress();
        check(!player.playing && player.position == 500, "playing cue must stop at stored cue point");
    }

    private static void unloadedCueIsIgnored() {
        FakePlayback player = new FakePlayback();
        player.loaded = false;
        player.position = 77;
        CueController cue = new CueController(player);
        cue.onPress();
        check(!player.playing && player.position == 77, "unloaded deck changed");
    }

    private static void libraryDeduplicatesAndClampsSelection() {
        TrackLibrary library = new TrackLibrary();
        library.add(new TrackLibrary.Track("one", "One"));
        library.add(new TrackLibrary.Track("two", "Two"));
        library.add(new TrackLibrary.Track("one", "Duplicate"));
        check(library.getTracks().size() == 2, "duplicate was added");
        library.moveSelection(99);
        check("two".equals(library.getSelected().getReference()), "upper clamp failed");
        library.moveSelection(-99);
        check("one".equals(library.getSelected().getReference()), "lower clamp failed");
    }

    private static void controllerRoutesBrowserCueAndLoad() {
        final StringBuilder calls = new StringBuilder();
        ControllerRouter router = new ControllerRouter(new ControllerRouter.Actions() {
            public void togglePlay(boolean deckA) { calls.append("play:").append(deckA).append(';'); }
            public void cue(boolean deckA, boolean pressed) { calls.append("cue:").append(deckA).append(':').append(pressed).append(';'); }
            public void load(boolean deckA) { calls.append("load:").append(deckA).append(';'); }
            public void browse(int delta) { calls.append("browse:").append(delta).append(';'); }
            public void jog(boolean deckA, int delta) { calls.append("jog:").append(deckA).append(':').append(delta).append(';'); }
        });
        check(router.onButton("DECK_A_CUE", true), "A cue was not handled");
        check(router.onButton("DECK_A_CUE", false), "A cue release was not handled");
        check(router.onButton("DECK_B_LOAD", true), "B load was not handled");
        check(router.onRelative("BROWSER", -2), "browser was not handled");
        check(router.onRelative("DECK_A_JOG", 3), "jog was not handled");
        check(!router.onButton("DECK_A_SYNC", true), "unsupported button was handled");
        check("cue:true:true;cue:true:false;load:false;browse:-2;jog:true:3;".equals(calls.toString()), "unexpected routes: " + calls);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
