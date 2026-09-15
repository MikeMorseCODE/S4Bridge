import com.s4bridge.app.hardware.S4Mk2Mapping;

import java.util.ArrayList;
import java.util.List;

public final class S4Mk2MappingTest {
    private static final class EventListener implements S4Mk2Mapping.Listener {
        final List<String> events = new ArrayList<String>();
        public void onButton(String control, boolean pressed) { events.add(control + ":" + pressed); }
        public void onAbsolute(String control, int raw, float normalized) { events.add(control + ":" + raw + ":" + normalized); }
        public void onRelative(String control, int delta) { events.add(control + ":" + delta); }
    }

    public static void main(String[] args) {
        testEveryButtonMapping();
        testEveryAbsoluteMapping();
        testMaskedValue();
        testJogWrapAndGate();
        testNibbleWrap();
        testMalformedAndUnknownReports();
        System.out.println("S4Mk2Mapping tests passed");
    }

    private static void testEveryButtonMapping() {
        Object[][] cases = new Object[][] {
            {0x0D,0x01,"DECK_A_PLAY"},{0x0D,0x02,"DECK_A_CUE"},{0x0D,0x04,"DECK_A_SYNC"},{0x0D,0x08,"DECK_A_SHIFT"},
            {0x0D,0x80,"DECK_A_HOTCUE_1"},{0x0D,0x40,"DECK_A_HOTCUE_2"},{0x0D,0x20,"DECK_A_HOTCUE_3"},{0x0D,0x10,"DECK_A_HOTCUE_4"},
            {0x0E,0x80,"DECK_A_REMIX_1"},{0x0E,0x40,"DECK_A_REMIX_2"},{0x0E,0x20,"DECK_A_REMIX_3"},{0x0E,0x10,"DECK_A_REMIX_4"},
            {0x0E,0x08,"DECK_A_LOOP_OUT"},{0x0E,0x04,"DECK_A_LOOP_IN"},{0x0E,0x02,"DECK_A_SLIP"},{0x0E,0x01,"DECK_A_KEY_RESET"},
            {0x13,0x02,"DECK_A_LOOP_SET"},{0x13,0x01,"DECK_A_LOOP_ACTIVATE"},{0x11,0x01,"DECK_A_JOG_TOUCH"},
            {0x0F,0x20,"DECK_A_DECK_SWITCH"},{0x0F,0x10,"DECK_A_LOAD"},{0x12,0x80,"DECK_A_FX_ON"},{0x12,0x40,"DECK_A_FX_BUTTON_1"},
            {0x12,0x20,"DECK_A_FX_BUTTON_2"},{0x12,0x10,"DECK_A_FX_BUTTON_3"},{0x11,0x08,"DECK_A_FX_SHOW_PARAMETERS"},
            {0x0C,0x01,"DECK_B_PLAY"},{0x0C,0x02,"DECK_B_CUE"},{0x0C,0x04,"DECK_B_SYNC"},{0x0C,0x08,"DECK_B_SHIFT"},
            {0x0C,0x80,"DECK_B_HOTCUE_1"},{0x0C,0x40,"DECK_B_HOTCUE_2"},{0x0C,0x20,"DECK_B_HOTCUE_3"},{0x0C,0x10,"DECK_B_HOTCUE_4"},
            {0x0B,0x80,"DECK_B_REMIX_1"},{0x0B,0x40,"DECK_B_REMIX_2"},{0x0B,0x20,"DECK_B_REMIX_3"},{0x0B,0x10,"DECK_B_REMIX_4"},
            {0x0B,0x08,"DECK_B_LOOP_OUT"},{0x0B,0x04,"DECK_B_LOOP_IN"},{0x0B,0x02,"DECK_B_SLIP"},{0x0B,0x01,"DECK_B_KEY_RESET"},
            {0x13,0x10,"DECK_B_LOOP_SET"},{0x13,0x08,"DECK_B_LOOP_ACTIVATE"},{0x11,0x02,"DECK_B_JOG_TOUCH"},
            {0x0A,0x20,"DECK_B_DECK_SWITCH"},{0x0A,0x10,"DECK_B_LOAD"},{0x10,0x08,"DECK_B_FX_ON"},{0x10,0x04,"DECK_B_FX_BUTTON_1"},
            {0x10,0x02,"DECK_B_FX_BUTTON_2"},{0x10,0x01,"DECK_B_FX_BUTTON_3"},{0x11,0x04,"DECK_B_FX_SHOW_PARAMETERS"},
            {0x0F,0x40,"CHANNEL_A_PFL"},{0x0A,0x40,"CHANNEL_B_PFL"},{0x0F,0x80,"CHANNEL_C_PFL"},{0x0A,0x80,"CHANNEL_D_PFL"},
            {0x12,0x02,"CHANNEL_A_FX1_ASSIGN"},{0x12,0x01,"CHANNEL_A_FX2_ASSIGN"},{0x10,0x80,"CHANNEL_B_FX1_ASSIGN"},{0x10,0x40,"CHANNEL_B_FX2_ASSIGN"},
            {0x12,0x08,"CHANNEL_C_FX1_ASSIGN"},{0x12,0x04,"CHANNEL_C_FX2_ASSIGN"},{0x10,0x20,"CHANNEL_D_FX1_ASSIGN"},{0x10,0x10,"CHANNEL_D_FX2_ASSIGN"},
            {0x11,0x20,"CHANNEL_A_PREGAIN_RESET"},{0x11,0x40,"CHANNEL_B_PREGAIN_RESET"},{0x11,0x10,"CHANNEL_C_PREGAIN_RESET"},{0x11,0x80,"CHANNEL_D_PREGAIN_RESET"},
            {0x13,0x04,"BROWSER_PUSH"},{0x0F,0x01,"PREVIEW_DECK"},{0x0F,0x04,"RECORDING"},{0x0A,0x08,"QUANTIZE"},{0x0A,0x02,"SNAP"}
        };
        for (Object[] entry : cases) {
            EventListener listener = new EventListener();
            byte[] oldReport = report(0x01, 21);
            byte[] newReport = report(0x01, 21);
            int offset = ((Integer) entry[0]).intValue();
            int mask = ((Integer) entry[1]).intValue();
            String name = (String) entry[2];
            newReport[offset] = (byte) mask;
            new S4Mk2Mapping(listener).parse(newReport, oldReport);
            assertEvent(listener, name + ":true");
        }
    }

