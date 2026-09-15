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
import android.graphics.Color;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.Window;

import com.s4bridge.app.engine.DeckEngine;
import com.s4bridge.app.engine.MixerEngine;
import com.s4bridge.app.core.CueController;
import com.s4bridge.app.core.ControllerRouter;
import com.s4bridge.app.core.TrackLibrary;
import com.s4bridge.app.hardware.S4Mk2Mapping;
import com.s4bridge.app.ui.S4BridgeUi;

import java.util.Arrays;
import java.util.HashMap;

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
    private S4BridgeUi ui;
    private Handler uiHandler;
    private volatile boolean uiRefreshPending;
    private volatile long shortReports,longReports;
    private volatile String lastControl="No controller event",lastValue="—";
    private boolean autoReconnect=true;
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
                if(isS4(d)){device=d;log("S4 MK2 connected");if(autoReconnect){if(usbManager.hasPermission(d))openAndStart();else requestUsbPermission();}updateStatus();}
                return;
            }
            if(!ACTION_USB_PERMISSION.equals(action))return;
            boolean granted=i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false);
            if(granted&&isS4(d)){device=d;log("USB permission granted");if(autoReconnect)openAndStart();updateStatus();}
            else log("USB permission denied");
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(8,12,22));
        getWindow().setNavigationBarColor(Color.rgb(8,12,22));
        uiHandler=new Handler(Looper.getMainLooper());
        usbManager=(UsbManager)getSystemService(Context.USB_SERVICE);
        buildUi(); restoreLibrary(); registerUsb(); createMapping(); scan(); updateStatus();
    }

    private void buildUi(){
        ui=new S4BridgeUi(this,new S4BridgeUi.Actions(){
            public void scan(){MainActivity.this.scan();}
            public void requestUsbPermission(){MainActivity.this.requestUsbPermission();}
            public void startCapture(){openAndStart();}
            public void stopCapture(){MainActivity.this.stopCapture();}
            public void addTracks(){pickTracks();}
            public void browse(int delta){moveBrowser(delta);}
            public void loadDeck(boolean isDeckA){loadSelected(isDeckA?deckA:deckB,isDeckA?cueA:cueB);}
            public void setAutoReconnect(boolean enabled){autoReconnect=enabled;getPreferences(MODE_PRIVATE).edit().putBoolean("auto_reconnect",enabled).apply();log("Auto-reconnect "+(enabled?"enabled":"disabled"));}
        });
        autoReconnect=getPreferences(MODE_PRIVATE).getBoolean("auto_reconnect",true);
    }

    private void registerUsb(){
        IntentFilter f=new IntentFilter(ACTION_USB_PERMISSION);
        f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(usbReceiver,f,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(usbReceiver,f);
    }

    private void createMapping(){
        mapping=new S4Mk2Mapping(new S4Mk2Mapping.Listener(){
            public void onButton(String c,boolean p){
                lastControl=c;lastValue=p?"PRESSED":"RELEASED";
                log(c+" "+(p?"DOWN":"UP"));
                if(!controllerRouter.onButton(c,p)){
                    if("DECK_A_JOG_TOUCH".equals(c))deckA.setJogTouched(p);
                    else if("DECK_B_JOG_TOUCH".equals(c))deckB.setJogTouched(p);
                }
                updateStatus();
            }
            public void onAbsolute(String c,int raw,float n){
                lastControl=c;lastValue=raw+"  ·  "+String.format(java.util.Locale.US,"%.3f",n);
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
                updateStatus();
            }
            public void onRelative(String c,int d){lastControl=c;lastValue=(d>0?"+":"")+d;controllerRouter.onRelative(c,d);updateStatus();}
        });
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
        if(captureRunning||connection==null||inEndpoint==null)return;old01=null;old02=null;shortReports=0;longReports=0;captureRunning=true;
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
        if(p.length==0)return; int id=p[0]&0xff;
        if(id==1){shortReports++;if(old01==null){old01=Arrays.copyOf(p,p.length);log("REPORT 01 initialized ("+p.length+" bytes)");updateStatus();return;}mapping.parse(p,old01);old01=Arrays.copyOf(p,p.length);}
        else if(id==2){longReports++;if(old02==null){old02=Arrays.copyOf(p,p.length);log("REPORT 02 initialized ("+p.length+" bytes)");updateStatus();return;}mapping.parse(p,old02);old02=Arrays.copyOf(p,p.length);}
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
        if(ui==null||uiHandler==null||uiRefreshPending)return;
        uiRefreshPending=true;
        uiHandler.postDelayed(new Runnable(){public void run(){
            uiRefreshPending=false;
            TrackLibrary.Track selected=library.getSelected();
            S4BridgeUi.State state=new S4BridgeUi.State();
            state.usbFound=device!=null;state.usbPermission=device!=null&&usbManager.hasPermission(device);state.captureRunning=captureRunning;state.autoReconnect=autoReconnect;state.interfaceCount=device==null?0:device.getInterfaceCount();
            state.librarySize=library.getTracks().size();state.selectedIndex=library.getSelectedIndex();state.selectedTrack=selected==null?"No track selected":selected.getName();
            state.deckAState=deckA.isPlaying()?"PLAYING":(deckA.isLoaded()?"READY":"EMPTY");state.deckBState=deckB.isPlaying()?"PLAYING":(deckB.isLoaded()?"READY":"EMPTY");
            state.deckAVolume=deckA.getChannelVolume();state.deckBVolume=deckB.getChannelVolume();state.deckATempo=deckA.getTempo();state.deckBTempo=deckB.getTempo();state.crossfader=mixer.getCrossfader();state.cueAMs=cueA.getCuePointMs();state.cueBMs=cueB.getCuePointMs();
            state.shortReports=shortReports;state.longReports=longReports;state.lastControl=lastControl;state.lastValue=lastValue;ui.render(state);
        }},50L);
    }

    private void stopCapture(){Thread oldThread=captureThread;captureRunning=false;captureThread=null;if(oldThread!=null)oldThread.interrupt();updateStatus();}
    private void closeConnection(){if(connection!=null){try{if(hidInterface!=null)connection.releaseInterface(hidInterface);}catch(Exception ignored){}try{connection.close();}catch(Exception ignored){}}connection=null;hidInterface=null;inEndpoint=null;}

    private void log(final String s){if(ui==null)return;runOnUiThread(new Runnable(){public void run(){ui.appendEvent(s);}});}

    @Override protected void onResume(){super.onResume();if(device==null)scan();if(autoReconnect&&device!=null&&usbManager.hasPermission(device)&&!captureRunning)openAndStart();}

    @Override protected void onDestroy(){stopCapture();closeConnection();deckA.release();deckB.release();try{unregisterReceiver(usbReceiver);}catch(Exception ignored){}super.onDestroy();}
}
