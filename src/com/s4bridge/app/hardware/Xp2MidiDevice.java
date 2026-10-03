package com.s4bridge.app.hardware;

import android.content.Context;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiReceiver;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

/** Android owns USB-MIDI framing; this backend never claims the S4 HID interface.
 * Public methods and listener callbacks run on the main looper.
 */
public final class Xp2MidiDevice {
    public interface Listener {
        void onStatus(String status);
        void onMessage(int port, int status, int data1, int data2);
        void onDisconnected();
    }
    private final MidiManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final ArrayList<MidiOutputPort> ports = new ArrayList<MidiOutputPort>();
    private MidiDevice device;
    private MidiInputPort feedback;
    private final Xp2LedFeedback leds;
    private int deviceId = -1;
    private volatile int generation;
    private boolean started;
    private final MidiManager.DeviceCallback callback = new MidiManager.DeviceCallback() {
        @Override public void onDeviceAdded(MidiDeviceInfo info) { scan(); }
        @Override public void onDeviceRemoved(MidiDeviceInfo info) {
            if (info.getId() == deviceId) { disconnect(); listener.onStatus("XP2 disconnected"); scan(); }
        }
    };
    public Xp2MidiDevice(Context context, Listener listener) {
        manager = (MidiManager) context.getSystemService(Context.MIDI_SERVICE);
        this.listener = listener;
        leds=new Xp2LedFeedback(new Xp2LedFeedback.Output(){public boolean send(byte[] message){
            if(feedback==null)return false;
            try{feedback.send(message,0,message.length);return true;}
            catch(IOException e){try{feedback.close();}catch(IOException ignored){}feedback=null;Xp2MidiDevice.this.listener.onStatus("XP2 input active · LED output failed");return false;}
        }});
    }
    public void start() {
        if (started) return;
        started = true;
        if (manager == null) { listener.onStatus("Android MIDI service unavailable"); return; }
        manager.registerDeviceCallback(callback, handler);
        scan();
    }
    public void scan() {
        if (!started || manager == null || deviceId != -1) return;
        for (MidiDeviceInfo info : manager.getDevices()) {
            String name = info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME, "");
            String product = info.getProperties().getString(MidiDeviceInfo.PROPERTY_PRODUCT, "");
            if (info.getType() != MidiDeviceInfo.TYPE_USB
                || !(name + " " + product).toUpperCase(Locale.US).contains("DDJ-XP2")) continue;
            open(info);
            return;
        }
        listener.onStatus("XP2 not found · connect USB and rescan");
    }
    private void open(final MidiDeviceInfo info) {
        deviceId = info.getId();
        final int token = ++generation;
        listener.onStatus("Opening XP2…");
        manager.openDevice(info, new MidiManager.OnDeviceOpenedListener() {
            @Override public void onDeviceOpened(MidiDevice opened) {
                if (!started || token != generation) { closeDevice(opened); return; }
                if (opened == null) { disconnect(); listener.onStatus("XP2 open failed · rescan to retry"); return; }
                device = opened;
                feedback=opened.openInputPort(0);
                invalidateLeds();
                for (MidiDeviceInfo.PortInfo portInfo : info.getPorts()) {
                    if (portInfo.getType() != MidiDeviceInfo.PortInfo.TYPE_OUTPUT) continue;
                    final int portNumber = portInfo.getPortNumber();
                    MidiOutputPort port = opened.openOutputPort(portNumber);
                    if (port == null) continue;
                    final MidiStreamParser parser = new MidiStreamParser(new MidiStreamParser.Listener() {
                        public void onMessage(final int status, final int data1, final int data2) {
                            handler.post(new Runnable() { public void run() {
                                if (started && token == generation){
                                    if((status&0xf0)==0x80||(status&0xf0)==0x90)leds.invalidate(status&15,data1);
                                    listener.onMessage(portNumber, status, data1, data2);
                                }
                            }});
                        }
                    });
                    port.connect(new MidiReceiver() {
                        @Override public void onSend(byte[] data, int offset, int count, long timestamp) {
                            synchronized (parser) { parser.accept(data, offset, count); }
                        }
                        @Override public void onFlush() { synchronized (parser) { parser.reset(); } }
                    });
                    ports.add(port);
                }
                if (ports.isEmpty()) { disconnect(); listener.onStatus("XP2 has no available MIDI output ports"); }
                else listener.onStatus("XP2 connected · " + ports.size() + " MIDI input stream(s) · LEDs "+(feedback==null?"unavailable":"ready"));
            }
        }, handler);
    }
    private void disconnect() {
        generation++;
        for (MidiOutputPort port : ports) try { port.close(); } catch (IOException ignored) {}
        ports.clear();
        if(feedback!=null)try{feedback.close();}catch(IOException ignored){}
        feedback=null;invalidateLeds();
        closeDevice(device);
        device = null;
        deviceId = -1;
        listener.onDisconnected();
    }
    private static void closeDevice(MidiDevice value) {
        if (value != null) try { value.close(); } catch (IOException ignored) {}
    }
    public void invalidateLeds(){leds.invalidate();}
    public void led(int channel,int note,boolean on){leds.set(channel,note,on);}
    public void stop() {
        if (!started) return;
        started = false;
        if (manager != null) manager.unregisterDeviceCallback(callback);
        disconnect();
    }
}
