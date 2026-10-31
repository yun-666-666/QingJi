package me.minis.ledger;
import android.content.Context;import android.view.accessibility.AccessibilityNodeInfo;import android.graphics.Rect;import org.json.*;import java.io.*;import java.util.*;
/** Local service-page evidence only; never participates in ledger decisions. */
final class ServiceNodeDiagnostic {
 static final String DIR="service-node-samples-v2";static final int MAX_BYTES=NodeSampleBudget.MAX_BYTES;static final long AGE=86400000L;
 static final class State {int count=0,examined=0;final NodeSampleBudget budget;JSONArray rows=new JSONArray();State(int bytes,int nodes,int base){budget=new NodeSampleBudget(bytes,nodes);count=base;}}
 static String capture(AccessibilityNodeInfo root,AccessibilityNodeInfo event,String activity)throws Exception{
  JSONObject o=new JSONObject();o.put("version",2);o.put("at",System.currentTimeMillis());o.put("activity",activity);
  State s=new State(NodeSampleBudget.ROOT_BYTES,NodeSampleBudget.ROOT_NODES,0);walk(root,-1,0,"root",s);
  State e=new State(NodeSampleBudget.EVENT_BYTES,NodeSampleBudget.EVENT_NODES,10000);if(event!=null&&String.valueOf(root.getPackageName()).equals(String.valueOf(event.getPackageName()))&&event.getWindowId()==root.getWindowId())walk(event,-1,0,"event",e);
  o.put("nodes",s.rows);o.put("eventNodes",e.rows);o.put("eventPresent",event!=null);
  State qs=new State(NodeSampleBudget.QUERY_BYTES,NodeSampleBudget.QUERY_NODES,20000);JSONArray results=new JSONArray();
  for(String q:AlipayPaymentCards.PKG.equals(String.valueOf(root.getPackageName()))?new String[]{"付款成功","支付成功","自动扣款成功","交易成功","支出","支付时间","付款方式","最近消息"}:new String[]{"已自动支付","扣费金额","付款金额","收款金额","微信支付"}){List<AccessibilityNodeInfo> ns=root.findAccessibilityNodeInfosByText(q);JSONObject result=new JSONObject();result.put("query",q);result.put("found",ns==null?0:ns.size());results.put(result);if(ns==null)continue;try{for(AccessibilityNodeInfo n:ns){if(n==null||!qs.budget.available())continue;JSONObject row=row(n,qs.count,-1,0,"query:"+q);if(qs.budget.add(row.toString())){qs.rows.put(row);qs.count++;}}}finally{for(AccessibilityNodeInfo n:ns)if(n!=null)n.recycle();}}
  o.put("queryNodes",qs.rows);o.put("queryResults",results);o.put("rootTruncated",s.budget.truncated());o.put("eventTruncated",e.budget.truncated());o.put("queryTruncated",qs.budget.truncated());o.put("rootExamined",s.examined);o.put("rootBytes",s.budget.bytes());o.put("truncated",s.budget.truncated()||e.budget.truncated()||qs.budget.truncated());String raw=o.toString();if(raw.getBytes("UTF-8").length>MAX_BYTES)throw new IOException("sample envelope exceeds limit");return raw;
 }
 private static JSONObject row(AccessibilityNodeInfo n,int id,int parent,int depth,String origin)throws Exception{
  JSONObject o=new JSONObject();o.put("id",id);o.put("parent",parent);o.put("depth",depth);o.put("origin",origin);o.put("class",String.valueOf(n.getClassName()));o.put("viewId",n.getViewIdResourceName());o.put("visible",n.isVisibleToUser());o.put("children",n.getChildCount());o.put("password",n.isPassword());o.put("editable",n.isEditable());Rect r=new Rect();n.getBoundsInScreen(r);o.put("bounds",r.flattenToString());
  if(!n.isPassword()&&!n.isEditable()){put(o,"text",n.getText());put(o,"desc",n.getContentDescription());}return o;
 }
 private static void put(JSONObject o,String key,CharSequence t)throws Exception{if(t==null)return;String v=t.toString();if(v.matches("(?s).*(?:验证码|校验码|动态密码|支付密码).*")||v.matches("\\d{4,8}")) {o.put(key,"[sensitive-or-code-omitted]");return;}o.put(key,v.length()>4096?v.substring(0,4096):v);o.put(key+"Length",v.length());}
 private static void walk(AccessibilityNodeInfo n,int parent,int depth,String origin,State s)throws Exception{
  if(!s.budget.available()||depth>64){s.budget.cut();return;}s.examined++;int id=s.count;JSONObject o=row(n,id,parent,depth,origin);if(!s.budget.add(o.toString()))return;s.count++;s.rows.put(o);if(n.isPassword()||n.isEditable())return;
  for(int i=0;i<n.getChildCount();i++){if(!s.budget.available()){s.budget.cut();break;}AccessibilityNodeInfo c=n.getChild(i);if(c!=null)try{walk(c,id,depth+1,origin,s);}finally{c.recycle();}}
 }
 private static long generation;
 static synchronized long generation(){return generation;}
 static synchronized void clear(Context c)throws IOException{generation++;for(String name:new String[]{"service-node-samples",DIR}){File dir=new File(c.getFilesDir(),name);File[] files=dir.listFiles(f->f.isFile()&&f.getName().startsWith("nodes-")&&f.getName().endsWith(".json"));if(files==null){if(dir.exists())throw new IOException("无法读取诊断目录");continue;}for(File f:files)if(!f.delete())throw new IOException("部分诊断样本未清除，请重试");}}
 static synchronized String save(Context c,String raw,long expected)throws Exception{
  if(expected!=generation||!LedgerDb.prefs(c).getBoolean("keep_raw",true)||!LedgerDb.prefs(c).getBoolean("probe_on",false))return "";
  byte[] bytes=raw.getBytes("UTF-8");if(bytes.length>MAX_BYTES)throw new IOException("sample exceeds limit");File dir=new File(c.getFilesDir(),DIR);if(!dir.exists()&&!dir.mkdirs())throw new IOException("sample directory");prune(dir);File f=new File(dir,"nodes-"+System.currentTimeMillis()+".json");try(FileOutputStream out=new FileOutputStream(f)){out.write(bytes);}prune(dir);return f.getName();
 }
 static synchronized int count(Context c){File d=new File(c.getFilesDir(),DIR);prune(d);File[] fs=d.listFiles();return fs==null?0:fs.length;}
 static synchronized void cleanup(Context c){long now=System.currentTimeMillis();PrivateFileCleanup.prune(new File(c.getFilesDir(),"service-node-samples"),now,AGE,8);PrivateFileCleanup.prune(new File(c.getFilesDir(),DIR),now,AGE,8);PrivateFileCleanup.staleTransfers(c.getCacheDir(),now);}
 private static void prune(File d){PrivateFileCleanup.prune(d,System.currentTimeMillis(),AGE,8);}
}
