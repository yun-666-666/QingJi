package me.minis.ledger;

import android.app.Notification;
import android.content.*;
import android.os.Bundle;
import android.service.notification.*;
import java.util.*;
import java.util.concurrent.*;

public final class PaymentListener extends NotificationListenerService {
 public static volatile boolean connected=false;
 private static volatile PaymentListener instance;
 public static int scanCurrent(){PaymentListener s=instance;if(s==null)return -1;s.gate.clear();try{StatusBarNotification[] ns=s.getActiveNotifications();for(StatusBarNotification n:ns)s.onNotificationPosted(n);return ns.length;}catch(Exception e){return -1;}}
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final NotificationGate gate=new NotificationGate();private final java.util.concurrent.atomic.AtomicLong pendingChecked=new java.util.concurrent.atomic.AtomicLong();private long lastStats=0;
 private synchronized void stats(boolean force){long now=android.os.SystemClock.elapsedRealtime();if(!force&&now-lastStats<60000)return;lastStats=now;long count=pendingChecked.getAndSet(0);if(count>0){PowerDetail.count("stats_flush");android.content.SharedPreferences p=LedgerDb.prefs(this);p.edit().putLong("checked",p.getLong("checked",0)+count).apply();}}

 public void onListenerConnected(){connected=true;instance=this;LedgerDb.prefs(this).edit().putLong("connected_at",System.currentTimeMillis()).apply();}
 public void onListenerDisconnected(){connected=false;instance=null;}
 public void onDestroy(){connected=false;instance=null;try{worker.execute(()->stats(true));}catch(RejectedExecutionException ignored){}worker.shutdown();super.onDestroy();}
 private synchronized void alipayTrace(String source,String stage){if(!AlipayPaymentCards.PKG.equals(source))return;android.content.SharedPreferences p=LedgerDb.prefs(this);String line=new java.text.SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date())+" "+stage;String[] old=p.getString("alipay_notification_trace","").split("\n");StringBuilder b=new StringBuilder(line);for(int i=0;i<Math.min(23,old.length);i++)if(!old[i].isEmpty())b.append('\n').append(old[i]);p.edit().putString("alipay_notification_trace",b.toString()).apply();}
 protected void dump(java.io.FileDescriptor fd,java.io.PrintWriter writer,String[] args){writer.println("Alipay notification stages (no text, amount or merchant):");writer.println(LedgerDb.prefs(this).getString("alipay_notification_trace","尚无记录"));}
 public void onNotificationPosted(StatusBarNotification sbn){
  if(sbn==null)return;final long observedAt=System.currentTimeMillis();alipayTrace(sbn.getPackageName(),"收到通知");
  if(!LedgerDb.prefs(this).getBoolean("capture",true)||!LedgerDb.allowed(this,sbn.getPackageName())){alipayTrace(sbn.getPackageName(),"监听暂停或来源禁用");return;}
  Notification n=sbn.getNotification();
  if(n==null||(n.flags&Notification.FLAG_GROUP_SUMMARY)!=0){alipayTrace(sbn.getPackageName(),n==null?"通知对象为空":"通知分组摘要，跳过");return;}
  Bundle b=n.extras;if(b==null){alipayTrace(sbn.getPackageName(),"通知无正文数据");return;}
  if(NotificationEvidence.media(n.category,b.getString("android.template",""),b.containsKey("android.mediaSession"))){PowerDetail.count("media_notification_skipped");alipayTrace(sbn.getPackageName(),"媒体类型通知，跳过");return;}
  LinkedHashSet<String> parts=new LinkedHashSet<>();
  add(parts,b.getCharSequence(Notification.EXTRA_TITLE));
  CharSequence big=b.getCharSequence(Notification.EXTRA_BIG_TEXT);add(parts,big!=null&&big.length()>0?big:b.getCharSequence(Notification.EXTRA_TEXT));
  add(parts,b.getCharSequence(Notification.EXTRA_SUB_TEXT));
  CharSequence[] lines=b.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);if((big==null||big.length()==0)&&b.getCharSequence(Notification.EXTRA_TEXT)==null&&lines!=null&&lines.length>0)add(parts,lines[lines.length-1]);
  StringBuilder text=new StringBuilder();for(String part:parts){if(text.length()>0)text.append('\n');text.append(part);}
  final String raw=text.toString(),source=sbn.getPackageName(),key=sbn.getKey(),title=String.valueOf(b.getCharSequence(Notification.EXTRA_TITLE,""));final long time=sbn.getPostTime();
  if(raw.isEmpty()){alipayTrace(source,"通知正文为空");return;}
  PowerProbe.notification();PowerDetail.source(source);pendingChecked.incrementAndGet();stats(false);
  if(System.currentTimeMillis()-LedgerDb.prefs(this).getLong("last_seen",0)>60000)LedgerDb.prefs(this).edit().putLong("last_seen",System.currentTimeMillis()).putString("last_source",LedgerDb.label(source)).apply();
  try(PowerDetail.Span early=PowerDetail.span("notification_gate")){if(!NotificationGate.hasMoneyUnit(raw)){PowerDetail.count("no_money");alipayTrace(source,"无金额单位，跳过");return;}}
  final long queuedAt=android.os.SystemClock.elapsedRealtime();
  try{worker.execute(()->{
   PowerDetail.delay(android.os.SystemClock.elapsedRealtime()-queuedAt);
   try(PowerDetail.Span span=PowerDetail.span("notification")){
    if(!LedgerDb.prefs(this).getBoolean("capture",true)||!LedgerDb.allowed(this,source))return;
    if(gate.duplicate(key,time,raw)){PowerDetail.count("duplicate");alipayTrace(source,"相同通知重复投递，跳过");return;}
    PaymentParser.Result result;try(PowerDetail.Span parse=PowerDetail.span("parse")){result=PaymentParser.parseNotification(source,title,raw);}
    if(result==null){PowerDetail.count("no_match");alipayTrace(source,"规则未匹配");return;}PowerDetail.count("matched");alipayTrace(source,result.confirmed?"匹配已确认交易":"匹配待确认交易");
    result.observedAt=observedAt;stats(true);
    LedgerDb.rememberLabel(this,source);
    String stored=raw;
    try(LedgerDb db=new LedgerDb(this)){if(db.capture(source,key,time,stored,result)){PowerDetail.count("saved");alipayTrace(source,"数据库已新增");LedgerDb.prefs(this).edit().putLong("last_saved",System.currentTimeMillis()).putString("last_error","").apply();}else{PowerDetail.count("db_duplicate");alipayTrace(source,"数据库去重或更新，未新增");}}
   }catch(Exception e){gate.failed(key,time,raw);PowerDetail.count("notification_error");alipayTrace(source,"解析或入库异常，可重试");LedgerDb.prefs(this).edit().putString("last_error","记录失败，请检查剩余存储空间并重新打开应用").apply();}
  });}catch(RejectedExecutionException ignored){}
 }
 private static void add(Set<String> parts,CharSequence s){if(s!=null&&!s.toString().trim().isEmpty())parts.add(s.toString().trim());}
}
