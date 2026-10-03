import com.s4bridge.app.hardware.MidiStreamParser;
import com.s4bridge.app.core.ControllerRouter;
import com.s4bridge.app.core.MidiLearnRouter;
import java.util.ArrayList;

public final class Xp2MidiTest {
    public static void main(String[] args) {
        final ArrayList<String> messages = new ArrayList<String>();
        MidiStreamParser parser = new MidiStreamParser(new MidiStreamParser.Listener() {
            public void onMessage(int status, int a, int b) { messages.add(status+":"+a+":"+b); }
        });
        parser.accept(new byte[]{0, (byte)0x91, 60}, 0, 3);
        check(messages.isEmpty(), "partial message must wait");
        parser.accept(new byte[]{100, (byte)0xf8, 61, 0, (byte)0x81, 60, 42}, 0, 7);
        check(messages.toString().equals("[145:60:100, 145:61:0, 129:60:42]"), "running status/realtime/release");
        messages.clear();
        parser.accept(new byte[]{(byte)0xc0, 1, 2, (byte)0xd1, 50, (byte)0xb0, 3, 127}, 0, 8);
        check(messages.toString().equals("[192:1:0, 192:2:0, 209:50:0, 176:3:127]"), "one-byte channel messages and CC");
        messages.clear();
        parser.accept(new byte[]{(byte)0xf0, 1, 2, (byte)0xf8, 3, (byte)0xf7, 60, 127}, 0, 8);
        check(messages.isEmpty(), "SysEx must not create notes");
        parser.accept(new byte[]{(byte)0x90, 60}, 0, 2); parser.reset();
        parser.accept(new byte[]{127}, 0, 1);
        check(messages.isEmpty(), "reset clears partial and running status");
        boolean rejected=false;
        try { parser.accept(new byte[1], 1, 1); } catch(IndexOutOfBoundsException e) { rejected=true; }
        check(rejected, "invalid ranges rejected");

        final ArrayList<String> actions = new ArrayList<String>();
        MidiLearnRouter router = new MidiLearnRouter(new ControllerRouter(new ControllerRouter.Actions() {
            public void togglePlay(boolean a) { actions.add("play:"+a); }
            public void cue(boolean a, boolean pressed) { actions.add("cue:"+a+":"+pressed); }
            public void load(boolean a) { actions.add("load:"+a); }
            public void browse(int delta) { throw new AssertionError("unexpected browse"); }
            public void jog(boolean a,int delta) { throw new AssertionError("unexpected jog"); }
        }));
        router.learn("DECK_A_PLAY");
        check(router.onMessage(0xb0,60,127)==null && router.getLearning()!=null,"CC cannot complete note learning");
        check(router.onMessage(0x90,60,0)==null,"release cannot learn");
        check("DECK_A_PLAY".equals(router.onMessage(0x90,60,100)),"learn note");
        router.onMessage(0x80,60,42);
        check(actions.isEmpty(),"learn press/release never performs action");
        router.onMessage(0x91,60,127); check(actions.isEmpty(),"channel isolation");
        router.onMessage(0x90,60,127); router.onMessage(0x90,60,80);
        check(actions.toString().equals("[play:true]"),"repeated note-on does not toggle twice");
        router.onMessage(0x90,60,0); router.onMessage(0x90,60,127);
        check(actions.size()==2,"zero velocity releases note");
        router.releaseAll(); actions.clear();
        router.bind(0,60,"DECK_B_CUE"); router.onMessage(0x90,60,127); router.releaseAll();
        check(actions.toString().equals("[cue:false:true, cue:false:false]"),"disconnect releases held cue");
        check(router.binding("DECK_A_PLAY")==null,"replacing note removes old action");
        router.bind(2,61,"DECK_B_CUE");
        check("2:61".equals(router.binding("DECK_B_CUE")),"reassign control replaces old note");
        actions.clear(); router.onMessage(0x90,60,127); check(actions.isEmpty(),"old binding inactive");
        router.onMessage(0x92,61,127); router.learn("DECK_A_LOAD");
        check(actions.toString().equals("[cue:false:true, cue:false:false]"),"learning releases held controls");
        router.cancelLearn(); check(router.getLearning()==null,"cancel learn");
        router.clear(); check(router.binding("DECK_B_CUE")==null,"clear bindings");
        System.out.println("XP2 MIDI framing and learn routing tests passed");
    }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
