package com.s4bridge.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.EditText;
import android.text.InputType;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.s4bridge.app.engine.DeckEngine;
import com.s4bridge.app.engine.MixerEngine;
import com.s4bridge.app.core.CueController;
import com.s4bridge.app.core.ControllerRouter;
import com.s4bridge.app.core.TrackLibrary;
import com.s4bridge.app.hardware.S4Mk2Mapping;
import com.s4bridge.app.hardware.Xp2MidiDevice;
import com.s4bridge.app.core.MidiLearnRouter;
import com.s4bridge.app.core.PcmClip;
import com.s4bridge.app.core.PerformanceDeck;
import com.s4bridge.app.core.Xp2PerformanceController;
import com.s4bridge.app.hardware.Xp2MidiMapping;
import com.s4bridge.app.engine.PcmDecoder;
import com.s4bridge.app.engine.PerformanceAudioOutput;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int VID=0x17CC, PID=0x1310, PICK_TRACKS=1001;
    private static final String ACTION_USB_PERMISSION="com.s4bridge.app.USB_PERMISSION";
    private static final String LIBRARY_PREFS="track_library";

    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbInterface hidInterface;
    private UsbEndpoint inEndpoint;
    private volatile boolean captureRunning;
    private Thread captureThread;
    private byte[] old01, old02;
    private TextView status, logView, captureSummary;
    private ScrollView logScroll;
    private LinearLayout content, bottomNav, captureEvents;
    private int currentScreen, eventCount, currentReportId, currentReportSize;
    private long captureStartedAt, lastContinuousUi;
    private String captureFilter="All";
    private final ArrayList<CaptureEvent> recentEvents=new ArrayList<CaptureEvent>();
    private static final int BG=Color.rgb(5,13,21), CARD=Color.rgb(12,25,36), BORDER=Color.rgb(39,61,78);
    private static final int TEXT=Color.rgb(235,243,251), MUTED=Color.rgb(150,170,190), BLUE=Color.rgb(35,132,255);
    private static final int GREEN=Color.rgb(25,224,139), CYAN=Color.rgb(18,205,221), ORANGE=Color.rgb(255,154,26), RED=Color.rgb(255,67,91);

    private static final class CaptureEvent{
        final long time; final String name,value,type; final int report,size;
        CaptureEvent(long t,String n,String v,String ty,int r,int s){time=t;name=n;value=v;type=ty;report=r;size=s;}
    }
    private final DeckEngine deckA=new DeckEngine("A"), deckB=new DeckEngine("B");
    private final MixerEngine mixer=new MixerEngine(deckA,deckB);
    private final CueController cueA=new CueController(deckA), cueB=new CueController(deckB);
    private final TrackLibrary library=new TrackLibrary();
    private final ControllerRouter controllerRouter=new ControllerRouter(new ControllerRouter.Actions(){
        public void togglePlay(boolean isDeckA){DeckEngine deck=isDeckA?deckA:deckB;deck.togglePlay();log("ENGINE "+deck.getName()+" playing="+deck.isPlaying());}
        public void cue(boolean isDeckA,boolean pressed){queueCue(isDeckA?cueA:cueB,pressed);}
        public void load(boolean isDeckA){queueLoad(isDeckA?deckA:deckB,isDeckA?cueA:cueB);}
        public void browse(final int delta){runOnUiThread(new Runnable(){public void run(){moveBrowser(delta);}});}
        public void jog(boolean isDeckA,int delta){if(isDeckA)deckA.jog(delta);else deckB.jog(delta);}
    });
    private S4Mk2Mapping mapping;
    private final MidiLearnRouter xp2Router=new MidiLearnRouter(controllerRouter);
    private Xp2MidiDevice xp2;
    private TextView xp2StatusView, xp2MonitorView, xp2LearnView;
    private String xp2Status="XP2 starting";
    private final PerformanceDeck[] samples=createSamples();
    private final ThreadPoolExecutor decoder=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<Runnable>(10));
    private final Future<?>[] loads=new Future<?>[10];
    private final int[] loadGeneration=new int[10];
    private boolean destroyed;
    private long lastMidiMonitor;
    private final Handler performanceHandler=new Handler(Looper.getMainLooper());
    private PerformanceAudioOutput audioOutput;
    private Xp2PerformanceController performanceController;
    private Xp2MidiMapping xp2Mapping;
    private final Runnable performanceTick=new Runnable(){public void run(){
        if(destroyed)return;
        if(performanceController!=null&&deckA.isPerformanceEnabled())performanceController.updateSync();
        refreshXp2Leds(); if(currentScreen==3)updateXp2Monitor();
        performanceHandler.postDelayed(this,100);
    }};
    private static PerformanceDeck[] createSamples(){PerformanceDeck[] result=new PerformanceDeck[8];for(int i=0;i<8;i++)result[i]=new PerformanceDeck();return result;}

    private final ArrayList<String> xp2Messages=new ArrayList<String>();
    private static final String[] XP2_ACTIONS={"DECK_A_PLAY","DECK_A_CUE","DECK_A_LOAD","DECK_B_PLAY","DECK_B_CUE","DECK_B_LOAD"};


    private final BroadcastReceiver usbReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            String action=i.getAction();
            UsbDevice d;
            if(Build.VERSION.SDK_INT>=33) d=i.getParcelableExtra(UsbManager.EXTRA_DEVICE,UsbDevice.class);
            else d=i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)){
                if(isS4(d)){log("S4 MK2 disconnected");stopCapture();closeConnection();device=null;updateStatus();}
                return;
            }
            if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)){
                if(isS4(d)){device=d;log("S4 MK2 connected");if(getSharedPreferences("ui_settings",MODE_PRIVATE).getBoolean("auto_reconnect",true)&&usbManager.hasPermission(d))openAndStart();else if(getSharedPreferences("ui_settings",MODE_PRIVATE).getBoolean("auto_permission",true))requestUsbPermission();updateStatus();}
                return;
            }
            if(!ACTION_USB_PERMISSION.equals(action))return;
            boolean granted=i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false);
            if(granted&&isS4(d)){device=d;log("USB permission granted");openAndStart();updateStatus();}
            else log("USB permission denied");
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        usbManager=(UsbManager)getSystemService(Context.USB_SERVICE);
        if(getSharedPreferences("ui_settings",MODE_PRIVATE).getBoolean("keep_screen",false))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi(); restoreLibrary(); registerUsb(); createMapping(); scan(); setupPerformance(); startXp2(); updateStatus();
    }

    private void buildUi(){
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        bottomNav=new LinearLayout(this); bottomNav.setOrientation(LinearLayout.HORIZONTAL); bottomNav.setPadding(dp(4),dp(5),dp(4),dp(5)); bottomNav.setBackgroundColor(CARD);
        root.addView(bottomNav,new LinearLayout.LayoutParams(-1,dp(64))); setContentView(root); showScreen(0);
    }

    private void showScreen(int screen){
        currentScreen=screen; xp2StatusView=null; xp2MonitorView=null; xp2LearnView=null; status=null; captureSummary=null; captureEvents=null; content.removeAllViews(); content.setPadding(dp(14),dp(12),dp(14),dp(16));
        if(screen==0)buildHome(); else if(screen==1)buildCapture(); else if(screen==2)buildMapping(); else if(screen==3)buildMidi(); else buildSettings();
        buildBottomNav(); updateStatus();
    }

    private void buildBottomNav(){
        bottomNav.removeAllViews(); String[] labels={"⌂\nHome","⌁\nCapture","▦\nMapping","▤\nMIDI","⚙\nSettings"};
        for(int i=0;i<labels.length;i++){final int index=i; TextView item=text(labels[i],12,i==currentScreen?BLUE:MUTED); item.setGravity(Gravity.CENTER); item.setPadding(0,dp(5),0,dp(3)); item.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showScreen(index);}}); bottomNav.addView(item,new LinearLayout.LayoutParams(0,-1,1f));}
    }

    private void buildHeader(String title){
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); TextView word=text(title,26,TEXT); word.setTypeface(Typeface.DEFAULT,Typeface.BOLD); row.addView(word,new LinearLayout.LayoutParams(0,dp(54),1f));
        if(!"S4Bridge".equals(title)){TextView brand=text("S4Bridge",13,BLUE); brand.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); row.addView(brand,new LinearLayout.LayoutParams(dp(100),dp(54)));} content.addView(row);
    }

    private void buildHome(){
        buildHeader("S4Bridge");
        LinearLayout connection=card(); status=text("",16,TEXT); status.setTypeface(Typeface.DEFAULT,Typeface.BOLD); connection.addView(status); TextView usb=text("USB controller status · tap to rescan",12,MUTED); connection.addView(usb); connection.setOnClickListener(new View.OnClickListener(){public void onClick(View v){scan();}}); content.addView(connection,lpCard());
        TextView controller=text("  DECK A          MIXER          DECK B  \n\n       ◉       ▥ ▥ ▥       ◉       \n\n PLAY  CUE       ║       CUE  PLAY",16,CYAN); controller.setGravity(Gravity.CENTER); controller.setTypeface(Typeface.MONOSPACE); controller.setBackground(shape(CARD,BORDER,12)); controller.setPadding(dp(8),dp(22),dp(8),dp(22)); content.addView(controller,new LinearLayout.LayoutParams(-1,dp(170)));
        LinearLayout facts=card(); facts.addView(text("VID:PID  17cc:1310                         HID mode",13,MUTED)); facts.addView(text("● S4 MK2                         Interface ID 4 · IN 0x84",12,GREEN)); content.addView(facts,lpCard());
        addAction(content,captureRunning?"■  Stop Capture":"⌁  Start Capture",captureRunning?RED:BLUE,new View.OnClickListener(){public void onClick(View v){if(captureRunning)stopCapture();else openAndStart();showScreen(0);}});
        LinearLayout actions=row(); addSmallAction(actions,"＋ Add tracks",new View.OnClickListener(){public void onClick(View v){pickTracks();}}); addSmallAction(actions,"↻ Reconnect",new View.OnClickListener(){public void onClick(View v){openAndStart();}}); content.addView(actions,lpCard());
        LinearLayout libraryCard=card(); libraryCard.addView(text("TRACK LIBRARY",12,MUTED)); libraryCard.addView(text(library.getSelected()==null?"No track selected":library.getSelected().getName(),16,TEXT));
        LinearLayout browse=row(); addSmallAction(browse,"‹ Previous",new View.OnClickListener(){public void onClick(View v){moveBrowser(-1);showScreen(0);}}); addSmallAction(browse,"Next ›",new View.OnClickListener(){public void onClick(View v){moveBrowser(1);showScreen(0);}}); libraryCard.addView(browse); content.addView(libraryCard,lpCard());
        LinearLayout decks=row(); addSmallAction(decks,"Load Deck A",new View.OnClickListener(){public void onClick(View v){loadSelected(deckA,cueA);}}); addSmallAction(decks,"Load Deck B",new View.OnClickListener(){public void onClick(View v){loadSelected(deckB,cueB);}}); content.addView(decks,lpCard());
        logView=text("",10,MUTED); logView.setVisibility(View.GONE); logScroll=new ScrollView(this); logScroll.addView(logView); content.addView(logScroll,new LinearLayout.LayoutParams(1,1));
    }

    private void buildCapture(){
        buildHeader("Capture"); LinearLayout live=card(); captureSummary=text("",16,captureRunning?GREEN:MUTED); live.addView(captureSummary); content.addView(live,lpCard());
        LinearLayout filters=row(); String[] names={"All","Buttons","Faders","Jog","Knobs","Other"}; for(int i=0;i<names.length;i++){final String name=names[i]; Button b=compactButton(name,name.equals(captureFilter)?BLUE:CARD); b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){captureFilter=name;buildCaptureEvents();}}); filters.addView(b,new LinearLayout.LayoutParams(0,dp(44),1f));} content.addView(filters);
        captureEvents=new LinearLayout(this); captureEvents.setOrientation(LinearLayout.VERTICAL); content.addView(captureEvents); buildCaptureEvents();
        LinearLayout actions=row(); addSmallAction(actions,captureRunning?"■ Stop":"▶ Start",new View.OnClickListener(){public void onClick(View v){if(captureRunning)stopCapture();else openAndStart();showScreen(1);}}); addSmallAction(actions,"Clear",new View.OnClickListener(){public void onClick(View v){recentEvents.clear();eventCount=0;buildCaptureEvents();}}); Button save=compactButton("Save Log · Future",CARD); save.setEnabled(false); actions.addView(save,new LinearLayout.LayoutParams(0,dp(48),1f)); content.addView(actions,lpCard());
    }

    private void buildCaptureEvents(){if(captureEvents==null)return;captureEvents.removeAllViews();for(int i=recentEvents.size()-1;i>=0;i--){CaptureEvent e=recentEvents.get(i);if(!matchesFilter(e))continue;LinearLayout row=card();String elapsed=String.format(Locale.US,"%02d:%02d.%03d",e.time/60000,(e.time/1000)%60,e.time%1000);row.addView(text(elapsed+"   "+e.name,14,TEXT));row.addView(text(e.value+"     report 0x"+String.format(Locale.US,"%02X",e.report)+" · "+e.size+" bytes",12,eventColor(e.type)));captureEvents.addView(row,lpCard());}}

    private boolean matchesFilter(CaptureEvent e){if("All".equals(captureFilter))return true;if("Buttons".equals(captureFilter))return "button".equals(e.type);if("Jog".equals(captureFilter))return e.name.indexOf("JOG")>=0;if("Faders".equals(captureFilter))return e.name.indexOf("VOLUME")>=0||e.name.indexOf("FADER")>=0;if("Knobs".equals(captureFilter))return "absolute".equals(e.type)&&e.name.indexOf("VOLUME")<0&&e.name.indexOf("FADER")<0;return !"button".equals(e.type)&&!"absolute".equals(e.type)&&e.name.indexOf("JOG")<0;}

    private void buildMapping(){buildHeader("Mapping");LinearLayout tabs=row();String[] t={"Deck A","Deck B","Mixer","FX","Global"};for(int i=0;i<t.length;i++){Button b=compactButton(t[i],i==0?BLUE:CARD);tabs.addView(b,new LinearLayout.LayoutParams(0,dp(44),1f));}content.addView(tabs);mappingGroup("PLAY / CUE",new String[][]{{"▶  DECK_A_PLAY","0x01:0D/01","Direct app · Play"},{"CUE  DECK_A_CUE","0x01:0D/02","Direct app · Cue"},{"SYNC  DECK_A_SYNC","0x01:0D/04","Future"}});mappingGroup("TRANSPORT",new String[][]{{"⇧  DECK_A_SHIFT","0x01:0D/08","Semantic event"},{"LOAD  DECK_A_LOAD","0x01:0F/10","Load selected track"}});mappingGroup("JOG WHEEL",new String[][]{{"◯  DECK_A_JOG_TOUCH","0x01:11/01","Jog touch"},{"↔  DECK_A_JOG","0x01:01 relative8","Nudge state"}});mappingGroup("FADERS",new String[][]{{"↕  CHANNEL_A_VOLUME","0x02:37 12-bit","Deck A volume"},{"↕  CHANNEL_B_VOLUME","0x02:39 12-bit","Deck B volume"},{"↔  CROSSFADER","0x02:07 12-bit","Equal-power mix"}});}

    private void mappingGroup(String title,String[][] rows){content.addView(text(title,12,MUTED));LinearLayout group=card();for(int i=0;i<rows.length;i++){TextView r=text(rows[i][0]+"\n     "+rows[i][1]+"   ·   "+rows[i][2]+"   ›",14,i==rows.length-1?CYAN:TEXT);r.setPadding(dp(4),dp(10),dp(4),dp(10));group.addView(r);}content.addView(group,lpCard());}

    private void buildMidi(){
        buildHeader("XP2 MIDI");
        LinearLayout state=card(); xp2StatusView=text(xp2Status,16,CYAN); state.addView(xp2StatusView); content.addView(state,lpCard());
        addAction(content,"Rescan XP2",BLUE,new View.OnClickListener(){public void onClick(View v){if(xp2!=null)xp2.scan();}});
        LinearLayout audio=card();
        addPerformanceSwitch(audio); content.addView(audio,lpCard());
        content.addView(text("BPM is manual. Set the first beat in Transport mode before syncing. Tempo changes also change pitch.",13,MUTED),lpCard());
        LinearLayout bpms=row();
        addSmallAction(bpms,"Set A BPM",new View.OnClickListener(){public void onClick(View v){editBpm(0);}});
        addSmallAction(bpms,"Set B BPM",new View.OnClickListener(){public void onClick(View v){editBpm(1);}});content.addView(bpms,lpCard());
        content.addView(text(String.format(Locale.US,"A %.1f BPM · B %.1f BPM",deckA.getPerformance().getBpm(),deckB.getPerformance().getBpm()),14,CYAN),lpCard());
        content.addView(text("XP2 layers: 1 Hot Cues · 2 Loops/Jump · 3 Sampler · 4 Transport/Grid · 5 Rolls · 6 Pad FX · 7 Saved Loops · 8 Transport. SHIFT+pad clears cues/loops or loads a sample from the selected library track.",13,MUTED),lpCard());
        content.addView(text("Tap Learn, then press an XP2 pad/button. Only MIDI notes are learned. Use a dedicated pad mode for these transport actions.",14,MUTED),lpCard());
        xp2LearnView=text("",13,ORANGE); content.addView(xp2LearnView,lpCard()); updateXp2Learn();
        for(int i=0;i<XP2_ACTIONS.length;i++){
            final String action=XP2_ACTIONS[i]; String binding=xp2Router.binding(action);
            addAction(content,"Learn "+action+" · "+(binding==null?"unassigned":binding),CARD,new View.OnClickListener(){public void onClick(View v){if(xp2Mapping!=null)xp2Mapping.reset();xp2Router.learn(action);updateXp2Learn();}});
        }
        LinearLayout actions=row();
        addSmallAction(actions,"Cancel learn",new View.OnClickListener(){public void onClick(View v){xp2Router.cancelLearn();updateXp2Learn();}});
        addSmallAction(actions,"Clear bindings",new View.OnClickListener(){public void onClick(View v){xp2Router.clear();getSharedPreferences("xp2_notes",MODE_PRIVATE).edit().clear().apply();showScreen(3);}});
        content.addView(actions,lpCard());
        content.addView(text("MIDI MONITOR · newest last · channel shown as 1–16",12,MUTED),lpCard());
        xp2MonitorView=text("",12,GREEN); xp2MonitorView.setTypeface(Typeface.MONOSPACE); content.addView(xp2MonitorView,lpCard()); updateXp2Monitor();
        content.addView(text("Performance layers require Performance audio. Learned notes override factory actions. Eight shared sampler slots play up to the first 15 seconds of loaded tracks.",13,MUTED),lpCard());
    }

    private void startXp2(){
        SharedPreferences prefs=getSharedPreferences("xp2_notes",MODE_PRIVATE);
        for(String action:XP2_ACTIONS){
            String saved=prefs.getString(action,null); if(saved==null)continue;
            try{String[] parts=saved.split(":");if(parts.length==2)xp2Router.bind(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),action);}catch(IllegalArgumentException ignored){}
        }
        xp2=new Xp2MidiDevice(this,new Xp2MidiDevice.Listener(){
            public void onStatus(String value){xp2Status=value;if(xp2StatusView!=null)xp2StatusView.setText(value);}
            public void onDisconnected(){xp2Router.releaseAll();xp2Router.cancelLearn();if(xp2Mapping!=null)xp2Mapping.reset();updateXp2Learn();}
            public void onMessage(int port,int status,int data1,int data2){
                // Ports are distinct streams. Mapping uses only the primary XP2 output port.
                if(port==0){
                    boolean learning=xp2Router.getLearning()!=null;
                    boolean override=xp2Router.hasBinding(status,data1);
                    String learned=xp2Router.onMessage(status,data1,data2);
                    if(!learning&&!override&&xp2Mapping!=null)xp2Mapping.onMessage(status,data1,data2);
                    if(learned!=null){
                        SharedPreferences.Editor editor=getSharedPreferences("xp2_notes",MODE_PRIVATE).edit().clear();
                        for(String action:XP2_ACTIONS){String binding=xp2Router.binding(action);if(binding!=null)editor.putString(action,binding);}
                        editor.apply();if(currentScreen==3)showScreen(3);
                    }
                }
                xp2Messages.add(String.format(Locale.US,"P%d CH%02d  %02X %02X %02X",port,(status&15)+1,status,data1,data2));
                while(xp2Messages.size()>24)xp2Messages.remove(0);
                long now=SystemClock.elapsedRealtime();if(now-lastMidiMonitor>=100){lastMidiMonitor=now;updateXp2Monitor();}
            }
        });
        xp2.start();
    }
    private void setupPerformance(){
        audioOutput=new PerformanceAudioOutput(deckA,deckB,samples,new PerformanceAudioOutput.Listener(){public void onAudioError(final String message){runOnUiThread(new Runnable(){public void run(){if(destroyed)return;deckA.pause();deckB.pause();for(PerformanceDeck sample:samples)sample.pause();Toast.makeText(MainActivity.this,"Audio output: "+message,Toast.LENGTH_LONG).show();}});}});
        performanceController=new Xp2PerformanceController(deckA.getPerformance(),deckB.getPerformance(),samples,new Xp2PerformanceController.Actions(){
            public void load(int deck){queueLoad(deck==0?deckA:deckB,deck==0?cueA:cueB);}
            public void browse(int delta){moveBrowser(delta);}
            public void cue(int deck,boolean pressed){queueCue(deck==0?cueA:cueB,pressed);}
            public void loadSample(int slot){TrackLibrary.Track selected=library.getSelected();if(selected!=null&&deckA.isPerformanceEnabled())decodeTrack(slot+2,selected);}
            public void changed(){updateStatus();}
            public void unsupportedDeck(){Toast.makeText(MainActivity.this,"Decks C/D are not available yet; select A/B on the XP2",Toast.LENGTH_SHORT).show();}
        });
        xp2Mapping=new Xp2MidiMapping(new Xp2MidiMapping.Listener(){
            public void pad(int deck,int mode,int index,boolean shifted,boolean pressed){if(deckA.isPerformanceEnabled())performanceController.pad(deck,mode,index,shifted,pressed);}
            public void button(int deck,String control,boolean pressed){
                if("LOAD".equals(control)||"UNSUPPORTED_DECK".equals(control)||deckA.isPerformanceEnabled())performanceController.button(deck,control,pressed);
            }
            public void browse(int delta){performanceController.browse(delta);}
            public void fx(int deck,String control,int slot,float value){if(deckA.isPerformanceEnabled())performanceController.fx(deck,control,slot,value);}
            public void mode(int deck,int mode){performanceController.mode(deck,mode);if(xp2!=null)xp2.invalidateLeds();}
        });
        if(getSharedPreferences("ui_settings",MODE_PRIVATE).getBoolean("performance_audio",false))enablePerformance(true);
        performanceHandler.post(performanceTick);
    }
    private void addPerformanceSwitch(LinearLayout parent){
        TextView label=text("Performance audio · hot cues, loops, sampler and FX\nChanging audio mode unloads the decks",14,TEXT);parent.addView(label);
        Switch toggle=new Switch(this);toggle.setText("Enable performance audio");toggle.setTextColor(TEXT);toggle.setChecked(deckA.isPerformanceEnabled());
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){public void onCheckedChanged(CompoundButton button,boolean value){enablePerformance(value);getSharedPreferences("ui_settings",MODE_PRIVATE).edit().putBoolean("performance_audio",value).apply();updateStatus();}});parent.addView(toggle);
    }
    private void enablePerformance(boolean enabled){
        xp2Router.releaseAll();xp2Router.cancelLearn();if(xp2Mapping!=null)xp2Mapping.reset();
        for(int i=0;i<loads.length;i++){loadGeneration[i]++;if(loads[i]!=null)loads[i].cancel(true);}decoder.purge();
        if(audioOutput!=null)audioOutput.stop();
        deckA.setPerformanceEnabled(enabled);deckB.setPerformanceEnabled(enabled);cueA.onTrackLoaded();cueB.onTrackLoaded();
        for(PerformanceDeck sample:samples)sample.load(null);
        if(enabled&&audioOutput!=null)audioOutput.start();
        if(xp2!=null)xp2.invalidateLeds();
    }
    private void editBpm(final int deck){
        final PerformanceDeck d=deck==0?deckA.getPerformance():deckB.getPerformance();
        final EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setText(String.format(Locale.US,"%.2f",d.getBpm()));input.selectAll();
        new AlertDialog.Builder(this).setTitle("Deck "+(deck==0?"A":"B")+" BPM (30–300)").setView(input)
            .setNegativeButton("Cancel",null).setPositiveButton("Set",new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){
                try{d.setBpm(Double.parseDouble(input.getText().toString()));showScreen(3);}
                catch(IllegalArgumentException e){Toast.makeText(MainActivity.this,"Enter a BPM from 30 to 300",Toast.LENGTH_SHORT).show();}
            }}).show();
    }
    private void decodeTrack(final int target,final TrackLibrary.Track track){
        if(loads[target]!=null)loads[target].cancel(true);decoder.purge();final int token=++loadGeneration[target];
        Toast.makeText(this,target<2?"Loading performance track…":"Loading up to 15 seconds into sample "+(target-1),Toast.LENGTH_SHORT).show();
        final Context app=getApplicationContext();
        try{loads[target]=decoder.submit(new Runnable(){public void run(){
            try{
                int limit=(int)Math.min(96L*1024*1024,Runtime.getRuntime().maxMemory()/8);
                if(target>=2)limit=(int)Math.min(192000L*4*15,Runtime.getRuntime().maxMemory()/64);
                final PcmClip clip=PcmDecoder.decode(app,Uri.parse(track.getReference()),limit,target>=2);
                runOnUiThread(new Runnable(){public void run(){
                    if(destroyed||token!=loadGeneration[target]||!deckA.isPerformanceEnabled())return;
                    if(target<2){DeckEngine deck=target==0?deckA:deckB;CueController cue=target==0?cueA:cueB;deck.loadPcm(clip,track.getReference());cue.onTrackLoaded();performanceController.trackLoaded(target);}
                    else samples[target-2].load(clip);
                    if(audioOutput!=null&&!audioOutput.isRunning())audioOutput.start();
                    if(xp2!=null)xp2.invalidateLeds();updateStatus();
                }});
            }catch(final Exception e){runOnUiThread(new Runnable(){public void run(){if(!destroyed&&token==loadGeneration[target])Toast.makeText(MainActivity.this,e.getMessage(),Toast.LENGTH_LONG).show();}});}
        }});}catch(RejectedExecutionException e){Toast.makeText(this,"Audio loading queue is full; try again shortly",Toast.LENGTH_SHORT).show();}
    }
    private void refreshXp2Leds(){
        if(xp2==null||xp2Mapping==null||performanceController==null||!deckA.isPerformanceEnabled()||xp2Router.getLearning()!=null)return;
        for(int deck=0;deck<2;deck++){
            PerformanceDeck d=deck==0?deckA.getPerformance():deckB.getPerformance();int mode=xp2Mapping.getMode(deck);
            for(int index=0;index<16;index++)for(int shift=0;shift<2;shift++){
                int channel=7+deck*2+shift,note=Xp2MidiMapping.padNote(mode,index);
                if(!xp2Router.hasBinding(0x90|channel,note))xp2.led(channel,note,performanceController.padLit(deck,mode,index));
            }
            xp2.led(deck,20,d.isLooping());xp2.led(deck,53,d.isQuantized());xp2.led(deck,88,performanceController.isSynced(deck));xp2.led(deck,104,d.isSilent());
            for(int slot=0;slot<3;slot++)xp2.led(4+deck,112+slot,d.isFxSelected(slot));xp2.led(4+deck,118,d.isFxHold());
        }
    }
    private void updateXp2Learn(){if(xp2LearnView!=null)xp2LearnView.setText(xp2Router.getLearning()==null?"Learn idle · assignments show channel 0–15:note":"Waiting for note: "+xp2Router.getLearning());}
    private void updateXp2Monitor(){if(xp2MonitorView==null)return;StringBuilder lines=new StringBuilder();for(String line:xp2Messages)lines.append(line).append('\n');xp2MonitorView.setText(lines.length()==0?"Waiting for MIDI…":lines.toString());}

    private void buildSettings(){buildHeader("Settings");final SharedPreferences prefs=getSharedPreferences("ui_settings",MODE_PRIVATE);content.addView(text("DEVICE",12,MUTED));LinearLayout deviceCard=card();addSwitch(deviceCard,"Auto reconnect","Reconnect on USB reattach",prefs.getBoolean("auto_reconnect",true),"auto_reconnect");addSwitch(deviceCard,"Request USB permission","Ask automatically when connected",prefs.getBoolean("auto_permission",true),"auto_permission");addDisabledSwitch(deviceCard,"S4 LED feedback","Future · S4 output reports not implemented");addSwitch(deviceCard,"Keep screen on","Prevent sleep while performing",prefs.getBoolean("keep_screen",false),"keep_screen");content.addView(deviceCard,lpCard());content.addView(text("APP",12,MUTED));LinearLayout app=card();app.addView(text("Theme                                      Dark",14,TEXT));app.addView(text("Log level                                  Info",14,TEXT));app.addView(text("HID display                         Coalesced",14,TEXT));TextView clear=text("Clear capture events                                  ›",14,TEXT);clear.setPadding(0,dp(14),0,dp(8));clear.setOnClickListener(new View.OnClickListener(){public void onClick(View v){recentEvents.clear();eventCount=0;}});app.addView(clear);content.addView(app,lpCard());content.addView(text("ABOUT",12,MUTED));LinearLayout about=card();about.addView(text("S4Bridge v0.1.0",16,TEXT));about.addView(text("Traktor Kontrol S4 MK2 on Android",13,MUTED));about.addView(text("GPL-2.0-or-later · Mapping attribution: Mixxx",13,BLUE));content.addView(about,lpCard());}

    private void addSwitch(LinearLayout parent,String title,String subtitle,boolean checked,final String key){LinearLayout row=row();TextView label=text(title+"\n"+subtitle,14,TEXT);row.addView(label,new LinearLayout.LayoutParams(0,dp(62),1f));Switch toggle=new Switch(this);toggle.setChecked(checked);toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){public void onCheckedChanged(CompoundButton b,boolean value){getSharedPreferences("ui_settings",MODE_PRIVATE).edit().putBoolean(key,value).apply();if("keep_screen".equals(key)){if(value)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}}});row.addView(toggle);parent.addView(row);}
    private void addDisabledSwitch(LinearLayout p,String t,String s){LinearLayout row=row();row.addView(text(t+"\n"+s,14,MUTED),new LinearLayout.LayoutParams(0,dp(62),1f));Switch sw=new Switch(this);sw.setEnabled(false);row.addView(sw);p.addView(row);}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout card(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(14),dp(12),dp(14),dp(12));v.setBackground(shape(CARD,BORDER,12));return v;}
    private LinearLayout.LayoutParams lpCard(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));return p;}
    private TextView text(String value,float size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(0,1.12f);return v;}
    private GradientDrawable shape(int fill,int stroke,float radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp((int)radius));d.setStroke(dp(1),stroke);return d;}
    private Button compactButton(String label,int color){Button b=new Button(this);b.setText(label);b.setTextColor(TEXT);b.setTextSize(11);b.setAllCaps(false);b.setPadding(dp(2),0,dp(2),0);b.setBackground(shape(color,BORDER,9));return b;}
    private void addAction(LinearLayout root,String label,int color,View.OnClickListener listener){Button b=compactButton(label,color);b.setTextSize(16);b.setOnClickListener(listener);root.addView(b,new LinearLayout.LayoutParams(-1,dp(58)));}
    private void addSmallAction(LinearLayout root,String label,View.OnClickListener listener){Button b=compactButton(label,CARD);b.setOnClickListener(listener);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1f);p.setMargins(dp(3),0,dp(3),0);root.addView(b,p);}
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private int eventColor(String type){return "button".equals(type)?GREEN:("relative".equals(type)?ORANGE:CYAN);}

    private void registerUsb(){
        IntentFilter f=new IntentFilter(ACTION_USB_PERMISSION);
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(usbReceiver,f,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(usbReceiver,f);
    }

    private void createMapping(){
        mapping=new S4Mk2Mapping(new S4Mk2Mapping.Listener(){
            public void onButton(String c,boolean p){
                log(c+" "+(p?"DOWN":"UP"));
                recordEvent(c,p?"PRESS":"RELEASE","button",true);
                if(!controllerRouter.onButton(c,p)){
                    if("DECK_A_JOG_TOUCH".equals(c))deckA.setJogTouched(p);
                    else if("DECK_B_JOG_TOUCH".equals(c))deckB.setJogTouched(p);
                }
                updateStatus();
            }
            public void onAbsolute(String c,int raw,float n){
                if("CROSSFADER".equals(c)) mixer.setCrossfader(n);
                else if("CHANNEL_A_VOLUME".equals(c)) deckA.setChannelVolume(n);
                else if("CHANNEL_B_VOLUME".equals(c)) deckB.setChannelVolume(n);
                else if("DECK_A_PITCH".equals(c)) deckA.setTempo(n);
                else if("DECK_B_PITCH".equals(c)) deckB.setTempo(n);
                else if("CHANNEL_A_EQ_LOW".equals(c)) deckA.setEqLow(n);
                else if("CHANNEL_A_EQ_MID".equals(c)) deckA.setEqMid(n);
                else if("CHANNEL_A_EQ_HIGH".equals(c)) deckA.setEqHigh(n);
                else if("CHANNEL_A_FILTER".equals(c)) deckA.setFilter(n);
                else if("CHANNEL_B_EQ_LOW".equals(c)) deckB.setEqLow(n);
                else if("CHANNEL_B_EQ_MID".equals(c)) deckB.setEqMid(n);
                else if("CHANNEL_B_EQ_HIGH".equals(c)) deckB.setEqHigh(n);
                else if("CHANNEL_B_FILTER".equals(c)) deckB.setFilter(n);
                recordEvent(c,String.format(Locale.US,"%d  ·  %.3f",raw,n),"absolute",false);
                updateStatus();
            }
            public void onRelative(String c,int d){controllerRouter.onRelative(c,d);recordEvent(c,(d>0?"+":"")+d,"relative",c.indexOf("JOG")<0);}
        });
    }

    private void recordEvent(final String name,final String value,final String type,boolean immediate){
        eventCount++; long now=SystemClock.elapsedRealtime(); if(!immediate&&now-lastContinuousUi<100)return; lastContinuousUi=now;
        final CaptureEvent event=new CaptureEvent(captureStartedAt==0?0:now-captureStartedAt,name,value,type,currentReportId,currentReportSize);
        runOnUiThread(new Runnable(){public void run(){
            if("absolute".equals(type)){for(int i=recentEvents.size()-1;i>=0;i--){CaptureEvent old=recentEvents.get(i);if(old.name.equals(name)&&old.type.equals(type)){recentEvents.remove(i);break;}}}
            recentEvents.add(event);while(recentEvents.size()>80)recentEvents.remove(0);if(currentScreen==1){buildCaptureEvents();updateCaptureSummary();}
        }});
    }

    private void queueCue(final CueController cue,final boolean pressed){runOnUiThread(new Runnable(){public void run(){if(pressed)cue.onPress();else cue.onRelease();updateStatus();}});}
    private void queueLoad(final DeckEngine deck,final CueController cue){runOnUiThread(new Runnable(){public void run(){loadSelected(deck,cue);}});}

    private void scan(){
        HashMap<String,UsbDevice> list=usbManager.getDeviceList(); device=null;
        for(UsbDevice d:list.values()) if(d.getVendorId()==VID&&d.getProductId()==PID){device=d;break;}
        log(device==null?"S4 MK2 not found":"S4 MK2 found; interfaces="+device.getInterfaceCount()+" permission="+usbManager.hasPermission(device)); updateStatus();
    }

    private boolean isS4(UsbDevice candidate){return candidate!=null&&candidate.getVendorId()==VID&&candidate.getProductId()==PID;}

    private void requestUsbPermission(){
        if(device==null)scan(); if(device==null)return;
        if(usbManager.hasPermission(device)){log("USB permission already granted");return;}
        Intent i=new Intent(ACTION_USB_PERMISSION).setPackage(getPackageName());
        PendingIntent pi=PendingIntent.getBroadcast(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_MUTABLE);
        usbManager.requestPermission(device,pi); log("USB permission requested");
    }

    private void openAndStart(){
        stopCapture(); closeConnection(); if(device==null)scan();
        if(device==null||!usbManager.hasPermission(device)){log("S4/permission unavailable");return;}
        connection=usbManager.openDevice(device); if(connection==null){log("openDevice failed");return;}
        for(int x=0;x<device.getInterfaceCount();x++){
            UsbInterface in=device.getInterface(x); if(in.getInterfaceClass()!=UsbConstants.USB_CLASS_HID)continue;
            for(int e=0;e<in.getEndpointCount();e++){
                UsbEndpoint ep=in.getEndpoint(e);
                if(ep.getAddress()==0x84&&ep.getDirection()==UsbConstants.USB_DIR_IN&&ep.getType()==UsbConstants.USB_ENDPOINT_XFER_INT){hidInterface=in;inEndpoint=ep;}
            }
        }
        if(hidInterface==null||inEndpoint==null){log("HID endpoint 0x84 not found");closeConnection();return;}
        boolean claimed=connection.claimInterface(hidInterface,true); log("HID interface id="+hidInterface.getId()+" claim="+claimed);
        if(!claimed){closeConnection();return;} startCapture();
    }

    private void startCapture(){
        if(captureRunning||connection==null||inEndpoint==null)return; old01=null;old02=null;captureRunning=true;
        captureStartedAt=SystemClock.elapsedRealtime(); eventCount=0;
        captureThread=new Thread(new Runnable(){public void run(){captureLoop();}},"S4-HID-Capture"); captureThread.start(); log("HID capture started"); updateStatus();
    }

    private void captureLoop(){
        Thread owner=Thread.currentThread();
        UsbDeviceConnection activeConnection=connection;
        UsbEndpoint activeEndpoint=inEndpoint;
        byte[] buf=new byte[Math.max(64,activeEndpoint.getMaxPacketSize())];
        while(captureRunning&&captureThread==owner){
            int len;
            try{len=activeConnection.bulkTransfer(activeEndpoint,buf,buf.length,250);}catch(Exception e){break;}
            if(len<=0)continue; handlePacket(Arrays.copyOf(buf,len));
        }
        if(captureThread==owner){captureRunning=false;captureThread=null;updateStatus();}
    }

    private void handlePacket(byte[] p){
        if(p.length==0)return; int id=p[0]&0xff; currentReportId=id;currentReportSize=p.length;
        if(id==1){ if(old01==null){old01=Arrays.copyOf(p,p.length);log("REPORT 01 initialized ("+p.length+" bytes)");return;} mapping.parse(p,old01);old01=Arrays.copyOf(p,p.length); }
        else if(id==2){ if(old02==null){old02=Arrays.copyOf(p,p.length);log("REPORT 02 initialized ("+p.length+" bytes)");return;} mapping.parse(p,old02);old02=Arrays.copyOf(p,p.length); }
    }

    private void pickTracks(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,PICK_TRACKS);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_TRACKS||resultCode!=RESULT_OK||data==null)return;
        ClipData clips=data.getClipData();
        if(clips!=null){for(int i=0;i<clips.getItemCount();i++)addTrack(clips.getItemAt(i).getUri());}
        else if(data.getData()!=null)addTrack(data.getData());
        updateStatus();
    }

    private void addTrack(Uri uri){
        try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
        library.add(new TrackLibrary.Track(uri.toString(),displayName(uri)));
        persistLibrary();
        log("LIBRARY added "+displayName(uri));
    }

    private void restoreLibrary(){
        SharedPreferences preferences=getSharedPreferences(LIBRARY_PREFS,MODE_PRIVATE);
        int count=preferences.getInt("count",0);
        for(int i=0;i<count;i++){
            String reference=preferences.getString("reference_"+i,null);
            String name=preferences.getString("name_"+i,null);
            if(reference!=null)library.add(new TrackLibrary.Track(reference,name));
        }
    }

    private void persistLibrary(){
        SharedPreferences.Editor editor=getSharedPreferences(LIBRARY_PREFS,MODE_PRIVATE).edit().clear();
        editor.putInt("count",library.getTracks().size());
        for(int i=0;i<library.getTracks().size();i++){
            TrackLibrary.Track track=library.getTracks().get(i);
            editor.putString("reference_"+i,track.getReference());
            editor.putString("name_"+i,track.getName());
        }
        editor.apply();
    }

    private void moveBrowser(int delta){
        library.moveSelection(delta);
        TrackLibrary.Track selected=library.getSelected();
        if(selected!=null)log("BROWSER "+(library.getSelectedIndex()+1)+"/"+library.getTracks().size()+" "+selected.getName());
        updateStatus();
    }

    private void loadSelected(DeckEngine deck,CueController cue){
        TrackLibrary.Track track=library.getSelected();
        if(track==null){log("LIBRARY empty; add tracks first");return;}
        Uri uri=Uri.parse(track.getReference());
        if(deck.isPerformanceEnabled()){decodeTrack(deck==deckA?0:1,track);return;}
        boolean loaded=deck.loadUri(this,uri);
        if(loaded)cue.onTrackLoaded();
        mixer.setCrossfader(mixer.getCrossfader());
        log("DECK "+deck.getName()+" load="+loaded+" track="+track.getName());
        updateStatus();
    }

    private String displayName(Uri uri){
        Cursor cursor=null;
        try{
            cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);
            if(cursor!=null&&cursor.moveToFirst())return cursor.getString(0);
        }catch(Exception ignored){}finally{if(cursor!=null)cursor.close();}
        return uri.getLastPathSegment();
    }

    private void updateStatus(){
        runOnUiThread(new Runnable(){public void run(){
            TrackLibrary.Track selected=library.getSelected();
            if(status!=null)status.setText("♧  Traktor Kontrol S4 MK2\n"+(device==null?"Not connected · tap to scan":(captureRunning?"Connected · Capturing":"Connected · Ready"))+"\n\nA "+(deckA.isPlaying()?"PLAYING":(deckA.isLoaded()?"READY":"EMPTY"))+"  ·  B "+(deckB.isPlaying()?"PLAYING":(deckB.isLoaded()?"READY":"EMPTY"))+"  ·  Library "+library.getTracks().size());
            updateCaptureSummary();
        }});
    }

    private void updateCaptureSummary(){if(captureSummary==null)return;long elapsed=captureStartedAt==0?0:SystemClock.elapsedRealtime()-captureStartedAt;captureSummary.setText((captureRunning?"●  Capturing…":"○  Capture stopped")+String.format(Locale.US,"\n%02d:%02d elapsed                         %,d events",elapsed/60000,(elapsed/1000)%60,eventCount));}

    private void stopCapture(){Thread oldThread=captureThread;captureRunning=false;captureThread=null;if(oldThread!=null)oldThread.interrupt();updateStatus();}
    private void closeConnection(){if(connection!=null){try{if(hidInterface!=null)connection.releaseInterface(hidInterface);}catch(Exception ignored){}try{connection.close();}catch(Exception ignored){}}connection=null;hidInterface=null;inEndpoint=null;}

    private void log(final String s){if(logView==null)return;runOnUiThread(new Runnable(){public void run(){logView.append(s+"\n");if(logScroll!=null)logScroll.post(new Runnable(){public void run(){logScroll.fullScroll(View.FOCUS_DOWN);}});}});}

    @Override protected void onResume(){super.onResume();if(device==null)scan();if(device!=null&&usbManager.hasPermission(device)&&!captureRunning&&getSharedPreferences("ui_settings",MODE_PRIVATE).getBoolean("auto_reconnect",true))openAndStart();}

    @Override protected void onDestroy(){destroyed=true;performanceHandler.removeCallbacks(performanceTick);decoder.shutdownNow();if(audioOutput!=null)audioOutput.stop();if(xp2!=null)xp2.stop();stopCapture();closeConnection();deckA.release();deckB.release();try{unregisterReceiver(usbReceiver);}catch(Exception ignored){}super.onDestroy();}
}
