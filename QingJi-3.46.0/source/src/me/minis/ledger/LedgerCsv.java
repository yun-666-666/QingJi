package me.minis.ledger;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
/** Constant application memory per row; no JSON, raw text or fingerprint materialized. */
final class LedgerCsv {
 static String cell(String s){if(s==null)s="";int i=0;while(i<s.length()&&(Character.isWhitespace(s.charAt(i))||s.charAt(i)=='\uFEFF'))i++;boolean formula=i<s.length()&&"=+-@".indexOf(s.charAt(i))>=0;boolean control=!s.isEmpty()&&(s.charAt(0)=='\t'||s.charAt(0)=='\r'||s.charAt(0)=='\n');return "\""+(formula||control?"'":"")+s.replace("\"","\"\"")+"\"";}
 static void write(SQLiteDatabase db,OutputStream stream)throws IOException {
  Writer out=new BufferedWriter(new OutputStreamWriter(new BackupStream.LimitedOutput(stream),"UTF-8"));
  SimpleDateFormat df=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA);
  db.beginTransactionNonExclusive();try{
   out.write("\uFEFF时间,来源,类型,金额（元）,商户,分类,状态\r\n");
   try(Cursor c=db.rawQuery("SELECT time,source,kind,cents,merchant,category,status FROM entries WHERE status IN ('confirmed','review') ORDER BY time DESC,_id DESC",null)){
    while(c.moveToNext()){
     String kind=c.getString(2);long cents=c.getLong(3);
     String[] cols={df.format(new Date(c.getLong(0))),LedgerDb.label(c.getString(1)),kind.equals("expense")?"支出":kind.equals("income")?"收入":"退款",(cents/100)+"."+String.format(Locale.ROOT,"%02d",cents%100),c.getString(4),c.getString(5),c.getString(6).equals("confirmed")?"已确认":"待确认"};
     for(int j=0;j<cols.length;j++){if(j>0)out.write(',');out.write(cell(cols[j]));}out.write("\r\n");
    }
   }out.flush();db.setTransactionSuccessful();
  }finally{db.endTransaction();}
 }
}
