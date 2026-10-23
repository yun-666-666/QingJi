package me.minis.ledger;

import android.accessibilityservice.AccessibilityService;
import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.os.*;
import android.view.Display;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.util.*;
import java.util.concurrent.*;

/** One lazy bundled recognizer, one crop in flight, no work on the ledger writer. */
final class PaymentOcr {
 interface Callback {boolean current(PaymentSnapshot snapshot);void complete(PaymentSnapshot snapshot,OcrPayment.Parsed parsed,String status);}
 private final AccessibilityService service;private final Handler main=new Handler(Looper.getMainLooper());
 private ExecutorService executor;private TextRecognizer recognizer;private boolean busy;private volatile boolean closed;private volatile int generation;private Runnable pending;
 private long last=-3000;private String completedFrame="";private int requests,completed;private long lastMillis;
 private final Runnable idleClose=()->{if(!busy)release();};
 PaymentOcr(AccessibilityService service){this.service=service;}
 String diagnostics(){return "离线 OCR：请求 "+requests+" · 完成 "+completed+" · 最近耗时 "+lastMillis+"ms";}
 void cancel(){generation++;completedFrame="";if(pending!=null){main.removeCallbacks(pending);pending=null;}}
 void close(){closed=true;cancel();main.removeCallbacks(idleClose);if(!busy)release();}
 private void release(){if(recognizer!=null){recognizer.close();recognizer=null;}if(executor!=null){executor.shutdown();executor=null;}}
 boolean request(PaymentSnapshot snapshot,ScreenProfile profile,int learnedTitleTop,Callback callback){
  if(Build.VERSION.SDK_INT<30||closed||!LedgerDb.prefs(service).getBoolean("ocr_capture",true)||!callback.current(snapshot))return false;
  String frame=OcrPayment.frame(snapshot);if(frame.equals(completedFrame))return false;
  OcrPayment.Area area=OcrPayment.area(snapshot,learnedTitleTop);if(area==null)return false;
  if(pending!=null){main.removeCallbacks(pending);pending=null;}
  long remaining=3000-(SystemClock.elapsedRealtime()-last);
  // Keep the newest page when checkout and success events arrive within one cooldown.
  if(busy||remaining>0){pending=()->request(snapshot,profile,learnedTitleTop,callback);main.postDelayed(pending,busy?3000:remaining);return true;}
  Rect windowBounds=new Rect();boolean found=false;List<AccessibilityWindowInfo> windows=service.getWindows();
  try{for(AccessibilityWindowInfo window:windows)if(window.getId()==snapshot.window){if(Build.VERSION.SDK_INT<34&&window.getDisplayId()!=Display.DEFAULT_DISPLAY)return false;window.getBoundsInScreen(windowBounds);found=true;break;}}
  finally{for(AccessibilityWindowInfo window:windows)window.recycle();}
  if(!found)return false;
  final int x=Build.VERSION.SDK_INT>=34?windowBounds.left:0,y=Build.VERSION.SDK_INT>=34?windowBounds.top:0,request=generation;
  if(executor==null)executor=Executors.newSingleThreadExecutor();main.removeCallbacks(idleClose);
  busy=true;last=SystemClock.elapsedRealtime();requests++;final long start=last;
  AccessibilityService.TakeScreenshotCallback capture=new AccessibilityService.TakeScreenshotCallback(){
   public void onSuccess(AccessibilityService.ScreenshotResult screenshot){
    Bitmap hardware=null,software=null,crop=null;
    try(HardwareBuffer buffer=screenshot.getHardwareBuffer()){
     if(closed||request!=generation){finish(null,"",false);return;}
     hardware=Bitmap.wrapHardwareBuffer(buffer,screenshot.getColorSpace());if(hardware==null){finish(null,"截图暂不可读",false);return;}
     software=hardware.copy(Bitmap.Config.ARGB_8888,false);
     if(software==null||area.left-x<0||area.top-y<0||area.right-x>software.getWidth()||area.bottom-y>software.getHeight()){finish(null,"截图区域已变化，等待下一页",false);return;}
     crop=Bitmap.createBitmap(software,area.left-x,area.top-y,area.right-area.left,area.bottom-area.top);
     int max=1600;double scale=Math.min(1.0,max/(double)Math.max(crop.getWidth(),crop.getHeight()));
     Bitmap input=scale<1?Bitmap.createScaledBitmap(crop,Math.max(1,(int)Math.round(crop.getWidth()*scale)),Math.max(1,(int)Math.round(crop.getHeight()*scale)),true):crop;
     if(input==crop)crop=null;if(input==software)software=null;
     final double scaleX=(area.right-area.left)/(double)input.getWidth(),scaleY=(area.bottom-area.top)/(double)input.getHeight();
     try{
      if(recognizer==null)recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
      recognizer.process(InputImage.fromBitmap(input,0)).addOnCompleteListener(executor,task->{
       try{
        if(!task.isSuccessful()){finish(null,"离线 OCR 暂未完成，原有识别继续工作",false);return;}
        List<PaymentSnapshot.Node> nodes=new ArrayList<>();int id=10000;
        for(Text.TextBlock block:task.getResult().getTextBlocks())for(Text.Line line:block.getLines()){
         Rect box=line.getBoundingBox();if(box==null)continue;
         nodes.add(new PaymentSnapshot.Node(id++,-1,snapshot.window,area.left+(int)Math.round(box.left*scaleX),area.top+(int)Math.round(box.top*scaleY),area.left+(int)Math.round(box.right*scaleX),area.top+(int)Math.round(box.bottom*scaleY),"","ocr",line.getText(),"",false));
        }
        OcrPayment.Parsed parsed=OcrPayment.parse(snapshot,nodes,profile);
        finish(parsed,parsed==null?"离线 OCR 已补读；尚未满足原有付款规则":"离线 OCR 已补全付款页面",true);
       }finally{input.recycle();}
      });
     }catch(RuntimeException e){input.recycle();finish(null,"离线 OCR 初始化失败，原有识别继续工作",false);}
    }catch(RuntimeException e){finish(null,"截图暂不可读，原有识别继续工作",false);}
    finally{if(crop!=null&&crop!=software)crop.recycle();if(software!=null)software.recycle();if(hardware!=null)hardware.recycle();}
   }
   public void onFailure(int error){finish(null,"系统暂未提供 OCR 截图（"+error+"）",false);}
   private void finish(OcrPayment.Parsed parsed,String status,boolean processed){main.post(()->{
    busy=false;lastMillis=SystemClock.elapsedRealtime()-start;if(processed)completed++;
    if(closed){release();return;}main.postDelayed(idleClose,60000);
    if(request!=generation||!LedgerDb.prefs(service).getBoolean("ocr_capture",true)||!callback.current(snapshot))return;
    if(parsed!=null)completedFrame=frame;
    callback.complete(snapshot,parsed,status);
   });}
  };
  try{if(Build.VERSION.SDK_INT>=34)service.takeScreenshotOfWindow(snapshot.window,executor,capture);else service.takeScreenshot(Display.DEFAULT_DISPLAY,executor,capture);}
  catch(RuntimeException e){busy=false;callback.complete(snapshot,null,"OCR 截图暂不可用");main.postDelayed(idleClose,60000);}
  return true;
 }
}
