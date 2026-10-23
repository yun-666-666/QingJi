package me.minis.ledger;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import org.json.*;
import java.security.MessageDigest;
import java.util.*;

public final class LedgerDb extends SQLiteOpenHelper {
 public static final String[] CATEGORIES={"餐饮","交通","购物","生活","医疗","娱乐","其他"};
 public static final String[] SOURCES={"com.eg.android.AlipayGphone","com.tencent.mm","com.unionpay","com.jingdong.app.mall"};
 public static final String[] LABELS={"支付宝","微信","云闪付","京东"};
 public LedgerDb(Context c){super(c,"ledger.db",null,9);app=c.getApplicationContext();setWriteAheadLoggingEnabled(true);}
 public void onCreate(SQLiteDatabase db){
  db.execSQL("CREATE TABLE entries (_id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, source TEXT NOT NULL, kind TEXT NOT NULL, cents INTEGER NOT NULL, merchant TEXT NOT NULL, category TEXT NOT NULL, status TEXT NOT NULL, raw TEXT NOT NULL, reason TEXT NOT NULL, nkey TEXT NOT NULL, semantic TEXT NOT NULL, fingerprint TEXT UNIQUE NOT NULL)");
  db.execSQL("CREATE INDEX by_time ON entries(time)");
  db.execSQL("CREATE INDEX by_key ON entries(nkey,time)");addIdentityColumns(db);addRetention(db);addCaptureIndexes(db);LedgerQuery.install(db);addFunding(db);addObserved(db);addTools(db);PaymentPageDb.install(db);
 }
 private void addCaptureIndexes(SQLiteDatabase db){db.execSQL("CREATE INDEX IF NOT EXISTS by_capture_match ON entries(source,kind,cents,time)");db.execSQL("CREATE INDEX IF NOT EXISTS by_status_time ON entries(status,time,_id)");}
 public void onUpgrade(SQLiteDatabase db,int oldV,int newV){if(oldV<2)addIdentityColumns(db);if(oldV<3)addRetention(db);if(oldV<4)addCaptureIndexes(db);if(oldV<5)LedgerQuery.install(db);if(oldV<6)addFunding(db);if(oldV<7){addObserved(db);for(String t:new String[]{"ui_insert","ui_update","ui_delete"})db.execSQL("DROP TRIGGER IF EXISTS "+t);LedgerQuery.install(db);db.execSQL("INSERT INTO ui_changes(id,os,ns) VALUES(0,'reset','reset')");}if(oldV<8)addTools(db);if(oldV<9)PaymentPageDb.install(db);}
 private void addTools(SQLiteDatabase db){db.execSQL("ALTER TABLE entries ADD COLUMN excluded INTEGER NOT NULL DEFAULT 0 CHECK(excluded IN (0,1))");LedgerPlans.install(db);UndoLog.install(db);}
 private void addObserved(SQLiteDatabase db){db.execSQL("ALTER TABLE entries ADD COLUMN observed_at INTEGER NOT NULL DEFAULT 0");}
 private void addFunding(SQLiteDatabase db){db.execSQL("ALTER TABLE entries ADD COLUMN funding TEXT NOT NULL DEFAULT ''");db.execSQL("CREATE INDEX IF NOT EXISTS by_funding_match ON entries(kind,cents,time)");}
 private void addRetention(SQLiteDatabase db){
  db.execSQL("ALTER TABLE entries ADD COLUMN ignored_at INTEGER NOT NULL DEFAULT 0");
  db.execSQL("CREATE INDEX by_ignored_at ON entries(status,ignored_at)");
  db.execSQL("CREATE INDEX by_received ON entries(received)");
  db.execSQL("CREATE TABLE ignored_keys (token TEXT PRIMARY KEY NOT NULL)");
  db.execSQL("UPDATE entries SET ignored_at=? WHERE status='ignored'",new Object[]{System.currentTimeMillis()});
 }
 public static final long IGNORED_RETENTION_MS=3L*24*60*60*1000;
 // Only irreversible hashes survive cleanup: no amount, source, merchant, time or raw text.
 private static Set<String> ignoredKeys(String source,String key,String txkey,String fingerprint){
  Set<String> keys=new HashSet<>();if(!fingerprint.isEmpty())keys.add(hash("fp|"+fingerprint));if(!txkey.isEmpty())keys.add(hash("tx|"+txkey));
  if(key.startsWith("wechat-receipt:")||key.startsWith("alipay-receipt:"))keys.add(hash("receipt|"+source+"|"+key));
  if(key.startsWith("alipay-preview:"))keys.add(hash("receipt|"+source+"|"+key));
  if(key.startsWith("flow:")){int split=key.indexOf(':',5);keys.add(hash("flow|"+source+"|"+(split>0?key.substring(0,split):key)));if(split>0)keys.add(hash("flow-receipt|"+source+"|"+key.substring(split+1)));}return keys;
 }
 private boolean wasPurged(SQLiteDatabase db,Set<String> keys){for(String key:keys)try(Cursor c=db.rawQuery("SELECT 1 FROM ignored_keys WHERE token=?",new String[]{key})){if(c.moveToFirst())return true;}return false;}
 public synchronized int cleanupIgnored(long now){
  if(now<IGNORED_RETENTION_MS)return 0;SQLiteDatabase db=getWritableDatabase();String[] args={""+(now-IGNORED_RETENTION_MS)};int count=0;
  db.beginTransaction();try{
   try(Cursor c=db.rawQuery("SELECT source,nkey,txkey,fingerprint FROM entries WHERE status='ignored' AND ignored_at>0 AND ignored_at<=?",args)){while(c.moveToNext())for(String key:ignoredKeys(c.getString(0),c.getString(1),c.getString(2),c.getString(3))){ContentValues v=new ContentValues();v.put("token",key);db.insertWithOnConflict("ignored_keys",null,v,SQLiteDatabase.CONFLICT_IGNORE);}}
   db.execSQL("DELETE FROM ui_changes WHERE id IN (SELECT _id FROM entries WHERE status='ignored' AND ignored_at>0 AND ignored_at<=?)",args);
   db.execSQL("DELETE FROM edit_log WHERE id IN (SELECT op FROM edit_after WHERE _id IN (SELECT _id FROM entries WHERE status='ignored' AND ignored_at>0 AND ignored_at<=?))",args);UndoLog.trim(db);count=db.delete("entries","status='ignored' AND ignored_at>0 AND ignored_at<=?",args);db.setTransactionSuccessful();
  }finally{db.endTransaction();}if(count>0)LedgerEvents.changed(app);return count;
 }
 public JSONArray backupIgnoredKeys()throws JSONException {JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT token FROM ignored_keys ORDER BY token",null)){while(c.moveToNext())out.put(c.getString(0));}return out;}
 private void addIdentityColumns(SQLiteDatabase db){db.execSQL("ALTER TABLE entries ADD COLUMN txkey TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE entries ADD COLUMN received INTEGER NOT NULL DEFAULT 0");db.execSQL("CREATE INDEX by_txkey ON entries(txkey)");}
 public static SharedPreferences prefs(Context c){return c.getSharedPreferences("settings",Context.MODE_PRIVATE);}
 private static Context app;
 public static boolean allowed(Context c,String pkg){return pkg!=null&&!pkg.equals(c.getPackageName());}
 public static void rememberLabel(Context c,String pkg){if(pkg==null||pkg.equals("manual")||pkg.endsWith(":screen")||prefs(c).contains("label_"+pkg))return;try{String name=c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString();prefs(c).edit().putString("label_"+pkg,name).apply();}catch(Exception ignored){}}
 public static String label(String source){if(source.endsWith(":screen"))return label(source.substring(0,source.length()-7))+" · 页面";if(app!=null){String name=prefs(app).getString("label_"+source,"");if(!name.isEmpty())return name;}for(int i=0;i<SOURCES.length;i++)if(SOURCES[i].equals(source))return LABELS[i];return source.equals("manual")?"手动":source;}
 static Map<String,String> knownLabels(){Map<String,String> out=new HashMap<>();if(app!=null)for(Map.Entry<String,?> e:prefs(app).getAll().entrySet())if(e.getKey().startsWith("label_")&&e.getValue() instanceof String)out.put(e.getKey().substring(6),(String)e.getValue());return out;}
 public static String hash(String s){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));return Hex.encode(b);}catch(Exception e){throw new IllegalStateException(e);}}
 public synchronized boolean capture(String source,String key,long time,String raw,PaymentParser.Result r){try(PowerDetail.Span span=PowerDetail.span("db_capture")){return captureInternal(source,key,time,raw,r);}}
 private boolean captureInternal(String source,String key,long time,String raw,PaymentParser.Result r){
  SQLiteDatabase db=getWritableDatabase();
  if(r.funding.isEmpty())r.funding=FundingEvidence.notification(source,raw);
  boolean originallyConfirmed=r.confirmed;
  ExpenseCategory.enrich(r,raw);
  String remembered=CategoryMemory.get(app,r.merchant,r.kind);
  if(!remembered.isEmpty())r.category=remembered;
  long received=time;TransactionIdentity.Info identity=TransactionIdentity.identify(source,raw,time,r);time=identity.time;
  String semantic=(r.paidEvidence?"paid:":"")+hash(r.kind+"|"+r.cents+"|"+r.merchant+"|"+r.confirmed);
  String fingerprint=hash(source+"|"+key+"|"+time+"|"+raw);boolean changed=false;
  db.beginTransaction();try{
   if(wasPurged(db,ignoredKeys(source,key,identity.key,fingerprint))){db.setTransactionSuccessful();return false;}
   if(key.startsWith("wechat-receipt:")||key.startsWith("alipay-receipt:")){try(Cursor c=db.rawQuery("SELECT _id FROM entries WHERE source=? AND nkey=? LIMIT 1",new String[]{source,key})){if(c.moveToFirst()){db.setTransactionSuccessful();return false;}}}
   if(!identity.key.isEmpty()){try(Cursor c=db.rawQuery("SELECT _id FROM entries WHERE txkey=? LIMIT 1",new String[]{identity.key})){if(c.moveToFirst()){db.setTransactionSuccessful();return false;}}}
   int screenUpdate=ScreenSessionDb.update(db,source,key,time,raw,r,prefs(app).getBoolean("keep_raw",true));if(screenUpdate>0){changed=screenUpdate==2;if(!r.pageSignature.isEmpty())try(Cursor mapped=db.rawQuery("SELECT _id FROM entries WHERE source=? AND nkey LIKE ? LIMIT 1",new String[]{source,ScreenSessionDb.group(key)+":%"})){if(mapped.moveToFirst())PaymentPageDb.remember(db,source,time,r,mapped.getLong(0));}db.setTransactionSuccessful();return false;}
   if(PaymentPageDb.apply(db,source,time,r)){db.setTransactionSuccessful();return false;}
   int dedup=CaptureDedup.apply(db,source,key,time,raw,r,identity.key,fingerprint,prefs(app).getBoolean("keep_raw",true));if(dedup>0){changed=dedup==2;db.setTransactionSuccessful();return false;}
   if(AlipayPreviewDb.suppress(db,source,key,time,r)){db.setTransactionSuccessful();return false;}
   if(key.startsWith("flow:")){
    String group=key.substring(0,key.indexOf(':',5)>0?key.indexOf(':',5):key.length());
    try(Cursor c=db.rawQuery("SELECT _id,status,reason,semantic FROM entries WHERE source=? AND (nkey=? OR nkey LIKE ?) LIMIT 1",new String[]{source,group,group+":%"})){if(c.moveToFirst()){if(r.confirmed&&c.getString(1).equals("review")&&!c.getString(2).contains("疑似重复")&&!c.getString(3).equals("user-edited")){ContentValues update=new ContentValues();update.put("status","confirmed");update.put("kind",r.kind);update.put("cents",r.cents);update.put("reason",r.reason);db.update("entries",update,"_id=?",new String[]{""+c.getLong(0)});changed=true;}db.setTransactionSuccessful();return false;}}
    int split=key.indexOf(':',5);if(split>0){String receiptId=key.substring(split+1);try(Cursor c=db.rawQuery("SELECT _id FROM entries WHERE source=? AND nkey LIKE ? LIMIT 1",new String[]{source,"flow:%:"+receiptId})){if(c.moveToFirst()){db.setTransactionSuccessful();return false;}}}
   }
   if(!identity.key.isEmpty()){try(Cursor c=db.rawQuery("SELECT _id FROM entries WHERE txkey=? LIMIT 1",new String[]{identity.key})){if(c.moveToFirst()){db.setTransactionSuccessful();return false;}}}
   if(identity.settlement){try(Cursor c=db.rawQuery("SELECT _id,source FROM entries WHERE cents=? AND kind=? AND status='confirmed' AND time BETWEEN ? AND ? LIMIT 1",new String[]{Long.toString(r.cents),r.kind,Long.toString(time-180000),Long.toString(time+180000)})){if(c.moveToFirst()){r.confirmed=false;r.reason="最终扣款通知关联此前预授权；原交易时间已有同金额记录 #"+c.getLong(0)+"，暂不再次计入支出，请核对是否同一笔";}}}
   try(Cursor c=db.rawQuery("SELECT time,semantic FROM entries WHERE nkey=? AND time BETWEEN ? AND ?",new String[]{key,Long.toString(time-600000),Long.toString(time+600000)})){
    while(c.moveToNext())if(PaymentParser.sameUpdate(key,key,c.getLong(0),time,c.getString(1),semantic)){db.setTransactionSuccessful();return false;}
   }
   if(source.endsWith(":screen")&&(r.pageSignature.isEmpty()||r.checkout.isEmpty()&&!r.pageStrong)){try(Cursor c=db.rawQuery("SELECT _id FROM entries WHERE source=? AND cents=? AND kind=? AND merchant=? AND nkey NOT LIKE 'alipay-preview:%' AND time BETWEEN ? AND ? LIMIT 1",new String[]{source,Long.toString(r.cents),r.kind,r.merchant,Long.toString(time-600000),Long.toString(time+600000)})){if(c.moveToFirst()){r.confirmed=false;r.reason="疑似重复：10分钟内相同页面金额/商户已有记录 #"+c.getLong(0)+"，可能是返回旧成功页；请对照核对，不能一起批量确认";}}}
   if(r.confirmed){try(Cursor c=db.rawQuery("SELECT _id,source FROM entries WHERE cents=? AND kind=? AND source<>? AND source<>'manual' AND status='confirmed' AND (merchant=? OR merchant='' OR ?='') AND time BETWEEN ? AND ? LIMIT 1",new String[]{Long.toString(r.cents),r.kind,source,r.merchant,r.merchant,Long.toString(time-180000),Long.toString(time+180000)})){if(c.moveToFirst()){r.confirmed=false;r.reason="疑似重复：3分钟内已有来自"+label(c.getString(1))+"的同金额记录 #"+c.getLong(0)+"，确认是另一笔再入账";}}}
   List<Long> oppositeReviews=new ArrayList<>();long oppositeConfirmed=0;
   try(Cursor c=db.rawQuery("SELECT _id,source,received,merchant,txkey,status FROM entries WHERE cents=? AND kind=? AND status IN ('confirmed','review') AND received BETWEEN ? AND ? ORDER BY _id DESC",new String[]{""+r.cents,r.kind,""+(received-180000),""+(received+180000)})){while(c.moveToNext()){if(!CrossSourceEvidence.related(source,c.getString(1),received,c.getLong(2),r.merchant,c.getString(3),identity.key,c.getString(4)))continue;if(c.getString(5).equals("confirmed")){oppositeConfirmed=c.getLong(0);break;}oppositeReviews.add(c.getLong(0));}}
   if(oppositeConfirmed>0){r.confirmed=false;r.reason=CrossSourceEvidence.reason(oppositeConfirmed);}
   // Manual entries have no provider transaction id. Only suggest a relation; never merge by amount.
   if(source.equals("com.tencent.mm:screen")&&key.startsWith("wechat-receipt:")){Calendar day=Calendar.getInstance();day.setTimeInMillis(time);day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);long start=day.getTimeInMillis();day.add(Calendar.DAY_OF_MONTH,1);try(Cursor c=db.rawQuery("SELECT _id,merchant FROM entries WHERE source='manual' AND status='confirmed' AND cents=? AND kind=? AND time>=? AND time<? ORDER BY ABS(time-?) LIMIT 1",new String[]{""+r.cents,r.kind,""+start,""+day.getTimeInMillis(),""+time})){if(c.moveToFirst()){r.confirmed=false;r.reason="疑似已手动记账：同日已有同金额"+(r.kind.equals("income")?"收入":"支出")+"记录 #"+c.getLong(0)+"（"+c.getString(1)+"）；未自动合并，若同一笔请忽略本条，另一笔才确认。"+r.reason;}}}
   // A named WeChat receipt may overlap an older anonymous success page. Keep both rows;
   // the amount is evidence for review only, never authority to assign the old merchant.
   linkAnonymousWechatPage(db,source,key,time,r);
   AlipayPreviewDb.relate(db,source,key,time,r);
   BankFunding.Match fundingMatch=BankFunding.match(db,source,time,r,originallyConfirmed);
   List<Long> genericRelations=GenericPaymentRelation.relate(db,source,time,r);
   ContentValues v=new ContentValues();v.put("time",time);v.put("source",source);v.put("kind",r.kind);v.put("cents",r.cents);v.put("merchant",r.merchant);v.put("category",r.category);v.put("status",r.confirmed?"confirmed":"review");v.put("raw",prefs(app).getBoolean("keep_raw",true)?(raw.length()>4000?raw.substring(0,4000):raw):"");v.put("reason",r.reason);v.put("nkey",key);v.put("semantic",semantic);v.put("fingerprint",fingerprint);v.put("txkey",identity.key);v.put("received",received);v.put("observed_at",r.observedAt>0?r.observedAt:System.currentTimeMillis());v.put("funding",r.funding);
   long id=db.insertWithOnConflict("entries",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(id==-1){try(Cursor existing=db.rawQuery("SELECT 1 FROM entries WHERE fingerprint=?",new String[]{fingerprint})){if(!existing.moveToFirst())throw new android.database.SQLException("capture insert failed");}}if(id!=-1&&r.confirmed)for(long other:oppositeReviews){ContentValues relation=new ContentValues();relation.put("reason",CrossSourceEvidence.reason(id));db.update("entries",relation,"_id=? AND status='review'",new String[]{""+other});}PaymentPageDb.remember(db,source,time,r,id);BankFunding.finish(db,id,source,fingerprint,fundingMatch);GenericPaymentRelation.finish(db,id,genericRelations);AlipayPreviewDb.retire(db,id,source,key,time,r);CaptureDedup.retirePreviews(db,id,source,key,time,r);db.setTransactionSuccessful();changed=id!=-1;return id!=-1;
  }finally{db.endTransaction();if(changed)LedgerEvents.changed(app);}
 }
 private static void linkAnonymousWechatPage(SQLiteDatabase db,String source,String key,long time,PaymentParser.Result r){
  if(!source.equals("com.tencent.mm:screen")||!key.startsWith("wechat-receipt:")||r.merchant.trim().isEmpty()||!r.kind.equals("expense")||r.cents<=0)return;
  Calendar day=Calendar.getInstance();day.setTimeInMillis(time);day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);long start=day.getTimeInMillis();day.add(Calendar.DAY_OF_MONTH,1);
  // Use the receipt's transaction time, not its reopening time; never span a local date.
  // screen: is the immediate success-page namespace. Do not include flows or other receipts.
  try(Cursor c=db.rawQuery("SELECT _id FROM entries WHERE source=? AND nkey LIKE 'screen:%' AND merchant='' AND status='confirmed' AND cents=? AND kind=? AND time>=? AND time<? AND time BETWEEN ? AND ? ORDER BY ABS(time-?),_id DESC LIMIT 1",new String[]{source,""+r.cents,r.kind,""+start,""+day.getTimeInMillis(),""+(time-600000),""+(time+600000),""+time})){
   if(c.moveToFirst()){r.confirmed=false;r.reason="疑似重复：同日10分钟内已有商户未识别的微信支付成功页记录 #"+c.getLong(0)+"；仅作疑似关联，未认定商户、未自动合并或修改原记录。若同一笔请核对后处理，另一笔才确认。"+r.reason;}
  }
 }
 public long[] widgetTotals(long now,int period,boolean net){long[] b=WidgetPeriods.bounds(now,period),out=new long[3];long end=WidgetPeriods.throughToday(now);try(PowerDetail.Span span=PowerDetail.span("db_widget")){android.database.sqlite.SQLiteDatabase db=getReadableDatabase();db.beginTransactionNonExclusive();try{for(int i=0;i<3;i++)out[i]=LedgerStats.amount(LedgerStats.totals(db,b[i],Math.min(b[i+1],end)),net,false);db.setTransactionSuccessful();}finally{db.endTransaction();}}return out;}
 public long[] summaryTotals(long now,boolean net){try(PowerDetail.Span span=PowerDetail.span("db_summary")){return summaryInternal(now,net);}}
 private long[] summaryInternal(long now,boolean net){
  SQLiteDatabase db=getReadableDatabase();long[] out=new long[3];db.beginTransactionNonExclusive();try{for(int i=0;i<3;i++){long[] r=LedgerStats.range(now,i,true,now);out[i]=LedgerStats.amount(LedgerStats.totals(db,r[0],r[1]),net,false);}db.setTransactionSuccessful();return out;}finally{db.endTransaction();}
 }
 public JSONArray all() throws JSONException {try(PowerDetail.Span span=PowerDetail.span("db_read")){return allInternal();}}
 // UI transports metadata only. Raw text and dedup material stay native until explicitly requested.
 public JSONArray brief() throws JSONException {classifyKnownMerchants();return readRows(new String[]{"_id","time","source","kind","cents","merchant","category","status"},"status != ?",new String[]{"ignored"});}
 public JSONArray ignored() throws JSONException {cleanupIgnored(System.currentTimeMillis());return readRows(new String[]{"_id","time","source","kind","cents","merchant","category","status"},"status = ?",new String[]{"ignored"});}
 public void exportCsv(java.io.OutputStream out)throws java.io.IOException{LedgerCsv.write(getReadableDatabase(),out);}
 public JSONObject ignoredPage(int requested,int size)throws JSONException {
  if(requested<0||size<1||size>100)throw new IllegalArgumentException("分页参数不合法");cleanupIgnored(System.currentTimeMillis());SQLiteDatabase db=getReadableDatabase();db.beginTransactionNonExclusive();try{
   long total=android.database.DatabaseUtils.longForQuery(db,"SELECT count(*) FROM entries WHERE status='ignored'",null);long page=Math.min((long)requested,Math.max(0,(total-1)/size));JSONArray rows=new JSONArray();
   try(Cursor c=db.query("entries",new String[]{"_id","time","source","kind","cents","merchant","category","status"},"status=?",new String[]{"ignored"},null,null,"time DESC,_id DESC",(page*size)+","+size)){while(c.moveToNext())rows.put(cursorRow(c));}
   JSONObject result=new JSONObject().put("rows",rows).put("total",total).put("page",page).put("size",size);db.setTransactionSuccessful();return result;
  }finally{db.endTransaction();}
 }
 private JSONArray readRows(String[] cols,String where,String[] args)throws JSONException {JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().query("entries",cols,where,args,null,null,"time DESC,_id DESC")){while(c.moveToNext())out.put(cursorRow(c));}return out;}
 private JSONObject cursorRow(Cursor c)throws JSONException {JSONObject o=new JSONObject();for(String col:c.getColumnNames()){int i=c.getColumnIndexOrThrow(col);if(col.equals("_id")||col.equals("time")||col.equals("cents")||col.equals("received")||col.equals("ignored_at")||col.equals("observed_at")||col.equals("excluded"))o.put(col,c.getLong(i));else o.put(col,c.getString(i));}return o;}
 public JSONObject detail(long id)throws JSONException {JSONArray a=readRows(null,"_id=?",new String[]{""+id});if(a.length()==0)throw new JSONException("记录已不存在，请刷新");JSONObject o=a.getJSONObject(0);long related=relatedId(o.optString("reason"));if(related>0&&related!=id){JSONArray b=readRows(new String[]{"_id","time","source","kind","cents","merchant","category","status"},"_id=?",new String[]{""+related});if(b.length()>0)o.put("related",b.getJSONObject(0));else o.put("relatedMissing",true);}return o;}
 private static long relatedId(String reason){java.util.regex.Matcher m=java.util.regex.Pattern.compile("记录 #(\\d+)").matcher(reason);if(!m.find())return 0;try{return Long.parseLong(m.group(1));}catch(Exception e){return 0;}}
 public void exportBackup(java.io.OutputStream stream)throws Exception {
  cleanupIgnored(System.currentTimeMillis());SQLiteDatabase sql=getReadableDatabase();sql.beginTransactionNonExclusive();
  try{long rows=android.database.DatabaseUtils.longForQuery(sql,"SELECT count(*) FROM entries",null),keys=android.database.DatabaseUtils.longForQuery(sql,"SELECT count(*) FROM ignored_keys",null);
   if(rows>BackupStream.MAX_ROWS||keys>BackupStream.MAX_KEYS)throw new java.io.IOException("账本超过备份安全上限，未生成备份");
   java.io.Writer w=new java.io.BufferedWriter(new java.io.OutputStreamWriter(new BackupStream.LimitedOutput(stream),"UTF-8"));
   w.write("{\"app\":\"notification-ledger\",\"version\":2,\"exportedAt\":"+System.currentTimeMillis()+",\"entries\":[");boolean first=true;
   try(Cursor c=sql.rawQuery("SELECT e.*,r.fingerprint AS relatedFingerprint FROM entries e LEFT JOIN entries r ON r._id=CASE WHEN instr(e.reason,'记录 #')>0 THEN CAST(substr(e.reason,instr(e.reason,'记录 #')+4) AS INTEGER) ELSE 0 END ORDER BY e._id",null)){while(c.moveToNext()){JSONObject o=cursorRow(c);if(o.isNull("relatedFingerprint"))o.remove("relatedFingerprint");if(!first)w.write(',');first=false;w.write(o.toString());}}
   w.write("],\"ignoredKeys\":[");first=true;try(Cursor c=sql.rawQuery("SELECT token FROM ignored_keys ORDER BY token",null)){while(c.moveToNext()){if(!first)w.write(',');first=false;w.write(JSONObject.quote(c.getString(0)));}}w.write("],\"plans\":"+JSONObject.quote(LedgerPlans.backup(sql))+"}");w.flush();sql.setTransactionSuccessful();
  }finally{sql.endTransaction();}
 }
 public int importBackup(BackupStream backup)throws Exception {
  SQLiteDatabase sql=getWritableDatabase();int count=0;sql.beginTransaction();try{
   LedgerPlans.restore(sql,backup.plans);
   try(Cursor c=backup.stage.rawQuery("SELECT token FROM markers",null)){while(c.moveToNext()){ContentValues v=new ContentValues();v.put("token",c.getString(0));sql.insertWithOnConflict("ignored_keys",null,v,SQLiteDatabase.CONFLICT_IGNORE);}}
   try(Cursor c=backup.stage.rawQuery("SELECT n,payload FROM rows ORDER BY n",null)){while(c.moveToNext()){JSONObject row=new JSONObject(c.getString(1));int n=importRow(sql,row,backup.version,System.currentTimeMillis(),null);count+=n;if(n>0){ContentValues v=new ContentValues();v.put("inserted",1);backup.stage.update("rows",v,"n=?",new String[]{""+c.getLong(0)});}}}
   // Resolve forward references only after every row has been merged. Never rewrite an existing local row.
   try(Cursor c=backup.stage.rawQuery("SELECT fp,ref,reason FROM rows WHERE inserted=1 AND ref<>''",null)){while(c.moveToNext()){String fp=c.getString(1),reason=c.getString(2);long old=relatedId(reason);if(old==0)continue;try(Cursor related=sql.rawQuery("SELECT _id FROM entries WHERE fingerprint=?",new String[]{fp})){if(related.moveToFirst()){ContentValues v=new ContentValues();v.put("reason",reason.replace("记录 #"+old,"记录 #"+related.getLong(0)));sql.update("entries",v,"fingerprint=?",new String[]{c.getString(0)});}}}}
   sql.setTransactionSuccessful();
  }finally{sql.endTransaction();}LedgerEvents.changed(app);return count;
 }
 public JSONArray backupRows()throws JSONException {cleanupIgnored(System.currentTimeMillis());JSONArray rows=readRows(null,null,null);Map<Long,String> ids=new HashMap<>();for(int i=0;i<rows.length();i++){JSONObject o=rows.getJSONObject(i);ids.put(o.getLong("_id"),o.getString("fingerprint"));}for(int i=0;i<rows.length();i++){JSONObject o=rows.getJSONObject(i);String fp=ids.get(relatedId(o.optString("reason")));if(fp!=null)o.put("relatedFingerprint",fp);}return rows;}
 public void restore(long id){cleanupIgnored(System.currentTimeMillis());ContentValues v=new ContentValues();v.put("status","review");v.put("ignored_at",0);v.put("reason","从已忽略恢复，请重新核对后确认；恢复不会直接计入统计");if(getWritableDatabase().update("entries",v,"_id=? AND status='ignored'",new String[]{""+id})!=1)throw new IllegalStateException("记录已不存在或已变化，请刷新");LedgerEvents.changed(app);}
 private JSONArray allInternal() throws JSONException {
  JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().query("entries",null,"status != ?",new String[]{"ignored"},null,null,"time DESC,_id DESC")){
   while(c.moveToNext()){JSONObject o=new JSONObject();for(String col:c.getColumnNames()){int i=c.getColumnIndexOrThrow(col);if(col.equals("_id")||col.equals("time")||col.equals("cents"))o.put(col,c.getLong(i));else o.put(col,c.getString(i));}out.put(o);}
  }return out;
 }
 public void save(long id,long time,String source,String kind,long cents,String merchant,String category,String status){try(PowerDetail.Span span=PowerDetail.span("db_edit")){saveInternal(id,time,source,kind,cents,merchant,category,status);}}
 private void saveInternal(long id,long time,String source,String kind,long cents,String merchant,String category,String status){
  if(id<0)throw new IllegalArgumentException("记录编号不合法");SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
  ContentValues v=new ContentValues();v.put("time",time);v.put("source",source);v.put("kind",kind);v.put("cents",cents);v.put("merchant",merchant);v.put("category",category);v.put("status",status);v.put("reason","");v.put("ignored_at",0);
  if(id>0){v.put("semantic","user-edited");if(db.update("entries",v,"_id=? AND status IN ('confirmed','review')",new String[]{Long.toString(id)})!=1)throw new IllegalStateException("记录已不存在或已变化，请刷新");}
  else{v.put("raw","");v.put("nkey","");v.put("semantic","");v.put("fingerprint",UUID.randomUUID().toString());db.insertOrThrow("entries",null,v);}
  if(id>0&&status.equals("confirmed"))linkOppositeReviews(id,source,kind,cents,merchant);db.setTransactionSuccessful();
  }finally{db.endTransaction();}LedgerEvents.changed(app);
 }
 private void linkOppositeReviews(long id,String source,String kind,long cents,String merchant){SQLiteDatabase db=getWritableDatabase();long received=0;String txkey="";try(Cursor own=db.rawQuery("SELECT received,txkey FROM entries WHERE _id=?",new String[]{""+id})){if(own.moveToFirst()){received=own.getLong(0);txkey=own.getString(1);}}if(received<=0)return;try(Cursor c=db.rawQuery("SELECT _id,source,received,merchant,txkey FROM entries WHERE _id<>? AND cents=? AND kind=? AND status='review' AND received BETWEEN ? AND ?",new String[]{""+id,""+cents,kind,""+(received-180000),""+(received+180000)})){while(c.moveToNext())if(CrossSourceEvidence.related(source,c.getString(1),received,c.getLong(2),merchant,c.getString(3),txkey,c.getString(4))){ContentValues relation=new ContentValues();relation.put("reason",CrossSourceEvidence.reason(id));db.update("entries",relation,"_id=? AND status='review'",new String[]{""+c.getLong(0)});}}}
 public void ignore(long id){ContentValues v=new ContentValues();v.put("status","ignored");v.put("ignored_at",System.currentTimeMillis());if(getWritableDatabase().update("entries",v,"_id=? AND status IN ('confirmed','review')",new String[]{Long.toString(id)})!=1)throw new IllegalStateException("记录已不存在或已变化，请刷新");LedgerEvents.changed(app);}
 public int batch(JSONArray ids,String action,String category)throws JSONException {
  if(ids.length()==0||ids.length()>5000)throw new JSONException("请选择1至5000笔记录");if(!action.equals("ignore")&&!action.equals("category"))throw new JSONException("不支持的操作");if(action.equals("category")&&!Arrays.asList(CATEGORIES).contains(category))throw new JSONException("分类不合法");
  SQLiteDatabase db=getWritableDatabase();Set<Long> unique=new HashSet<>();int changed=0;db.beginTransaction();try{for(int i=0;i<ids.length();i++){long id=ids.getLong(i);if(id<=0)throw new JSONException("记录编号不合法");if(!unique.add(id))continue;ContentValues v=new ContentValues();if(action.equals("ignore")){v.put("status","ignored");v.put("ignored_at",System.currentTimeMillis());}else {v.put("category",category);v.put("semantic","user-edited");}int n=db.update("entries",v,"_id=? AND status='confirmed'",new String[]{""+id});if(n!=1)throw new JSONException("所选记录已变化，请刷新后重新选择");changed+=n;}db.setTransactionSuccessful();}finally{db.endTransaction();}if(!db.inTransaction())LedgerEvents.changed(app);return changed;
 }
 public int reviewBatch(JSONArray ids,String action)throws JSONException {
  if(ids.length()==0||ids.length()>5000)throw new JSONException("请选择1至5000笔待确认记录");if(!action.equals("ignore")&&!action.equals("confirm"))throw new JSONException("不支持的操作");
  SQLiteDatabase db=getWritableDatabase();Set<Long> unique=new LinkedHashSet<>();List<Object[]> confirmed=new ArrayList<>();int changed=0;db.beginTransaction();try{for(int i=0;i<ids.length();i++){long id=ids.getLong(i);if(id<=0)throw new JSONException("记录编号不合法");if(!unique.add(id))continue;
   try(Cursor c=db.rawQuery("SELECT source,kind,cents,merchant,status,reason,time,funding FROM entries WHERE _id=?",new String[]{""+id})){if(!c.moveToFirst()||!c.getString(4).equals("review"))throw new JSONException("所选记录已变化，请刷新后重新选择");if(action.equals("confirm")&&(c.getString(5).contains("疑似重复")||c.getString(5).contains("已手动记账")||BankFunding.suspect(db,id,c.getString(0),c.getString(1),c.getLong(2),c.getLong(6),c.getString(7))||!GenericPaymentRelation.candidates(db,id,c.getString(0),c.getString(1),c.getLong(2),c.getLong(6)).isEmpty()))throw new JSONException("所选记录包含疑似重复，请逐笔核对；同一笔请忽略，另一笔才单独入账");if(action.equals("confirm"))confirmed.add(new Object[]{id,c.getString(0),c.getString(1),c.getLong(2),c.getString(3)});}
   ContentValues v=new ContentValues();v.put("status",action.equals("confirm")?"confirmed":"ignored");v.put("ignored_at",action.equals("confirm")?0:System.currentTimeMillis());if(action.equals("confirm")){v.put("reason","");v.put("semantic","user-edited");}int n=db.update("entries",v,"_id=? AND status='review'",new String[]{""+id});if(n!=1)throw new JSONException("所选记录已变化，请刷新后重新选择");changed+=n;
  }for(Object[] row:confirmed)linkOppositeReviews((Long)row[0],(String)row[1],(String)row[2],(Long)row[3],(String)row[4]);db.setTransactionSuccessful();}finally{db.endTransaction();}if(!db.inTransaction())LedgerEvents.changed(app);return changed;
 }
 public void classifyKnownMerchants(){if(prefs(app).getBoolean("category_hui33",false))return;getWritableDatabase().execSQL("UPDATE entries SET category='生活' WHERE category='其他' AND source<>'manual' AND (merchant LIKE '慧生活%' OR merchant LIKE '你在慧生活%')");prefs(app).edit().putBoolean("category_hui33",true).apply();}
 void notifyChanged(){LedgerEvents.changed(app);}
 public void clearRaw(){ContentValues v=new ContentValues();v.put("raw","");SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try{sql.update("entries",v,null,null);sql.update("edit_before",v,null,null);sql.update("edit_after",v,null,null);sql.setTransactionSuccessful();}finally{sql.endTransaction();}LedgerEvents.changed(app);}
 public void clearAll(){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{UndoLog.clear(db);db.delete("payment_pages",null,null);db.delete("entries",null,null);db.delete("ignored_keys",null,null);db.delete("ui_selection",null,null);db.delete("ui_changes",null,null);db.execSQL("INSERT INTO ui_changes(id,os,ns) VALUES(0,'reset','reset')");db.setTransactionSuccessful();}finally{db.endTransaction();}LedgerEvents.changed(app);}
 public int importBackup(JSONObject backup) throws JSONException {return importBackup(backup,true);}
 private int importBackup(JSONObject backup,boolean notify) throws JSONException {
  int version=backup.optInt("version");if((version!=1&&version!=2)||!backup.optString("app").equals("notification-ledger"))throw new JSONException("不是此应用的备份格式");
  JSONArray rows=backup.getJSONArray("entries");if(rows.length()>50000)throw new JSONException("备份超过50000条");
  SQLiteDatabase db=getWritableDatabase();db.beginTransaction();int count=0;Map<Long,String[]> refs=new HashMap<>();
  try{JSONArray keys=backup.optJSONArray("ignoredKeys");if(keys!=null){if(keys.length()>200000)throw new JSONException("去重标记过多");for(int i=0;i<keys.length();i++){String key=keys.getString(i);if(!key.matches("[0-9a-f]{64}"))throw new JSONException("去重标记不合法");ContentValues marker=new ContentValues();marker.put("token",key);db.insertWithOnConflict("ignored_keys",null,marker,SQLiteDatabase.CONFLICT_IGNORE);}}
  long importNow=System.currentTimeMillis();for(int i=0;i<rows.length();i++){
   count+=importRow(db,rows.getJSONObject(i),version,importNow,refs);
  }for(Map.Entry<Long,String[]> ref:refs.entrySet()){try(Cursor c=db.rawQuery("SELECT _id FROM entries WHERE fingerprint=?",new String[]{ref.getValue()[0]})){if(c.moveToFirst()){String reason=ref.getValue()[1];long old=relatedId(reason);ContentValues v=new ContentValues();v.put("reason",reason.replace("记录 #"+old,"记录 #"+c.getLong(0)));db.update("entries",v,"_id=?",new String[]{""+ref.getKey()});}}}db.setTransactionSuccessful();}finally{db.endTransaction();}if(notify)LedgerEvents.changed(app);return count;
 }
 private int importRow(SQLiteDatabase db,JSONObject o,int version,long importNow,Map<Long,String[]> refs)throws JSONException {
   String kind=o.getString("kind"),status=o.getString("status"),category=o.getString("category");long cents=o.getLong("cents"),time=o.getLong("time");
   if(!Arrays.asList("expense","income","refund").contains(kind)||!(version==2?Arrays.asList("confirmed","review","ignored"):Arrays.asList("confirmed","review")).contains(status)||!Arrays.asList(CATEGORIES).contains(category)||cents<=0||cents>99999999999L||time<0||time>4102444800000L)throw new JSONException("记录金额、日期、状态或分类不合法");
   ContentValues v=new ContentValues();v.put("kind",kind);v.put("status",status);v.put("category",category);v.put("cents",cents);v.put("time",time);
   for(String k:new String[]{"source","merchant","raw","reason","nkey","semantic","fingerprint"}){String x=o.optString(k,"");if(x.length()>4000)throw new JSONException("字段过长");v.put(k,x);}
   if(o.optString("fingerprint").isEmpty())throw new JSONException("缺少唯一标识");
   long observed=o.optLong("observed_at",0);if(observed<0||observed>4102444800000L)throw new JSONException("采集时间不合法");v.put("observed_at",observed);int excluded=o.optInt("excluded",0);if(excluded!=0&&excluded!=1)throw new JSONException("统计排除字段不合法");v.put("excluded",excluded);
   String funding=o.optString("funding","");if(!FundingEvidence.valid(funding))throw new JSONException("付款来源字段不合法");v.put("funding",funding);
   String txkey=o.optString("txkey","");long received=o.optLong("received",0);if(txkey.length()>4000||received<0||received>4102444800000L)throw new JSONException("去重信息不合法");v.put("txkey",txkey);v.put("received",received);
   if(v.getAsString("source").isEmpty()||v.getAsString("source").length()>255||v.getAsString("merchant").length()>100)throw new JSONException("来源或商户字段不合法");
   // Existing local rows always win; fill absent identity only, never overwrite user edits/status.
   try(Cursor c=db.rawQuery("SELECT _id,txkey FROM entries WHERE fingerprint=?",new String[]{o.getString("fingerprint")})){if(c.moveToFirst()){if(c.getString(1).isEmpty()&&!txkey.isEmpty()){ContentValues identity=new ContentValues();identity.put("txkey",txkey);identity.put("received",received);db.update("entries",identity,"_id=?",new String[]{""+c.getLong(0)});}return 0;}}
   if(wasPurged(db,ignoredKeys(o.getString("source"),o.optString("nkey"),txkey,o.getString("fingerprint"))))return 0;
   long ignoredAt=o.optLong("ignored_at",0);if(ignoredAt<0||ignoredAt>4102444800000L)throw new JSONException("忽略时间不合法");v.put("ignored_at",status.equals("ignored")?(ignoredAt>0?Math.min(ignoredAt,importNow):importNow):0);
   long related=relatedId(o.optString("reason"));if(related>0)v.put("reason",o.optString("reason").replace("记录 #"+related,"记录（备份关联待核对）"));
   long inserted=db.insertOrThrow("entries",null,v);if(inserted!=-1){String ref=o.optString("relatedFingerprint");if(refs!=null&&!ref.isEmpty())refs.put(inserted,new String[]{ref,o.optString("reason")});return 1;}return 0;
 }
}
