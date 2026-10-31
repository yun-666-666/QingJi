package me.minis.ledger;
import android.content.*;
/** Last mutation outcome only; no transaction content stored here, never automatic replay. */
final class LedgerTaskState {
 private static final String PROCESS=java.util.UUID.randomUUID().toString();
 static void start(Context c,String label){LedgerDb.prefs(c).edit().putString("task_process",PROCESS).putString("task_state","running").putString("task_label",label).putString("task_message","").commit();}
 static void finish(Context c,boolean ok,String message){LedgerDb.prefs(c).edit().putString("task_state",ok?"success":"failed").putString("task_message",message).commit();}
 static String status(Context c){SharedPreferences p=LedgerDb.prefs(c);String state=p.getString("task_state",""),label=p.getString("task_label","操作");if(state.equals("running"))return p.getString("task_process","").equals(PROCESS)?label+"处理中，请勿重复提交":label+"曾被系统中断，请核对结果后再操作；未自动重试，数据库未完成的事务会回滚";if(state.equals("success"))return ""; // Success is acknowledged by the live UI callback, never replayed on launch.
 if(state.equals("failed"))return p.getString("task_message",label+"失败，请重试");return "";}
}
