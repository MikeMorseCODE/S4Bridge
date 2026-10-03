import com.s4bridge.app.hardware.UsbMidiPackets;
import java.util.*;
public class UsbMidiPacketsTest {
    public static void main(String[] args){
        final List<String> events=new ArrayList<String>();
        UsbMidiPackets p=new UsbMidiPackets(new UsbMidiPackets.Listener(){public void message(int c,int s,int a,int b){events.add(c+":"+s+":"+a+":"+b);}});
        p.accept(new byte[]{0x29,(byte)0x91},2);
        p.accept(new byte[]{60,127,0x28,(byte)0x81,60,0,0,0,0,0},10);
        p.accept(new byte[]{9,(byte)0x80,60,1,12,(byte)0xc0,7,(byte)255},8);
        if(!events.equals(Arrays.asList("2:145:60:127","2:129:60:0","0:192:7:0")))throw new AssertionError(events);
        if(!Arrays.equals(UsbMidiPackets.encode(2,new byte[]{(byte)0x81,60,0}),new byte[]{0x28,(byte)0x81,60,0}))throw new AssertionError("note off");
        try{p.accept(new byte[0],1);throw new AssertionError("bounds");}catch(IllegalArgumentException expected){}
        try{UsbMidiPackets.encode(16,new byte[]{(byte)0x90,60,0});throw new AssertionError("cable");}catch(IllegalArgumentException expected){}
        System.out.println("UsbMidiPacketsTest passed");
    }
}
