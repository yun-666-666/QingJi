package me.minis.ledger;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.util.*;

/** Isolated databases on the connected Android device; never opens the user's ledger.db. */
final class FinanceDeviceChecks {
 final Context context;final List<String> results=new ArrayList<>();LedgerDb ledger;SQLiteDatabase db;
 FinanceDeviceChecks(Context c){context=c;}
 void check(boolean condition,String name){if(!condition)throw new AssertionError(name);results.add(name);}
 interface Action{void run()throws Exception;}
 void rejects(Action action,String name)throws Exception{try{action.run();}catch(Exception expected){results.add(name);return;}throw new AssertionError(name+" accepted invalid mutation");}
 JSONObject json(String text)throws JSONException{return new JSONObject(text);}
 Object mutate(String op,JSONObject p)throws Exception{return UndoLog.record(ledger,op,()->FinanceService.write(context,ledger,op,p));}
 long add(String kind,long amount,String merchant)throws Exception{ledger.save(0,System.currentTimeMillis()-10000,"manual",kind,amount,merchant,"其他","confirmed");return FinanceData.number(db,"SELECT max(_id) FROM entries");}
 long lastOperation(){return FinanceData.number(db,"SELECT max(id) FROM edit_log");}
 String run()throws Exception{
  String name="finance-check-"+UUID.randomUUID()+".db",restored="finance-restore-"+UUID.randomUUID()+".db";
  try{ledger=new LedgerDb(context,name);db=ledger.getWritableDatabase();long expense=add("expense",10000,"组合测试消费"),refund=add("refund",2000,"组合测试退款");
   mutate("split",new JSONObject().put("entry",expense).put("rows",new JSONArray().put(json("{category:'餐饮',cents:6000}")).put(json("{category:'生活',cents:4000}"))));
   JSONObject first=FinanceData.query(db,"SELECT * FROM finance WHERE type='split' AND category='餐饮'").getJSONObject(0);
   mutate("refund",new JSONObject().put("entry",refund).put("peer",expense).put("split",first.getString("id")).put("cents",2000));
   check(FinanceData.detail(db,expense).getLong("paid")==8000,"100 split 60/40 with 20 refund leaves 80");
   check(FinanceQueries.budget(db,0,Long.MAX_VALUE,"餐饮")==4000&&FinanceQueries.budget(db,0,Long.MAX_VALUE,"生活")==4000,"Category budget counts split items and assigned refund once");
   check(LedgerStats.totals(db,0,Long.MAX_VALUE)[0]==10000&&LedgerStats.totals(db,0,Long.MAX_VALUE)[2]==2000,"Cash flow retains original expense and refund");
   rejects(()->mutate("refund",new JSONObject().put("entry",refund).put("peer",expense).put("split",first.getString("id")).put("cents",1)),"Refund capacity cannot be exceeded");
   rejects(()->ledger.save(expense,System.currentTimeMillis(),"manual","expense",9000,"组合测试消费","餐饮","confirmed"),"Linked parent amount change is atomic and refused");
   for(String label:new String[]{"旅行","家庭"}){JSONObject tag=(JSONObject)mutate("tag",new JSONObject().put("name",label));mutate("taglink",new JSONObject().put("tag",tag.getString("id")).put("entries",new JSONArray().put(expense)));}
   JSONObject project=FinanceQueries.project(db,new JSONObject());check(project.getLong("expense")==10000&&project.getLong("refund")==2000,"Overlapping project tags deduplicate globally");
   long paid=add("expense",20000,"代垫"),receipt=add("income",8000,"结算回款");JSONObject caseRow=(JSONObject)mutate("case",new JSONObject().put("entry",paid).put("kind","reimbursement").put("name","同事").put("cents",16000));
   mutate("settle",new JSONObject().put("case",caseRow.getString("id")).put("entry",receipt).put("cents",8000));
   JSONObject d=FinanceData.detail(db,paid);check(d.getLong("personal")==12000&&d.getLong("receivable")==8000,"Reimbursement reports actual burden and outstanding separately");
   long loan=add("expense",50000,"借出本金");JSONObject loanCase=(JSONObject)mutate("case",new JSONObject().put("entry",loan).put("kind","loanOut").put("name","朋友").put("cents",50000));
   rejects(()->mutate("settle",new JSONObject().put("case",loanCase.getString("id")).put("entry",receipt).put("cents",1)),"Same income cannot settle reimbursement and loan twice");
   long repayment=add("income",20000,"归还本金");mutate("settle",new JSONObject().put("case",loanCase.getString("id")).put("entry",repayment).put("cents",20000));
   check(FinanceData.sum(db,"type='settlement' AND peer=?",""+loan)==20000,"Loan partial repayment leaves 300");
   long[] totals=LedgerStats.totals(db,0,Long.MAX_VALUE);check(totals[0]==30000&&totals[1]==0,"Loan principal and settlements excluded from ordinary totals");
   JSONObject goal=(JSONObject)mutate("goal",new JSONObject().put("name","购买计划").put("cents",400000));mutate("allocation",new JSONObject().put("goal",goal.getString("id")).put("cents",100000));long goalOp=lastOperation();
   check(FinanceQueries.state(db).getJSONArray("goals").getJSONObject(0).getLong("allocated")==100000&&LedgerStats.totals(db,0,Long.MAX_VALUE)[0]==totals[0],"Goal 25 percent creates no cash flow");
   UndoLog.undo(ledger,goalOp);check(FinanceQueries.state(db).getJSONArray("goals").getJSONObject(0).getLong("allocated")==0,"Goal allocation undo restores zero");
   long x=add("expense",1234,"重复账目"),y=add("expense",1234,"重复账目");mutate("merge",new JSONObject().put("entry",x).put("peer",y).put("rev",FinanceData.entry(db,x).getLong("rev")).put("peerRev",FinanceData.entry(db,y).getLong("rev")));long mergeOp=lastOperation();
   check(FinanceData.entry(db,y).getString("status").equals("ignored")&&FinanceData.number(db,"SELECT count(*) FROM finance WHERE type='merge'")==1,"Merge retains redirect and source evidence");UndoLog.undo(ledger,mergeOp);check(FinanceData.entry(db,y).getString("status").equals("confirmed"),"Merge undo restores both payments");
   JSONObject conditions=json("{merchant:'热水',keyword:'淋浴',mode:'any',match:'contains',kind:''}"),rule=new JSONObject().put("name","热水规则").put("category","生活").put("priority",1).put("conditions",conditions);mutate("rule",rule);PaymentParser.Result parsed=new PaymentParser.Result();parsed.cents=300;parsed.kind="expense";parsed.merchant="平台";FinanceRules.apply(db,parsed,"商品：热水淋浴");check(parsed.category.equals("生活"),"Personal rule classifies actual product text");
   long manual=add("expense",300,"热水测试");JSONObject preview=FinanceRules.preview(db,rule,false);check(preview.getLong("protected")>=1,"Rule preview marks manual protection");rule.put("revision",preview.getLong("revision"));mutate("ruleApply",rule);check(FinanceData.entry(db,manual).getString("category").equals("其他"),"Applying rule skips manually entered records");
   JSONObject calendar=FinanceQueries.calendar(db,new JSONObject().put("year",Calendar.getInstance().get(Calendar.YEAR)).put("month",Calendar.getInstance().get(Calendar.MONTH)));check(calendar.getJSONArray("rows").length()==1,"Calendar aggregates current month in one query");
   ByteArrayOutputStream buffer=new ByteArrayOutputStream();ledger.exportBackup(buffer);try(LedgerDb copy=new LedgerDb(context,restored);BackupStream backup=BackupStream.read(new ByteArrayInputStream(buffer.toByteArray()),context.getCacheDir())){copy.importBackup(backup);SQLiteDatabase restoredDb=copy.getReadableDatabase();check(FinanceData.number(restoredDb,"SELECT count(*) FROM finance")==FinanceData.number(db,"SELECT count(*) FROM finance"),"Version 3 backup restores every financial object");check(Arrays.equals(LedgerStats.totals(restoredDb,0,Long.MAX_VALUE),LedgerStats.totals(db,0,Long.MAX_VALUE)),"Backup restore preserves all unified totals");FinanceData.validateAll(restoredDb);check(true,"Restored references validate");}
   importChecks(); importCommitChecks(); migrationCheck();
   return "PASS: "+results.size()+" isolated Android finance checks\n"+String.join("\n",results)+"\n";
  }finally{if(ledger!=null)ledger.close();context.deleteDatabase(name);context.deleteDatabase(restored);}
 }
 String runExtended()throws Exception{
  String name="finance-combinations-"+UUID.randomUUID()+".db";
  try{ledger=new LedgerDb(context,name);db=ledger.getWritableDatabase();
   long x=add("expense",10000,"关联合并"),y=add("expense",10000,"关联合并"),r=add("refund",2000,"退款");
   long stale=FinanceData.entry(db,y).getLong("rev");mutate("split",new JSONObject().put("entry",y).put("rows",new JSONArray().put(json("{category:'餐饮',cents:6000}")).put(json("{category:'生活',cents:4000}"))));
   rejects(()->mutate("split",new JSONObject().put("entry",y).put("rev",stale).put("rows",new JSONArray())),"Stale split editor cannot overwrite newer allocation");
   JSONObject split=FinanceData.query(db,"SELECT * FROM finance WHERE type='split' AND category='餐饮'").getJSONObject(0);
   mutate("refund",new JSONObject().put("entry",r).put("peer",y).put("split",split.getString("id")).put("cents",2000));
   mutate("merge",new JSONObject().put("entry",x).put("peer",y).put("rev",FinanceData.entry(db,x).getLong("rev")).put("peerRev",FinanceData.entry(db,y).getLong("rev")));long operation=lastOperation();
   check(ledger.detail(y).getLong("_id")==x&&FinanceData.detail(db,y).getLong("paid")==8000,"Merged old ID opens canonical bill with split and refund");
   UndoLog.undo(ledger,operation);check(FinanceData.detail(db,y).getLong("paid")==8000&&FinanceData.entry(db,y).getString("status").equals("confirmed")&&FinanceData.detail(db,x).getLong("paid")==10000,"Undo merged bill restores referenced split and refund atomically");
   JSONObject tag=(JSONObject)mutate("tag",new JSONObject().put("name","组合项目"));mutate("taglink",new JSONObject().put("tag",tag.getString("id")).put("entries",new JSONArray().put(y).put(r)));
   JSONObject project=FinanceQueries.project(db,new JSONObject().put("tag",tag.getString("id")).put("category","餐饮"));check(project.getLong("expense")==6000&&project.getLong("refund")==2000&&project.getLong("personal")==4000,"Project category totals use split share and count tagged refund once");
   JSONObject refundTag=(JSONObject)mutate("tag",new JSONObject().put("name","退款项目"));mutate("taglink",new JSONObject().put("tag",refundTag.getString("id")).put("entries",new JSONArray().put(r)));check(FinanceQueries.project(db,new JSONObject().put("tag",refundTag.getString("id"))).getLong("refund")==2000,"Directly tagged refund remains visible without tagged original");
   db.beginTransaction();try{for(int i=0;i<85;i++)add("expense",100,"全量标签夹具"+i);db.setTransactionSuccessful();}finally{db.endTransaction();}
   LedgerQuery query=new LedgerQuery(ledger);query.select(json("{scope:'book',mode:'all',filter:{status:'confirmed',all:true,q:'全量标签夹具'}}"));int tagged=(Integer)UndoLog.record(ledger,"批量标签",()->query.tagSelected(tag.getString("id")));check(tagged==85&&FinanceData.number(db,"SELECT count(*) FROM finance WHERE type='taglink' AND category=?",tag.getString("id"))==87,"Bulk tags cover all 85 matching bills beyond first page");UndoLog.undo(ledger,lastOperation());check(FinanceData.number(db,"SELECT count(*) FROM finance WHERE type='taglink' AND category=?",tag.getString("id"))==2,"Bulk tag undo preserves pre-existing project members");
   db.execSQL("UPDATE entries SET txkey='provider|com.tencent.mm|expense|420000000000001' WHERE _id=?",new Object[]{x});db.execSQL("UPDATE entries SET txkey='provider|com.tencent.mm|expense|420000000000002' WHERE _id=?",new Object[]{y});
   rejects(()->mutate("merge",new JSONObject().put("entry",x).put("peer",y).put("peerRev",FinanceData.entry(db,y).getLong("rev"))),"Different strong transaction IDs cannot merge");
   JSONObject imported=new JSONObject().put("txkey","provider|com.tencent.mm|expense|420000000000003").put("source","com.tencent.mm").put("providerId","420000000000003").put("kind","expense").put("cents",10000).put("time",FinanceData.entry(db,x).getLong("time"));check(BillImport.match(db,imported).getString("status").equals("missing"),"Import preserves another same-amount payment with distinct strong ID");
   long principal=add("income",50000,"借入"),repayment=add("expense",20000,"还款");JSONObject loan=(JSONObject)mutate("case",new JSONObject().put("entry",principal).put("kind","loanIn").put("name","朋友").put("cents",50000));long beforeExpense=LedgerStats.totals(db,0,Long.MAX_VALUE)[0];mutate("settle",new JSONObject().put("case",loan.getString("id")).put("entry",repayment).put("cents",20000));long repaymentOp=lastOperation();
   check(LedgerStats.totals(db,0,Long.MAX_VALUE)[0]==beforeExpense-20000&&FinanceQueries.cases(db,json("{kind:'loans'}")).getJSONArray("rows").getJSONObject(0).getLong("settled")==20000,"Borrow 500 repay 200 leaves 300 and excludes principal expense");UndoLog.undo(ledger,repaymentOp);check(FinanceData.detail(db,principal).getLong("settled")==0,"Undo loan repayment restores outstanding balance");
   long now=System.currentTimeMillis();db.execSQL("UPDATE entries SET excluded=1 WHERE _id=?",new Object[]{x});db.execSQL("UPDATE entries SET time=? WHERE _id=?",new Object[]{WidgetPeriods.throughToday(now)+1000,y});JSONObject day=FinanceQueries.day(db,new JSONObject().put("time",now));Calendar date=Calendar.getInstance();JSONObject month=FinanceQueries.calendar(db,new JSONObject().put("year",date.get(Calendar.YEAR)).put("month",date.get(Calendar.MONTH)));JSONObject current=month.getJSONArray("rows").getJSONObject(0);check(day.getLong("expense")==current.getLong("expense")&&day.getLong("refund")==current.getLong("refund")&&day.getLong("total")==current.getLong("count"),"Calendar and day detail agree while excluding future and excluded bills");
   db.beginTransaction();try{for(int i=0;i<205;i++){long entry=add("expense",100,"分页案件"+i);FinanceData.put(db,FinanceData.object("case",entry,0,100,"reimbursement","分页",new JSONObject()));}db.setTransactionSuccessful();}finally{db.endTransaction();}
   check(FinanceQueries.cases(db,json("{kind:'reimbursement',page:5}")).getJSONArray("rows").length()==5,"Case pagination reaches records beyond 200");
   formatChecks();
   return "PASS: "+results.size()+" additional Android combination checks\n"+String.join("\n",results)+"\n";
  }finally{if(ledger!=null)ledger.close();context.deleteDatabase(name);}
 }
 void formatChecks()throws Exception{
  String[][] rows={{"支付宝交易记录明细查询"},{"交易创建时间","付款时间","交易分类","交易对方","商品名称","收/支","金额(元)","交易状态","交易订单号"},{"2026-09-01 12:00:00","","日用","文件格式夹具","商品","支出","9.19","交易成功","202609010000000001"}};
  StringBuilder csv=new StringBuilder("\uFEFF");for(String[] row:rows)csv.append(String.join(",",row)).append("\r\n");
  File file=File.createTempFile("statement-format-",".input",context.getCacheDir());
  try{
   try(OutputStream out=new FileOutputStream(file)){out.write(csv.toString().getBytes("GB18030"));}
   List<List<String>> parsed=new ArrayList<>();BillImport.readFile(file,parsed::add,0);check(parsed.size()==3&&parsed.get(2).get(3).equals("文件格式夹具"),"GB18030 CSV preserves Chinese text");
   try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new FileOutputStream(file))){zip.putNextEntry(new java.util.zip.ZipEntry("bill.csv"));zip.write(csv.toString().getBytes("UTF-8"));zip.closeEntry();}
   parsed.clear();BillImport.readFile(file,parsed::add,0);check(parsed.size()==3&&parsed.get(2).get(8).equals("202609010000000001"),"ZIP single CSV preserves long transaction identifier");
   try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new FileOutputStream(file))){zip.putNextEntry(new java.util.zip.ZipEntry("xl/workbook.xml"));zip.write("<workbook/>".getBytes("UTF-8"));zip.closeEntry();zip.putNextEntry(new java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"));StringBuilder xml=new StringBuilder("<worksheet><sheetData>");for(int r=0;r<rows.length;r++){xml.append("<row>");for(int col=0;col<rows[r].length;col++)xml.append("<c r='"+(char)('A'+col)+(r+1)+"' t='inlineStr'><is><t>").append(rows[r][col]).append("</t></is></c>");xml.append("</row>");}xml.append("</sheetData></worksheet>");zip.write(xml.toString().getBytes("UTF-8"));zip.closeEntry();}
   BillImport.prepare(context,ledger,android.net.Uri.fromFile(file));JSONObject preview=(JSONObject)BillImport.request(context,ledger,"importPreview",new JSONObject());JSONObject row=preview.getJSONArray("rows").getJSONObject(0);check(row.getString("status").equals("missing")&&new JSONObject(row.getString("payload")).getLong("cents")==919,"XLSX parses statement and falls back from blank payment time");
   check(preview.getJSONObject("summary").getString("platform").equals("com.eg.android.AlipayGphone")&&preview.getLong("allRows")==1,"Statement preview includes platform, range and total rows");
  }finally{BillImport.request(context,ledger,"importCancel",new JSONObject());file.delete();}
 }
 void importChecks()throws Exception{
  List<List<String>> rows=new ArrayList<>();BillImport.csv(new PushbackReader(new StringReader("a,b\r\n\"店,铺\",\"两行\n内容\"\r\n"),1),rows::add);check(rows.size()==2&&rows.get(1).get(0).equals("店,铺")&&rows.get(1).get(1).equals("两行\n内容"),"CSV quoted comma and newline");
  File stage=File.createTempFile("finance-import-check-",".db",context.getCacheDir());try(SQLiteDatabase staged=SQLiteDatabase.openOrCreateDatabase(stage,null)){
   staged.execSQL("CREATE TABLE rows(n INTEGER PRIMARY KEY,payload TEXT,status TEXT,reason TEXT)");BillImport.Parser parser=new BillImport.Parser(staged,db);
   parser.row(Arrays.asList("微信支付账单明细"));parser.row(Arrays.asList("交易时间","交易类型","交易对方","商品","收/支","金额(元)","当前状态","交易单号"));
   parser.row(Arrays.asList("2026-10-01 12:00:00","商户消费","测试商户","商品","支出","10.00","支付成功","420000000000001"));
   parser.row(Arrays.asList("2026-10-01 12:00:01","商户消费","测试商户","商品","支出","10.00","支付成功","420000000000002"));
   parser.row(Arrays.asList("2026-10-01 12:00:02","商户消费","测试商户","商品","支出","10.00","交易关闭","420000000000003"));
   check(FinanceData.number(staged,"SELECT count(*) FROM rows WHERE status='missing'")==2&&FinanceData.number(staged,"SELECT count(*) FROM rows WHERE status='invalid'")==1,"Distinct transaction IDs preserved; closed payment rejected");
  }finally{SQLiteDatabase.deleteDatabase(stage);}
 }
 void importCommitChecks()throws Exception{
  File csv=File.createTempFile("statement-fixture-",".csv",context.getCacheDir());
  try{
   String head="微信支付账单明细\n交易时间,交易类型,交易对方,商品,收/支,金额(元),当前状态,交易单号\n";
   String content=head+"2026-09-01 12:00:00,商户消费,导入夹具,商品,支出,9.17,支付成功,420000000000007\n";
   try(Writer w=new OutputStreamWriter(new FileOutputStream(csv),"UTF-8")){w.write(content);}
   long before=FinanceData.number(db,"SELECT count(*) FROM entries");BillImport.prepare(context,ledger,android.net.Uri.fromFile(csv));
   check(FinanceData.number(db,"SELECT count(*) FROM entries")==before,"Statement preview writes no ledger rows");
   BillImport.request(context,ledger,"importSelect",new JSONObject().put("selected",true));mutate("importCommit",new JSONObject());long op=lastOperation();
   check(FinanceData.number(db,"SELECT count(*) FROM entries")==before+1,"Selected statement commit adds one payment");
   BillImport.prepare(context,ledger,android.net.Uri.fromFile(csv));BillImport.request(context,ledger,"importSelect",new JSONObject().put("selected",true));JSONObject repeated=(JSONObject)mutate("importCommit",new JSONObject());
   check(repeated.getLong("count")==0&&FinanceData.number(db,"SELECT count(*) FROM entries")==before+1,"Same statement reimport is idempotent");UndoLog.undo(ledger,op);check(FinanceData.number(db,"SELECT count(*) FROM entries")==before,"Statement undo removes its own additions and provenance");
   try(Writer w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csv),"UTF-8"))){w.write(head);for(int i=0;i<100000;i++)w.write("2026-09-01 12:00:00,商户消费,大文件夹具,商品,支出,9.18,支付成功,"+String.format(Locale.ROOT,"420%012d",i)+"\n");}
   long started=android.os.SystemClock.elapsedRealtime();BillImport.prepare(context,ledger,android.net.Uri.fromFile(csv));JSONObject preview=(JSONObject)BillImport.request(context,ledger,"importPreview",new JSONObject());check(preview.getLong("total")==100000&&preview.getJSONArray("rows").length()==40,"100000-row statement stages on disk and returns only 40 rows ("+(android.os.SystemClock.elapsedRealtime()-started)+" ms)");
  }finally{BillImport.request(context,ledger,"importCancel",new JSONObject());csv.delete();}
 }
 void migrationCheck()throws Exception{
  String name="migration-fixture-"+UUID.randomUUID()+".db";try{
   long row;String original;try(LedgerDb old=new LedgerDb(context,name)){SQLiteDatabase sql=old.getWritableDatabase();old.save(0,1000,"manual","expense",321,"升级前账单","其他","confirmed");row=FinanceData.number(sql,"SELECT max(_id) FROM entries");original=FinanceData.entry(sql,row).toString();JSONArray triggers=FinanceData.query(sql,"SELECT name FROM sqlite_master WHERE type='trigger' AND name LIKE 'finance_%'");for(int i=0;i<triggers.length();i++)sql.execSQL("DROP TRIGGER "+triggers.getJSONObject(i).getString("name"));sql.execSQL("DROP VIEW ledger_parts");for(String t:new String[]{"finance_before","finance_after","finance"})sql.execSQL("DROP TABLE "+t);sql.setVersion(9);}
   try(LedgerDb migrated=new LedgerDb(context,name)){SQLiteDatabase sql=migrated.getWritableDatabase();check(sql.getVersion()==10&&FinanceData.entry(sql,row).toString().equals(original),"Version 9 migration preserves original row contents");FinanceData.validateAll(sql);}
  }finally{context.deleteDatabase(name);}
 }
}
