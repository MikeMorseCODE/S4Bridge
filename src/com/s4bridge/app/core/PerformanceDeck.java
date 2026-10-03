package com.s4bridge.app.core;

import java.util.Arrays;

/** Sample-position transport, rendered by one shared audio output. All state is synchronized. */
public final class PerformanceDeck implements Playback {
    private PcmClip clip;
    private double position, rate = 1, bpm = 120, gridOrigin, loopStart, loopEnd, slip;
    private boolean playing, looping, rolling, quantize, silent;
    private final double[] cues = new double[16], savedStart = new double[8], savedEnd = new double[8];
    private final PerformanceFx fx = new PerformanceFx();
    public PerformanceDeck() { clearMarkers(); }
    private void clearMarkers() { Arrays.fill(cues,-1); Arrays.fill(savedStart,-1); Arrays.fill(savedEnd,-1); }
    public synchronized void load(PcmClip value) {
        clip=value; position=gridOrigin=0; playing=looping=rolling=silent=false; clearMarkers(); fx.reset();
    }
    public synchronized boolean isLoaded() { return clip!=null; }
    public synchronized boolean isPlaying() { return playing; }
    public synchronized int getPositionMs() { return clip==null?0:(int)(position*1000/clip.sampleRate); }
    public synchronized int getDurationMs() { return clip==null?0:clip.durationMs(); }
    public synchronized double getFrame() { return position; }
    public synchronized void play() { if(clip!=null) { if(position>=clip.frames)position=0; playing=true; } }
    public synchronized void pause() { playing=false; }
    public synchronized void seekToMs(int ms) { if(clip!=null)seekFrame((double)Math.max(0,ms)*clip.sampleRate/1000); }
    private void seekFrame(double frame) {
        position=Math.max(0,Math.min(clip.frames,frame)); looping=rolling=false;
    }
    public synchronized void togglePlay() { if(playing)pause();else play(); }
    public synchronized void setBpm(double value) { if(Double.isNaN(value)||value<30||value>300)throw new IllegalArgumentException("BPM 30–300 required"); bpm=value; }
    public synchronized double getBpm() { return bpm; }
    public synchronized double getEffectiveBpm() { return bpm*rate; }
    public synchronized void setRate(double value) { if(Double.isNaN(value))return;rate=Math.max(0.25,Math.min(4,value)); }
    public synchronized double getRate() { return rate; }
    public synchronized void setQuantize(boolean value) { quantize=value; }
    public synchronized boolean isQuantized() { return quantize; }
    public synchronized void setSilent(boolean value) { silent=value; }
    public synchronized boolean isSilent() { return silent; }
    public synchronized void setGridOrigin() { gridOrigin=position; }
    public synchronized void moveGrid(double beats) { if(clip!=null)gridOrigin+=beatFrames()*beats; }
    private double beatFrames() { return clip.sampleRate*60.0/bpm; }
    private double anchor() { return quantize?Math.max(0,gridOrigin+Math.floor((position-gridOrigin)/beatFrames())*beatFrames()):position; }
    public synchronized boolean hasCue(int index) { return index>=0&&index<cues.length&&cues[index]>=0; }
    public synchronized void hotCue(int index, boolean clear) {
        if(clip==null||index<0||index>=cues.length)return;
        if(clear){cues[index]=-1;return;}
        if(cues[index]<0)cues[index]=Math.min(clip.frames-1,anchor());
        seekFrame(cues[index]); silent=false; playing=true;
    }
    public synchronized void jumpBeats(double beats) { if(clip!=null)seekFrame(position+beats*beatFrames()); }
    public synchronized void setLoop(double beats, boolean roll) {
        if(clip==null||beats<=0||Double.isNaN(beats))return;
        if(!rolling)slip=position;
        loopStart=Math.min(clip.frames-1,anchor());
        loopEnd=Math.min(clip.frames,loopStart+beats*beatFrames());
        if(loopEnd-loopStart<1)return;
        looping=true; rolling=roll; playing=true; position=loopStart;
    }
    public synchronized void toggleLoop() { if(looping)exitLoop();else setLoop(4,false); }
    public synchronized void exitLoop() {
        if(rolling&&clip!=null){position=Math.min(clip.frames,slip);if(position>=clip.frames)playing=false;}
        looping=rolling=false;
    }
    public synchronized boolean isLooping() { return looping; }
    public synchronized boolean isRolling() { return rolling; }
    public synchronized double getLoopBeats() { return clip==null?0:(loopEnd-loopStart)/beatFrames(); }
    public synchronized void resizeLoop(double factor) {
        if(!looping||factor<=0)return;
        double length=Math.max(1,Math.min(clip.frames-loopStart,(loopEnd-loopStart)*factor));
        loopEnd=loopStart+length;
        position=loopStart+positiveMod(position-loopStart,length);
    }
    public synchronized void moveLoop(double beats) {
        if(!looping)return;double delta=beats*beatFrames();
        delta=Math.max(-loopStart,Math.min(clip.frames-loopEnd,delta));
        loopStart+=delta;loopEnd+=delta;position+=delta;
    }
    public synchronized boolean hasSavedLoop(int slot) { return slot>=0&&slot<8&&savedStart[slot]>=0; }
    public synchronized void savedLoop(int slot, boolean clear) {
        if(clip==null||slot<0||slot>=8)return;
        if(clear){savedStart[slot]=savedEnd[slot]=-1;return;}
        if(savedStart[slot]<0){if(!looping)setLoop(4,false);savedStart[slot]=loopStart;savedEnd[slot]=loopEnd;}
        else {loopStart=savedStart[slot];loopEnd=savedEnd[slot];position=loopStart;looping=playing=true;rolling=false;}
    }
    public synchronized void loopIn() { if(clip!=null){loopStart=anchor();looping=rolling=false;} }
    public synchronized void loopOut() {
        if(clip!=null&&position>loopStart+1){loopEnd=position;position=loopStart;looping=playing=true;rolling=false;}
    }
    public synchronized void matchTempo(double effectiveBpm) { setRate(effectiveBpm/bpm); }
    /** Align fractional beat phase to another deck. BPM/grid remain manually supplied. */
    public synchronized double beatPhase() { return clip==null?0:positiveMod((position-gridOrigin)/beatFrames(),1); }
    public synchronized void alignPhase(double phase) {
        if(clip!=null){double beat=Math.floor((position-gridOrigin)/beatFrames());seekFrame(gridOrigin+(beat+phase)*beatFrames());}
    }
    public synchronized void fxSelect(int slot) { fx.select(slot); }
    public synchronized void fxTouch(int slot) { fx.touchPulse(slot); }
    public synchronized void fxGate(int slot,boolean value) { fx.gate(slot,value); }
    public synchronized void fxAmount(int slot,float value) { fx.amount(slot,value); }
    public synchronized void fxHold() { fx.toggleHold(); }
    public synchronized boolean isFxSelected(int slot) { return fx.selected(slot); }
    public synchronized boolean isFxHold() { return fx.isHeld(); }
    public synchronized void releasePerformance() { exitLoop();silent=false;fx.release(); }
    /** Adds stereo samples; fractional playback supports rate changes and sample-rate conversion. */
    public synchronized void render(float[] out,int frames,int outputRate,float gain) {
        if(clip==null)return;
        fx.prepare(outputRate);
        double step=clip.sampleRate*rate/outputRate;
        for(int i=0;i<frames;i++){
            float left=0,right=0;
            if(playing){
                if(looping&&position>=loopEnd)position=loopStart+positiveMod(position-loopStart,loopEnd-loopStart);
                if(position>=clip.frames){playing=false;position=clip.frames;}
                else {left=clip.sample(position,0);right=clip.sample(position,1);position+=step;if(rolling)slip+=step;}
            }
            fx.process(left,right,bpm*rate);
            if(!silent){out[i*2]+=fx.left()*gain;out[i*2+1]+=fx.right()*gain;}
        }
    }
    private static double positiveMod(double value,double divisor) { return ((value%divisor)+divisor)%divisor; }
}
