package com.s4bridge.app;

import android.app.Activity;
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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.SeekBar;
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
import com.s4bridge.app.hardware.Xp2MidiInput;
import com.s4bridge.app.core.Xp2Mapping;

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
    private TextView status, logView, captureSummary, xp2HomeStatus;
    private final TextView[] deckLabels=new TextView[2];
    private final Button[] playButtons=new Button[2];
    private final SeekBar[] volumeSliders=new SeekBar[2];
    private SeekBar crossfaderSlider;
    private final PlatterView[] platters=new PlatterView[2];
    private final Handler performanceHandler=new Handler();
    private final Runnable performanceTick=new Runnable(){public void run(){refreshPerformance();performanceHandler.postDelayed(this,200);}};
    private final class PlatterView extends View {
        private final DeckEngine deck;private final int accent;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        PlatterView(DeckEngine deck,int accent){super(MainActivity.this);this.deck=deck;this.accent=accent;setContentDescription("Deck "+deck.getName()+" playback position");}
        @Override protected void onDraw(Canvas canvas){
            float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.42f;
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(12,12,14));canvas.drawCircle(x,y,r,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(BORDER);canvas.drawCircle(x,y,r,paint);canvas.drawCircle(x,y,r*.82f,paint);
            int duration=deck.getDurationMs();float progress=duration>0?Math.min(1f,deck.getPositionMs()/(float)duration):0f;
            paint.setColor(accent);paint.setStrokeWidth(dp(4));canvas.drawArc(new RectF(x-r,y-r,x+r,y+r),-90,progress*360,false,paint);
            double angle=deck.getPositionMs()/1800.0*Math.PI*2-Math.PI/2;paint.setStrokeWidth(dp(2));canvas.drawLine(x+(float)Math.cos(angle)*r*.62f,y+(float)Math.sin(angle)*r*.62f,x+(float)Math.cos(angle)*r*.78f,y+(float)Math.sin(angle)*r*.78f,paint);
            paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.DEFAULT_BOLD);paint.setTextSize(dp(22));canvas.drawText(deck.getName(),x,y-dp(4),paint);
            paint.setTypeface(Typeface.MONOSPACE);paint.setTextSize(dp(12));paint.setColor(TEXT);canvas.drawText(clock(deck.getPositionMs()),x,y+dp(19),paint);
        }
    }
    private String clock(int ms){return String.format(Locale.US,"%02d:%02d",ms/60000,(ms/1000)%60);}

    private ScrollView logScroll;
    private LinearLayout content, bottomNav, captureEvents;
    private int currentScreen, eventCount, currentReportId, currentReportSize;
    private long captureStartedAt, lastContinuousUi;
    private String captureFilter="All";
    private final ArrayList<CaptureEvent> recentEvents=new ArrayList<CaptureEvent>();
    private static final int BG=Color.rgb(15,15,17), CARD=Color.rgb(26,26,29), BORDER=Color.rgb(58,58,63);
    private static final int TEXT=Color.rgb(235,243,251), MUTED=Color.rgb(150,170,190), BLUE=Color.rgb(35,132,255);
    private static final int GREEN=Color.rgb(95,204,113), CYAN=Color.rgb(51,167,255), ORANGE=Color.rgb(255,92,88), RED=Color.rgb(255,67,91);

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
    private Xp2MidiInput xp2Input;
    private String xp2Status="XP2 MIDI starting";
    private final int[][] hotCues=new int[2][8];
    private final boolean[] xp2Muted=new boolean[2];
    private final float[] xp2SavedVolume={1f,1f};
    private void startXp2(){
        for(int[] cues:hotCues)Arrays.fill(cues,-1);
        final Xp2Mapping xp2=new Xp2Mapping(new Xp2Mapping.Listener(){
            public void onAction(final String action,final int deck,final int slot,final boolean pressed){
                runOnUiThread(new Runnable(){public void run(){handleXp2(action,deck,slot,pressed);}});
            }
        });
        xp2Input=new Xp2MidiInput(this,new Xp2MidiInput.Listener(){
            public void onBytes(byte[] bytes,int offset,int count){xp2.receive(bytes,offset,count);}
            public void onStatus(String value){xp2Status=value;if(currentScreen==3)showScreen(3);else updateStatus();}
        });
    }
    private void handleXp2(String action,int deck,int slot,boolean pressed){
        if(deck<0||deck>1)return;
        DeckEngine target=deck==0?deckA:deckB;
        if("load_track".equals(action)){if(pressed)loadSelected(target,deck==0?cueA:cueB);}
        else if("cue_point".equals(action)&&slot>=0&&slot<8){
            if(pressed&&target.isLoaded()){if(hotCues[deck][slot]<0)hotCues[deck][slot]=target.getPositionMs();else target.seekToMs(hotCues[deck][slot]);}
        }else if("mute".equals(action)){
            if(pressed){if(!xp2Muted[deck]){xp2SavedVolume[deck]=target.getChannelVolume();target.setChannelVolume(0f);}else target.setChannelVolume(xp2SavedVolume[deck]);xp2Muted[deck]=!xp2Muted[deck];}
        }else if(pressed)log("XP2 unsupported engine action: "+action);
        recordEvent("XP2 "+action+" deck="+deck+" slot="+slot,pressed?"PRESS":"RELEASE","button",true);
        updateStatus();
    }

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
        buildUi(); restoreLibrary(); registerUsb(); createMapping(); startXp2(); scan(); updateStatus();
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
        currentScreen=screen; status=null; captureSummary=null; captureEvents=null; content.removeAllViews(); content.setPadding(dp(14),dp(12),dp(14),dp(16));
        if(screen==0)buildHome(); else if(screen==1)buildCapture(); else if(screen==2)buildMapping(); else if(screen==3)buildMidi(); else buildSettings();
        buildBottomNav(); updateStatus();
    }

    private void buildBottomNav(){
        bottomNav.removeAllViews(); String[] labels={"⌂\nDecks","⌁\nCapture","▦\nMapping","▤\nMIDI","⚙\nSettings"};
        for(int i=0;i<labels.length;i++){final int index=i; TextView item=text(labels[i],12,i==currentScreen?BLUE:MUTED); item.setGravity(Gravity.CENTER); item.setPadding(0,dp(5),0,dp(3)); item.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showScreen(index);}}); bottomNav.addView(item,new LinearLayout.LayoutParams(0,-1,1f));}
    }

    private void buildHeader(String title){
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); TextView word=text(title,26,TEXT); word.setTypeface(Typeface.DEFAULT,Typeface.BOLD); row.addView(word,new LinearLayout.LayoutParams(0,dp(54),1f));
        if(!"S4Bridge".equals(title)){TextView brand=text("S4Bridge",13,BLUE); brand.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL); row.addView(brand,new LinearLayout.LayoutParams(dp(100),dp(54)));} content.addView(row);
    }

    private void buildHome(){
        buildHeader("S4Bridge");
        LinearLayout connections=card();status=text("",14,TEXT);connections.addView(status);
        xp2HomeStatus=text(xp2Status,13,CYAN);connections.addView(xp2HomeStatus);content.addView(connections,lpCard());
        LinearLayout reconnect=row();addSmallAction(reconnect,"Connect S4",new View.OnClickListener(){public void onClick(View v){scan();if(device!=null&&!usbManager.hasPermission(device))requestUsbPermission();else openAndStart();}});
        addSmallAction(reconnect,"Connect XP2",new View.OnClickListener(){public void onClick(View v){if(xp2Input!=null)xp2Input.reconnect();}});content.addView(reconnect,lpCard());
        LinearLayout decks=new LinearLayout(this);
        boolean wide=true;decks.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        for(int i=0;i<2;i++){LinearLayout panel=buildDeck(i);LinearLayout.LayoutParams params=wide?new LinearLayout.LayoutParams(0,-2,1f):new LinearLayout.LayoutParams(-1,-2);params.setMargins(dp(3),dp(6),dp(3),dp(6));decks.addView(panel,params);}content.addView(decks);
        LinearLayout mix=card();mix.addView(text("A                         MIX                         B",12,MUTED));
        crossfaderSlider=new SeekBar(this);crossfaderSlider.setMax(1000);crossfaderSlider.setProgress((int)(mixer.getCrossfader()*1000));
        crossfaderSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){if(user)mixer.setCrossfader(value/1000f);}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
        mix.addView(crossfaderSlider,new LinearLayout.LayoutParams(-1,dp(52)));content.addView(mix,lpCard());
        LinearLayout tracks=card();LinearLayout heading=row();heading.addView(text("LIBRARY  /  ALL TRACKS",12,MUTED),new LinearLayout.LayoutParams(0,dp(48),1f));addSmallAction(heading,"＋ Add music",new View.OnClickListener(){public void onClick(View v){pickTracks();}});tracks.addView(heading);
        tracks.addView(text("#     TITLE",11,MUTED));
        if(library.getTracks().isEmpty())tracks.addView(text("Add audio files, select a track, then load a deck.",14,TEXT));
        else for(int i=0;i<library.getTracks().size();i++){
            final int index=i;TextView item=text(String.format(Locale.US,"%02d   ",i+1)+library.getTracks().get(i).getName(),16,i==library.getSelectedIndex()?CYAN:TEXT);item.setPadding(dp(8),dp(12),dp(8),dp(12));item.setBackgroundColor(i==library.getSelectedIndex()?Color.rgb(41,62,81):i%2==0?Color.rgb(31,31,34):CARD);item.setOnClickListener(new View.OnClickListener(){public void onClick(View v){library.moveSelection(index-library.getSelectedIndex());showScreen(0);}});tracks.addView(item);
        }
        LinearLayout loads=row();addSmallAction(loads,"Load A",new View.OnClickListener(){public void onClick(View v){loadSelected(deckA,cueA);}});addSmallAction(loads,"Load B",new View.OnClickListener(){public void onClick(View v){loadSelected(deckB,cueB);}});tracks.addView(loads);content.addView(tracks,lpCard());
        logView=text("",10,MUTED);logView.setVisibility(View.GONE);logScroll=new ScrollView(this);logScroll.addView(logView);content.addView(logScroll,new LinearLayout.LayoutParams(1,1));
    }
    private LinearLayout buildDeck(final int index){
        final DeckEngine deck=index==0?deckA:deckB;final CueController cue=index==0?cueA:cueB;int accent=index==0?CYAN:ORANGE;
        LinearLayout panel=card();panel.addView(text("DECK "+deck.getName(),13,accent));deckLabels[index]=text("",14,TEXT);deckLabels[index].setMaxLines(3);deckLabels[index].setMinHeight(dp(60));panel.addView(deckLabels[index]);
        platters[index]=new PlatterView(deck,accent);panel.addView(platters[index],new LinearLayout.LayoutParams(-1,dp(145)));
        LinearLayout transport=row();playButtons[index]=compactButton("PLAY",accent);playButtons[index].setTextColor(BG);playButtons[index].setTextSize(16);
        playButtons[index].setOnClickListener(new View.OnClickListener(){public void onClick(View v){deck.togglePlay();updateStatus();}});transport.addView(playButtons[index],new LinearLayout.LayoutParams(0,dp(64),1f));
        Button cueButton=compactButton("CUE",CARD);cueButton.setTextSize(16);cueButton.setOnTouchListener(new View.OnTouchListener(){public boolean onTouch(View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){cue.onPress();updateStatus();return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){cue.onRelease();if(e.getAction()==MotionEvent.ACTION_UP)v.performClick();updateStatus();return true;}return true;}});transport.addView(cueButton,new LinearLayout.LayoutParams(0,dp(64),1f));panel.addView(transport);
        panel.addView(text("CHANNEL VOLUME",11,MUTED));volumeSliders[index]=new SeekBar(this);volumeSliders[index].setMax(1000);volumeSliders[index].setProgress((int)(deck.getChannelVolume()*1000));
        volumeSliders[index].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean user){if(user)deck.setChannelVolume(value/1000f);}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});panel.addView(volumeSliders[index],new LinearLayout.LayoutParams(-1,dp(52)));return panel;
    }
    private void refreshPerformance(){
        if(currentScreen!=0)return;
        if(xp2HomeStatus!=null)xp2HomeStatus.setText(xp2Status);
        for(int i=0;i<2;i++){
            DeckEngine deck=i==0?deckA:deckB;String title="No track loaded";
            for(TrackLibrary.Track track:library.getTracks())if(track.getReference().equals(deck.getLoadedPath())){title=track.getName();break;}
            if(deckLabels[i]!=null)deckLabels[i].setText(title+"\n"+(deck.isLoaded()?"−"+clock(Math.max(0,deck.getDurationMs()-deck.getPositionMs())):"EMPTY"));
            if(platters[i]!=null)platters[i].invalidate();
            if(playButtons[i]!=null){playButtons[i].setText(deck.isPlaying()?"PAUSE":"PLAY");playButtons[i].setEnabled(deck.isLoaded());}
            if(volumeSliders[i]!=null&&!volumeSliders[i].isPressed())volumeSliders[i].setProgress((int)(deck.getChannelVolume()*1000));
        }
        if(crossfaderSlider!=null&&!crossfaderSlider.isPressed())crossfaderSlider.setProgress((int)(mixer.getCrossfader()*1000));
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
        buildHeader("XP2 MIDI Input");LinearLayout info=card();info.addView(text(xp2Status,16,CYAN));
        info.addView(text("DDJ XP2 V9 · imported MIDI mapping",14,TEXT));
        info.addView(text("Active: load selected track, 8 hot cues per deck, mute. First cue press stores the current position; later presses jump to it. Cues reset when loading a track.",14,MUTED));
        info.addView(text("Loops, pitch bend, FX and LED feedback need additional engine/output support. MIDI output is not active.",14,ORANGE));content.addView(info,lpCard());
        addAction(content,"Reconnect XP2",BLUE,new View.OnClickListener(){public void onClick(View v){if(xp2Input!=null)xp2Input.reconnect();}});
    }

    private void buildSettings(){buildHeader("Settings");final SharedPreferences prefs=getSharedPreferences("ui_settings",MODE_PRIVATE);content.addView(text("DEVICE",12,MUTED));LinearLayout deviceCard=card();addSwitch(deviceCard,"Auto reconnect","Reconnect on USB reattach",prefs.getBoolean("auto_reconnect",true),"auto_reconnect");addSwitch(deviceCard,"Request USB permission","Ask automatically when connected",prefs.getBoolean("auto_permission",true),"auto_permission");addDisabledSwitch(deviceCard,"LED feedback","Future · output reports not implemented");addSwitch(deviceCard,"Keep screen on","Prevent sleep while performing",prefs.getBoolean("keep_screen",false),"keep_screen");content.addView(deviceCard,lpCard());content.addView(text("APP",12,MUTED));LinearLayout app=card();app.addView(text("Theme                                      Dark",14,TEXT));app.addView(text("Log level                                  Info",14,TEXT));app.addView(text("HID display                         Coalesced",14,TEXT));TextView clear=text("Clear capture events                                  ›",14,TEXT);clear.setPadding(0,dp(14),0,dp(8));clear.setOnClickListener(new View.OnClickListener(){public void onClick(View v){recentEvents.clear();eventCount=0;}});app.addView(clear);content.addView(app,lpCard());content.addView(text("ABOUT",12,MUTED));LinearLayout about=card();about.addView(text("S4Bridge v0.1.0",16,TEXT));about.addView(text("Traktor Kontrol S4 MK2 on Android",13,MUTED));about.addView(text("GPL-2.0-or-later · Mapping attribution: Mixxx",13,BLUE));content.addView(about,lpCard());}

    private void addSwitch(LinearLayout parent,String title,String subtitle,boolean checked,final String key){LinearLayout row=row();TextView label=text(title+"\n"+subtitle,14,TEXT);row.addView(label,new LinearLayout.LayoutParams(0,dp(62),1f));Switch toggle=new Switch(this);toggle.setChecked(checked);toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){public void onCheckedChanged(CompoundButton b,boolean value){getSharedPreferences("ui_settings",MODE_PRIVATE).edit().putBoolean(key,value).apply();if("keep_screen".equals(key)){if(value)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}}});row.addView(toggle);parent.addView(row);}
    private void addDisabledSwitch(LinearLayout p,String t,String s){LinearLayout row=row();row.addView(text(t+"\n"+s,14,MUTED),new LinearLayout.LayoutParams(0,dp(62),1f));Switch sw=new Switch(this);sw.setEnabled(false);row.addView(sw);p.addView(row);}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout card(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(10),dp(10),dp(10),dp(10));v.setBackground(shape(CARD,BORDER,3));return v;}
    private LinearLayout.LayoutParams lpCard(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(6),0,dp(6));return p;}
    private TextView text(String value,float size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(0,1.12f);return v;}
    private GradientDrawable shape(int fill,int stroke,float radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp((int)radius));d.setStroke(dp(1),stroke);return d;}
    private Button compactButton(String label,int color){Button b=new Button(this);b.setText(label);b.setTextColor(TEXT);b.setTextSize(11);b.setAllCaps(false);b.setPadding(dp(2),0,dp(2),0);b.setBackground(shape(color,BORDER,3));return b;}
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
        showScreen(0);
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
        boolean loaded=deck.loadUri(this,uri);
        if(loaded){cue.onTrackLoaded();Arrays.fill(hotCues[deck==deckA?0:1],-1);}
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
            if(status!=null)status.setText("S4 MK2 · "+(device==null?"Not connected":captureRunning?"HID input active":usbManager.hasPermission(device)?"Ready":"USB permission needed"));
            refreshPerformance();updateCaptureSummary();
        }});
    }

    private void updateCaptureSummary(){if(captureSummary==null)return;long elapsed=captureStartedAt==0?0:SystemClock.elapsedRealtime()-captureStartedAt;captureSummary.setText((captureRunning?"●  Capturing…":"○  Capture stopped")+String.format(Locale.US,"\n%02d:%02d elapsed                         %,d events",elapsed/60000,(elapsed/1000)%60,eventCount));}

    private void stopCapture(){Thread oldThread=captureThread;captureRunning=false;captureThread=null;if(oldThread!=null)oldThread.interrupt();updateStatus();}
    private void closeConnection(){if(connection!=null){try{if(hidInterface!=null)connection.releaseInterface(hidInterface);}catch(Exception ignored){}try{connection.close();}catch(Exception ignored){}}connection=null;hidInterface=null;inEndpoint=null;}

    private void log(final String s){if(logView==null)return;runOnUiThread(new Runnable(){public void run(){logView.append(s+"\n");if(logScroll!=null)logScroll.post(new Runnable(){public void run(){logScroll.fullScroll(View.FOCUS_DOWN);}});}});}

    @Override protected void onResume(){super.onResume();performanceHandler.removeCallbacks(performanceTick);performanceHandler.post(performanceTick);if(device==null)scan();if(device!=null&&usbManager.hasPermission(device)&&!captureRunning&&getSharedPreferences("ui_settings",MODE_PRIVATE).getBoolean("auto_reconnect",true))openAndStart();}

    @Override protected void onPause(){performanceHandler.removeCallbacks(performanceTick);super.onPause();}

    @Override protected void onDestroy(){performanceHandler.removeCallbacks(performanceTick);if(xp2Input!=null)xp2Input.close();stopCapture();closeConnection();deckA.release();deckB.release();try{unregisterReceiver(usbReceiver);}catch(Exception ignored){}super.onDestroy();}
}
