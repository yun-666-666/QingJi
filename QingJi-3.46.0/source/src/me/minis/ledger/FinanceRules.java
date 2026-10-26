package me.minis.ledger;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

final class FinanceRules {
 static JSONObject validate(JSONObject p)throws JSONException{
  FinanceData.category(p.getString("category"));JSONObject d=p.getJSONObject("conditions");
  String merchant=d.optString("merchant").trim(),keyword=d.optString("keyword").trim(),kind=d.optString("kind"),mode=d.optString("mode","all");
  if(merchant.length()>100||keyword.length()>100||!Arrays.asList("","expense","income","refund").contains(kind)||!Arrays.asList("all","any").contains(mode))throw new IllegalArgumentException("规则条件不合法");
  if(merchant.isEmpty()&&keyword.isEmpty()&&kind.isEmpty()&&!d.has("min")&&!d.has("max"))throw new IllegalArgumentException("至少填写一项规则条件");
  if(d.optLong("min",0)<0||d.optLong("max",FinanceData.MAX)>FinanceData.MAX||d.optLong("min",0)>d.optLong("max",FinanceData.MAX))throw new IllegalArgumentException("规则金额范围不合法");
  if(!Arrays.asList("exact","contains").contains(d.optString("match","contains")))throw new IllegalArgumentException("匹配方式不合法");
  d.put("merchant",merchant).put("keyword",keyword).put("mode",mode).put("enabled",p.optBoolean("enabled",true));return d;
 }
 static boolean matches(JSONObject d,String merchant,String text,String kind,long amount){
  List<Boolean> tests=new ArrayList<>();String m=d.optString("merchant"),k=d.optString("keyword");
  if(!m.isEmpty())tests.add(d.optString("match").equals("exact")?merchant.equalsIgnoreCase(m):merchant.toLowerCase(Locale.ROOT).contains(m.toLowerCase(Locale.ROOT)));
  if(!k.isEmpty())tests.add(text.toLowerCase(Locale.ROOT).contains(k.toLowerCase(Locale.ROOT)));
  if(!d.optString("kind").isEmpty())tests.add(d.optString("kind").equals(kind));
  if(d.has("min")||d.has("max"))tests.add(amount>=d.optLong("min",0)&&amount<=d.optLong("max",FinanceData.MAX));
  return !tests.isEmpty()&&(d.optString("mode","all").equals("any")?tests.contains(true):!tests.contains(false));
 }
 static JSONArray enabled(SQLiteDatabase db)throws JSONException{return FinanceData.query(db,"SELECT * FROM finance WHERE type='rule' ORDER BY cents,id");}
 static JSONObject first(JSONArray rules,String merchant,String text,String kind,long cents)throws JSONException{
  for(int i=0;i<rules.length();i++){JSONObject rule=rules.getJSONObject(i),d=new JSONObject(rule.getString("data"));if(d.optBoolean("enabled",true)&&matches(d,merchant,text,kind,cents))return rule;}return null;
 }
 static JSONObject apply(SQLiteDatabase db,PaymentParser.Result r,String raw)throws JSONException{JSONObject rule=first(enabled(db),r.merchant,raw,r.kind,r.cents);if(rule!=null)r.category=rule.getString("category");return rule;}
 static JSONObject save(SQLiteDatabase db,JSONObject p)throws JSONException{JSONObject d=validate(p);String label=p.getString("name").trim();if(label.isEmpty())throw new IllegalArgumentException("请填写规则名称");long rank=p.optLong("priority",100);if(rank<0||rank>9999)throw new IllegalArgumentException("顺序须为0至9999，数字小的优先");JSONObject o=FinanceData.object("rule",0,0,rank,p.getString("category"),label,d);String id=p.optString("id");if(!id.isEmpty()){JSONObject prior=FinanceData.get(db,id);if(prior==null||!prior.getString("type").equals("rule"))throw new IllegalArgumentException("规则不存在");FinanceData.checkRevision(prior,p);o.put("id",id).put("rev",prior.getLong("rev")+1);}FinanceData.put(db,o);return o;}
 static JSONObject preview(SQLiteDatabase db,JSONObject p,boolean apply)throws Exception{
  JSONObject d=validate(p);JSONArray sample=new JSONArray(),conflicts=new JSONArray(),rules=enabled(db);long matches=0,protectedCount=0,changed=0;String category=p.getString("category");
  if(apply&&p.getLong("revision")!=LedgerQuery.revision(db))throw new IllegalStateException("预览后账本发生变化，请重新预览");
  try(Cursor c=db.rawQuery("SELECT _id,merchant,raw,kind,cents,category,semantic,time,source FROM entries WHERE status IN ('confirmed','review') ORDER BY time DESC",null)){while(c.moveToNext()){
   if(!matches(d,c.getString(1),c.getString(2),c.getString(3),c.getLong(4)))continue;matches++;boolean locked=c.getString(6).equals("user-edited")||c.getString(8).equals("manual")||FinanceData.number(db,"SELECT count(*) FROM finance WHERE type='split' AND entry=?",""+c.getLong(0))>0;if(locked)protectedCount++;
   if(sample.length()<20)sample.put(new JSONObject().put("_id",c.getLong(0)).put("merchant",c.getString(1)).put("cents",c.getLong(4)).put("before",c.getString(5)).put("after",category).put("protected",locked));
   JSONObject rule=first(rules,c.getString(1),c.getString(2),c.getString(3),c.getLong(4));if(rule!=null&&!rule.getString("id").equals(p.optString("id"))&&!rule.getString("category").equals(category)&&conflicts.length()<20)conflicts.put(new JSONObject().put("entry",c.getLong(0)).put("rule",rule.getString("label")).put("category",rule.getString("category")));
   if(apply&&!locked&&!c.getString(5).equals(category)&&FinanceData.number(db,"SELECT count(*) FROM finance WHERE type='split' AND entry=?",""+c.getLong(0))==0){ContentValues v=new ContentValues();v.put("category",category);db.update("entries",v,"_id=?",new String[]{""+c.getLong(0)});changed++;}
  }}return new JSONObject().put("matched",matches).put("protected",protectedCount).put("changed",changed).put("sample",sample).put("conflicts",conflicts).put("revision",LedgerQuery.revision(db));
 }
 static void evidence(SQLiteDatabase db,long id,String source,long sourceTime,PaymentParser.Result r,String categoryBasis)throws JSONException{
  if(id<=0)return;JSONObject d=new JSONObject().put("sourceType",source.endsWith(":screen")?"页面识别":"通知识别").put("sourceTime",sourceTime).put("amount",r.cents).put("merchant",r.merchant).put("category",r.category).put("categoryBasis",categoryBasis).put("reason",r.reason).put("amountBasis",r.paidEvidence?"解析器在此次识别中确认实付字段":"解析器返回的金额；未保留字段级提取路径").put("merchantBasis",r.merchant.isEmpty()?"未提取到商户":"此次解析返回的商户；未保留字段级提取路径");
  FinanceData.put(db,FinanceData.object("evidence",id,0,0,"","采集时记录",d));
 }
 static JSONArray duplicates(SQLiteDatabase db,long id)throws JSONException{
  JSONObject e=FinanceData.entry(db,id);return FinanceData.query(db,"SELECT _id,time,source,kind,cents,merchant,category,status,txkey,semantic FROM entries WHERE _id<>? AND kind=? AND cents=? AND status IN ('confirmed','review') AND time BETWEEN ? AND ? AND NOT EXISTS(SELECT 1 FROM finance f WHERE f.type='distinct' AND ((f.entry=? AND f.peer=entries._id) OR (f.peer=? AND f.entry=entries._id))) ORDER BY ABS(time-?) LIMIT 40",""+id,e.getString("kind"),""+e.getLong("cents"),""+(e.getLong("time")-86400000),""+(e.getLong("time")+86400000),""+id,""+id,""+e.getLong("time"));
 }
 static boolean strongConflict(String a,String b){
  if(!a.startsWith("provider|")||!b.startsWith("provider|"))return false;int x=a.lastIndexOf('|'),y=b.lastIndexOf('|');return a.substring(0,x).equals(b.substring(0,y))&&!a.substring(x+1).equals(b.substring(y+1));
 }
 static Object duplicate(SQLiteDatabase db,JSONObject p,boolean merge)throws Exception{
  long id=p.getLong("entry"),other=p.getLong("peer");if(id==other)throw new IllegalArgumentException("请选择两笔不同记录");JSONObject a=FinanceData.entry(db,id),b=FinanceData.entry(db,other);FinanceData.checkRevision(a,p);if(b.getLong("rev")!=p.getLong("peerRev"))throw new IllegalStateException("对照记录已变化，请重新核对");
  if(!Arrays.asList("confirmed","review").contains(a.getString("status"))||!Arrays.asList("confirmed","review").contains(b.getString("status")))throw new IllegalArgumentException("记录已处理");
  if(!merge){FinanceData.put(db,FinanceData.object("distinct",id,other,0,"","已确认是不同消费",new JSONObject()));for(long row:new long[]{id,other}){ContentValues v=new ContentValues();v.put("status","confirmed");v.put("reason","");v.put("semantic","user-edited");db.update("entries",v,"_id=?",new String[]{""+row});}return true;}
  if(a.getLong("cents")!=b.getLong("cents")||!a.getString("kind").equals(b.getString("kind")))throw new IllegalArgumentException("金额或方向冲突，请先纠正后合并");
  if(strongConflict(a.optString("txkey"),b.optString("txkey")))throw new IllegalArgumentException("同平台不同交易号代表两笔交易，不能合并");
  for(String type:new String[]{"split","case"})if(FinanceData.number(db,"SELECT count(DISTINCT entry) FROM finance WHERE type=? AND entry IN (?,?)",type,""+id,""+other)>1)throw new IllegalArgumentException("两笔都有"+(type.equals("split")?"拆分":"结算案件")+"，请先处理冲突");
  if((a.getString("semantic").equals("user-edited")||a.getString("source").equals("manual"))&&(b.getString("semantic").equals("user-edited")||b.getString("source").equals("manual"))&&(!a.getString("merchant").equals(b.getString("merchant"))||!a.getString("category").equals(b.getString("category"))))throw new IllegalArgumentException("两笔手工字段不一致，请先核对后合并");
  ContentValues canonical=new ContentValues();canonical.put("status","confirmed");canonical.put("reason","");if(b.getString("semantic").equals("user-edited")||b.getString("source").equals("manual")){for(String k:new String[]{"merchant","category"})canonical.put(k,b.getString(k));canonical.put("semantic","user-edited");}db.update("entries",canonical,"_id=?",new String[]{""+id});
  JSONArray rel=FinanceData.query(db,"SELECT * FROM finance WHERE entry=? OR peer=?",""+other,""+other);for(int i=0;i<rel.length();i++){JSONObject f=rel.getJSONObject(i);if(f.getLong("entry")==other)f.put("entry",id);if(f.getLong("peer")==other)f.put("peer",id);f.put("rev",f.getLong("rev")+1);FinanceData.put(db,f);}
  String sourceCategory=b.getString("source"),sourceId="",txkey=b.getString("txkey");if(txkey.startsWith("provider|")){String[] parts=txkey.split("\\|",4);if(parts.length==4){sourceCategory=parts[1]+"|"+parts[2];sourceId=parts[3];}}
  if(sourceId.isEmpty()||FinanceData.number(db,"SELECT count(*) FROM finance WHERE type='source' AND category=? AND label=?",sourceCategory,sourceId)==0){JSONObject source=FinanceData.object("source",id,0,0,sourceCategory,sourceId,new JSONObject().put("time",b.getLong("time")).put("originalId",other).put("txkey",txkey));FinanceData.put(db,source);}
  FinanceData.put(db,FinanceData.object("merge",other,id,0,"","合并重定向",new JSONObject()));ContentValues removed=new ContentValues();removed.put("status","ignored");removed.put("ignored_at",0);db.update("entries",removed,"_id=?",new String[]{""+other});FinanceData.validateAll(db);return id;
 }
}
