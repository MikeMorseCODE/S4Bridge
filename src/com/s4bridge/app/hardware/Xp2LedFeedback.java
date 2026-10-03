package com.s4bridge.app.hardware;

/** Changed-state Note On/Off output. Failed sends remain eligible for retry. */
public final class Xp2LedFeedback {
    public interface Output { boolean send(byte[] message); }
    private final Output output;
    private final int[][] cache=new int[16][128];
    public Xp2LedFeedback(Output output){this.output=output;invalidate();}
    public void invalidate(){for(int[] row:cache)java.util.Arrays.fill(row,-1);}
    public void invalidate(int channel,int note){if(channel>=0&&channel<16&&note>=0&&note<128)cache[channel][note]=-1;}
    public void set(int channel,int note,boolean on){
        if(channel<0||channel>15||note<0||note>127)throw new IllegalArgumentException("Invalid MIDI LED address");
        int value=on?127:0;if(cache[channel][note]==value)return;
        if(output.send(new byte[]{(byte)((on?0x90:0x80)|channel),(byte)note,(byte)value}))cache[channel][note]=value;
    }
}
