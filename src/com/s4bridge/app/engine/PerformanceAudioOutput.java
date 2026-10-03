package com.s4bridge.app.engine;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;
import com.s4bridge.app.core.PerformanceDeck;
import java.util.Arrays;

/** Single stereo AudioTrack mixes both decks and eight shared sampler slots. */
public final class PerformanceAudioOutput {
    public interface Listener { void onAudioError(String message); }
    private final DeckEngine a,b;
    private final PerformanceDeck[] samples;
    private final Listener listener;
    private Thread thread;
    private volatile boolean running;
    private volatile AudioTrack track;
    public PerformanceAudioOutput(DeckEngine a,DeckEngine b,PerformanceDeck[] samples,Listener listener){this.a=a;this.b=b;this.samples=samples;this.listener=listener;}
    public synchronized void start(){
        if(running)return;
        if(thread!=null&&thread.isAlive()){listener.onAudioError("Output is still stopping; retry loading shortly");return;}
        int rate=48000;int minimum=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
        try{
            if(minimum<=0)throw new IllegalStateException("48 kHz output unavailable");
            final AudioTrack active=new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(minimum,4096)).build();
            if(active.getState()!=AudioTrack.STATE_INITIALIZED){active.release();throw new IllegalStateException("AudioTrack initialization failed");}
            track=active;running=true;active.play();
            thread=new Thread(new Runnable(){public void run(){render(active);}},"S4Bridge-PCM");thread.start();
        }catch(RuntimeException e){running=false;if(track!=null){track.release();track=null;}listener.onAudioError(e.getMessage());}
    }
    private void render(AudioTrack active){
        float[] mix=new float[512];short[] pcm=new short[512];
        try{
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            while(running){
                Arrays.fill(mix,0);
                a.getPerformance().render(mix,256,48000,a.getChannelVolume()*a.getCrossfaderGain());
                b.getPerformance().render(mix,256,48000,b.getChannelVolume()*b.getCrossfaderGain());
                for(PerformanceDeck sample:samples)sample.render(mix,256,48000,0.5f);
                for(int i=0;i<pcm.length;i++)pcm[i]=(short)Math.max(-32768,Math.min(32767,Math.round(mix[i]*32767)));
                int offset=0;
                while(running&&offset<pcm.length){int wrote=active.write(pcm,offset,pcm.length-offset,AudioTrack.WRITE_BLOCKING);if(wrote<=0)throw new IllegalStateException("AudioTrack write error "+wrote);offset+=wrote;}
            }
        }catch(RuntimeException e){if(running)listener.onAudioError(e.getMessage());}
        finally{running=false;try{active.pause();active.flush();}catch(Exception ignored){}active.release();}
    }
    public synchronized void stop(){
        running=false;
        if(track!=null){try{track.pause();track.flush();}catch(Exception ignored){}}
        Thread old=thread;if(old!=null){old.interrupt();try{old.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
        // Do not release a track still referenced by a rendering thread.
        if(old==null){if(track!=null)track.release();track=null;}
        else if(!old.isAlive()){track=null;thread=null;}
    }
    public synchronized boolean isRunning(){return running;}
}
