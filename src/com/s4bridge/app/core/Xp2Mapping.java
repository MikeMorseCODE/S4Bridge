package com.s4bridge.app.core;

import java.util.ArrayList;

/** Imported from the supplied DDJ XP2 V9 XML. Channels are zero-based on the wire. */
public final class Xp2Mapping {
    public interface Listener { void onAction(String action, int deck, int slot, boolean pressed); }
    private static final class Binding {
        int channel, note, deck, slot; String action, edge;
        Binding(int c,int n,String a,int d,int s,String e){channel=c;note=n;action=a;deck=d;slot=s;edge=e;}
    }
    private final ArrayList<Binding> bindings=new ArrayList<Binding>();
    private final Listener listener;
    private int runningStatus, count, first;
    public Xp2Mapping(Listener listener){
        this.listener=listener;
        add(0, 20, "auto_loop_enable", 0, 0, "press");
        add(9, 43, "cue_point", 1, 3, "any");
        add(0, 104, "mute", 0, 0, "press");
        add(7, 79, "codfather_fx", 0, 3, "press");
        add(0, 10, "pitch_bend_down", 0, 0, "any");
        add(7, 73, "codfather_st", 0, 1, "press");
        add(1, 10, "pitch_bend_down", 1, 0, "any");
        add(1, 40, "dvs_playback_mode_request", 1, 0, "press");
        add(1, 48, "dvs_playback_mode_request", 1, 0, "press");
        add(1, 48, "dvs_sticker_lock_enabled", 0, 0, "press");
        add(9, 45, "cue_point", 1, 5, "any");
        add(9, 72, "codfather_st", 1, 0, "press");
        add(7, 68, "cue_point", 0, 4, "any");
        add(7, 71, "cue_point", 0, 7, "any");
        add(7, 42, "cue_point", 0, 2, "any");
        add(9, 66, "cue_point", 1, 2, "any");
        add(0, 56, "dvs_playback_mode_request", 0, 0, "press");
        add(6, 89, "add_song_to_prepare_crate", 0, 0, "press");
        add(9, 41, "cue_point", 1, 1, "any");
        add(7, 45, "cue_point", 0, 5, "any");
        add(0, 96, "bpm_transition", 1, 0, "press");
        add(1, 96, "bpm_transition", 0, 0, "press");
        add(1, 56, "dvs_playback_mode_request", 1, 0, "press");
        add(0, 57, "dvs_sticker_lock_enabled", 0, 0, "press");
        add(9, 69, "cue_point", 1, 5, "any");
        add(1, 76, "auto_loop_specific_length", 1, 7, "any");
        add(9, 71, "cue_point", 1, 7, "any");
        add(7, 74, "codfather_st", 0, 2, "press");
        add(7, 77, "codfather_fx", 0, 1, "press");
        add(9, 78, "codfather_fx", 1, 2, "press");
        add(9, 79, "codfather_fx", 1, 3, "press");
        add(1, 121, "pitch_bend_up", 1, 0, "any");
        add(0, 40, "dvs_playback_mode_request", 0, 0, "press");
        add(0, 21, "dvs_sticker_lock_enabled", 0, 0, "press");
        add(0, 21, "dvs_sticker_lock_enabled", 0, 0, "release");
        add(0, 21, "dvs_playback_mode_request", 0, 0, "release");
        add(0, 21, "dvs_playback_mode_request", 0, 0, "press");
        add(0, 21, "load_track", 0, 0, "press");
        add(9, 42, "cue_point", 1, 2, "any");
        add(7, 43, "cue_point", 0, 3, "any");
        add(7, 40, "cue_point", 0, 0, "any");
        add(1, 104, "mute", 1, 0, "press");
        add(7, 72, "codfather_st", 0, 0, "press");
        add(0, 77, "auto_loop_specific_length", 0, 8, "any");
        add(0, 121, "pitch_bend_up", 0, 0, "any");
        add(1, 21, "dvs_sticker_lock_enabled", 0, 0, "press");
        add(1, 21, "dvs_sticker_lock_enabled", 0, 0, "release");
        add(1, 21, "dvs_playback_mode_request", 1, 0, "release");
        add(1, 21, "dvs_playback_mode_request", 1, 0, "press");
        add(1, 21, "load_track", 1, 0, "press");
        add(7, 44, "cue_point", 0, 4, "any");
        add(7, 69, "cue_point", 0, 5, "any");
        add(7, 64, "cue_point", 0, 0, "any");
        add(9, 47, "cue_point", 1, 7, "any");
        add(9, 65, "cue_point", 1, 1, "any");
        add(7, 67, "cue_point", 0, 3, "any");
        add(9, 73, "codfather_st", 1, 1, "press");
        add(9, 64, "cue_point", 1, 0, "any");
        add(9, 75, "codfather_st", 1, 3, "press");
        add(9, 46, "cue_point", 1, 6, "any");
        add(9, 68, "cue_point", 1, 4, "any");
        add(9, 76, "codfather_fx", 1, 0, "press");
        add(7, 75, "codfather_st", 0, 3, "press");
        add(1, 20, "auto_loop_enable", 1, 0, "press");
        add(7, 66, "cue_point", 0, 2, "any");
        add(7, 70, "cue_point", 0, 6, "any");
        add(7, 46, "cue_point", 0, 6, "any");
        add(1, 77, "auto_loop_specific_length", 1, 8, "any");
        add(7, 47, "cue_point", 0, 7, "any");
        add(9, 74, "codfather_st", 1, 2, "press");
        add(0, 76, "auto_loop_specific_length", 0, 7, "any");
        add(7, 41, "cue_point", 0, 1, "any");
        add(9, 70, "cue_point", 1, 6, "any");
        add(7, 78, "codfather_fx", 0, 2, "press");
        add(9, 40, "cue_point", 1, 0, "any");
        add(9, 67, "cue_point", 1, 3, "any");
        add(7, 76, "codfather_fx", 0, 0, "press");
        add(9, 44, "cue_point", 1, 4, "any");
        add(9, 77, "codfather_fx", 1, 1, "press");
        add(1, 57, "pntdj_plugin_enable", 0, 0, "press");
        add(6, 88, "add_song_to_prepare_crate", 0, 0, "press");
        add(7, 65, "cue_point", 0, 1, "any");
        add(0, 48, "dvs_playback_mode_request", 0, 0, "press");
        add(0, 48, "dvs_sticker_lock_enabled", 0, 0, "press");
        add(1, 96, "bpm_transition", 0, 0, "press");
        add(9, 68, "cue_point", 1, 4, "any");
        add(0, 121, "pitch_bend_up", 0, 0, "any");
        add(1, 56, "dvs_playback_mode_request", 1, 0, "press");
        add(0, 57, "dvs_sticker_lock_enabled", 0, 0, "press");
        add(7, 78, "codfather_fx", 0, 2, "press");
        add(9, 70, "cue_point", 1, 6, "any");
        add(9, 74, "codfather_st", 1, 2, "press");
        add(9, 69, "cue_point", 1, 5, "any");
        add(1, 76, "auto_loop_specific_length", 1, 7, "any");
        add(1, 20, "auto_loop_enable", 1, 0, "press");
        add(7, 75, "codfather_st", 0, 3, "press");
        add(9, 73, "codfather_st", 1, 1, "press");
        add(7, 65, "cue_point", 0, 1, "any");
        add(7, 70, "cue_point", 0, 6, "any");
        add(7, 66, "cue_point", 0, 2, "any");
        add(9, 71, "cue_point", 1, 7, "any");
        add(9, 65, "cue_point", 1, 1, "any");
        add(7, 74, "codfather_st", 0, 2, "press");
        add(7, 77, "codfather_fx", 0, 1, "press");
        add(9, 78, "codfather_fx", 1, 2, "press");
        add(9, 79, "codfather_fx", 1, 3, "press");
        add(7, 64, "cue_point", 0, 0, "any");
        add(1, 77, "auto_loop_specific_length", 1, 8, "any");
        add(0, 76, "auto_loop_specific_length", 0, 7, "any");
        add(1, 121, "pitch_bend_up", 1, 0, "any");
        add(0, 40, "dvs_playback_mode_request", 0, 0, "press");
        add(0, 21, "dvs_sticker_lock_enabled", 0, 0, "press");
        add(0, 21, "dvs_sticker_lock_enabled", 0, 0, "release");
        add(0, 21, "dvs_playback_mode_request", 0, 0, "release");
        add(0, 21, "dvs_playback_mode_request", 0, 0, "press");
        add(0, 21, "load_track", 0, 0, "press");
        add(9, 42, "cue_point", 1, 2, "any");
        add(9, 40, "cue_point", 1, 0, "any");
        add(7, 46, "cue_point", 0, 6, "any");
        add(7, 44, "cue_point", 0, 4, "any");
        add(7, 43, "cue_point", 0, 3, "any");
        add(7, 40, "cue_point", 0, 0, "any");
        add(7, 47, "cue_point", 0, 7, "any");
        add(1, 104, "mute", 1, 0, "press");
        add(6, 88, "add_song_to_prepare_crate", 0, 0, "press");
        add(0, 96, "bpm_transition", 1, 0, "press");
        add(6, 66, "widget_cycle", 0, 0, "press");
        add(9, 77, "codfather_fx", 1, 1, "press");
        add(9, 75, "codfather_st", 1, 3, "press");
        add(7, 45, "cue_point", 0, 5, "any");
        add(7, 41, "cue_point", 0, 1, "any");
        add(7, 72, "codfather_st", 0, 0, "press");
        add(0, 48, "dvs_playback_mode_request", 0, 0, "press");
        add(0, 48, "dvs_sticker_lock_enabled", 0, 0, "press");
        add(7, 79, "codfather_fx", 0, 3, "press");
        add(9, 76, "codfather_fx", 1, 0, "press");
        add(0, 20, "auto_loop_enable", 0, 0, "press");
        add(9, 46, "cue_point", 1, 6, "any");
        add(9, 66, "cue_point", 1, 2, "any");
        add(0, 56, "dvs_playback_mode_request", 0, 0, "press");
        add(9, 41, "cue_point", 1, 1, "any");
        add(1, 48, "dvs_playback_mode_request", 1, 0, "press");
        add(1, 48, "dvs_sticker_lock_enabled", 0, 0, "press");
        add(9, 67, "cue_point", 1, 3, "any");
        add(9, 45, "cue_point", 1, 5, "any");
        add(7, 42, "cue_point", 0, 2, "any");
        add(0, 104, "mute", 0, 0, "press");
        add(9, 44, "cue_point", 1, 4, "any");
        add(7, 69, "cue_point", 0, 5, "any");
        add(7, 67, "cue_point", 0, 3, "any");
        add(7, 68, "cue_point", 0, 4, "any");
        add(7, 71, "cue_point", 0, 7, "any");
        add(9, 47, "cue_point", 1, 7, "any");
        add(1, 21, "dvs_sticker_lock_enabled", 0, 0, "press");
        add(1, 21, "dvs_sticker_lock_enabled", 0, 0, "release");
        add(1, 21, "dvs_playback_mode_request", 1, 0, "release");
        add(1, 21, "dvs_playback_mode_request", 1, 0, "press");
        add(1, 21, "load_track", 1, 0, "press");
        add(1, 10, "pitch_bend_down", 1, 0, "any");
        add(1, 40, "dvs_playback_mode_request", 1, 0, "press");
        add(9, 72, "codfather_st", 1, 0, "press");
        add(6, 89, "add_song_to_prepare_crate", 0, 0, "press");
        add(7, 73, "codfather_st", 0, 1, "press");
        add(0, 10, "pitch_bend_down", 0, 0, "any");
        add(7, 76, "codfather_fx", 0, 0, "press");
        add(9, 43, "cue_point", 1, 3, "any");
        add(1, 57, "pntdj_plugin_enable", 0, 0, "press");
        add(9, 64, "cue_point", 1, 0, "any");
        add(0, 77, "auto_loop_specific_length", 0, 8, "any");
    }
    private void add(int c,int n,String a,int d,int s,String e){
        for(Binding b:bindings)if(b.channel==c&&b.note==n&&b.action.equals(a)&&b.deck==d&&b.slot==s&&b.edge.equals(e))return;
        bindings.add(new Binding(c,n,a,d,s,e));
    }
    /** MIDI byte stream, including split messages, running status and interleaved realtime. */
    public void receive(byte[] data,int offset,int length){
        for(int i=offset;i<offset+length;i++){
            int value=data[i]&255;
            if(value>=248)continue;
            if(value>=128){runningStatus=value<240?value:0;count=0;continue;}
            if(runningStatus==0)continue;
            int kind=runningStatus&240;
            if(kind==192||kind==208){count=0;continue;}
            if(count++==0){first=value;continue;}
            count=0;
            if(kind==144||kind==128)dispatch(runningStatus&15,first,kind==144&&value>0);
        }
    }
    private void dispatch(int channel,int note,boolean pressed){
        for(Binding b:bindings)if(b.channel==channel&&b.note==note&&
            ("any".equals(b.edge)||("press".equals(b.edge)&&pressed)||("release".equals(b.edge)&&!pressed)))
            listener.onAction(b.action,b.deck,b.slot,pressed);
    }
}
