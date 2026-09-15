package com.s4bridge.app.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

/** Programmatic phone UI kept resource-free for the legacy Termux build path. */
public final class S4BridgeUi {
    public interface Actions {
        void scan();
        void requestUsbPermission();
        void startCapture();
        void stopCapture();
        void addTracks();
        void browse(int delta);
        void loadDeck(boolean deckA);
        void setAutoReconnect(boolean enabled);
    }

    public static final class State {
        public boolean usbFound;
        public boolean usbPermission;
        public boolean captureRunning;
        public boolean autoReconnect;
        public int interfaceCount;
        public int librarySize;
        public int selectedIndex;
        public String selectedTrack;
        public String deckAState;
        public String deckBState;
        public float deckAVolume;
        public float deckBVolume;
        public float crossfader;
        public float deckATempo;
        public float deckBTempo;
        public int cueAMs;
        public int cueBMs;
        public long shortReports;
        public long longReports;
        public String lastControl;
        public String lastValue;
    }

    private static final int BG=Color.rgb(8,12,22);
    private static final int SURFACE=Color.rgb(19,27,43);
    private static final int SURFACE_2=Color.rgb(28,39,59);
    private static final int TEXT=Color.rgb(241,245,249);
    private static final int MUTED=Color.rgb(148,163,184);
    private static final int CYAN=Color.rgb(34,211,238);
    private static final int GREEN=Color.rgb(52,211,153);
    private static final int AMBER=Color.rgb(251,191,36);

    private final Activity activity;
    private final Actions actions;
    private final FrameLayout pages;
    private final LinearLayout nav;
    private final View[] pageViews=new View[5];
    private final Button[] navButtons=new Button[5];
    private TextView homeConnection,homeLibrary,deckA,deckB,crossfader;
    private TextView captureState,captureCounts,captureEvent,captureLog;
    private TextView mappingLive;
    private Switch autoReconnect;

