package com.s4bridge.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import com.s4bridge.app.engine.DeckEngine;
import java.util.Locale;

/** Two independent touch regions: full-track waveform and circular jog transport. */
public final class DeckSurface extends View {
    private final DeckEngine deck;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent;
    private final float density;
    private int gesture, pointer=-1;
    private double lastAngle, scrubPosition;
    private float rotation;
    private int lastPosition;
    public DeckSurface(Context context,DeckEngine deck,int accent){
        super(context);this.deck=deck;this.accent=accent;density=getResources().getDisplayMetrics().density;
        setContentDescription("Deck "+deck.getName()+" waveform and jog wheel. Drag waveform to seek; rotate wheel to scrub.");
    }
    private void color(int value){paint.setColor(value);paint.setStyle(Paint.Style.FILL);}
    private float wheelY(){return getHeight()*0.69f;}
    private float radius(){return Math.min(getHeight()*0.26f,getWidth()*0.28f);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);int position=deck.getPositionMs(),duration=deck.getDurationMs();
        if(gesture!=2&&deck.isPlaying())rotation+=(position-lastPosition)*0.12f;lastPosition=position;
        float w=getWidth(),h=getHeight();
        color(Color.WHITE);paint.setTextSize(15*density);
        c.drawText("DECK "+deck.getName()+"  "+(deck.isPlaying()?"PLAYING":deck.isLoaded()?"READY":"EMPTY"),0,18*density,paint);
        color(0xff93a7bd);paint.setTextSize(11*density);
        c.drawText(time(position)+" / "+time(duration),0,34*density,paint);
        float top=h*.19f,bottom=h*.39f,mid=(top+bottom)/2;
        color(0xff101d2c);c.drawRect(0,top,w,bottom,paint);
        float[] peaks=deck.getWaveform();
        if(peaks!=null){paint.setStrokeWidth(Math.max(1,w/peaks.length));
            for(int i=0;i<peaks.length;i++){float x=(i+.5f)*w/peaks.length;float amp=peaks[i]*(bottom-top)*.47f;color(x<=w*position/Math.max(1,duration)?accent:0xff425973);c.drawLine(x,mid-amp,x,mid+amp,paint);}}
        else{color(0xff93a7bd);paint.setTextSize(10*density);c.drawText(deck.getWaveformStatus(),4*density,mid+4*density,paint);}
        color(Color.WHITE);paint.setStrokeWidth(2*density);float playhead=w*Math.min(1,(float)position/Math.max(1,duration));c.drawLine(playhead,top,playhead,bottom,paint);
        float cx=w/2,cy=wheelY(),r=radius();color(accent);c.drawCircle(cx,cy,r,paint);color(0xff162638);c.drawCircle(cx,cy,r-3*density,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(density);paint.setColor(0xff3e536c);
        for(int i=0;i<4;i++)c.drawCircle(cx,cy,r*(.5f+i*.11f),paint);
        color(accent);double angle=Math.toRadians(rotation-90);c.drawCircle(cx+(float)Math.cos(angle)*r*.81f,cy+(float)Math.sin(angle)*r*.81f,4*density,paint);
        color(0xff0b1421);c.drawCircle(cx,cy,r*.4f,paint);color(Color.WHITE);paint.setTextSize(13*density);paint.setTextAlign(Paint.Align.CENTER);c.drawText(gesture==2?"SCRUB":"JOG",cx,cy+4*density,paint);paint.setTextAlign(Paint.Align.LEFT);
        if(isAttachedToWindow()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(50);
    }
    private static String time(int ms){return String.format(Locale.US,"%d:%02d.%01d",ms/60000,(ms/1000)%60,(ms/100)%10);}
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            if(!deck.isLoaded())return false;
            float x=e.getX(),y=e.getY();
            if(y>=getHeight()*.19f&&y<=getHeight()*.39f)gesture=1;
            else if(Math.hypot(x-getWidth()/2f,y-wheelY())<=radius())gesture=2;
            else return false;
            pointer=e.getPointerId(0);getParent().requestDisallowInterceptTouchEvent(true);
            lastAngle=Math.atan2(y-wheelY(),x-getWidth()/2f);scrubPosition=deck.getPositionMs();
            if(gesture==1)seek(x);invalidate();return true;
        }
        if(gesture==0)return false;
        int index=e.findPointerIndex(pointer);
        if(action==MotionEvent.ACTION_MOVE&&index>=0){
            float x=e.getX(index),y=e.getY(index);
            if(gesture==1)seek(x);
            else if(Math.hypot(x-getWidth()/2f,y-wheelY())>radius()*.2f){
                double angle=Math.atan2(y-wheelY(),x-getWidth()/2f),delta=angle-lastAngle;
                if(delta>Math.PI)delta-=2*Math.PI;if(delta<-Math.PI)delta+=2*Math.PI;
                lastAngle=angle;rotation+=Math.toDegrees(delta);
                scrubPosition=Math.max(0,Math.min(deck.getDurationMs(),scrubPosition+delta*2000/(2*Math.PI)));
                deck.seekToMs((int)scrubPosition);
            }
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL||(action==MotionEvent.ACTION_POINTER_UP&&e.getPointerId(e.getActionIndex())==pointer)){
            gesture=0;pointer=-1;getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;
        }
        return true;
    }
    private void seek(float x){deck.seekToMs((int)(Math.max(0,Math.min(1,x/Math.max(1,getWidth())))*deck.getDurationMs()));}
}
