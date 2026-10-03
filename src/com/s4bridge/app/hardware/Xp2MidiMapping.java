package com.s4bridge.app.hardware;

/** Factory MIDI addresses from Pioneer DDJ-XP2 MIDI Message List e1/j1 (2019).
 * Pad functions are S4Bridge layers, not a claim of Serato/rekordbox emulation.
 */
public final class Xp2MidiMapping {
    public interface Listener {
        void pad(int deck,int mode,int index,boolean shifted,boolean pressed);
        void button(int deck,String control,boolean pressed);
        void browse(int delta);
        void fx(int deck,String control,int slot,float value);
        void mode(int deck,int mode);
    }
    private final Listener listener;
    private final boolean[][] held=new boolean[16][128];
    private final int[][] msb=new int[2][3],lsb=new int[2][3];
    private final int[] modes={0,0};
    private final boolean[] rolling={false,false};
    public Xp2MidiMapping(Listener listener){this.listener=listener;}
    public static int padNote(int mode,int index){
        if(mode<0||mode>7||index<0||index>15)throw new IllegalArgumentException("Pad out of range");
        return mode*16+(3-index/4)*4+index%4;
    }
    public int getMode(int deck){return modes[deck];}
    public boolean onMessage(int status,int data1,int data2){
        int channel=status&15,kind=status&0xf0;
        if(kind==0xb0){
            if(channel==6&&(data1==64||data1==100)){
                int delta=data2<64?data2:data2-128;if(delta!=0)listener.browse(delta);return true;
            }
            if(channel==4||channel==5){
                int deck=channel-4,slot=data1>=34&&data1<=36?data1-34:data1-2;
                if(slot>=0&&slot<3){
                    if(data1>=34)lsb[deck][slot]=data2;else msb[deck][slot]=data2;
                    listener.fx(deck,"AMOUNT",slot,((msb[deck][slot]<<7)|lsb[deck][slot])/16383f);return true;
                }
            }
            return false;
        }
        if(kind!=0x80&&kind!=0x90)return false;
        boolean pressed=kind==0x90&&data2>0;
        if(channel>=7&&channel<=10){
            int deck=(channel-7)/2,mode=data1/16,low=data1%16,index=(3-low/4)*4+low%4;
            if(held[channel][data1]==pressed)return true;held[channel][data1]=pressed;
            if(pressed&&modes[deck]!=mode){releaseRoll(deck);modes[deck]=mode;listener.mode(deck,mode);}
            boolean shifted=(channel&1)==0;
            if(mode==4&&index<8&&!shifted){
                // One active roll per deck. Releases from superseded pads must not stop it.
                if(pressed)rolling[deck]=true;
            }
            listener.pad(deck,mode,index,shifted,pressed);return true;
        }
        if(channel==6){
            if(data1==64)return true; // SHIFT is encoded independently in pad channel/address.
            if(data1==70||data1==71||data1==88||data1==89){
                if(edge(channel,data1,pressed))listener.button(data1==70||data1==88?0:1,"LOAD",pressed);return true;
            }
            if(data1==72||data1==73||data1==96||data1==97){listener.button(2,"UNSUPPORTED_DECK",pressed);return true;}
            return false;
        }
        if(channel==4||channel==5){
            if(!edge(channel,data1,pressed))return true;
            int deck=channel-4;
            if(pressed&&data1>=112&&data1<=114){listener.fx(deck,"SELECT",data1-112,0);return true;}
            if(pressed&&data1>=71&&data1<=73){listener.fx(deck,"TOUCH",data1-71,0);return true;}
            if(pressed&&data1==118){listener.fx(deck,"HOLD",0,0);return true;}
            return data1>=71&&data1<=73||data1>=112&&data1<=118;
        }
        if(channel==2||channel==3){listener.button(channel,"UNSUPPORTED_DECK",pressed);return true;}
        if(channel>1)return false;
        if(!edge(channel,data1,pressed))return true;
        int[] selectors={27,30,32,34,105,107,109,111};
        for(int mode=0;mode<selectors.length;mode++)if(data1==selectors[mode]){
            if(pressed){releaseRoll(channel);modes[channel]=mode;listener.mode(channel,mode);}return true;
        }
        String control=null;
        switch(data1){
            case 20:control="LOOP";break;case 16:control="HALF_LOOP";break;case 17:control="DOUBLE_LOOP";break;
            case 76:control="LOOP_IN";break;case 77:control="LOOP_OUT";break;
            case 53:control="QUANTIZE";break;case 88:control="SYNC";break;case 92:control="SYNC_OFF";break;
            case 104:control="SILENT";break;
            default:if(data1>=36&&data1<=43)control="PARAM_LEFT";else if(data1>=44&&data1<=51)control="PARAM_RIGHT";
                else if(data1>=1&&data1<=8)control="MOVE_LEFT";else if(data1==9||data1>=122)control="MOVE_RIGHT";
        }
        if(control!=null){listener.button(channel,control,pressed);return true;}
        return false;
    }
    private boolean edge(int channel,int note,boolean pressed){boolean changed=held[channel][note]!=pressed;held[channel][note]=pressed;return changed;}
    private void releaseRoll(int deck){if(rolling[deck]){rolling[deck]=false;listener.button(deck,"RELEASE_ROLL",true);}}
    public void reset(){
        for(int deck=0;deck<2;deck++){releaseRoll(deck);listener.button(deck,"RELEASE_ALL",true);}
        for(int channel=0;channel<16;channel++)java.util.Arrays.fill(held[channel],false);
        for(int deck=0;deck<2;deck++){java.util.Arrays.fill(msb[deck],0);java.util.Arrays.fill(lsb[deck],0);}
    }
}
