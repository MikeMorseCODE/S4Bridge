package com.s4bridge.app.hardware;

public class S4Mk2Mapping {
    public interface Listener {
        void onButton(String control, boolean pressed);
        void onAbsolute(String control, int raw, float normalized);
        void onRelative(String control, int delta);
    }

    private final Listener listener;
    public S4Mk2Mapping(Listener listener) { this.listener = listener; }

    public void parse(byte[] now, byte[] old) {
        if (now == null || old == null || now.length < 2 || old.length < 2) return;
        int reportId = now[0] & 0xFF;
        switch (reportId) {
            case 0x01: parseShort(now, old); break;
            case 0x02: parseLong(now, old); break;
            default: break;
        }
    }

    private void parseShort(byte[] n, byte[] o) {
        button(n,o,0x0D,0x01,"DECK_A_PLAY"); button(n,o,0x0D,0x02,"DECK_A_CUE");
        button(n,o,0x0D,0x04,"DECK_A_SYNC"); button(n,o,0x0D,0x08,"DECK_A_SHIFT");
        button(n,o,0x0D,0x80,"DECK_A_HOTCUE_1"); button(n,o,0x0D,0x40,"DECK_A_HOTCUE_2");
        button(n,o,0x0D,0x20,"DECK_A_HOTCUE_3"); button(n,o,0x0D,0x10,"DECK_A_HOTCUE_4");
        button(n,o,0x0E,0x80,"DECK_A_REMIX_1"); button(n,o,0x0E,0x40,"DECK_A_REMIX_2");
        button(n,o,0x0E,0x20,"DECK_A_REMIX_3"); button(n,o,0x0E,0x10,"DECK_A_REMIX_4");
        button(n,o,0x0E,0x08,"DECK_A_LOOP_OUT"); button(n,o,0x0E,0x04,"DECK_A_LOOP_IN");
        button(n,o,0x0E,0x02,"DECK_A_SLIP"); button(n,o,0x0E,0x01,"DECK_A_KEY_RESET");
        button(n,o,0x13,0x02,"DECK_A_LOOP_SET"); button(n,o,0x13,0x01,"DECK_A_LOOP_ACTIVATE");
        button(n,o,0x11,0x01,"DECK_A_JOG_TOUCH"); relative8(n,o,0x01,"DECK_A_JOG");
        button(n,o,0x0F,0x20,"DECK_A_DECK_SWITCH"); button(n,o,0x0F,0x10,"DECK_A_LOAD");
        button(n,o,0x12,0x80,"DECK_A_FX_ON"); button(n,o,0x12,0x40,"DECK_A_FX_BUTTON_1");
        button(n,o,0x12,0x20,"DECK_A_FX_BUTTON_2"); button(n,o,0x12,0x10,"DECK_A_FX_BUTTON_3");
        button(n,o,0x11,0x08,"DECK_A_FX_SHOW_PARAMETERS");

        button(n,o,0x0C,0x01,"DECK_B_PLAY"); button(n,o,0x0C,0x02,"DECK_B_CUE");
        button(n,o,0x0C,0x04,"DECK_B_SYNC"); button(n,o,0x0C,0x08,"DECK_B_SHIFT");
        button(n,o,0x0C,0x80,"DECK_B_HOTCUE_1"); button(n,o,0x0C,0x40,"DECK_B_HOTCUE_2");
        button(n,o,0x0C,0x20,"DECK_B_HOTCUE_3"); button(n,o,0x0C,0x10,"DECK_B_HOTCUE_4");
        button(n,o,0x0B,0x80,"DECK_B_REMIX_1"); button(n,o,0x0B,0x40,"DECK_B_REMIX_2");
        button(n,o,0x0B,0x20,"DECK_B_REMIX_3"); button(n,o,0x0B,0x10,"DECK_B_REMIX_4");
        button(n,o,0x0B,0x08,"DECK_B_LOOP_OUT"); button(n,o,0x0B,0x04,"DECK_B_LOOP_IN");
        button(n,o,0x0B,0x02,"DECK_B_SLIP"); button(n,o,0x0B,0x01,"DECK_B_KEY_RESET");
        button(n,o,0x13,0x10,"DECK_B_LOOP_SET"); button(n,o,0x13,0x08,"DECK_B_LOOP_ACTIVATE");
        button(n,o,0x11,0x02,"DECK_B_JOG_TOUCH"); relative8(n,o,0x05,"DECK_B_JOG");
        button(n,o,0x0A,0x20,"DECK_B_DECK_SWITCH"); button(n,o,0x0A,0x10,"DECK_B_LOAD");
        button(n,o,0x10,0x08,"DECK_B_FX_ON"); button(n,o,0x10,0x04,"DECK_B_FX_BUTTON_1");
        button(n,o,0x10,0x02,"DECK_B_FX_BUTTON_2"); button(n,o,0x10,0x01,"DECK_B_FX_BUTTON_3");
        button(n,o,0x11,0x04,"DECK_B_FX_SHOW_PARAMETERS");

        button(n,o,0x0F,0x40,"CHANNEL_A_PFL"); button(n,o,0x0A,0x40,"CHANNEL_B_PFL");
        button(n,o,0x0F,0x80,"CHANNEL_C_PFL"); button(n,o,0x0A,0x80,"CHANNEL_D_PFL");
        button(n,o,0x12,0x02,"CHANNEL_A_FX1_ASSIGN"); button(n,o,0x12,0x01,"CHANNEL_A_FX2_ASSIGN");
        button(n,o,0x10,0x80,"CHANNEL_B_FX1_ASSIGN"); button(n,o,0x10,0x40,"CHANNEL_B_FX2_ASSIGN");
        button(n,o,0x12,0x08,"CHANNEL_C_FX1_ASSIGN"); button(n,o,0x12,0x04,"CHANNEL_C_FX2_ASSIGN");
        button(n,o,0x10,0x20,"CHANNEL_D_FX1_ASSIGN"); button(n,o,0x10,0x10,"CHANNEL_D_FX2_ASSIGN");
        button(n,o,0x11,0x20,"CHANNEL_A_PREGAIN_RESET"); button(n,o,0x11,0x40,"CHANNEL_B_PREGAIN_RESET");
        button(n,o,0x11,0x10,"CHANNEL_C_PREGAIN_RESET"); button(n,o,0x11,0x80,"CHANNEL_D_PREGAIN_RESET");
        button(n,o,0x13,0x04,"BROWSER_PUSH"); button(n,o,0x0F,0x01,"PREVIEW_DECK");
        button(n,o,0x0F,0x04,"RECORDING"); maskedValue(n,o,0x0F,0x06,1,"PLAY_SHIFTER");
        button(n,o,0x0A,0x08,"QUANTIZE"); button(n,o,0x0A,0x02,"SNAP");
    }

