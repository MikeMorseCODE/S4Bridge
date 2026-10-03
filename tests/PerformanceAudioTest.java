import com.s4bridge.app.core.*;
import com.s4bridge.app.hardware.Xp2MidiMapping;
import com.s4bridge.app.hardware.Xp2LedFeedback;
import java.util.ArrayList;

public final class PerformanceAudioTest {
    public static void main(String[] args){
        renderAndResample();hotCuesAndSilentCue();loopBoundariesAndSlip();savedLoopsAndPhase();effectsAreAudible();factoryMapping();layerRouting();ledFeedback();
        System.out.println("Performance audio, FX, factory mapping and layer tests passed");
    }
    private static PcmClip ramp(int frames){short[] samples=new short[frames*2];for(int i=0;i<frames;i++){samples[i*2]=(short)(i%1000);samples[i*2+1]=(short)(-(i%1000));}return new PcmClip(samples,8000);}
    private static PcmClip constant(int frames){short[] samples=new short[frames*2];java.util.Arrays.fill(samples,(short)16384);return new PcmClip(samples,8000);}
    private static void renderAndResample(){
        PerformanceDeck d=new PerformanceDeck();d.load(ramp(100));d.play();float[] out=new float[20];d.render(out,10,8000,1);
        for(int i=0;i<10;i++){near(out[i*2],i/32768.0,"left PCM");near(out[i*2+1],-i/32768.0,"right PCM");}
        near(d.getFrame(),10,"position per sample");
        d.seekToMs(0);d.setRate(2);java.util.Arrays.fill(out,0);d.render(out,10,8000,1);near(d.getFrame(),20,"rate affects playback");
        d.seekToMs(0);d.setRate(1);d.render(new float[20],10,16000,1);near(d.getFrame(),5,"sample-rate conversion");
        d.render(new float[400],200,8000,1);check(!d.isPlaying(),"end stops playback");d.play();near(d.getFrame(),0,"play restarts ended track");
        d.load(null);check(!d.isLoaded()&&!d.isPlaying(),"unload stops");
    }
    private static void hotCuesAndSilentCue(){
        PerformanceDeck d=new PerformanceDeck();d.load(ramp(8000));d.seekToMs(250);d.hotCue(3,false);check(d.hasCue(3)&&d.isPlaying(),"empty pad creates cue and plays");
        d.seekToMs(500);d.setSilent(true);d.hotCue(3,false);near(d.getFrame(),2000,"cue jumps to stored frame");check(!d.isSilent(),"hotcue releases silent cue");
        d.setSilent(true);float[] out=new float[20];d.render(out,10,8000,1);for(float value:out)near(value,0,"silent cue mutes audio");near(d.getFrame(),2010,"silent cue keeps transport moving");
        d.hotCue(3,true);check(!d.hasCue(3),"shift clears cue");d.load(ramp(8000));check(!d.hasCue(3),"load clears track markers");
        d.setBpm(120);d.setQuantize(true);d.seekToMs(620);d.hotCue(0,false);near(d.getFrame(),4000,"cue quantizes to supplied beat grid");
    }
    private static void loopBoundariesAndSlip(){
        PerformanceDeck d=new PerformanceDeck();d.load(ramp(8000));d.setBpm(120);d.seekToMs(100);d.setLoop(0.125,false);
        float[] out=new float[2400];d.render(out,1200,8000,1);near(d.getFrame(),1000,"multiple loop wraps remain inside loop");check(d.isLooping(),"auto loop active");
        d.resizeLoop(0.5);near(d.getLoopBeats(),0.0625,"loop halves");d.moveLoop(0.5);near(d.getFrame(),3000,"loop movement keeps relative position");d.exitLoop();check(!d.isLooping(),"loop exits");
        d.seekToMs(100);d.setLoop(0.125,true);d.render(new float[2400],1200,8000,1);near(d.getFrame(),1000,"roll wraps");d.exitLoop();near(d.getFrame(),2000,"roll returns to moving slip timeline");
        d.seekToMs(950);d.setLoop(4,true);d.render(new float[1600],800,8000,1);d.exitLoop();check(!d.isPlaying(),"slip beyond end stops");
    }
    private static void savedLoopsAndPhase(){
        PerformanceDeck d=new PerformanceDeck();d.load(ramp(16000));d.setBpm(120);d.seekToMs(250);d.setLoop(1,false);d.savedLoop(0,false);check(d.hasSavedLoop(0),"loop saved");
        d.exitLoop();d.seekToMs(1000);d.savedLoop(0,false);near(d.getFrame(),2000,"saved loop recalls start");d.savedLoop(0,true);check(!d.hasSavedLoop(0),"saved loop cleared");
        d.exitLoop();d.seekToMs(875);near(d.beatPhase(),0.75,"fractional beat phase");d.alignPhase(0.25);near(d.getFrame(),5000,"sync aligns fractional phase");d.matchTempo(150);near(d.getEffectiveBpm(),150,"tempo sync uses manual source BPM");
    }
    private static void effectsAreAudible(){
        PerformanceDeck dry=new PerformanceDeck();dry.load(constant(8000));dry.play();float[] plain=new float[2000];dry.render(plain,1000,8000,1);
        PerformanceDeck wet=new PerformanceDeck();wet.load(constant(8000));wet.play();wet.fxSelect(0);wet.fxAmount(0,1);wet.fxTouch(0);float[] filtered=new float[2000];wet.render(filtered,1000,8000,1);check(filtered[0]<plain[0],"filter changes audio");
        wet.fxTouch(0);float[] released=new float[2];wet.render(released,1,8000,1);near(released[0],0.5,"second strip pulse releases FX");
        wet.fxSelect(2);wet.fxAmount(2,1);wet.fxTouch(2);float[] tremolo=new float[2000];wet.render(tremolo,1000,8000,1);float minimum=1;for(float value:tremolo)minimum=Math.min(minimum,value);check(minimum<0.4,"tremolo changes gain");
        wet.releasePerformance();float[] reset=new float[2];wet.render(reset,1,8000,1);near(reset[0],0.5,"disconnect releases FX gates");
        PerformanceDeck echo=new PerformanceDeck();echo.load(constant(8000));echo.play();echo.fxSelect(1);echo.fxAmount(1,1);echo.fxTouch(1);float[] delay=new float[4200];echo.render(delay,2100,8000,1);near(delay[0],0,"echo starts with empty delay");check(delay[4000]>0.4,"echo outputs delayed audio");
        for(float value:delay)check(!Float.isNaN(value)&&Math.abs(value)<2,"effect finite and bounded");
    }
    private static void factoryMapping(){
        final ArrayList<String> events=new ArrayList<String>();
        Xp2MidiMapping mapping=new Xp2MidiMapping(new Xp2MidiMapping.Listener(){
            public void pad(int d,int m,int i,boolean s,boolean p){events.add("pad:"+d+":"+m+":"+i+":"+s+":"+p);}
            public void button(int d,String c,boolean p){events.add(c+":"+d+":"+p);}
            public void browse(int delta){events.add("browse:"+delta);}
            public void fx(int d,String c,int slot,float value){events.add("fx:"+d+":"+c+":"+slot+":"+value);}
            public void mode(int d,int m){events.add("mode:"+d+":"+m);}
        });
        for(int deck=0;deck<2;deck++)for(int shift=0;shift<2;shift++)for(int mode=0;mode<8;mode++)for(int pad=0;pad<16;pad++){
            events.clear();int status=0x90|7+deck*2+shift,note=Xp2MidiMapping.padNote(mode,pad);
            mapping.onMessage(status,note,127);mapping.onMessage(status,note,100);mapping.onMessage(status,note,0);
            check(events.contains("pad:"+deck+":"+mode+":"+pad+":"+(shift==1)+":true"),"factory pad/channel decode");
            check(events.contains("pad:"+deck+":"+mode+":"+pad+":"+(shift==1)+":false"),"factory pad release");
            int presses=0;for(String e:events)if(e.startsWith("pad:")&&e.endsWith(":true"))presses++;check(presses==1,"held notes deduplicate");
        }
        events.clear();mapping.onMessage(0xb6,64,1);mapping.onMessage(0xb6,64,127);mapping.onMessage(0xb6,100,126);check(events.toString().equals("[browse:1, browse:-1, browse:-2]"),"signed relative browser");
        events.clear();mapping.onMessage(0xb4,2,127);mapping.onMessage(0xb4,34,127);check(events.get(1).equals("fx:0:AMOUNT:0:1.0"),"14bit strip halves retained");
        events.clear();mapping.onMessage(0x90,88,127);mapping.onMessage(0x90,88,0);check(events.contains("SYNC:0:true"),"verified sync note");
        events.clear();mapping.onMessage(0x96,72,127);check(events.contains("UNSUPPORTED_DECK:2:true"),"C/D not aliased to A/B");
        events.clear();mapping.onMessage(0x90,105,127);check(events.contains("mode:0:4"),"shifted pad mode selector");mapping.reset();
    }
    private static void layerRouting(){
        final PerformanceDeck a=new PerformanceDeck(),b=new PerformanceDeck();a.load(ramp(16000));b.load(ramp(16000));
        PerformanceDeck[] samples=new PerformanceDeck[8];for(int i=0;i<8;i++)samples[i]=new PerformanceDeck();samples[0].load(constant(100));
        final ArrayList<String> actions=new ArrayList<String>();
        Xp2PerformanceController c=new Xp2PerformanceController(a,b,samples,new Xp2PerformanceController.Actions(){
            public void load(int d){actions.add("load:"+d);}public void browse(int delta){actions.add("browse:"+delta);}
            public void cue(int d,boolean pressed){actions.add("cue:"+d+":"+pressed);}public void loadSample(int s){actions.add("sample:"+s);}
            public void changed(){}public void unsupportedDeck(){actions.add("unsupported");}
        });
        c.pad(0,0,2,false,true);check(a.hasCue(2)&&c.padLit(0,0,2),"hotcue LED follows state");c.pad(0,0,2,true,true);check(!a.hasCue(2),"shift deletes cue");
        c.pad(0,4,0,false,true);c.pad(0,4,1,false,true);c.pad(0,4,0,false,false);check(a.isRolling(),"old roll release cannot stop new roll");c.pad(0,4,1,false,false);check(!a.isRolling(),"active roll release exits");
        c.pad(0,2,0,false,true);check(samples[0].isPlaying(),"sample trigger plays");c.pad(1,2,8,false,true);check(!samples[0].isPlaying(),"shared sampler stop");c.pad(0,2,1,true,true);check(actions.contains("sample:1"),"shift loads selected library sample");
        c.pad(0,3,6,false,true);c.pad(0,3,6,false,false);check(actions.contains("cue:0:true")&&actions.contains("cue:0:false"),"transport cue both edges");
        b.setBpm(150);c.button(0,"SYNC",true);near(a.getEffectiveBpm(),150,"sync matches peer tempo");b.setRate(1.1);c.updateSync();near(a.getEffectiveBpm(),165,"sync follows master rate changes");c.button(1,"SYNC",true);check(!c.isSynced(0)&&c.isSynced(1),"sync prevents follower cycles");
        c.mode(0,5);c.pad(0,5,0,false,true);c.mode(0,0);float[] out=new float[2];a.render(out,1,8000,1);check(!a.isRolling(),"mode change releases performance gestures");
        c.button(0,"RELEASE_ALL",true);check(!a.isFxHold(),"disconnect clears holds");
    }
    private static void ledFeedback(){
        final ArrayList<String> sent=new ArrayList<String>();final boolean[] available={true};
        Xp2LedFeedback leds=new Xp2LedFeedback(new Xp2LedFeedback.Output(){public boolean send(byte[] bytes){if(!available[0])return false;sent.add((bytes[0]&255)+":"+(bytes[1]&255)+":"+(bytes[2]&255));return true;}});
        leds.set(7,12,true);leds.set(7,12,true);leds.set(7,12,false);
        check(sent.toString().equals("[151:12:127, 135:12:0]"),"documented LED note on/off and changed-state output");
        available[0]=false;leds.set(9,28,true);available[0]=true;leds.set(9,28,true);check(sent.size()==3,"failed sends retry");
        leds.invalidate();leds.set(9,28,true);check(sent.size()==4,"reconnect forces state replay");
        boolean rejected=false;try{leds.set(16,0,true);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"invalid LED channel rejected");
    }
    private static void near(double actual,double expected,String message){check(Math.abs(actual-expected)<0.001,message+" got "+actual+" expected "+expected);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
