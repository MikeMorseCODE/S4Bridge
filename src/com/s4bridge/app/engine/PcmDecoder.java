package com.s4bridge.app.engine;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import com.s4bridge.app.core.PcmClip;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/** Bounded whole-track decoding, executed off the UI/audio threads. */
public final class PcmDecoder {
    private PcmDecoder() {}
    public static PcmClip decode(Context context,Uri uri,int byteLimit,boolean truncate) throws IOException {
        MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
        ArrayList<short[]> chunks=new ArrayList<short[]>();int total=0,rate=0,channels=0,encoding=AudioFormat.ENCODING_PCM_16BIT;
        try {
            extractor.setDataSource(context,uri,null);
            MediaFormat source=null;
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat f=extractor.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("audio/")){source=f;extractor.selectTrack(i);break;}
            }
            if(source==null)throw new IOException("No audio track");
            rate=source.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=source.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            if(truncate)byteLimit=Math.min(byteLimit,rate*4*15);
            codec=MediaCodec.createDecoderByType(source.getString(MediaFormat.KEY_MIME));
            codec.configure(source,null,null,0);codec.start();
            boolean inputEnded=false,outputEnded=false;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long lastProgress=android.os.SystemClock.elapsedRealtime();
            while(!outputEnded){
                if(Thread.currentThread().isInterrupted())throw new IOException("Decode cancelled");
                if(!inputEnded){
                    int in=codec.dequeueInputBuffer(10000);
                    if(in>=0){
                        ByteBuffer buffer=codec.getInputBuffer(in);int count=extractor.readSampleData(buffer,0);
                        if(count<0){codec.queueInputBuffer(in,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true;}
                        else {codec.queueInputBuffer(in,0,count,extractor.getSampleTime(),0);extractor.advance();}
                    }
                }
                int out=codec.dequeueOutputBuffer(info,10000);
                if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    MediaFormat f=codec.getOutputFormat();int newRate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE),newChannels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if(total>0&&(newRate!=rate||newChannels!=channels))throw new IOException("Audio format changed mid-track");
                    rate=newRate;channels=newChannels;
                    encoding=f.containsKey(MediaFormat.KEY_PCM_ENCODING)?f.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
                } else if(out>=0){
                    lastProgress=android.os.SystemClock.elapsedRealtime();
                    if(channels<1||channels>2)throw new IOException("Only mono/stereo audio supported");
                    if(encoding!=AudioFormat.ENCODING_PCM_16BIT&&encoding!=AudioFormat.ENCODING_PCM_FLOAT)throw new IOException("Unsupported PCM encoding");
                    if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
                        ByteBuffer buffer=codec.getOutputBuffer(out).duplicate().order(ByteOrder.LITTLE_ENDIAN);
                        buffer.position(info.offset);buffer.limit(info.offset+info.size);
                        int bytesPerSample=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;
                        int frames=info.size/(channels*bytesPerSample);
                        int capacity=(byteLimit/2-total)/2;
                        if(frames>capacity&&!truncate)throw new IOException("Track exceeds performance audio memory limit; use a shorter track or MediaPlayer mode");
                        frames=Math.min(frames,capacity);
                        short[] stereo=new short[frames*2];
                        for(int i=0;i<frames;i++){
                            short left=read(buffer,encoding),right=channels==2?read(buffer,encoding):left;
                            stereo[i*2]=left;stereo[i*2+1]=right;
                        }
                        chunks.add(stereo);total+=stereo.length;
                        if(truncate&&frames==capacity)outputEnded=true;
                    }
                    outputEnded|=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    codec.releaseOutputBuffer(out,false);
                }
                if(android.os.SystemClock.elapsedRealtime()-lastProgress>15000)throw new IOException("Audio decoder stalled");
            }
            if(total<2)throw new IOException("Empty audio track");
            short[] samples=new short[total];int offset=0;
            for(short[] chunk:chunks){System.arraycopy(chunk,0,samples,offset,chunk.length);offset+=chunk.length;}
            return new PcmClip(samples,rate);
        } catch(RuntimeException e){throw new IOException("Audio decode failed: "+e.getMessage(),e);}
        finally {if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}extractor.release();}
    }
    private static short read(ByteBuffer buffer,int encoding){
        if(encoding==AudioFormat.ENCODING_PCM_16BIT)return buffer.getShort();
        float value=buffer.getFloat();if(Float.isNaN(value))value=0;
        return (short)Math.max(-32768,Math.min(32767,Math.round(value*32767)));
    }
}
