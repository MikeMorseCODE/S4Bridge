package com.s4bridge.app.hardware;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.*;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Explicit USB-host fallback when Android does not expose a controller through MidiManager. */
public final class DirectUsbMidiDevice {
    private static final String PERMISSION="com.s4bridge.app.XP2_USB_PERMISSION";
    private final Context context;
    private final UsbManager manager;
    private final Xp2MidiDevice.Listener listener;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private Session session;
    private String pendingName;
    private boolean registered;
    private int generation;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
        UsbDevice d=intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if(d==null)return;
        if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())){
            if(d.getDeviceName().equals(pendingName)||(session!=null&&d.getDeviceName().equals(session.device.getDeviceName()))){disconnect();listener.onStatus("USB MIDI disconnected · reconnect and tap Connect USB");}
        }else if(PERMISSION.equals(intent.getAction())&&d.getDeviceName().equals(pendingName)){
            pendingName=null;
            if(manager.hasPermission(d))open(d);else listener.onStatus("USB access denied · tap Connect USB to retry");
        }
    }};
    public DirectUsbMidiDevice(Context context,Xp2MidiDevice.Listener listener){this.context=context;this.listener=listener;manager=(UsbManager)context.getSystemService(Context.USB_SERVICE);}
    public void start(){
        if(registered)return;IntentFilter f=new IntentFilter(PERMISSION);f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else context.registerReceiver(receiver,f);registered=true;
    }
    public static boolean isMidi(UsbInterface intf){
        // MIDI 2.0 UMP uses different framing; do not feed it to the MIDI 1 parser.
        return intf.getInterfaceClass()==UsbConstants.USB_CLASS_AUDIO&&intf.getInterfaceSubclass()==3&&intf.getInterfaceProtocol()==0;
    }
    public static boolean eligible(UsbDevice d){
        for(int i=0;i<d.getInterfaceCount();i++){UsbInterface intf=d.getInterface(i);if(!isMidi(intf))continue;
            for(int j=0;j<intf.getEndpointCount();j++){UsbEndpoint ep=intf.getEndpoint(j);if(ep.getDirection()==UsbConstants.USB_DIR_IN&&ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK)return true;}}
        return false;
    }
    public static String label(UsbDevice d){
        String name=null;try{name=d.getProductName();}catch(SecurityException ignored){}
        return (name==null?"USB device":name)+String.format(Locale.US," [%04x:%04x]",d.getVendorId(),d.getProductId());
    }
    public boolean active(){return session!=null||pendingName!=null;}
    public void connect(UsbDevice d){
        disconnect();if(!registered)start();
        if(!eligible(d)){listener.onStatus("No MIDI 1.0 bulk input on "+label(d));return;}
        if(manager.hasPermission(d)){open(d);return;}
        pendingName=d.getDeviceName();
        Intent request=new Intent(PERMISSION).setPackage(context.getPackageName());
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);
        try{manager.requestPermission(d,PendingIntent.getBroadcast(context,31,request,flags));listener.onStatus("USB detected · allow Android's USB access prompt for "+label(d));}
        catch(RuntimeException e){pendingName=null;listener.onStatus("USB permission request failed: "+e.getMessage());}
    }
    private void open(final UsbDevice d){
        UsbDeviceConnection connection=null;
        try{
            connection=manager.openDevice(d);if(connection==null){listener.onStatus("USB open failed · reconnect and grant access");return;}
            for(int i=0;i<d.getInterfaceCount();i++){
                UsbInterface intf=d.getInterface(i);if(!isMidi(intf))continue;UsbEndpoint input=null,output=null;
                for(int j=0;j<intf.getEndpointCount();j++){UsbEndpoint ep=intf.getEndpoint(j);if(ep.getType()!=UsbConstants.USB_ENDPOINT_XFER_BULK)continue;if(ep.getDirection()==UsbConstants.USB_DIR_IN)input=ep;else output=ep;}
                if(input==null||!connection.claimInterface(intf,true))continue;
                if(intf.getAlternateSetting()!=0&&!connection.setInterface(intf)){connection.releaseInterface(intf);continue;}
                final Session next=new Session(d,connection,intf,input,output,++generation);session=next;next.start();
                listener.onStatus("USB MIDI connected · "+label(d)+" · interface "+intf.getId()+" · waiting for pads");return;
            }
            connection.close();listener.onStatus("USB detected but MIDI interface could not be claimed · close other MIDI apps");
        }catch(RuntimeException e){if(connection!=null)connection.close();session=null;listener.onStatus("USB open failed: "+e.getMessage());}
    }
    public boolean send(byte[] message){return session!=null&&session.output!=null&&!session.outputFailed&&session.queue.offer(UsbMidiPackets.encode(0,message));}
    public void disconnect(){generation++;pendingName=null;Session old=session;session=null;if(old!=null)old.close();listener.onDisconnected();}
    public void stop(){disconnect();if(registered){context.unregisterReceiver(receiver);registered=false;}}
    private final class Session {
        final UsbDevice device;final UsbDeviceConnection connection;final UsbInterface intf;final UsbEndpoint input,output;final int token;
        final ArrayBlockingQueue<byte[]> queue=new ArrayBlockingQueue<byte[]>(256);
        volatile boolean closed,outputFailed;
        Thread reader,writer;
        Session(UsbDevice d,UsbDeviceConnection c,UsbInterface i,UsbEndpoint in,UsbEndpoint out,int token){device=d;connection=c;intf=i;input=in;output=out;this.token=token;}
        void start(){
            reader=new Thread(new Runnable(){public void run(){read();}},"XP2-USB-IN");reader.start();
            if(output!=null){writer=new Thread(new Runnable(){public void run(){write();}},"XP2-USB-OUT");writer.start();}
        }
        void read(){
            UsbMidiPackets parser=new UsbMidiPackets(new UsbMidiPackets.Listener(){public void message(final int cable,final int status,final int first,final int second){
                ui.post(new Runnable(){public void run(){if(!closed&&generation==token)listener.onMessage(cable,status,first,second);}});
            }});
            byte[] bytes=new byte[Math.max(64,input.getMaxPacketSize())];
            try{while(!closed){int count=connection.bulkTransfer(input,bytes,bytes.length,250);if(count>0)parser.accept(bytes,count);}}
            catch(final RuntimeException e){ui.post(new Runnable(){public void run(){if(generation==token){disconnect();listener.onStatus("USB MIDI read stopped: "+e.getMessage());}}});}
        }
        void write(){
            try{while(!closed){byte[] packet=queue.poll(250,TimeUnit.MILLISECONDS);if(packet==null)continue;
                if(connection.bulkTransfer(output,packet,packet.length,100)!=packet.length){outputFailed=true;queue.clear();ui.post(new Runnable(){public void run(){if(!closed&&generation==token)listener.onStatus("USB MIDI input connected · LED write failed");}});break;}}}
            catch(InterruptedException ignored){Thread.currentThread().interrupt();}
            catch(RuntimeException e){outputFailed=true;}
        }
        void close(){closed=true;if(reader!=null)reader.interrupt();if(writer!=null)writer.interrupt();
            // In-flight operations use bounded timeouts; closing wakes USB I/O and invalidates callbacks.
            try{connection.releaseInterface(intf);}catch(Exception ignored){}connection.close();queue.clear();
        }
    }
}
