import com.s4bridge.app.core.PcmClip;
import com.s4bridge.app.core.PerformanceDeck;
public final class WaveformTest {
    public static void main(String[] args){
        PcmClip clip=new PcmClip(new short[]{0,0,-32768,0,0,16384,0,0},8000);
        float[] peaks=clip.waveform(2);
        if(peaks.length!=2||peaks[0]!=1||peaks[1]!=.5f)throw new AssertionError("stereo peak or negative full scale");
        if(clip.waveform(8)[2]!=1)throw new AssertionError("short clip bins");
        try{clip.waveform(0);throw new AssertionError("invalid bins");}catch(IllegalArgumentException expected){}
        PerformanceDeck deck=new PerformanceDeck();deck.load(new PcmClip(new short[16000],8000));
        if(deck.getDurationMs()!=1000)throw new AssertionError("duration");
        deck.seekToMs(1200);if(deck.getPositionMs()!=1000)throw new AssertionError("end clamp");
        deck.seekToMs(-1);if(deck.getPositionMs()!=0)throw new AssertionError("start clamp");
        deck.load(null);if(deck.getDurationMs()!=0)throw new AssertionError("unload");
        System.out.println("WaveformTest passed");
    }
}
