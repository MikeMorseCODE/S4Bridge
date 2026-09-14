package com.s4bridge.app;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.s4bridge.app.engine.DeckEngine;
import com.s4bridge.app.engine.MixerEngine;
import com.s4bridge.app.hardware.S4Mk2Mapping;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int VID=0x17CC, PID=0x1310, AUDIO_PERMISSION_REQUEST=1001;
    private static final String ACTION_USB_PERMISSION="com.s4bridge.app.USB_PERMISSION";
    private static final String TEST_TRACK_A="/sdcard/Music/Virtual DJ Mixer/Virtual DJ Mixer 2026_08_26 02_02_01.mp3";
    private static final String TEST_TRACK_B="/sdcard/Music/Becky Hill - Afterglow (Acoustic).opus";

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
    private S4Mk2Mapping mapping;

    private final BroadcastReceiver usbReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(!ACTION_USB_PERMISSION.equals(i.getAction())) return;
            UsbDevice d;
            if(Build.VERSION.SDK_INT>=33) d=i.getParcelableExtra(UsbManager.EXTRA_DEVICE,UsbDevice.class);
            else d=i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            boolean granted=i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false);
            if(granted&&d!=null){ device=d; log("USB permission granted"); updateStatus(); }
            else log("USB permission denied");
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        usbManager=(UsbManager)getSystemService(Context.USB_SERVICE);
        buildUi(); registerUsb(); createMapping(); scan(); ensureAudioPermissionAndLoad(); updateStatus();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(20,20,20,20);
        TextView title=new TextView(this); title.setText("S4Bridge DJ — reconstructed baseline"); title.setTextSize(22); root.addView(title);
        status=new TextView(this); status.setTextSize(16); root.addView(status);
        addButton(root,"SCAN FOR S4 MK2",new View.OnClickListener(){public void onClick(View v){scan();}});
        addButton(root,"REQUEST USB PERMISSION",new View.OnClickListener(){public void onClick(View v){requestUsbPermission();}});
        addButton(root,"OPEN + START HID",new View.OnClickListener(){public void onClick(View v){openAndStart();}});
        addButton(root,"STOP HID",new View.OnClickListener(){public void onClick(View v){stopCapture();}});
        addButton(root,"RELOAD BOTH DECKS",new View.OnClickListener(){public void onClick(View v){ensureAudioPermissionAndLoad();}});
        logView=new TextView(this); logView.setTextIsSelectable(true); logScroll=new ScrollView(this); logScroll.addView(logView);
        root.addView(logScroll,new LinearLayout.LayoutParams(-1,0,1f)); setContentView(root);
    }

    private void addButton(LinearLayout root,String text,View.OnClickListener l){ Button b=new Button(this); b.setText(text); b.setOnClickListener(l); root.addView(b); }

    private void registerUsb(){
        IntentFilter f=new IntentFilter(ACTION_USB_PERMISSION);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(usbReceiver,f,Context.RECEIVER_NOT_EXPORTED); else registerReceiver(usbReceiver,f);
    }

    private void createMapping(){
        mapping=new S4Mk2Mapping(new S4Mk2Mapping.Listener(){
            public void onButton(String c,boolean p){
                log(c+" "+(p?"DOWN":"UP"));
                if("DECK_A_PLAY".equals(c)&&p){deckA.togglePlay();log("ENGINE A playing="+deckA.isPlaying());}
                else if("DECK_B_PLAY".equals(c)&&p){deckB.togglePlay();log("ENGINE B playing="+deckB.isPlaying());}
                else if("DECK_A_JOG_TOUCH".equals(c)) deckA.setJogTouched(p);
                else if("DECK_B_JOG_TOUCH".equals(c)) deckB.setJogTouched(p);
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
            public void onRelative(String c,int d){ if("DECK_A_JOG".equals(c))deckA.jog(d); else if("DECK_B_JOG".equals(c))deckB.jog(d); }
        });
    }

    private void scan(){
        HashMap<String,UsbDevice> list=usbManager.getDeviceList(); device=null;
        for(UsbDevice d:list.values()) if(d.getVendorId()==VID&&d.getProductId()==PID){device=d;break;}
        log(device==null?"S4 MK2 not found":"S4 MK2 found; interfaces="+device.getInterfaceCount()+" permission="+usbManager.hasPermission(device)); updateStatus();
    }

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
        byte[] buf=new byte[Math.max(64,inEndpoint.getMaxPacketSize())];
        while(captureRunning){
            int len;
            try{len=connection.bulkTransfer(inEndpoint,buf,buf.length,250);}catch(Exception e){break;}
            if(len<=0)continue; handlePacket(Arrays.copyOf(buf,len));
        }
        captureRunning=false; updateStatus();
    }

    private void handlePacket(byte[] p){
        if(p.length==0)return; int id=p[0]&0xff;
        if(id==1){ if(old01==null){old01=Arrays.copyOf(p,p.length);log("REPORT 01 initialized ("+p.length+" bytes)");return;} mapping.parse(p,old01);old01=Arrays.copyOf(p,p.length); }
        else if(id==2){ if(old02==null){old02=Arrays.copyOf(p,p.length);log("REPORT 02 initialized ("+p.length+" bytes)");return;} mapping.parse(p,old02);old02=Arrays.copyOf(p,p.length); }
    }

    private void ensureAudioPermissionAndLoad(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO},AUDIO_PERMISSION_REQUEST);return;}
        if(Build.VERSION.SDK_INT>=23&&Build.VERSION.SDK_INT<33&&checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},AUDIO_PERMISSION_REQUEST);return;}
        loadTracks();
    }

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==AUDIO_PERMISSION_REQUEST&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)loadTracks();}

    private void loadTracks(){
        boolean a=deckA.loadFile(TEST_TRACK_A), b=deckB.loadFile(TEST_TRACK_B); mixer.setCrossfader(mixer.getCrossfader());
        log("DECK A load="+a); log("DECK B load="+b); updateStatus();
    }

    private void updateStatus(){
        if(status==null)return; runOnUiThread(new Runnable(){public void run(){
            status.setText(String.format(Locale.US,"USB %s | HID %s\nA %s vol %.3f tempo %+.3f\nB %s vol %.3f tempo %+.3f\nCrossfader %.3f",
                    device==null?"OFF":"FOUND",captureRunning?"RUNNING":"STOPPED",deckA.isPlaying()?"PLAYING":(deckA.isLoaded()?"READY":"EMPTY"),deckA.getChannelVolume(),deckA.getTempo(),deckB.isPlaying()?"PLAYING":(deckB.isLoaded()?"READY":"EMPTY"),deckB.getChannelVolume(),deckB.getTempo(),mixer.getCrossfader()));
        }});
    }

    private void stopCapture(){captureRunning=false;if(captureThread!=null)captureThread.interrupt();captureThread=null;updateStatus();}
    private void closeConnection(){if(connection!=null){try{if(hidInterface!=null)connection.releaseInterface(hidInterface);}catch(Exception ignored){}try{connection.close();}catch(Exception ignored){}}connection=null;hidInterface=null;inEndpoint=null;}

    private void log(final String s){if(logView==null)return;runOnUiThread(new Runnable(){public void run(){logView.append(s+"\n");if(logScroll!=null)logScroll.post(new Runnable(){public void run(){logScroll.fullScroll(View.FOCUS_DOWN);}});}});}

    @Override protected void onDestroy(){stopCapture();closeConnection();deckA.release();deckB.release();try{unregisterReceiver(usbReceiver);}catch(Exception ignored){}super.onDestroy();}
}