    private void parseLong(byte[] n, byte[] o) {
        relativeNibble(n,o,0x01,false,"DECK_A_LOOP_MOVE"); relativeNibble(n,o,0x01,true,"DECK_A_LOOP_SIZE");
        relativeNibble(n,o,0x02,false,"BROWSER"); relativeNibble(n,o,0x02,true,"DECK_B_LOOP_MOVE");
        relativeNibble(n,o,0x03,false,"DECK_B_LOOP_SIZE"); relativeNibble(n,o,0x03,true,"CHANNEL_A_PREGAIN");
        relativeNibble(n,o,0x04,false,"CHANNEL_B_PREGAIN"); relativeNibble(n,o,0x04,true,"CHANNEL_C_PREGAIN");
        relativeNibble(n,o,0x05,false,"CHANNEL_D_PREGAIN");
        absolute16(n,o,0x09,"DECK_A_PITCH"); absolute16(n,o,0x0B,"DECK_B_PITCH");
        absolute16(n,o,0x37,"CHANNEL_A_VOLUME"); absolute16(n,o,0x39,"CHANNEL_B_VOLUME");
        absolute16(n,o,0x3B,"CHANNEL_C_VOLUME"); absolute16(n,o,0x3D,"CHANNEL_D_VOLUME");
        absolute16(n,o,0x07,"CROSSFADER"); absolute16(n,o,0x0D,"HEADPHONE_MIX");
        absolute16(n,o,0x17,"CHANNEL_A_EQ_LOW"); absolute16(n,o,0x19,"CHANNEL_A_EQ_MID"); absolute16(n,o,0x1B,"CHANNEL_A_EQ_HIGH"); absolute16(n,o,0x1D,"CHANNEL_A_FILTER");
        absolute16(n,o,0x1F,"CHANNEL_B_EQ_LOW"); absolute16(n,o,0x21,"CHANNEL_B_EQ_MID"); absolute16(n,o,0x23,"CHANNEL_B_EQ_HIGH"); absolute16(n,o,0x25,"CHANNEL_B_FILTER");
        absolute16(n,o,0x27,"CHANNEL_C_EQ_LOW"); absolute16(n,o,0x29,"CHANNEL_C_EQ_MID"); absolute16(n,o,0x2B,"CHANNEL_C_EQ_HIGH"); absolute16(n,o,0x2D,"CHANNEL_C_FILTER");
        absolute16(n,o,0x2F,"CHANNEL_D_EQ_LOW"); absolute16(n,o,0x31,"CHANNEL_D_EQ_MID"); absolute16(n,o,0x33,"CHANNEL_D_EQ_HIGH"); absolute16(n,o,0x35,"CHANNEL_D_FILTER");
        absolute16(n,o,0x3F,"FX1_DRY_WET"); absolute16(n,o,0x41,"FX1_PARAM_1"); absolute16(n,o,0x43,"FX1_PARAM_2"); absolute16(n,o,0x45,"FX1_PARAM_3");
        absolute16(n,o,0x47,"FX2_DRY_WET"); absolute16(n,o,0x49,"FX2_PARAM_1"); absolute16(n,o,0x4B,"FX2_PARAM_2"); absolute16(n,o,0x4D,"FX2_PARAM_3");
    }

