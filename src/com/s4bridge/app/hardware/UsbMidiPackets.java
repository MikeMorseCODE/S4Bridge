package com.s4bridge.app.hardware;

/** USB MIDI 1.0 four-byte event packets (USB-IF MIDI10 section 4). */
public final class UsbMidiPackets {
    public interface Listener { void message(int cable,int status,int first,int second); }
    private final Listener listener;
    private final byte[] packet=new byte[4];
    private int pending;
    public UsbMidiPackets(Listener listener){this.listener=listener;}
    public void accept(byte[] bytes,int length){
        if(length<0||length>bytes.length)throw new IllegalArgumentException("Invalid transfer size");
        for(int i=0;i<length;i++){
            packet[pending++]=bytes[i];if(pending<4)continue;pending=0;
            int header=packet[0]&255,cin=header&15,status=packet[1]&255;
            if(cin<8||cin>14||(status>>4)!=cin)continue;
            int first=packet[2]&255,second=packet[3]&255;
            if(first>127||((cin!=12&&cin!=13)&&second>127))continue;
            listener.message(header>>4,status,first,cin==12||cin==13?0:second);
        }
    }
    public static byte[] encode(int cable,byte[] message){
        if(cable<0||cable>15||message.length!=3)throw new IllegalArgumentException("Invalid MIDI packet");
        int status=message[0]&255,cin=status>>4;
        if(cin<8||cin>14||(message[1]&255)>127||(message[2]&255)>127)throw new IllegalArgumentException("Not channel MIDI");
        return new byte[]{(byte)((cable<<4)|cin),message[0],message[1],(byte)(cin==12||cin==13?0:message[2])};
    }
}
