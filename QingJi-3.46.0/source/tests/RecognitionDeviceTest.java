package me.minis.ledger;

import android.app.*;
import android.os.Bundle;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;

/** Instrumentation uses an isolated in-memory database and synthetic notifications only. */
public final class RecognitionDeviceTest extends Instrumentation {
 private int checks;
 private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle result=new Bundle();try{notifications();fusion();result.putString("stream","PASS: "+checks+" device checks\n");finish(Activity.RESULT_OK,result);}catch(Throwable error){result.putString("stream","FAIL: "+error.getClass().getSimpleName()+": "+error.getMessage()+"\n");finish(Activity.RESULT_CANCELED,result);}}
 private void notifications(){
  Notification n=new Notification.Builder(getTargetContext(),"test").setContentTitle("银行").setContentText("支付28…").setStyle(new Notification.BigTextStyle().bigText("支付成功28.50元")).build();
  List<NotificationPayload> rows=NotificationPayload.read(n,"test",1000);check(rows.size()==1&&rows.get(0).text.contains("28.50"),"expanded text");
  n=new Notification.Builder(getTargetContext(),"test").setStyle(new Notification.MessagingStyle("本人").addMessage("收款到账10.00元",1000,"微信支付").addMessage("收款到账20.00元",2000,"微信支付")).build();
  rows=NotificationPayload.read(n,"test",3000);check(rows.size()==2&&rows.get(0).time==1000&&rows.get(1).time==2000,"messages keep independent timestamps");
  check(!rows.get(0).text.contains("20.00")&&!rows.get(1).text.contains("10.00"),"messages never joined");
  n.flags|=Notification.FLAG_GROUP_SUMMARY;check(NotificationPayload.read(n,"group",3000).isEmpty(),"group summary cannot duplicate child messages");
  n=new Notification.Builder(getTargetContext(),"test").setContentTitle("银行").setStyle(new Notification.InboxStyle().addLine("消费10.00元").addLine("消费20.00元")).build();
  rows=NotificationPayload.read(n,"test",3000);check(rows.size()==1&&rows.get(0).text.contains("20.00"),"untimestamped inbox retains latest line");
  n.flags|=Notification.FLAG_GROUP_SUMMARY;check(NotificationPayload.read(n,"test",3000).isEmpty(),"ambiguous group summary ignored");
 }
 private void fusion(){
  try(SQLiteDatabase db=SQLiteDatabase.create(null)){
   db.execSQL("CREATE TABLE entries(_id INTEGER PRIMARY KEY,source TEXT,cents INTEGER,kind TEXT,merchant TEXT,status TEXT,semantic TEXT,reason TEXT,txkey TEXT,category TEXT)");
   db.execSQL("INSERT INTO entries VALUES(1,'com.tencent.mm',2850,'expense','','review','','','same-id','其他')");
   PaymentParser.Result r=new PaymentParser.Result();r.cents=2850;r.merchant="街角咖啡店";r.confirmed=true;r.category="餐饮";
   check(IdentityEvidence.merge(db,"com.tencent.mm:screen","same-id",r)==2,"same id enriches existing review");
   try(Cursor c=db.rawQuery("SELECT COUNT(*),status,merchant FROM entries",null)){c.moveToFirst();check(c.getInt(0)==1&&c.getString(1).equals("confirmed")&&c.getString(2).equals(r.merchant),"one enriched row");}
   r.cents=2950;check(IdentityEvidence.merge(db,"com.tencent.mm:screen","same-id",r)==2,"conflicting amount flagged");
   try(Cursor c=db.rawQuery("SELECT status,cents FROM entries",null)){c.moveToFirst();check(c.getString(0).equals("review")&&c.getLong(1)==2850,"conflict retains amount for review");}
   r.cents=2850;check(IdentityEvidence.merge(db,"com.tencent.mm:screen","same-id",r)==1,"conflict cannot silently reconfirm");
   db.execSQL("UPDATE entries SET semantic='user-edited',status='confirmed'");r.cents=9900;
   check(IdentityEvidence.merge(db,"com.tencent.mm:screen","same-id",r)==1,"manual edits preserved");
   check(IdentityEvidence.merge(db,"com.tencent.mm:screen","different-id",r)==0,"different transaction remains independent");
  }
 }
}