    private static void testEveryAbsoluteMapping() {
        Object[][] cases = new Object[][] {
            {0x09,"DECK_A_PITCH"},{0x0B,"DECK_B_PITCH"},{0x37,"CHANNEL_A_VOLUME"},{0x39,"CHANNEL_B_VOLUME"},
            {0x3B,"CHANNEL_C_VOLUME"},{0x3D,"CHANNEL_D_VOLUME"},{0x07,"CROSSFADER"},{0x0D,"HEADPHONE_MIX"},
            {0x17,"CHANNEL_A_EQ_LOW"},{0x19,"CHANNEL_A_EQ_MID"},{0x1B,"CHANNEL_A_EQ_HIGH"},{0x1D,"CHANNEL_A_FILTER"},
            {0x1F,"CHANNEL_B_EQ_LOW"},{0x21,"CHANNEL_B_EQ_MID"},{0x23,"CHANNEL_B_EQ_HIGH"},{0x25,"CHANNEL_B_FILTER"},
            {0x27,"CHANNEL_C_EQ_LOW"},{0x29,"CHANNEL_C_EQ_MID"},{0x2B,"CHANNEL_C_EQ_HIGH"},{0x2D,"CHANNEL_C_FILTER"},
            {0x2F,"CHANNEL_D_EQ_LOW"},{0x31,"CHANNEL_D_EQ_MID"},{0x33,"CHANNEL_D_EQ_HIGH"},{0x35,"CHANNEL_D_FILTER"},
            {0x3F,"FX1_DRY_WET"},{0x41,"FX1_PARAM_1"},{0x43,"FX1_PARAM_2"},{0x45,"FX1_PARAM_3"},
            {0x47,"FX2_DRY_WET"},{0x49,"FX2_PARAM_1"},{0x4B,"FX2_PARAM_2"},{0x4D,"FX2_PARAM_3"}
        };
        for (Object[] entry : cases) {
            EventListener listener = new EventListener();
            byte[] oldReport = report(0x02, 80);
            byte[] newReport = report(0x02, 80);
            int offset = ((Integer) entry[0]).intValue();
            String name = (String) entry[1];
            newReport[offset] = (byte) 0xFF;
            newReport[offset + 1] = 0x0F;
            new S4Mk2Mapping(listener).parse(newReport, oldReport);
            assertEvent(listener, name + ":4095:1.0");
        }
    }

