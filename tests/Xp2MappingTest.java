import com.s4bridge.app.core.Xp2Mapping;
import java.util.ArrayList;
public final class Xp2MappingTest {
    public static void main(String[] args){
        final ArrayList<String> events=new ArrayList<String>();
        Xp2Mapping mapping=new Xp2Mapping(new Xp2Mapping.Listener(){public void onAction(String a,int d,int s,boolean p){events.add(a+":"+d+":"+s+":"+p);}});
        mapping.receive(new byte[]{(byte)0x99,43},0,2);
        if(!events.isEmpty())throw new AssertionError("partial message");
        mapping.receive(new byte[]{(byte)0xf8,127,43,0},0,4);
        if(events.size()!=2||!events.get(0).equals("cue_point:1:3:true")||!events.get(1).equals("cue_point:1:3:false"))throw new AssertionError(events.toString());
        events.clear();mapping.receive(new byte[]{(byte)0x90,21,127,(byte)0x80,21,0},0,6);
        if(!events.contains("load_track:0:0:true")||events.contains("load_track:0:0:false"))throw new AssertionError(events.toString());
        System.out.println("Xp2MappingTest passed");
    }
}
