package com.s4bridge.app.core;

/** Immutable interleaved stereo PCM. Ownership of the sample array transfers here. */
public final class PcmClip {
    final short[] samples;
    public final int sampleRate, frames;
    public PcmClip(short[] samples, int sampleRate) {
        if (samples == null || samples.length < 2 || (samples.length & 1) != 0 || sampleRate < 8000 || sampleRate > 192000)
            throw new IllegalArgumentException("Invalid stereo PCM");
        this.samples = samples; this.sampleRate = sampleRate; frames = samples.length / 2;
    }
    public int durationMs() { return (int)((long)frames * 1000 / sampleRate); }
    /** Peak envelope of both channels; computed once off the UI thread. */
    public float[] waveform(int bins) {
        if(bins<1||bins>8192)throw new IllegalArgumentException("Waveform bins 1–8192");
        float[] peaks=new float[bins];
        for(int frame=0;frame<frames;frame++){
            int bin=(int)((long)frame*bins/frames);
            int peak=Math.max(Math.abs((int)samples[frame*2]),Math.abs((int)samples[frame*2+1]));
            peaks[bin]=Math.max(peaks[bin],peak/32768f);
        }
        return peaks;
    }
    float sample(double frame, int channel) {
        int index = Math.max(0, Math.min(frames - 1, (int)frame));
        int next = Math.min(frames - 1, index + 1);
        float fraction = (float)(frame - Math.floor(frame));
        return (samples[index * 2 + channel] * (1 - fraction) + samples[next * 2 + channel] * fraction) / 32768f;
    }
}