    public S4BridgeUi(Activity activity,Actions actions) {
        this.activity=activity;
        this.actions=actions;
        LinearLayout root=new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(appBar(),new LinearLayout.LayoutParams(-1,dp(64)));
        pages=new FrameLayout(activity);
        root.addView(pages,new LinearLayout.LayoutParams(-1,0,1f));
        nav=new LinearLayout(activity);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(68)));
        activity.setContentView(root);
        addPage(0,homePage());
        addPage(1,capturePage());
        addPage(2,mappingPage());
        addPage(3,midiPage());
        addPage(4,settingsPage());
        addNav("HOME",0);addNav("CAPTURE",1);addNav("MAPPING",2);addNav("MIDI",3);addNav("SETTINGS",4);
        showPage(0);
    }

    public void render(State state) {
        String connection=state.usbFound?(state.usbPermission?"CONNECTED · USB ACCESS GRANTED":"FOUND · PERMISSION REQUIRED"):"CONTROLLER NOT FOUND";
        homeConnection.setText(connection+"\nNative Instruments S4 MK2 · 17cc:1310");
        homeConnection.setTextColor(state.usbFound?(state.usbPermission?GREEN:AMBER):MUTED);
        homeLibrary.setText(String.format(Locale.US,"%d tracks  ·  %s",state.librarySize,state.selectedTrack));
        deckA.setText(deckText("DECK A",state.deckAState,state.deckAVolume,state.deckATempo,state.cueAMs));
        deckB.setText(deckText("DECK B",state.deckBState,state.deckBVolume,state.deckBTempo,state.cueBMs));
        crossfader.setText(String.format(Locale.US,"CROSSFADER   A  %.0f%%  ━━━●━━━  %.0f%%  B",(1f-state.crossfader)*100f,state.crossfader*100f));
        captureState.setText(state.captureRunning?"CAPTURING HID INPUT":"CAPTURE STOPPED");
        captureState.setTextColor(state.captureRunning?GREEN:MUTED);
        captureCounts.setText(String.format(Locale.US,"%d USB interfaces  ·  HID interface ID 4  ·  IN 0x84\nReport 01: %d packets  ·  Report 02: %d packets",state.interfaceCount,state.shortReports,state.longReports));
        captureEvent.setText(state.lastControl+"\n"+state.lastValue);
        mappingLive.setText("LIVE INPUT\n"+state.lastControl+"   "+state.lastValue);
        if(autoReconnect.isChecked()!=state.autoReconnect)autoReconnect.setChecked(state.autoReconnect);
    }

    public void appendEvent(String event) {
        captureLog.append(event+"\n");
        if(captureLog.length()>12000)captureLog.setText(captureLog.getText().subSequence(captureLog.length()-8000,captureLog.length()));
    }

    private String deckText(String name,String state,float volume,float tempo,int cueMs) {
        return String.format(Locale.US,"%s                         %s\nVOL  %.0f%%     TEMPO  %+.1f%%     CUE  %.1fs",name,state,volume*100f,tempo*100f,cueMs/1000f);
    }

    private View appBar() {
        LinearLayout bar=new LinearLayout(activity);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(20),0,dp(20),0);bar.setBackgroundColor(SURFACE);
        TextView brand=text("S4",24,TEXT);brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD);bar.addView(brand);
        TextView title=text("  S4BRIDGE",18,TEXT);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
        TextView prototype=badge("PROTOTYPE",CYAN);bar.addView(prototype);
        return bar;
    }

    private View homePage() {
        LinearLayout body=page("HOME","Controller and deck overview");
        homeConnection=text("",15,MUTED);body.addView(card(homeConnection));
        LinearLayout controls=row();controls.addView(action("SCAN",new View.OnClickListener(){public void onClick(View v){actions.scan();}}));controls.addView(action("USB ACCESS",new View.OnClickListener(){public void onClick(View v){actions.requestUsbPermission();}}));body.addView(controls);
        homeLibrary=text("",15,TEXT);body.addView(section("LIBRARY",homeLibrary));
        body.addView(action("ADD AUDIO DOCUMENTS",new View.OnClickListener(){public void onClick(View v){actions.addTracks();}}));
        LinearLayout browser=row();browser.addView(action("◀ PREVIOUS",new View.OnClickListener(){public void onClick(View v){actions.browse(-1);}}));browser.addView(action("NEXT ▶",new View.OnClickListener(){public void onClick(View v){actions.browse(1);}}));body.addView(browser);
        deckA=text("",15,TEXT);body.addView(section("",deckA));body.addView(action("LOAD SELECTED → DECK A",new View.OnClickListener(){public void onClick(View v){actions.loadDeck(true);}}));
        deckB=text("",15,TEXT);body.addView(section("",deckB));body.addView(action("LOAD SELECTED → DECK B",new View.OnClickListener(){public void onClick(View v){actions.loadDeck(false);}}));
        crossfader=text("",14,CYAN);crossfader.setGravity(Gravity.CENTER);body.addView(card(crossfader));
        return scroll(body);
    }

    private View capturePage() {
        LinearLayout body=page("CAPTURE","Live S4 MK2 USB/HID monitor");
        captureState=text("",17,MUTED);captureState.setTypeface(Typeface.DEFAULT,Typeface.BOLD);body.addView(card(captureState));
        LinearLayout controls=row();controls.addView(action("START CAPTURE",new View.OnClickListener(){public void onClick(View v){actions.startCapture();}}));controls.addView(action("STOP",new View.OnClickListener(){public void onClick(View v){actions.stopCapture();}}));body.addView(controls);
        captureCounts=text("",14,TEXT);body.addView(section("USB PATH",captureCounts));
        captureEvent=text("No controller event\n—",20,CYAN);body.addView(section("LATEST SEMANTIC EVENT",captureEvent));
        captureLog=text("",12,MUTED);captureLog.setTypeface(Typeface.MONOSPACE);body.addView(section("EVENT LOG",captureLog));
        return scroll(body);
    }

    private View mappingPage() {
        LinearLayout body=page("MAPPING","Known-good S4 MK2 semantic map");
        mappingLive=text("LIVE INPUT\nNo controller event",16,CYAN);body.addView(card(mappingLive));
        body.addView(mappingGroup("DECK A / B","PLAY · CUE · SYNC · SHIFT\nLOAD · JOG TOUCH · JOG RELATIVE\nHOTCUE 1–4 · LOOP · SLIP · FX"));
        body.addView(mappingGroup("MIXER","CHANNEL A/B VOLUME · EQ LOW/MID/HIGH\nFILTER · PREGAIN · PFL · CROSSFADER\n12-bit absolute values normalized 0…1"));
        body.addView(mappingGroup("BROWSER / ENCODERS","BROWSER · LOOP MOVE/SIZE · PREGAIN\n4-bit relative wrap correction enabled"));
        body.addView(note("Mapping is read-only in this prototype. The proven Mixxx-derived offsets and masks are preserved."));
        return scroll(body);
    }

    private View midiPage() {
        LinearLayout body=page("MIDI","Controller translation and routing");
        body.addView(preview("MIDI OUTPUT","PREVIEW · NOT IMPLEMENTED","Future HID-to-MIDI translation will appear here. No MIDI packets are currently emitted."));
        body.addView(disabledButton("CREATE VIRTUAL MIDI PORT"));
        body.addView(preview("MAPPING EXPORT","PREVIEW · NOT IMPLEMENTED","Export and import are intentionally disabled until the mapping format is defined."));
        body.addView(disabledButton("EXPORT MAPPING"));
        return scroll(body);
    }

    private View settingsPage() {
        LinearLayout body=page("SETTINGS","Connection and application behavior");
        autoReconnect=new Switch(activity);autoReconnect.setText("Auto-reconnect controller");autoReconnect.setTextColor(TEXT);autoReconnect.setTextSize(16);autoReconnect.setPadding(dp(16),dp(14),dp(16),dp(14));autoReconnect.setChecked(true);autoReconnect.setOnClickListener(new View.OnClickListener(){public void onClick(View v){actions.setAutoReconnect(autoReconnect.isChecked());}});body.addView(card(autoReconnect));
        body.addView(mappingGroup("TARGET HARDWARE","Traktor Kontrol S4 MK2\nUSB 17cc:1310 · interface ID 4 · endpoint 0x84"));
        body.addView(preview("AUDIO ENGINE","MEDIAPLAYER PROTOTYPE","Native low-latency audio, DSP, MIDI output, and LED output are not implemented in this milestone."));
        body.addView(note("S4Bridge prototype · GPL-2.0-or-later\nMapping derived from the Mixxx S4 MK2 controller mapping."));
        return scroll(body);
    }

    private LinearLayout page(String title,String subtitle) {LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(18),dp(16),dp(28));TextView heading=text(title,26,TEXT);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);body.addView(heading);TextView sub=text(subtitle,14,MUTED);sub.setPadding(0,0,0,dp(12));body.addView(sub);return body;}
    private View scroll(View child){ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.addView(child);return scroll;}
    private LinearLayout row(){LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.HORIZONTAL);return row;}
    private void addPage(int index,View page){pageViews[index]=page;pages.addView(page,new FrameLayout.LayoutParams(-1,-1));}
    private void addNav(String label,final int index){Button button=new Button(activity);button.setText(label);button.setTextSize(10);button.setAllCaps(false);button.setTextColor(MUTED);button.setBackgroundColor(SURFACE);button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showPage(index);}});navButtons[index]=button;nav.addView(button,new LinearLayout.LayoutParams(0,-1,1f));}
    private void showPage(int index){for(int i=0;i<pageViews.length;i++){pageViews[i].setVisibility(i==index?View.VISIBLE:View.GONE);navButtons[i].setTextColor(i==index?CYAN:MUTED);} }
    private TextView text(String value,float size,int color){TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setLineSpacing(0,1.12f);return view;}
    private TextView badge(String value,int color){TextView view=text(value,11,color);view.setPadding(dp(9),dp(5),dp(9),dp(5));view.setBackground(round(SURFACE_2,color));return view;}
    private View card(View child){LinearLayout box=new LinearLayout(activity);box.setPadding(dp(16),dp(14),dp(16),dp(14));box.setBackground(round(SURFACE,Color.TRANSPARENT));box.addView(child,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(6));box.setLayoutParams(lp);return box;}
    private View section(String label,View child){LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);if(label.length()>0){TextView heading=text(label,11,MUTED);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(heading);}box.addView(child);return card(box);}
    private View mappingGroup(String label,String value){return section(label,text(value,14,TEXT));}
    private View preview(String label,String status,String value){LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);LinearLayout top=row();TextView heading=text(label,15,TEXT);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1f));top.addView(badge(status,AMBER));box.addView(top);TextView description=text(value,13,MUTED);description.setPadding(0,dp(10),0,0);box.addView(description);return card(box);}
    private View note(String value){TextView note=text(value,13,MUTED);note.setPadding(dp(4),dp(12),dp(4),dp(12));return note;}
    private Button action(String label,View.OnClickListener listener){Button button=new Button(activity);button.setText(label);button.setTextColor(TEXT);button.setTextSize(12);button.setBackground(round(SURFACE_2,CYAN));button.setOnClickListener(listener);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1f);lp.setMargins(dp(3),dp(4),dp(3),dp(4));button.setLayoutParams(lp);return button;}
    private Button disabledButton(String label){Button button=action(label,null);button.setEnabled(false);button.setText(label+" · UNAVAILABLE");button.setAlpha(.45f);return button;}
    private GradientDrawable round(int fill,int stroke){GradientDrawable drawable=new GradientDrawable();drawable.setColor(fill);drawable.setCornerRadius(dp(12));if(stroke!=Color.TRANSPARENT)drawable.setStroke(dp(1),stroke);return drawable;}
    private int dp(int value){return (int)(value*activity.getResources().getDisplayMetrics().density+.5f);}
}
