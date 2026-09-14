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
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.s4bridge.app.engine.DeckEngine;
import com.s4bridge.app.engine.MixerEngine;
import com.s4bridge.app.core.CueController;
import com.s4bridge.app.core.ControllerRouter;
import com.s4bridge.app.core.TrackLibrary;
import com.s4bridge.app.hardware.S4Mk2Mapping;

import java.util.Arrays;
import java.util.HashMap;
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
    private TextView status, logView;
    private ScrollView logScroll;
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
                if(isS4(d)){device=d;log("S4 MK2 connected");if(usbManager.hasPermission(d))openAndStart();else requestUsbPermission();updateStatus();}
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
        buildUi(); restoreLibrary(); registerUsb(); createMapping(); scan(); updateStatus();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(20,20,20,20);
        TextView title=new TextView(this); title.setText("S4Bridge DJ — reconstructed baseline"); title.setTextSize(22); root.addView(title);
        status=new TextView(this); status.setTextSize(16); root.addView(status);
        addButton(root,"SCAN FOR S4 MK2",new View.OnClickListener(){public void onClick(View v){scan();}});
        addButton(root,"REQUEST USB PERMISSION",new View.OnClickListener(){public void onClick(View v){requestUsbPermission();}});
        addButton(root,"OPEN + START HID",new View.OnClickListener(){public void onClick(View v){openAndStart();}});
        addButton(root,"STOP HID",new View.OnClickListener(){public void onClick(View v){stopCapture();}});
        addButton(root,"ADD TRACKS TO LIBRARY",new View.OnClickListener(){public void onClick(View v){pickTracks();}});
        addButton(root,"PREVIOUS LIBRARY TRACK",new View.OnClickListener(){public void onClick(View v){moveBrowser(-1);}});
        addButton(root,"NEXT LIBRARY TRACK",new View.OnClickListener(){public void onClick(View v){moveBrowser(1);}});
        addButton(root,"LOAD SELECTED TO DECK A",new View.OnClickListener(){public void onClick(View v){loadSelected(deckA,cueA);}});
        addButton(root,"LOAD SELECTED TO DECK B",new View.OnClickListener(){public void onClick(View v){loadSelected(deckB,cueB);}});
        logView=new TextView(this); logView.setTextIsSelectable(true); logScroll=new ScrollView(this); logScroll.addView(logView);
        root.addView(logScroll,new LinearLayout.LayoutParams(-1,0,1f)); setContentView(root);
    }

    private void addButton(LinearLayout root,String text,View.OnClickListener l){ Button b=new Button(this); b.setText(text); b.setOnClickListener(l); root.addView(b); }

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
                updateStatus();
            }
            public void onRelative(String c,int d){controllerRouter.onRelative(c,d);}
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
        if(captureRunning||connection==null||inEndpoint==null)return; old01=null;old02=null;captureRunning=true;
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
        if(status==null)return; runOnUiThread(new Runnable(){public void run(){
            TrackLibrary.Track selected=library.getSelected();
            status.setText(String.format(Locale.US,"USB %s | HID %s\nLibrary %d: %s\nA %s vol %.3f tempo %+.3f cue %.1fs\nB %s vol %.3f tempo %+.3f cue %.1fs\nCrossfader %.3f",
                    device==null?"OFF":"FOUND",captureRunning?"RUNNING":"STOPPED",library.getTracks().size(),selected==null?"(empty)":selected.getName(),deckA.isPlaying()?"PLAYING":(deckA.isLoaded()?"READY":"EMPTY"),deckA.getChannelVolume(),deckA.getTempo(),cueA.getCuePointMs()/1000.0f,deckB.isPlaying()?"PLAYING":(deckB.isLoaded()?"READY":"EMPTY"),deckB.getChannelVolume(),deckB.getTempo(),cueB.getCuePointMs()/1000.0f,mixer.getCrossfader()));
        }});
    }

    private void stopCapture(){Thread oldThread=captureThread;captureRunning=false;captureThread=null;if(oldThread!=null)oldThread.interrupt();updateStatus();}
    private void closeConnection(){if(connection!=null){try{if(hidInterface!=null)connection.releaseInterface(hidInterface);}catch(Exception ignored){}try{connection.close();}catch(Exception ignored){}}connection=null;hidInterface=null;inEndpoint=null;}

    private void log(final String s){if(logView==null)return;runOnUiThread(new Runnable(){public void run(){logView.append(s+"\n");if(logScroll!=null)logScroll.post(new Runnable(){public void run(){logScroll.fullScroll(View.FOCUS_DOWN);}});}});}

    @Override protected void onResume(){super.onResume();if(device==null)scan();if(device!=null&&usbManager.hasPermission(device)&&!captureRunning)openAndStart();}

    @Override protected void onDestroy(){stopCapture();closeConnection();deckA.release();deckB.release();try{unregisterReceiver(usbReceiver);}catch(Exception ignored){}super.onDestroy();}
}
