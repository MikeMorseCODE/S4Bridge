package com.s4bridge.app.core;

import com.s4bridge.app.hardware.Xp2MidiMapping;

/** S4Bridge's eight performance layers operating on actual PCM deck state. */
public final class Xp2PerformanceController implements Xp2MidiMapping.Listener {
    public interface Actions {
        void load(int deck);
        void browse(int delta);
        void cue(int deck,boolean pressed);
        void loadSample(int slot);
        void changed();
        void unsupportedDeck();
    }
    private final PerformanceDeck[] decks,samples;
    private final Actions actions;
    private final int[] rollPad={-1,-1};
    private final boolean[] synced={false,false};
    private final boolean[][] fxPads=new boolean[2][3];
    public static final double[] LOOP_BEATS={0.125,0.25,0.5,1,2,4,8,16};
    public Xp2PerformanceController(PerformanceDeck a,PerformanceDeck b,PerformanceDeck[] samples,Actions actions){
        if(samples.length!=8)throw new IllegalArgumentException("Eight sample slots required");
        this.decks=new PerformanceDeck[]{a,b};this.samples=samples;this.actions=actions;
    }
    public void updateSync(){for(int deck=0;deck<2;deck++)if(synced[deck])decks[deck].matchTempo(decks[1-deck].getEffectiveBpm());}
    public boolean isSynced(int deck){return synced[deck];}
    public void trackLoaded(int deck){synced[deck]=false;rollPad[deck]=-1;}
    public void pad(int deck,int mode,int index,boolean shifted,boolean pressed){
        PerformanceDeck d=decks[deck];
        if(mode==4&&index<8&&!shifted){
            if(pressed){d.setLoop(LOOP_BEATS[index],true);rollPad[deck]=index;}
            else if(rollPad[deck]==index){d.exitLoop();rollPad[deck]=-1;}
        }else if(mode==5&&index<3){
            if(pressed){if(!d.isFxSelected(index))d.fxSelect(index);fxPads[deck][index]=true;d.fxGate(index,true);}
            else if(fxPads[deck][index]){fxPads[deck][index]=false;d.fxGate(index,false);}
        }else if(pressed){
            switch(mode){
                case 0:d.hotCue(index,shifted);break;
                case 1:if(index<8)d.setLoop(LOOP_BEATS[index],false);else d.jumpBeats(jumpSize(index-8));break;
                case 2:if(index<8){if(shifted)actions.loadSample(index);else {samples[index].seekToMs(0);samples[index].play();}}else samples[index-8].pause();break;
                case 3:case 7:transport(deck,index,shifted,true);break;
                case 4:d.jumpBeats(jumpSize(index%8));break;
                case 5:if(index==3)d.fxHold();else if(index>=4&&index<7)d.fxAmount(index-4,shifted?0.25f:0.75f);break;
                case 6:if(index<8)d.savedLoop(index,shifted);else manualLoop(deck,index-8);break;
                default:break;
            }
        }else if(mode==3||mode==7)transport(deck,index,shifted,false);
        actions.changed();
    }
    private static double jumpSize(int index){return new double[]{-1,1,-2,2,-4,4,-8,8}[index];}
    private void transport(int deck,int index,boolean shifted,boolean pressed){
        PerformanceDeck d=decks[deck];
        if(index==6){actions.cue(deck,pressed);return;}
        if(!pressed)return;
        switch(index){
            case 0:d.jumpBeats(-1);break;case 1:d.jumpBeats(1);break;
            case 2:d.setRate(shifted?1:d.getRate()-0.01);synced[deck]=false;break;
            case 3:d.setRate(shifted?1:d.getRate()+0.01);synced[deck]=false;break;
            case 4:d.setQuantize(!d.isQuantized());break;case 5:sync(deck,shifted);break;
            case 7:d.togglePlay();break;case 8:d.setGridOrigin();break;
            case 9:d.moveGrid(-0.01);break;case 10:d.moveGrid(0.01);break;
            case 11:actions.load(deck);break;case 12:d.loopIn();break;case 13:d.loopOut();break;
            case 14:d.toggleLoop();break;case 15:d.exitLoop();break;
        }
    }
    private void manualLoop(int deck,int index){PerformanceDeck d=decks[deck];switch(index){case 0:d.loopIn();break;case 1:d.loopOut();break;case 2:d.toggleLoop();break;case 3:d.exitLoop();break;case 4:d.resizeLoop(0.5);break;case 5:d.resizeLoop(2);break;case 6:d.moveLoop(-1);break;case 7:d.moveLoop(1);break;}}
    private void sync(int deck,boolean off){
        synced[deck]=!off;
        if(off){decks[deck].setRate(1);return;}
        synced[1-deck]=false;
        PerformanceDeck master=decks[1-deck];decks[deck].matchTempo(master.getEffectiveBpm());decks[deck].alignPhase(master.beatPhase());
        synced[deck]=Math.abs(decks[deck].getEffectiveBpm()-master.getEffectiveBpm())<0.01;
    }
    public void button(int deck,String control,boolean pressed){
        if("UNSUPPORTED_DECK".equals(control)){if(pressed)actions.unsupportedDeck();return;}
        if(!pressed)return;PerformanceDeck d=decks[deck];
        if("LOAD".equals(control))actions.load(deck);
        else if("LOOP".equals(control))d.toggleLoop();else if("HALF_LOOP".equals(control))d.resizeLoop(0.5);
        else if("DOUBLE_LOOP".equals(control))d.resizeLoop(2);else if("LOOP_IN".equals(control))d.loopIn();else if("LOOP_OUT".equals(control))d.loopOut();
        else if("QUANTIZE".equals(control))d.setQuantize(!d.isQuantized());else if("SYNC".equals(control))sync(deck,false);
        else if("SYNC_OFF".equals(control))sync(deck,true);else if("SILENT".equals(control))d.setSilent(!d.isSilent());
        else if("PARAM_LEFT".equals(control))d.resizeLoop(0.5);else if("PARAM_RIGHT".equals(control))d.resizeLoop(2);
        else if("MOVE_LEFT".equals(control))d.moveLoop(-1);else if("MOVE_RIGHT".equals(control))d.moveLoop(1);
        else if("RELEASE_ROLL".equals(control)){if(d.isRolling())d.exitLoop();rollPad[deck]=-1;}
        else if("RELEASE_ALL".equals(control)){d.releasePerformance();actions.cue(deck,false);rollPad[deck]=-1;java.util.Arrays.fill(fxPads[deck],false);}
        actions.changed();
    }
    public void browse(int delta){actions.browse(delta);}
    public void fx(int deck,String control,int slot,float value){
        PerformanceDeck d=decks[deck];if("SELECT".equals(control))d.fxSelect(slot);
        else if("TOUCH".equals(control))d.fxTouch(slot);else if("AMOUNT".equals(control))d.fxAmount(slot,value);else if("HOLD".equals(control))d.fxHold();actions.changed();
    }
    public void mode(int deck,int mode){
        if(decks[deck].isRolling())decks[deck].exitLoop();rollPad[deck]=-1;
        actions.cue(deck,false);
        for(int slot=0;slot<3;slot++){if(fxPads[deck][slot])decks[deck].fxGate(slot,false);fxPads[deck][slot]=false;}
        actions.changed();
    }
    public boolean padLit(int deck,int mode,int index){
        PerformanceDeck d=decks[deck];
        switch(mode){
            case 0:return d.hasCue(index);
            case 1:case 4:return index<8&&d.isLooping()&&Math.abs(d.getLoopBeats()-LOOP_BEATS[index])<0.01;
            case 2:return index<8?samples[index].isLoaded():samples[index-8].isPlaying();
            case 3:case 7:if(index==7)return d.isPlaying();if(index==5)return synced[deck];if(index==4)return d.isQuantized();return false;
            case 5:return index<3?d.isFxSelected(index):index==3&&d.isFxHold();
            case 6:return index<8?d.hasSavedLoop(index):index==10&&d.isLooping();
            default:return false;
        }
    }
}
