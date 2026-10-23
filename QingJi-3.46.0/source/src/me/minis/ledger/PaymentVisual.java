package me.minis.ledger;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.os.*;
import android.view.Display;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.List;
import java.util.concurrent.Executor;

/** Opt-in, in-memory screenshot processing; only a success-label fingerprint is stored. */
final class PaymentVisual {
 interface Callback {boolean current(PaymentSnapshot s);void result(String status,boolean matched);void matched(PaymentSnapshot snapshot,int titleTop);}
 private final AccessibilityService service;private final Executor executor;private final Handler main=new Handler(Looper.getMainLooper());
 private long last=-10000;private boolean busy;private int generation;
 PaymentVisual(AccessibilityService service,Executor executor){this.service=service;this.executor=executor;}
 void cancel(){generation++;}
 static void clear(android.content.Context context){SharedPreferences p=LedgerDb.prefs(context);SharedPreferences.Editor e=p.edit();for(String key:p.getAll().keySet())if(key.startsWith("visual_pattern_"))e.remove(key);e.apply();}
 void inspect(PaymentSnapshot snapshot,boolean learn,Callback callback){
  if(Build.VERSION.SDK_INT<30||busy||SystemClock.elapsedRealtime()-last<3000||snapshot.truncated||snapshot.editable||snapshot.width<=0||snapshot.height<=0)return;
  SharedPreferences prefs=LedgerDb.prefs(service);if(!prefs.getBoolean("visual_capture",false)||!callback.current(snapshot))return;
  String key="visual_pattern_"+snapshot.pkg;String saved=prefs.getString(key,"");PaymentSnapshot.Node anchor=snapshot.success();
  Rect area=null;String expected="";
  if(learn&&anchor!=null){
   if(anchor.left<snapshot.left||anchor.top<snapshot.top||anchor.right>snapshot.left+snapshot.width||anchor.bottom>snapshot.top+snapshot.height||anchor.right<=anchor.left||anchor.bottom<=anchor.top)return;
   // Only the standalone constant success label, never merchant/amount fields or a whole-page thumbnail.
   if(anchor.bottom-anchor.top>snapshot.height/6||anchor.right-anchor.left>snapshot.width*9/10)return;
   area=new Rect(anchor.left-snapshot.left,anchor.top-snapshot.top,anchor.right-snapshot.left,anchor.bottom-snapshot.top);
  }else{
   String[] parts=saved.split(",",8);if(parts.length!=8)return;
   try{if(Long.parseLong(parts[0])!=snapshot.version||Integer.parseInt(parts[1])!=snapshot.width||Integer.parseInt(parts[2])!=snapshot.height)return;
    area=new Rect(Integer.parseInt(parts[3]),Integer.parseInt(parts[4]),Integer.parseInt(parts[5]),Integer.parseInt(parts[6]));expected=parts[7];
   }catch(NumberFormatException e){return;}
  }
  Rect windowBounds=new Rect();boolean found=false;List<AccessibilityWindowInfo> windows=service.getWindows();
  try{for(AccessibilityWindowInfo window:windows)if(window.getId()==snapshot.window){if(window.getDisplayId()!=Display.DEFAULT_DISPLAY&&Build.VERSION.SDK_INT<34)return;window.getBoundsInScreen(windowBounds);found=true;break;}}
  finally{for(AccessibilityWindowInfo window:windows)window.recycle();}
  if(!found)return;
  final int originX=Build.VERSION.SDK_INT>=34?windowBounds.left:0,originY=Build.VERSION.SDK_INT>=34?windowBounds.top:0;
  final Rect roi=area;final String reference=expected;final boolean training=learn&&anchor!=null;final int request=generation;
  busy=true;last=SystemClock.elapsedRealtime();
  AccessibilityService.TakeScreenshotCallback capture=new AccessibilityService.TakeScreenshotCallback(){
   public void onSuccess(AccessibilityService.ScreenshotResult result){
    Bitmap hardware=null,software=null,crop=null,small=null;String feature="";
    try(HardwareBuffer buffer=result.getHardwareBuffer()){
     hardware=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());if(hardware==null)return;
     software=hardware.copy(Bitmap.Config.ARGB_8888,false);if(software==null)return;
     Rect bounds=new Rect(roi);bounds.offset(snapshot.left-originX,snapshot.top-originY);
     if(bounds.width()<=0||bounds.height()<=0||bounds.left<0||bounds.top<0||bounds.right>software.getWidth()||bounds.bottom>software.getHeight())return;
     crop=Bitmap.createBitmap(software,bounds.left,bounds.top,bounds.width(),bounds.height());small=Bitmap.createScaledBitmap(crop,VisualPattern.WIDTH,VisualPattern.HEIGHT,true);
     int[] pixels=new int[VisualPattern.SIZE];small.getPixels(pixels,0,VisualPattern.WIDTH,0,0,VisualPattern.WIDTH,VisualPattern.HEIGHT);feature=VisualPattern.encode(pixels);
    }catch(RuntimeException failure){feature="";}finally{
     if(small!=null&&small!=crop)small.recycle();if(crop!=null&&crop!=software)crop.recycle();if(software!=null)software.recycle();if(hardware!=null)hardware.recycle();
     final String value=feature;main.post(()->{busy=false;if(request!=generation||!prefs.getBoolean("visual_capture",false)||!callback.current(snapshot))return;
      if(value.isEmpty()){callback.result("截图没有足够的页面特征，等待文字或通知证据",false);return;}
      if(training){prefs.edit().putString(key,snapshot.version+","+snapshot.width+","+snapshot.height+","+roi.left+","+roi.top+","+roi.right+","+roi.bottom+","+value).apply();callback.result("已学习当前版本成功标题的局部外观（不含金额）",false);}
      else{boolean matched=VisualPattern.matches(reference,value);callback.result(matched?"外观匹配本机成功页，正在补读文字":"页面外观不匹配，等待文字或通知证据",matched);if(matched)callback.matched(snapshot,snapshot.top+roi.top);}
     });
    }
   }
   public void onFailure(int error){main.post(()->{busy=false;if(request==generation)callback.result("系统未提供截图（错误 "+error+"）；继续使用节点与通知",false);});}
  };
  try{if(Build.VERSION.SDK_INT>=34)service.takeScreenshotOfWindow(snapshot.window,executor,capture);else service.takeScreenshot(Display.DEFAULT_DISPLAY,executor,capture);}
  catch(RuntimeException e){busy=false;callback.result("截图暂不可用；继续使用节点与通知",false);}
 }
}
