package me.minis.ledger;
import android.content.*;import java.util.concurrent.*;import org.json.*;
/** App-scoped serialized worker: an Activity teardown does not cancel committed writes. */
public final class LedgerTasks {
 public static final ExecutorService worker=Executors.newSingleThreadExecutor();
 public interface Callback{void done(JSONObject value);}
 public static void run(Context context,JSONObject request,Callback callback){final Context app=context.getApplicationContext();worker.execute(()->{boolean mutation=request.optBoolean("mutation");if(mutation)LedgerTaskState.start(app,"账目操作");JSONObject result=new JSONObject();try{result.put("id",request.getString("id"));}catch(Exception ignored){}try(LedgerDb db=new LedgerDb(app)){
   String op=request.getString("op");JSONObject p=request.optJSONObject("args");if(p==null)p=new JSONObject();Object value;
   if(op.startsWith("finance.")){String action=op.substring(8);final JSONObject args=p;if(FinanceService.readOnly(action))value=FinanceService.read(app,db,action,args);else value=UndoLog.record(db,"财务："+FinanceService.label(action),()->FinanceService.write(app,db,action,args));}
   else if(op.equals("save")){String kind=p.getString("kind"),category=p.getString("category"),merchant=p.getString("merchant"),source=p.optString("source","manual");long cents=p.getLong("cents"),time=p.getLong("time");if(!java.util.Arrays.asList("expense","income","refund").contains(kind)||!java.util.Arrays.asList(LedgerDb.CATEGORIES).contains(category)||merchant.length()>100||cents<=0||cents>99999999999L||time<0||time>4102444800000L||source.isEmpty()||source.length()>255)throw new JSONException("输入不合法，请核对金额、日期和分类");boolean remember=p.optBoolean("rememberCategory",false);if(remember&&ExpenseCategory.key(merchant,kind).isEmpty())throw new JSONException("请填写具体商户后再记住分类");final JSONObject fields=p;UndoLog.record(db,"保存账目",()->{long entry=fields.optLong("_id",0);db.save(entry,time,source,kind,cents,merchant.trim(),category,"confirmed");if(entry==0)entry=android.database.DatabaseUtils.longForQuery(db.getWritableDatabase(),"SELECT last_insert_rowid()",null);LedgerPlans.exclude(db,entry,fields.optBoolean("excluded",false));FinanceData.validateEntry(db.getWritableDatabase(),entry);return true;});if(remember)CategoryMemory.set(app,merchant,kind,category);value=true;}
   else if(op.equals("clearCategories")){CategoryMemory.clear(app);value=true;}
   else if(op.equals("ignore")){final long id=p.getLong("id");value=UndoLog.record(db,"忽略账目",()->{db.ignore(id);return true;});}
   else if(op.equals("restore")){final long id=p.getLong("id");value=UndoLog.record(db,"恢复待确认",()->{db.restore(id);return true;});}
   else if(op.equals("history"))value=UndoLog.list(db.getReadableDatabase(),p.optLong("entry",0));
   else if(op.equals("undo"))value=UndoLog.undo(db,p.getLong("operation"));
   else if(op.equals("plans"))value=LedgerPlans.state(app,db);
   else if(op.equals("budget")){LedgerPlans.budget(db,p);value=true;}
   else if(op.equals("budgetStyle")){BudgetUsage.saveStyle(app,p.getString("style"));value=true;}
   else if(op.equals("reminder")){value=LedgerPlans.reminder(db,p);}
   else if(op.equals("ackReminder")){LedgerPlans.acknowledge(db,p.getString("id"));value=true;}
   else if(op.equals("backupSettings")){BackupVault.settings(app,p);value=true;}
   else if(op.equals("backupNow")){BackupVault.run(app,db,true);value=true;}
   else if(op.equals("detail"))value=db.detail(p.getLong("id"));
   else if(op.equals("ignored"))value=db.ignoredPage(p.optInt("page",0),5);
   else throw new JSONException("不支持的任务");
   if(java.util.Arrays.asList("budget","reminder","ackReminder").contains(op))LedgerPlans.check(app,db);result.put("ok",true).put("value",value);
  }catch(Exception e){try{result.put("ok",false).put("error",e.getMessage()==null?"操作失败，请重试":e.getMessage());}catch(Exception ignored){}}
  if(mutation)LedgerTaskState.finish(app,result.optBoolean("ok"),result.optBoolean("ok")?"账目操作已完成":result.optString("error"));callback.done(result);
 });}
}
