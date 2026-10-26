package me.minis.ledger;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.json.*;
import java.io.*;
import java.nio.charset.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

/** Local disk staging. Preview never mutates the ledger; commit rechecks every identity. */
final class BillImport {
 private static final long LIMIT=128L*1024*1024;
 interface RowSink {void row(List<String> row)throws Exception;}
 static File stageFile(Context c){return new File(c.getCacheDir(),"statement-preview.db");}
 static SQLiteDatabase stage(Context c){SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(stageFile(c),null);db.execSQL("CREATE TABLE IF NOT EXISTS rows(n INTEGER PRIMARY KEY,payload TEXT,status TEXT,reason TEXT,entry INTEGER DEFAULT 0,rev INTEGER DEFAULT 0,selected INTEGER DEFAULT 0)");db.execSQL("CREATE TABLE IF NOT EXISTS metadata(key TEXT PRIMARY KEY,value TEXT)");return db;}
 static void prepare(Context c,LedgerDb ledger,Uri uri)throws Exception{
  File input=File.createTempFile("qingji-statement-",".input",c.getCacheDir());
  try{try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(input)){copy(in,out,LIMIT);}
   try(SQLiteDatabase db=stage(c)){db.beginTransaction();try{db.delete("rows",null,null);db.delete("metadata",null,null);ContentValues v=new ContentValues();v.put("key","batch");v.put("value",UUID.randomUUID().toString());db.insertOrThrow("metadata",null,v);ContentValues doc=new ContentValues();doc.put("key","document");doc.put("value",uri.toString());db.insertOrThrow("metadata",null,doc);Parser parser=new Parser(db,ledger.getReadableDatabase());parser.document=uri.toString();readFile(input,parser::row,0);if(parser.header==null)throw new IOException("未找到支持的账单表头。支持微信/支付宝 CSV、XLSX 及单账单 ZIP；需要交易时间、金额、收支、状态和交易对方列。");ContentValues summary=new ContentValues();summary.put("key","summary");summary.put("value",new JSONObject().put("platform",parser.platform).put("start",parser.first==Long.MAX_VALUE?0:parser.first).put("end",parser.last).toString());db.insertOrThrow("metadata",null,summary);db.setTransactionSuccessful();}finally{db.endTransaction();}}
  }finally{input.delete();}
 }
 static void copy(InputStream in,OutputStream out,long limit)throws IOException{byte[] b=new byte[32768];long size=0;int n;while((n=in.read(b))!=-1){size+=n;if(size>limit)throw new IOException("账单解压或文件超过128MiB");out.write(b,0,n);}}
 static void readFile(File input,RowSink sink,int depth)throws Exception{
  byte[] head=new byte[4];try(InputStream in=new FileInputStream(input)){in.read(head);}if(head[0]=='P'&&head[1]=='K'){
   try(ZipFile zip=new ZipFile(input)){long total=0;Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();if(e.getSize()<0||e.getSize()>LIMIT||(total+=e.getSize())>LIMIT)throw new IOException("压缩账单超过限制");}
    if(zip.getEntry("xl/workbook.xml")!=null){xlsx(zip,sink);return;}
    if(depth>0)throw new IOException("不支持多层压缩包");List<ZipEntry> files=new ArrayList<>();entries=zip.entries();while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();if(!e.isDirectory()&&e.getName().toLowerCase(Locale.ROOT).matches(".*\\.(csv|xlsx)$"))files.add(e);}if(files.size()!=1)throw new IOException("压缩包需包含一个账单 CSV 或 XLSX，请分别选择账单");File extracted=File.createTempFile("qingji-statement-",".table",input.getParentFile());try{try(InputStream in=zip.getInputStream(files.get(0));OutputStream out=new FileOutputStream(extracted)){copy(in,out,LIMIT);}readFile(extracted,sink,depth+1);}finally{extracted.delete();}
   }return;
  }
  Charset charset=StandardCharsets.UTF_8;
  try(Reader r=new InputStreamReader(new FileInputStream(input),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT))){char[] b=new char[16384];while(r.read(b)!=-1){}}
  catch(CharacterCodingException e){charset=Charset.forName("GB18030");}
  try(PushbackReader r=new PushbackReader(new BufferedReader(new InputStreamReader(new FileInputStream(input),charset)),1)){csv(r,sink);}
 }
 static void csv(PushbackReader r,RowSink sink)throws Exception{
  List<String> row=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false,start=true;int v;
  while((v=r.read())!=-1){char ch=(char)v;if(quoted){if(ch=='"'){int next=r.read();if(next=='"')field.append('"');else{quoted=false;if(next!=-1)r.unread(next);}}else field.append(ch);}else if(ch=='"'&&start){quoted=true;start=false;}else if(ch==','){row.add(field.toString());field.setLength(0);start=true;}else if(ch=='\n'||ch=='\r'){if(ch=='\r'){int next=r.read();if(next!=-1&&next!='\n')r.unread(next);}row.add(field.toString());sink.row(row);row=new ArrayList<>();field.setLength(0);start=true;}else{field.append(ch);start=false;}if(field.length()>16000||row.size()>100)throw new IOException("账单单元格或列数过大");}
  if(quoted)throw new IOException("CSV 引号未闭合");if(field.length()>0||!row.isEmpty()){row.add(field.toString());sink.row(row);}
 }
 static XmlPullParser xml(InputStream in)throws Exception{XmlPullParser p=Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);p.setInput(in,"UTF-8");return p;}
 static void xlsx(ZipFile zip,RowSink sink)throws Exception{
  List<String> strings=new ArrayList<>();ZipEntry shared=zip.getEntry("xl/sharedStrings.xml");if(shared!=null)try(InputStream in=zip.getInputStream(shared)){XmlPullParser p=xml(in);StringBuilder text=new StringBuilder();long chars=0;for(int event=p.getEventType();event!=XmlPullParser.END_DOCUMENT;event=p.next()){if(event==XmlPullParser.START_TAG&&p.getName().equals("si"))text.setLength(0);if(event==XmlPullParser.START_TAG&&p.getName().equals("t"))text.append(p.nextText());if(event==XmlPullParser.END_TAG&&p.getName().equals("si")){chars+=text.length();if(text.length()>16000||chars>32L*1024*1024||strings.size()>500000)throw new IOException("XLSX 文本过大");strings.add(text.toString());}}}
  ZipEntry sheet=zip.getEntry("xl/worksheets/sheet1.xml");if(sheet==null)throw new IOException("XLSX 未找到首张工作表");
  try(InputStream in=zip.getInputStream(sheet)){XmlPullParser p=xml(in);List<String> row=null;String type="",value="";int col=0;boolean formula=false;for(int event=p.getEventType();event!=XmlPullParser.END_DOCUMENT;event=p.next()){
   if(event==XmlPullParser.START_TAG){String tag=p.getName();if(tag.equals("row"))row=new ArrayList<>();if(tag.equals("c")){String ref=p.getAttributeValue(null,"r");col=0;if(ref!=null)for(char ch:ref.toCharArray()){if(ch<'A'||ch>'Z')break;col=col*26+ch-'A'+1;}col--;if(col<0||col>=100)throw new IOException("XLSX 列数超出范围");type=p.getAttributeValue(null,"t");value="";formula=false;}if(tag.equals("f"))formula=true;if(tag.equals("v")||tag.equals("t"))value+=p.nextText();}
   if(event==XmlPullParser.END_TAG&&p.getName().equals("c")&&row!=null){if(formula)value="[公式单元格不导入]";else if("s".equals(type)){int index=Integer.parseInt(value);if(index<0||index>=strings.size())throw new IOException("XLSX 文本索引错误");value=strings.get(index);}while(row.size()<=col)row.add("");row.set(col,value);}
   if(event==XmlPullParser.END_TAG&&p.getName().equals("row")&&row!=null){sink.row(row);row=null;}
  }}
 }
 static final class Parser {
  final SQLiteDatabase stage,ledger;Map<String,Integer> header;String platform="",document="";int number=0;long first=Long.MAX_VALUE,last=0;long start=android.os.SystemClock.elapsedRealtime();
  Parser(SQLiteDatabase stage,SQLiteDatabase ledger){this.stage=stage;this.ledger=ledger;}
  String clean(String s){return s.replace("\uFEFF","").replace("\t","").trim();}
  String field(List<String> row,String...names){for(String name:names){Integer i=header.get(name);if(i!=null&&i<row.size()&&!clean(row.get(i)).isEmpty())return clean(row.get(i));}return "";}
  void row(List<String> row)throws Exception{
   if(++number>1000100||android.os.SystemClock.elapsedRealtime()-start>180000)throw new IOException("账单超过100万行或解析时间上限");
   if(header==null){for(String cell:row){if(cell.contains("微信"))platform="com.tencent.mm";if(cell.contains("支付宝"))platform="com.eg.android.AlipayGphone";}Map<String,Integer> names=new HashMap<>();for(int i=0;i<row.size();i++)names.put(clean(row.get(i)),i);if((names.containsKey("交易时间")||names.containsKey("付款时间")||names.containsKey("交易创建时间"))&&(names.containsKey("金额(元)")||names.containsKey("金额（元）")||names.containsKey("金额"))){header=names;if(platform.isEmpty())platform=names.containsKey("微信支付单号")||names.containsKey("交易单号")?"com.tencent.mm":"com.eg.android.AlipayGphone";}return;}
   if(row.stream().allMatch(s->s.trim().isEmpty()))return;
   JSONObject o=new JSONObject();String status="invalid",reason="",merchant=field(row,"交易对方","商户","商户名称"),state=field(row,"当前状态","交易状态"),direction=field(row,"收/支","收支"),kind=direction.equals("收入")?"income":"expense",type=field(row,"交易类型","类型","交易分类"),tx=field(row,"交易单号","交易订单号","交易号","微信支付单号"),product=field(row,"商品","商品名称","商品说明","用途");
   try{String date=field(row,"付款时间","交易时间","交易创建时间");long time=parseDate(date);first=Math.min(first,time);last=Math.max(last,time);String amount=field(row,"金额(元)","金额（元）","金额").replace("¥","").replace("￥","").replace(",","").trim();long cents=SearchAmount.parse(amount);FinanceData.money(cents);
    if(merchant.length()>100||merchant.isEmpty())throw new IllegalArgumentException("缺少交易对方或名称过长");
    if(type.contains("退款")||direction.equals("退款")){kind="refund";if(!state.contains("成功")&&!state.contains("到账"))throw new IllegalArgumentException("退款尚未成功");}
    else if(!Arrays.asList("支出","收入").contains(direction))throw new IllegalArgumentException("不计收支/转账/充值行需核对，不自动转为消费");
    if(state.contains("关闭")||state.contains("失败")||state.contains("取消")||state.contains("待"))throw new IllegalArgumentException("交易未完成："+state);
    if(state.contains("退款")&&!kind.equals("refund"))throw new IllegalArgumentException("原消费附带退款状态，需单独核对实际退款流水");
    if(!state.contains("成功")&&!state.contains("已支付")&&!state.contains("已收钱")&&!state.contains("已转账")&&!state.contains("到账"))throw new IllegalArgumentException("无法确认交易完成状态："+state);
    String txkey=tx.matches("[A-Za-z0-9]{12,64}")?"provider|"+platform+"|"+kind+"|"+tx:"";
    o.put("time",time).put("source",platform).put("kind",kind).put("cents",cents).put("merchant",merchant).put("product",product.length()>100?product.substring(0,100):product).put("category",ExpenseCategory.infer(merchant+" "+product)).put("txkey",txkey).put("providerId",txkey.isEmpty()?"":tx);
    if(!document.isEmpty()){JSONArray imported=FinanceData.query(ledger,"SELECT * FROM finance WHERE type='statement' AND label=? AND category=?",document,""+number);for(int i=0;i<imported.length();i++){JSONObject source=imported.getJSONObject(i),v=new JSONObject(source.getString("data"));if(v.optLong("time")==time&&v.optLong("cents")==cents&&v.optString("merchant").equals(merchant)&&v.optString("kind").equals(kind)){o.put("replayEntry",source.getLong("entry"));break;}}}
    JSONObject match=match(ledger,o);status=match.getString("status");reason=match.getString("reason");o.put("match",match);
   }catch(Exception e){reason=e.getMessage()==null?"无法解析此行":e.getMessage();}
   ContentValues v=new ContentValues();v.put("n",number);v.put("payload",o.toString());v.put("status",status);v.put("reason",reason);stage.insertOrThrow("rows",null,v);
  }
 }
 static long parseDate(String text)throws Exception{for(String pattern:new String[]{"yyyy-MM-dd HH:mm:ss","yyyy/MM/dd HH:mm:ss","yyyy-MM-dd HH:mm","yyyy/MM/dd HH:mm"}){SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.CHINA);f.setLenient(false);java.text.ParsePosition pos=new java.text.ParsePosition(0);Date date=f.parse(text,pos);if(date!=null&&pos.getIndex()==text.length()&&date.getTime()>=0&&date.getTime()<=4102444800000L)return date.getTime();}throw new IllegalArgumentException("交易时间格式不支持");}
 static JSONObject match(SQLiteDatabase db,JSONObject o)throws Exception{
  String tx=o.getString("txkey");JSONArray candidates;
  if(o.optLong("replayEntry")>0){JSONObject e=FinanceData.entry(db,o.getLong("replayEntry"));return new JSONObject().put("status","matched").put("reason","此文件行已处理，保留原账当前字段").put("candidates",new JSONArray().put(new JSONObject().put("_id",e.getLong("_id"))));}
  if(!tx.isEmpty()){candidates=FinanceData.query(db,"SELECT e._id,e.cents,e.kind,e.status,v.rev FROM entries e JOIN ui_version v ON v.id=e._id WHERE e.txkey=? OR e._id IN (SELECT entry FROM finance WHERE type='source' AND category=? AND label=?)",tx,o.getString("source")+"|"+o.getString("kind"),o.getString("providerId"));JSONArray resolved=new JSONArray();Set<Long> seen=new HashSet<>();for(int i=0;i<candidates.length();i++){long canonical=FinanceData.canonical(db,candidates.getJSONObject(i).getLong("_id"));if(seen.add(canonical))resolved.put(FinanceData.entry(db,canonical));}candidates=resolved;if(candidates.length()>1)return new JSONObject().put("status","conflict").put("reason","同一交易号关联多笔，须逐笔核对").put("candidates",candidates);if(candidates.length()>0){JSONObject e=candidates.getJSONObject(0);boolean same=e.getLong("cents")==o.getLong("cents")&&e.getString("kind").equals(o.getString("kind"))&&e.getString("status").equals("confirmed");return new JSONObject().put("status",same?"matched":"conflict").put("reason",same?"同平台交易号一致，仅关联来源，不覆盖手工字段":"交易号一致但金额、方向或状态冲突").put("candidates",candidates);}}
  candidates=FinanceData.query(db,"SELECT e._id,e.time,e.cents,e.kind,e.merchant,e.status,e.txkey,v.rev FROM entries e JOIN ui_version v ON v.id=e._id WHERE e.kind=? AND e.cents=? AND e.time BETWEEN ? AND ? AND e.status IN ('confirmed','review') ORDER BY ABS(e.time-?) LIMIT 10",o.getString("kind"),""+o.getLong("cents"),""+(o.getLong("time")-86400000),""+(o.getLong("time")+86400000),""+o.getLong("time"));
  JSONArray compatible=new JSONArray();for(int i=0;i<candidates.length();i++){JSONObject row=candidates.getJSONObject(i);if(!FinanceRules.strongConflict(tx,row.optString("txkey")))compatible.put(row);}candidates=compatible;
  return new JSONObject().put("status",candidates.length()>0?"duplicate":"missing").put("reason",candidates.length()>0?"同方向同金额时间相近；仅为候选，必须逐笔确认":"未找到匹配流水；新增前会再次核对").put("candidates",candidates);
 }
 static Object request(Context c,LedgerDb h,String op,JSONObject p)throws Exception{
  if(op.equals("importCancel")){SQLiteDatabase.deleteDatabase(stageFile(c));return true;}
  try(SQLiteDatabase staged=stage(c)){
   if(op.equals("importSelect")){ContentValues v=new ContentValues();v.put("selected",p.optBoolean("selected")?1:0);if(p.has("row"))staged.update("rows",v,"n=? AND status IN ('missing','matched')",new String[]{""+p.getLong("row")});else staged.update("rows",v,"status IN ('missing','matched')",null);return true;}
   if(op.equals("importPreview")){int page=Math.max(0,p.optInt("page"));String status=p.optString("status");if(!Arrays.asList("","matched","missing","duplicate","conflict","invalid").contains(status))throw new IllegalArgumentException("分组不合法");JSONObject summary=new JSONObject();try(Cursor metadata=staged.rawQuery("SELECT value FROM metadata WHERE key='summary'",null)){if(metadata.moveToFirst())summary=new JSONObject(metadata.getString(0));}return new JSONObject().put("summary",summary).put("allRows",FinanceData.number(staged,"SELECT count(*) FROM rows")).put("groups",FinanceData.query(staged,"SELECT status,count(*) AS count FROM rows GROUP BY status")).put("rows",FinanceData.query(staged,"SELECT * FROM rows WHERE (?='' OR status=?) ORDER BY n LIMIT 40 OFFSET "+((long)page*40),status,status)).put("total",FinanceData.number(staged,"SELECT count(*) FROM rows WHERE (?='' OR status=?)",status,status)).put("selected",FinanceData.number(staged,"SELECT count(*) FROM rows WHERE selected=1"));}
   if(op.equals("importCommit")){SQLiteDatabase db=h.getWritableDatabase();long count=0;String batch;try(Cursor b=staged.rawQuery("SELECT value FROM metadata WHERE key='batch'",null)){if(!b.moveToFirst())throw new IllegalArgumentException("请先选择账单文件");batch=b.getString(0);}JSONArray decisions=p.optJSONArray("decisions");Map<Long,JSONObject> resolve=new HashMap<>();if(decisions!=null)for(int i=0;i<decisions.length();i++)resolve.put(decisions.getJSONObject(i).getLong("row"),decisions.getJSONObject(i));
    try(Cursor rows=staged.rawQuery("SELECT n,payload,status FROM rows WHERE selected=1 OR status IN ('duplicate','conflict') ORDER BY n",null)){while(rows.moveToNext()){
     long n=rows.getLong(0);JSONObject o=new JSONObject(rows.getString(1)),decision=resolve.get(n);if(Arrays.asList("duplicate","conflict").contains(rows.getString(2))&&decision==null)continue;
     if(o.optLong("replayEntry")>0)continue;
     if(!o.getString("providerId").isEmpty()&&FinanceData.number(db,"SELECT count(*) FROM finance WHERE type='source' AND category=? AND label=?",o.getString("source")+"|"+o.getString("kind"),o.getString("providerId"))>0)continue;
     String document="";try(Cursor doc=staged.rawQuery("SELECT value FROM metadata WHERE key='document'",null)){if(doc.moveToFirst())document=doc.getString(0);}
     if(!document.isEmpty()){boolean replay=false;JSONArray prior=FinanceData.query(db,"SELECT data FROM finance WHERE type='statement' AND label=? AND category=?",document,""+n);for(int j=0;j<prior.length();j++){JSONObject v=new JSONObject(prior.getJSONObject(j).getString("data"));if(v.optLong("row")==n&&v.optLong("time")==o.getLong("time")&&v.optLong("cents")==o.getLong("cents")&&v.optString("merchant").equals(o.getString("merchant"))&&v.optString("kind").equals(o.getString("kind"))){replay=true;break;}}if(replay)continue;}
     JSONObject current=match(db,o);String status=current.getString("status");long id=0;if(status.equals("matched"))id=current.getJSONArray("candidates").getJSONObject(0).getLong("_id");
     else if(decision!=null&&decision.optLong("entry")>0){JSONObject target=FinanceData.entry(db,decision.getLong("entry"));if(!target.getString("status").equals("confirmed")||FinanceRules.strongConflict(target.optString("txkey"),o.getString("txkey"))||target.getLong("rev")!=decision.getLong("rev")||target.getLong("cents")!=o.getLong("cents")||!target.getString("kind").equals(o.getString("kind")))throw new IllegalArgumentException("第"+n+"行关联记录已变化或金额冲突");id=target.getLong("_id");}
     else{if(status.equals("conflict")||status.equals("duplicate")&&(decision==null||!decision.optBoolean("separate")))throw new IllegalArgumentException("第"+n+"行出现疑重，请重新核对；本批未提交");h.save(0,o.getLong("time"),o.getString("source"),o.getString("kind"),o.getLong("cents"),o.getString("merchant"),o.getString("category"),"confirmed");id=FinanceData.number(db,"SELECT last_insert_rowid()");ContentValues v=new ContentValues();v.put("txkey",o.getString("txkey"));db.update("entries",v,"_id=?",new String[]{""+id});}
     if(o.getString("providerId").isEmpty()||FinanceData.number(db,"SELECT count(*) FROM finance WHERE type='source' AND category=? AND label=?",o.getString("source")+"|"+o.getString("kind"),o.getString("providerId"))==0){FinanceData.put(db,FinanceData.object("source",id,0,0,o.getString("source")+"|"+o.getString("kind"),o.getString("providerId"),new JSONObject().put("batch",batch).put("row",n).put("time",o.getLong("time")).put("kind",o.getString("kind")).put("cents",o.getLong("cents"))));count++;if(!document.isEmpty())FinanceData.put(db,FinanceData.object("statement",id,0,0,""+n,document,new JSONObject().put("row",n).put("time",o.getLong("time")).put("kind",o.getString("kind")).put("cents",o.getLong("cents")).put("merchant",o.getString("merchant"))));}
    }}if(count==0)return new JSONObject().put("count",0);FinanceData.put(db,FinanceData.object("batch",0,0,0,"",batch,new JSONObject().put("count",count).put("time",System.currentTimeMillis())));return new JSONObject().put("count",count);
   }
  }throw new IllegalArgumentException("不支持的导入操作");
 }
}
