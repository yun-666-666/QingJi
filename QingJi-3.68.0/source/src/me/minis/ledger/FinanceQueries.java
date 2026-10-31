package me.minis.ledger;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

final class FinanceQueries {
 static JSONObject overview(SQLiteDatabase db,JSONObject input)throws Exception{
  JSONObject q=new JSONObject(input.toString());q.put("status","confirmed").put("kind","expense").put("counted",true);q.remove("category");q.remove("group");LedgerQuery.Filter f=new LedgerQuery.Filter(q);
  Map<String,long[]> categories=new LinkedHashMap<>();long total=0,count=0,current=-1,ordinary=0,original=0;List<String> names=new ArrayList<>();List<Long> amounts=new ArrayList<>();
  try(Cursor c=db.rawQuery("SELECT e._id,e.part_category,e.part_cents,e.cents,"+ordinary("e")+" FROM ledger_parts e WHERE "+f.where+" ORDER BY e._id,e.part_id",f.args())){while(c.moveToNext()){
   if(current!=c.getLong(0)){if(current>=0){addParts(categories,names,amounts,ordinary,original);total+=ordinary;count++;}current=c.getLong(0);ordinary=c.getLong(4);original=c.getLong(3);names.clear();amounts.clear();}names.add(c.getString(1));amounts.add(c.getLong(2));
  }}if(current>=0){addParts(categories,names,amounts,ordinary,original);total+=ordinary;count++;}
  JSONArray rows=new JSONArray();for(Map.Entry<String,long[]> e:categories.entrySet())rows.put(new JSONObject().put("category",e.getKey()).put("cents",e.getValue()[0]).put("count",e.getValue()[1]));return new JSONObject().put("rows",rows).put("cents",total).put("count",count).put("revision",LedgerQuery.revision(db));
 }
 private static void addParts(Map<String,long[]> totals,List<String> names,List<Long> amounts,long ordinary,long original){
  long assigned=0;long[] distributed=new long[amounts.size()];for(int i=0;i<amounts.size();i++){distributed[i]=java.math.BigInteger.valueOf(amounts.get(i)).multiply(java.math.BigInteger.valueOf(ordinary)).divide(java.math.BigInteger.valueOf(original)).longValueExact();assigned+=distributed[i];}
  for(int i=0;assigned<ordinary;i++,assigned++)distributed[i%distributed.length]++;
  Set<String> counted=new HashSet<>();for(int i=0;i<names.size();i++){long[] row=totals.computeIfAbsent(names.get(i),key->new long[2]);row[0]+=distributed[i];if(counted.add(names.get(i)))row[1]++;}
 }
 // Principal and settlement are cash movements, not ordinary consumption/income.
 static String ordinary(String alias){return alias+".cents-COALESCE((SELECT sum(f.cents) FROM finance f WHERE f.entry="+alias+"._id AND (f.type='case' AND f.category IN ('loanOut','loanIn') OR f.type='settlement')),0)";}
 static long budget(SQLiteDatabase db,long start,long end,String category){
  String filter=category.isEmpty()?"":" AND part_category=?";String[] args=category.isEmpty()?new String[]{""+start,""+end}:new String[]{""+start,""+end,category};
  long total=FinanceData.number(db,"SELECT COALESCE(sum(CASE kind WHEN 'expense' THEN part_cents WHEN 'refund' THEN -part_cents ELSE 0 END),0) FROM ledger_parts WHERE status='confirmed' AND excluded=0 AND time>=? AND time<?"+filter,args);
  // Explicit opt-in per case; default budget behavior remains unchanged.
  try(Cursor c=db.rawQuery("SELECT f.*,e.category AS entry_category FROM finance f JOIN entries e ON e._id=f.entry WHERE f.type='case' AND e.status='confirmed' AND e.excluded=0 AND e.kind='expense' AND e.time>=? AND e.time<?",new String[]{""+start,""+end})){while(c.moveToNext())try{JSONObject f=UndoLog.row(c);if(new JSONObject(f.getString("data")).optBoolean("budgetExclude")){if(category.isEmpty())total-=f.getLong("cents");else{JSONArray parts=FinanceData.query(db,"SELECT part_category,part_cents,cents FROM ledger_parts WHERE _id=? ORDER BY part_id",""+f.getLong("entry"));List<String> names=new ArrayList<>();List<Long> amounts=new ArrayList<>();for(int i=0;i<parts.length();i++){names.add(parts.getJSONObject(i).getString("part_category"));amounts.add(parts.getJSONObject(i).getLong("part_cents"));}Map<String,long[]> shares=new HashMap<>();if(parts.length()>0){addParts(shares,names,amounts,f.getLong("cents"),parts.getJSONObject(0).getLong("cents"));if(shares.containsKey(category))total-=shares.get(category)[0];}}}}catch(JSONException e){throw new IllegalStateException(e);}}
  return total;
 }
 static JSONObject state(SQLiteDatabase db)throws Exception{
  JSONObject o=new JSONObject();o.put("rules",FinanceData.query(db,"SELECT * FROM finance WHERE type='rule' ORDER BY cents,id"));o.put("tags",FinanceData.query(db,"SELECT * FROM finance WHERE type='tag' ORDER BY label,id"));o.put("goals",FinanceData.query(db,"SELECT * FROM finance WHERE type='goal' ORDER BY label,id"));o.put("layout",FinanceData.get(db,"home-layout"));
  JSONArray goals=o.getJSONArray("goals");Map<String,Long> allocated=new HashMap<>();JSONArray a=FinanceData.query(db,"SELECT cents,data FROM finance WHERE type='allocation'");for(int i=0;i<a.length();i++){JSONObject r=a.getJSONObject(i);String id=new JSONObject(r.getString("data")).getString("goal");allocated.put(id,allocated.getOrDefault(id,0L)+r.getLong("cents"));}for(int i=0;i<goals.length();i++){JSONObject g=goals.getJSONObject(i);g.put("allocated",allocated.getOrDefault(g.getString("id"),0L));}
  return o;
 }
 static JSONObject calendar(SQLiteDatabase db,JSONObject p)throws Exception{
  int year=p.getInt("year"),month=p.getInt("month");if(year<1970||year>2099||month<0||month>11)throw new IllegalArgumentException("日期超出范围");Calendar c=Calendar.getInstance();c.clear();c.set(year,month,1);long start=c.getTimeInMillis();c.add(Calendar.MONTH,1);long end=c.getTimeInMillis(),through=Math.min(end,WidgetPeriods.throughToday(System.currentTimeMillis()));
  JSONArray rows=FinanceData.query(db,"SELECT CAST(strftime('%d',time/1000,'unixepoch','localtime') AS INTEGER) AS day,COALESCE(sum(CASE WHEN e.kind='expense' THEN "+ordinary("e")+" ELSE 0 END),0) AS expense,COALESCE(sum(CASE WHEN e.kind='refund' THEN e.cents ELSE 0 END),0) AS refund,COALESCE(sum(CASE WHEN e.kind='income' THEN "+ordinary("e")+" ELSE 0 END),0) AS income,count(*) AS count FROM entries e WHERE e.status='confirmed' AND e.excluded=0 AND e.time>=? AND e.time<? GROUP BY day",""+start,""+through);
  return new JSONObject().put("rows",rows).put("year",year).put("month",month).put("start",start).put("end",end).put("timezone",TimeZone.getDefault().getID());
 }
 static long share(List<String> names,List<Long> amounts,long value,long original,String category){
  if(category.isEmpty())return value;Map<String,long[]> shares=new HashMap<>();addParts(shares,names,amounts,value,original);return shares.containsKey(category)?shares.get(category)[0]:0;
 }
 static JSONObject project(SQLiteDatabase db,JSONObject p)throws Exception{
  String tag=p.optString("tag"),category=p.optString("category");if(!category.isEmpty())FinanceData.category(category);long end=WidgetPeriods.throughToday(System.currentTimeMillis());
  String eligible="e.status='confirmed' AND e.excluded=0 AND e.time<"+end+" AND e._id IN (SELECT entry FROM finance WHERE type='taglink' AND (?='' OR category=?))";
  String filter=category.isEmpty()?"":" AND e._id IN (SELECT _id FROM ledger_parts WHERE part_category=?)";String[] args=category.isEmpty()?new String[]{tag,tag}:new String[]{tag,tag,category};
  String visible="r.status='confirmed' AND r.excluded=0 AND r.time<"+end;
  long expense=0,refund=0,personal=0,pending=0;
  // One ordered parts cursor; split rounding follows the same integer allocation as category overview.
  String sql="SELECT e._id,e.cents,e.kind,e.part_category,e.part_cents,"+ordinary("e")+" AS ordinary,COALESCE((SELECT sum(f.cents) FROM finance f JOIN entries r ON r._id=f.entry WHERE f.type='refund' AND f.peer=e._id AND "+visible+" AND (?='' OR f.category=?)),0) AS refunded,COALESCE((SELECT sum(f.cents) FROM finance f JOIN entries r ON r._id=f.entry WHERE f.type='settlement' AND f.category='reimbursement' AND f.peer=e._id AND "+visible+"),0) AS settled,COALESCE((SELECT sum(f.cents) FROM finance f WHERE f.type='case' AND f.category='reimbursement' AND f.entry=e._id),0) AS owed FROM ledger_parts e WHERE "+eligible+" AND e.kind='expense' ORDER BY e._id,e.part_id";
  List<String> names=new ArrayList<>();List<Long> amounts=new ArrayList<>();long id=-1,original=0,ordinary=0,refunded=0,settled=0,owed=0;
  try(Cursor c=db.rawQuery(sql,new String[]{category,category,tag,tag})){while(c.moveToNext()){
   if(id!=c.getLong(0)&&id>=0){long part=share(names,amounts,ordinary,original,category),paid=share(names,amounts,settled,original,category);expense+=part;refund+=refunded;personal+=part-refunded-paid;pending+=share(names,amounts,Math.max(0,owed-settled),original,category);names.clear();amounts.clear();}
   id=c.getLong(0);original=c.getLong(1);ordinary=c.getLong(5);refunded=c.getLong(6);settled=c.getLong(7);owed=c.getLong(8);names.add(c.getString(3));amounts.add(c.getLong(4));
  }}if(id>=0){long part=share(names,amounts,ordinary,original,category),paid=share(names,amounts,settled,original,category);expense+=part;refund+=refunded;personal+=part-refunded-paid;pending+=share(names,amounts,Math.max(0,owed-settled),original,category);}
  // A directly tagged refund contributes only amounts not already counted through a tagged original expense.
  String direct="SELECT COALESCE(sum(e.part_cents),0) FROM ledger_parts e WHERE "+eligible+" AND e.kind='refund' AND (?='' OR e.part_category=?) AND (e.part_id='' OR NOT EXISTS(SELECT 1 FROM finance f JOIN entries r ON r._id=f.peer WHERE f.id=e.part_id AND "+visible+" AND r._id IN (SELECT entry FROM finance WHERE type='taglink' AND (?='' OR category=?))))";
  long extra=FinanceData.number(db,direct,tag,tag,category,category,tag,tag);refund+=extra;personal-=extra;
  long total=FinanceData.number(db,"SELECT count(*) FROM entries e WHERE "+eligible+filter,args),offset=Math.max(0,p.optInt("page"))*40L;
  return new JSONObject().put("expense",expense).put("refund",refund).put("personal",personal).put("pending",pending).put("rows",FinanceData.query(db,"SELECT e._id,e.merchant,e.time,e.cents,e.kind,e.source,e.category FROM entries e WHERE "+eligible+filter+" ORDER BY e.time DESC,e._id DESC LIMIT 40 OFFSET "+offset,args)).put("total",total).put("more",offset+40<total);
 }
 static JSONObject cases(SQLiteDatabase db,JSONObject p)throws JSONException{
  String filter=p.optString("kind").equals("loans")?"f.category IN ('loanIn','loanOut')":"f.category='reimbursement'";long offset=Math.max(0,p.optInt("page"))*40L;
  String where="f.type='case' AND "+filter;long total=FinanceData.number(db,"SELECT count(*) FROM finance f WHERE "+where);
  return new JSONObject().put("total",total).put("more",offset+40<total).put("rows",FinanceData.query(db,"SELECT f.*,e.time,e.cents AS original,e.kind,COALESCE((SELECT sum(s.cents) FROM finance s WHERE s.type='settlement' AND s.peer=f.entry),0) AS settled FROM finance f JOIN entries e ON e._id=f.entry WHERE "+where+" ORDER BY e.time DESC,f.id LIMIT 40 OFFSET "+offset));
 }
 static JSONObject day(SQLiteDatabase db,JSONObject p)throws Exception{
  Calendar c=Calendar.getInstance();c.setTimeInMillis(p.getLong("time"));c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);long start=c.getTimeInMillis();c.add(Calendar.DATE,1);long end=Math.min(c.getTimeInMillis(),WidgetPeriods.throughToday(System.currentTimeMillis())),offset=Math.max(0,p.optInt("page"))*40L;
  String where="e.status='confirmed' AND e.excluded=0 AND e.time>=? AND e.time<?";String[] args={""+start,""+end};long total=FinanceData.number(db,"SELECT count(*) FROM entries e WHERE "+where,args);
  return LedgerStats.json(db,start,end).put("total",total).put("more",offset+40<total).put("rows",FinanceData.query(db,"SELECT e._id,e.merchant,e.time,e.cents,e.kind,e.source,e.category FROM entries e WHERE "+where+" ORDER BY e.time DESC,e._id DESC LIMIT 40 OFFSET "+offset,args));
 }
 static Object read(SQLiteDatabase db,String op,JSONObject p)throws Exception{
  if(op.equals("cases"))return cases(db,p);
  if(op.equals("day"))return day(db,p);
  if(op.equals("layoutRead"))return FinanceData.get(db,"home-layout");
  if(op.equals("state"))return state(db);
  if(op.equals("detail"))return FinanceData.detail(db,p.getLong("entry"));
  if(op.equals("calendar"))return calendar(db,p);
  if(op.equals("project"))return project(db,p);
  if(op.equals("duplicates"))return FinanceRules.duplicates(db,p.getLong("entry"));
  if(op.equals("rulePreview"))return FinanceRules.preview(db,p,false);
  if(op.equals("goalDetail")){JSONArray rows=FinanceData.query(db,"SELECT * FROM finance WHERE type='allocation' ORDER BY id");JSONArray a=new JSONArray();for(int i=0;i<rows.length();i++)if(new JSONObject(rows.getJSONObject(i).getString("data")).optString("goal").equals(p.getString("goal")))a.put(rows.getJSONObject(i));return a;}
  if(op.equals("list")){String kind=p.optString("kind"),q=p.optString("q");int page=Math.max(0,p.optInt("page"));if(!Arrays.asList("","expense","income","refund").contains(kind)||q.length()>200)throw new IllegalArgumentException("查询不合法");String where="status='confirmed' AND (?='' OR kind=?) AND (?='' OR instr(merchant,?)>0 OR CAST(_id AS TEXT)=?)";return new JSONObject().put("rows",FinanceData.query(db,"SELECT e._id,e.time,e.kind,e.cents,e.merchant,e.category,v.rev FROM entries e JOIN ui_version v ON v.id=e._id WHERE "+where+" ORDER BY time DESC,_id DESC LIMIT 40 OFFSET "+((long)page*40),kind,kind,q,q,q)).put("total",FinanceData.number(db,"SELECT count(*) FROM entries WHERE "+where,kind,kind,q,q,q));}
  throw new IllegalArgumentException("不支持的查询");
 }
}