    private static void testJogWrapAndGate() {
        EventListener listener = new EventListener();
        S4Mk2Mapping mapping = new S4Mk2Mapping(listener);
        byte[] oldReport = report(0x01, 21);
        byte[] newReport = report(0x01, 21);
        oldReport[0x01] = (byte) 250;
        newReport[0x01] = 3;
        mapping.parse(newReport, oldReport);
        assertEvent(listener, "DECK_A_JOG:9");

        listener.events.clear();
        oldReport[0x01] = 0;
        newReport[0x01] = 40;
        mapping.parse(newReport, oldReport);
        if (!listener.events.isEmpty()) throw new AssertionError("Startup jog jump was not gated: " + listener.events);
    }

    private static void testMaskedValue() {
        EventListener listener = new EventListener();
        byte[] oldReport = report(0x01, 21);
        byte[] newReport = report(0x01, 21);
        newReport[0x0F] = 0x06;
        new S4Mk2Mapping(listener).parse(newReport, oldReport);
        assertEvent(listener, "PLAY_SHIFTER:3:1.0");
    }

    private static void testNibbleWrap() {
        Object[][] cases = new Object[][] {
            {0x01,false,"DECK_A_LOOP_MOVE"},{0x01,true,"DECK_A_LOOP_SIZE"},
            {0x02,false,"BROWSER"},{0x02,true,"DECK_B_LOOP_MOVE"},
            {0x03,false,"DECK_B_LOOP_SIZE"},{0x03,true,"CHANNEL_A_PREGAIN"},
            {0x04,false,"CHANNEL_B_PREGAIN"},{0x04,true,"CHANNEL_C_PREGAIN"},
            {0x05,false,"CHANNEL_D_PREGAIN"}
        };
        for (Object[] entry : cases) {
            EventListener listener = new EventListener();
            byte[] oldReport = report(0x02, 80);
            byte[] newReport = report(0x02, 80);
            int offset = ((Integer) entry[0]).intValue();
            boolean high = ((Boolean) entry[1]).booleanValue();
            oldReport[offset] = (byte) (high ? 0xF0 : 0x0F);
            newReport[offset] = 0;
            new S4Mk2Mapping(listener).parse(newReport, oldReport);
            assertEvent(listener, entry[2] + ":1");
        }
    }

    private static void testMalformedAndUnknownReports() {
        EventListener listener = new EventListener();
        S4Mk2Mapping mapping = new S4Mk2Mapping(listener);
        mapping.parse(null, null);
        mapping.parse(new byte[0], new byte[0]);
        mapping.parse(report(0x7F, 2), report(0x7F, 2));
        mapping.parse(report(0x01, 2), report(0x01, 2));
        if (!listener.events.isEmpty()) throw new AssertionError("Malformed report emitted events: " + listener.events);
    }

    private static byte[] report(int id, int length) {
        byte[] report = new byte[length];
        report[0] = (byte) id;
        return report;
    }

    private static void assertEvent(EventListener listener, String expected) {
        if (!listener.events.contains(expected)) throw new AssertionError("Missing " + expected + " in " + listener.events);
    }
}
