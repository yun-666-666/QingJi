package me.minis.ledger;
import android.graphics.*;
/** Render only on an actual widget update; bounded bitmap, no animations/timers. */
final class WidgetChart {
 static Bitmap draw(long[] values,String[] labels,int width,int height){return draw(values,labels,width,height,false);}
 static Bitmap draw(long[] values,String[] labels,int width,int height,boolean dark){
  int w=Math.max(240,Math.min(1000,width)),h=Math.max(192,Math.min(600,height));Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(3);float top=54,bottom=h-40;double lo=Math.min(0,Math.min(values[0],Math.min(values[1],values[2]))),hi=Math.max(0,Math.max(values[0],Math.max(values[1],values[2])));if(hi==lo)hi=lo+100;double pad=(hi-lo)*.12;lo-=pad;hi+=pad;float[] xs={w*.17f,w*.5f,w*.83f},ys=new float[3];for(int i=0;i<3;i++)ys[i]=bottom-(float)((values[i]-lo)/(hi-lo))*(bottom-top);
  p.setColor(dark?0xff3b4259:0xffe6e8f2);p.setStrokeWidth(1);p.setPathEffect(new DashPathEffect(new float[]{5,7},0));for(int i=0;i<3;i++){float y=top+(bottom-top)*i/2;c.drawLine(12,y,w-12,y,p);}p.setPathEffect(null);
  Path curve=new Path();curve.moveTo(xs[0],ys[0]);for(int i=1;i<3;i++){float mid=(xs[i-1]+xs[i])/2;curve.cubicTo(mid,ys[i-1],mid,ys[i],xs[i],ys[i]);}
  Path fill=new Path(curve);fill.lineTo(xs[2],bottom);fill.lineTo(xs[0],bottom);fill.close();p.setShader(new LinearGradient(0,top,0,bottom,0x326e6bd2,0x026e6bd2,Shader.TileMode.CLAMP));p.setStyle(Paint.Style.FILL);c.drawPath(fill,p);p.setShader(null);p.setColor(AppAppearance.accent(dark));p.setStrokeWidth(3.5f);p.setStrokeCap(Paint.Cap.ROUND);p.setStyle(Paint.Style.STROKE);c.drawPath(curve,p);p.setStyle(Paint.Style.FILL);
  for(int i=0;i<3;i++){p.setColor(dark?AppAppearance.surface(true):0xfff6f7fc);c.drawCircle(xs[i],ys[i],6,p);p.setColor(AppAppearance.accent(dark));c.drawCircle(xs[i],ys[i],3.5f,p);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));String text="¥"+WidgetPeriods.money(values[i]);p.setTextSize(21);float max=w*.30f;if(p.measureText(text)>max)p.setTextSize(p.getTextSize()*max/p.measureText(text));c.drawText(text,xs[i],30,p);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(17);p.setColor(AppAppearance.muted(dark));if(p.measureText(labels[i])>max)p.setTextSize(p.getTextSize()*max/p.measureText(labels[i]));c.drawText(labels[i],xs[i],h-9,p);}return b;
 }
}