    private void button(byte[] now, byte[] old, int offset, int mask, String name) {
        if (offset >= now.length || offset >= old.length) return;
        boolean current = (now[offset] & mask) != 0;
        boolean previous = (old[offset] & mask) != 0;
        if (current != previous) listener.onButton(name, current);
    }

    private void absolute16(byte[] now, byte[] old, int offset, String name) {
        if (offset + 1 >= now.length || offset + 1 >= old.length) return;
        int current = ((now[offset] & 0xFF) | ((now[offset + 1] & 0xFF) << 8)) & 0x0FFF;
        int previous = ((old[offset] & 0xFF) | ((old[offset + 1] & 0xFF) << 8)) & 0x0FFF;
        if (current != previous) listener.onAbsolute(name, current, current / 4095.0f);
    }

    private void relative8(byte[] now, byte[] old, int offset, String name) {
        if (offset >= now.length || offset >= old.length) return;
        int current = now[offset] & 0xFF, previous = old[offset] & 0xFF;
        int delta = current - previous;
        if (delta > 127) delta -= 256; else if (delta < -128) delta += 256;
        if (Math.abs(delta) > 32) return;
        if (delta != 0) listener.onRelative(name, delta);
    }

    private void relativeNibble(byte[] now, byte[] old, int offset, boolean high, String name) {
        if (offset >= now.length || offset >= old.length) return;
        int cb = now[offset] & 0xFF, pb = old[offset] & 0xFF;
        int current = high ? ((cb >> 4) & 0x0F) : (cb & 0x0F);
        int previous = high ? ((pb >> 4) & 0x0F) : (pb & 0x0F);
        int delta = current - previous;
        if (delta > 7) delta -= 16; else if (delta < -8) delta += 16;
        if (delta != 0) listener.onRelative(name, delta);
    }

    private void maskedValue(byte[] now, byte[] old, int offset, int mask, int shift, String name) {
        if (offset >= now.length || offset >= old.length) return;
        int current = (now[offset] & mask) >> shift;
        int previous = (old[offset] & mask) >> shift;
        if (current != previous) listener.onAbsolute(name, current, current / 3.0f);
    }
}
