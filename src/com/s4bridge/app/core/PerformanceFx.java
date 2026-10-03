package com.s4bridge.app.core;

import java.util.Arrays;

/** Three bounded effects: low-pass filter, feedback echo, beat tremolo. */
public final class PerformanceFx {
    private final boolean[] selected=new boolean[3],touched=new boolean[3];
    private final float[] amounts={0.5f,0.5f,0.5f};
    private boolean hold;
    private int sampleRate=48000,write;
    private float[] delayL=new float[96000],delayR=new float[96000];
    private float lowL,lowR,left,right,filterAlpha;
    public PerformanceFx(){updateFilter();}
    private void updateFilter(){float cutoff=Math.min(sampleRate*0.45f,20000f*(float)Math.pow(0.015,amounts[0]));filterAlpha=1-(float)Math.exp(-2*Math.PI*cutoff/sampleRate);}
    private double phase;
    public void prepare(int rate) {
        if(rate!=sampleRate){sampleRate=rate;delayL=new float[rate*2];delayR=new float[rate*2];write=0;updateFilter();}
    }
    public void reset() { Arrays.fill(delayL,0);Arrays.fill(delayR,0);lowL=lowR=0;phase=0;write=0;release(); }
    public void release() { Arrays.fill(selected,false);Arrays.fill(touched,false);hold=false; }
    public void select(int slot) { if(slot>=0&&slot<3){selected[slot]=!selected[slot];if(!selected[slot])touched[slot]=false;} }
    // Pioneer touch/release each emit a Note On + Note Off pulse. Ignore the immediate Off.
    public void touchPulse(int slot) { if(slot>=0&&slot<3)touched[slot]=hold||!touched[slot]; }
    public void gate(int slot,boolean value) { if(slot>=0&&slot<3)touched[slot]=value; }
    public void amount(int slot,float value) { if(slot>=0&&slot<3&&!Float.isNaN(value)){amounts[slot]=Math.max(0,Math.min(1,value));if(slot==0)updateFilter();} }
    public void toggleHold() { hold=!hold;if(!hold)Arrays.fill(touched,false); }
    public boolean selected(int slot) { return selected[slot]; }
    public boolean isHeld() { return hold; }
    public void process(float l,float r,double bpm) {
        lowL+=filterAlpha*(l-lowL);lowR+=filterAlpha*(r-lowR);
        if(selected[0]&&touched[0]){l=lowL;r=lowR;}
        int distance=Math.max(1,Math.min(delayL.length-1,(int)(sampleRate*30.0/bpm)));
        int read=(write-distance+delayL.length)%delayL.length;
        float echoL=delayL[read],echoR=delayR[read];
        boolean echo=selected[1]&&touched[1];
        delayL[write]=echo?l+echoL*0.45f:0;delayR[write]=echo?r+echoR*0.45f:0;
        write=(write+1)%delayL.length;
        if(echo){l=l*(1-amounts[1])+echoL*amounts[1];r=r*(1-amounts[1])+echoR*amounts[1];}
        phase+=bpm*2/(60*sampleRate);phase-=Math.floor(phase);
        if(selected[2]&&touched[2]){float level=1-amounts[2]*(0.5f-0.5f*(float)Math.cos(2*Math.PI*phase));l*=level;r*=level;}
        left=l;right=r;
    }
    public float left() { return left; }
    public float right() { return right; }
}
