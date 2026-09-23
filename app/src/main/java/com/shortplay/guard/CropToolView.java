package com.shortplay.guard;

import android.content.Context;
import android.graphics.*;
import android.view.*;

public class CropToolView extends View {
    private final Bitmap bitmap;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float left, top, right, bottom, downX, downY;
    private boolean moving;

    public CropToolView(Context context, Bitmap source) {
        super(context);
        bitmap = source;
        paint.setFilterBitmap(true);
        setBackgroundColor(Color.BLACK);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (bitmap == null) return;
        float scale = Math.min(getWidth()/(float)bitmap.getWidth(), getHeight()/(float)bitmap.getHeight());
        float w=bitmap.getWidth()*scale, h=bitmap.getHeight()*scale;
        float ox=(getWidth()-w)/2f, oy=(getHeight()-h)/2f;
        c.drawBitmap(bitmap,null,new RectF(ox,oy,ox+w,oy+h),paint);
        if (right<=left) {
            float size=Math.min(w,h)*.78f;
            left=ox+(w-size)/2f; top=oy+(h-size)/2f; right=left+size; bottom=top+size;
        }
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.argb(150,0,0,0));
        c.drawRect(0,0,getWidth(),top,paint); c.drawRect(0,bottom,getWidth(),getHeight(),paint);
        c.drawRect(0,top,left,bottom,paint); c.drawRect(right,top,getWidth(),bottom,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); paint.setColor(Color.WHITE);
        c.drawRect(left,top,right,bottom,paint);
        paint.setStrokeWidth(1); paint.setColor(Color.argb(190,255,255,255));
        float cw=right-left,ch=bottom-top;
        for(int i=1;i<3;i++){c.drawLine(left+cw*i/3,top,left+cw*i/3,bottom,paint);c.drawLine(left,top+ch*i/3,right,top+ch*i/3,paint);}
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();moving=e.getX()>=left&&e.getX()<=right&&e.getY()>=top&&e.getY()<=bottom;return true;}
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&moving){
            float dx=e.getX()-downX,dy=e.getY()-downY,w=right-left,h=bottom-top;
            float nl=Math.max(0,Math.min(getWidth()-w,left+dx)),nt=Math.max(0,Math.min(getHeight()-h,top+dy));
            left=nl;top=nt;right=nl+w;bottom=nt+h;downX=e.getX();downY=e.getY();invalidate();return true;
        }
        return true;
    }

    public Bitmap getCroppedBitmap() {
        float scale=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());
        float ox=(getWidth()-bitmap.getWidth()*scale)/2f,oy=(getHeight()-bitmap.getHeight()*scale)/2f;
        int x=Math.max(0,Math.round((left-ox)/scale));
        int y=Math.max(0,Math.round((top-oy)/scale));
        int r=Math.min(bitmap.getWidth(),Math.round((right-ox)/scale));
        int b=Math.min(bitmap.getHeight(),Math.round((bottom-oy)/scale));
        return Bitmap.createBitmap(bitmap,x,y,Math.max(1,r-x),Math.max(1,b-y));
    }
}