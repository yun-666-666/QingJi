package me.minis.ledger;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.Writer;
import org.json.*;

final class FinanceBackup {
 static void write(SQLiteDatabase db,Writer out)throws Exception{
  boolean first=true;try(Cursor c=db.rawQuery("SELECT f.*,e.fingerprint AS entry_ref,p.fingerprint AS peer_ref FROM finance f LEFT JOIN entries e ON e._id=f.entry LEFT JOIN entries p ON p._id=f.peer ORDER BY f.id",null)){
   while(c.moveToNext()){JSONObject o=UndoLog.row(c);o.remove("entry");o.remove("peer");if(!first)out.write(',');first=false;out.write(JSONObject.quote(o.toString()));}
  }
 }
 static void restore(SQLiteDatabase db,SQLiteDatabase stage)throws Exception{
  try(Cursor c=stage.rawQuery("SELECT payload FROM finance",null)){while(c.moveToNext()){
   JSONObject o=new JSONObject(c.getString(0));for(String key:new String[]{"entry","peer"}){String ref=o.optString(key+"_ref","");long id=0;if(!ref.isEmpty()&&!ref.equals("null")){try(Cursor e=db.rawQuery("SELECT _id FROM entries WHERE fingerprint=?",new String[]{ref})){if(!e.moveToFirst())throw new IllegalArgumentException("备份的关系引用缺失，本次恢复已取消");id=e.getLong(0);}}o.remove(key+"_ref");o.put(key,id);}
   if(FinanceData.get(db,o.getString("id"))==null)FinanceData.put(db,o);
  }}FinanceData.validateAll(db);
 }
 static void undoObjects(SQLiteDatabase db,long op)throws Exception{
  try(Cursor c=db.rawQuery("SELECT * FROM finance_after WHERE op=?",new String[]{""+op})){while(c.moveToNext()){
   JSONObject after=UndoLog.row(c),current=FinanceData.get(db,after.getString("id"));boolean deleted=after.getString("type").equals("deleted");
   if(deleted?current!=null:!UndoLog.equal(after,current))throw new IllegalStateException("关联关系后来已修改，不能覆盖；本次未撤销");
  }}
  try(Cursor c=db.rawQuery("SELECT id FROM finance_after WHERE op=?",new String[]{""+op})){while(c.moveToNext())db.delete("finance","id=?",new String[]{c.getString(0)});}
 }
 static void restoreUndoObjects(SQLiteDatabase db,long op)throws Exception{
  try(Cursor c=db.rawQuery("SELECT * FROM finance_before WHERE op=?",new String[]{""+op})){while(c.moveToNext())FinanceData.put(db,UndoLog.row(c));}
 }
}
