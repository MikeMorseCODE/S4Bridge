package com.s4bridge.app.hardware;

import android.content.Context;
import android.media.midi.MidiManager;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.Locale;

/** Android owns USB MIDI enumeration and permission; never claim the S4 HID interface here. */
public final class Xp2MidiInput {
    public interface Listener { void onBytes(byte[] data,int offset,int count); void onStatus(String status); }
    private final MidiManager manager;
    private final Listener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private MidiDevice device;
    private MidiOutputPort port;
    private int generation, deviceId=-1;
    private boolean closed;
    private final MidiManager.DeviceCallback callback=new MidiManager.DeviceCallback(){
        @Override public void onDeviceAdded(MidiDeviceInfo info){connect();}
        @Override public void onDeviceRemoved(MidiDeviceInfo info){if(info.getId()==deviceId){disconnect();listener.onStatus("XP2 disconnected");connect();}}
    };
    public Xp2MidiInput(Context context,Listener listener){
        this.listener=listener;manager=(MidiManager)context.getSystemService(Context.MIDI_SERVICE);
        if(manager!=null){manager.registerDeviceCallback(callback,handler);connect();}
        else listener.onStatus("Android MIDI unavailable");
    }
    public void reconnect(){disconnect();connect();}
    private void connect(){
        if(closed||manager==null||deviceId!=-1)return;
        for(final MidiDeviceInfo info:manager.getDevices()){
            String name=info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME,"")+" "+info.getProperties().getString(MidiDeviceInfo.PROPERTY_PRODUCT,"");
            if(info.getType()!=MidiDeviceInfo.TYPE_USB||!name.toUpperCase(Locale.US).replace("-","").replace("_","").replace(" ","").contains("XP2"))continue;
            deviceId=info.getId();final int token=++generation;
            manager.openDevice(info,new MidiManager.OnDeviceOpenedListener(){public void onDeviceOpened(MidiDevice opened){
                if(closed||token!=generation){if(opened!=null)try{opened.close();}catch(IOException ignored){}return;}
                if(opened==null){deviceId=-1;listener.onStatus("XP2 MIDI open failed; tap reconnect");return;}
                device=opened;
                for(MidiDeviceInfo.PortInfo p:info.getPorts())if(p.getType()==MidiDeviceInfo.PortInfo.TYPE_OUTPUT){port=device.openOutputPort(p.getPortNumber());if(port!=null)break;}
                if(port==null){disconnect();listener.onStatus("XP2 has no available MIDI output port");return;}
                port.connect(new MidiReceiver(){@Override public void onSend(byte[] data,int offset,int count,long timestamp){listener.onBytes(data,offset,count);}});
                listener.onStatus("XP2 MIDI connected");
            }},handler);
            listener.onStatus("Opening XP2 MIDI…");return;
        }
        listener.onStatus("XP2 not found · connect USB and tap reconnect");
    }
    private void disconnect(){++generation;deviceId=-1;if(port!=null)try{port.close();}catch(IOException ignored){}port=null;if(device!=null)try{device.close();}catch(IOException ignored){}device=null;}
    public void close(){closed=true;if(manager!=null)manager.unregisterDeviceCallback(callback);disconnect();}
}
