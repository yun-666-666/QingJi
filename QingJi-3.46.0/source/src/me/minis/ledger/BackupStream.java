package me.minis.ledger;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import android.util.JsonReader;
import android.util.JsonToken;
import org.json.JSONObject;
import java.io.*;
import java.util.HashSet;
import java.util.Set;

/** Disk staging: untrusted documents are validated before touching the ledger. */
public final class BackupStream implements AutoCloseable {
 private static final java.util.regex.Pattern NUMBER_FIELD=java.util.regex.Pattern.compile("_id|time|cents|received|ignored_at|observed_at|excluded"),TEXT_FIELD=java.util.regex.Pattern.compile("source|kind|merchant|category|status|raw|reason|nkey|semantic|fingerprint|txkey|funding|relatedFingerprint"),MARKER=java.util.regex.Pattern.compile("[0-9a-f]{64}");
 public static final long MAX_BYTES=512L*1024*1024;
 public static final int MAX_ROWS=1000000,MAX_KEYS=4000000;
 final SQLiteDatabase stage;private final File file;public int version;public long rows,keys;public String plans="";
 private BackupStream(File f){file=f;stage=SQLiteDatabase.openOrCreateDatabase(f,null);stage.execSQL("CREATE TABLE rows(n INTEGER PRIMARY KEY,payload TEXT NOT NULL,inserted INTEGER NOT NULL DEFAULT 0,fp TEXT,ref TEXT,reason TEXT)");stage.execSQL("CREATE TABLE markers(token TEXT PRIMARY KEY)");}
 public static final class LimitedInput extends FilterInputStream {
  private long count;public LimitedInput(InputStream in){super(in);}
  private void add(int n)throws IOException{if(n>0&&(count+=n)>MAX_BYTES)throw new IOException("备份超过512MiB安全上限");}
  public int read()throws IOException{int v=super.read();if(v>=0)add(1);return v;}
  public int read(byte[] b,int off,int len)throws IOException{int n=in.read(b,off,len);add(n);return n;}
 }
 public static final class LimitedOutput extends FilterOutputStream {
  private long count;public LimitedOutput(OutputStream out){super(out);}
  private void add(int n)throws IOException{if((count+=n)>MAX_BYTES)throw new IOException("备份超过512MiB安全上限；未生成可用备份");}
  public void write(int v)throws IOException{add(1);out.write(v);}
  public void write(byte[] b,int off,int len)throws IOException{add(len);out.write(b,off,len);}
 }
 public static BackupStream read(InputStream in,File directory)throws Exception{
  File file;try{file=File.createTempFile("qingji-import-",".db",directory);}catch(IOException e){throw new BackupFailure.Stage(e);}BackupStream b=null;
  try{b=new BackupStream(file);b.parse(BackupFailure.source(in));return b;}catch(Exception e){if(b!=null)b.close();else file.delete();if(e instanceof BackupFailure.Read)throw e;if(e instanceof android.database.SQLException)throw new BackupFailure.Stage(e);throw new BackupFailure.Format(e);}
 }
 private static final class TokenReader extends FilterReader {
  private boolean quoted,escape;private int length;
  TokenReader(Reader in){super(in);}
  private void check(int v)throws IOException{char c=(char)v;if(quoted){if(++length>24002)throw new IOException("备份单字段过长");if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='\"'){quoted=false;length=0;}}else if(c=='\"'){quoted=true;length=0;}else if(Character.isWhitespace(c)||"{}[],:".indexOf(c)>=0)length=0;else if(++length>64)throw new IOException("备份数字字段过长");}
  public int read()throws IOException{int v=super.read();if(v>=0)check(v);return v;}
  public int read(char[] b,int off,int len)throws IOException{int n=in.read(b,off,len);for(int i=0;i<n;i++)check(b[off+i]);return n;}
 }
 private void parse(InputStream input)throws Exception{
  stage.beginTransaction();try(JsonReader r=new JsonReader(new TokenReader(new InputStreamReader(new LimitedInput(input),"UTF-8")))){
   r.setLenient(false);Set<String> seen=new HashSet<>();String app="";r.beginObject();
   while(r.hasNext()){
    String name=r.nextName();if(!seen.add(name))throw new IOException("重复的备份字段");
    if(name.equals("app"))app=r.nextString();
    else if(name.equals("version"))version=r.nextInt();
    else if(name.equals("exportedAt"))r.nextLong();
    else if(name.equals("plans")){plans=r.nextString();if(plans.length()>22000)throw new IOException("计划备份过长");}
    else if(name.equals("entries")){r.beginArray();while(r.hasNext()){
     if(++rows>MAX_ROWS)throw new IOException("备份超过100万条安全上限");
     JSONObject o=new JSONObject();Set<String> fields=new HashSet<>();r.beginObject();
     while(r.hasNext()){String k=r.nextName();if(!fields.add(k)||fields.size()>24)throw new IOException("记录字段重复或过多");
      if(NUMBER_FIELD.matcher(k).matches())o.put(k,r.nextLong());
      else if(TEXT_FIELD.matcher(k).matches()){String v=r.nextString();if(v.length()>4000)throw new IOException("记录字段过长");o.put(k,v);}
      else throw new IOException("不支持的记录字段："+k);
     }r.endObject();ContentValues v=new ContentValues();v.put("n",rows);v.put("payload",o.toString());v.put("fp",o.optString("fingerprint"));v.put("ref",o.optString("relatedFingerprint"));v.put("reason",o.optString("reason"));stage.insertOrThrow("rows",null,v);
    }r.endArray();}
    else if(name.equals("ignoredKeys")){r.beginArray();while(r.hasNext()){if(++keys>MAX_KEYS)throw new IOException("去重标记超过安全上限");String key=r.nextString();if(!MARKER.matcher(key).matches())throw new IOException("去重标记不合法");ContentValues v=new ContentValues();v.put("token",key);stage.insertWithOnConflict("markers",null,v,SQLiteDatabase.CONFLICT_IGNORE);}r.endArray();}
    else throw new IOException("不支持的备份字段："+name);
   }r.endObject();if(r.peek()!=JsonToken.END_DOCUMENT)throw new IOException("备份尾部有额外内容");
   if(!app.equals("notification-ledger")||(version!=1&&version!=2)||!seen.contains("entries"))throw new IOException("不是轻记支持的备份格式");
   stage.setTransactionSuccessful();
  }finally{stage.endTransaction();}
 }
 public void close(){try{stage.close();}finally{SQLiteDatabase.deleteDatabase(file);}}
}
