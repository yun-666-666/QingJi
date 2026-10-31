from pathlib import Path
import subprocess,tempfile,sys
r=Path('/var/minis/shared/qingji-3.50');src=r/'work/src/me/minis/ledger'
with tempfile.TemporaryDirectory(prefix='dedup368-') as tmp:
 t=Path(tmp)
 def write(name,content):
  p=t/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
 write('bridge.py', '''import sqlite3,sys,base64
D=lambda s:base64.b64decode(s).decode()
a=sys.argv;c=sqlite3.connect(a[1]);sql=D(a[2]);args=[D(x) for x in a[3:]]
cur=c.execute(sql,args)
if cur.description:
 for row in cur:print('\\t'.join('~' if x is None else base64.b64encode(str(x).encode()).decode() for x in row))
else:print(cur.rowcount)
c.commit()
''')
 write('android/content/ContentValues.java','package android.content; public class ContentValues extends java.util.LinkedHashMap<String,Object>{}')
 write('android/database/Cursor.java','''package android.database;import java.util.*;public class Cursor implements AutoCloseable{List<String[]> rows;int i=-1;public Cursor(List<String[]> r){rows=r;}public boolean moveToFirst(){i=0;return i<rows.size();}public boolean moveToNext(){return ++i<rows.size();}public String getString(int n){return rows.get(i)[n];}public long getLong(int n){return Long.parseLong(getString(n));}public boolean isNull(int n){return getString(n)==null;}public void close(){}}''')
 write('android/database/sqlite/SQLiteDatabase.java', '''package android.database.sqlite;
import java.util.*;import java.io.*;import android.database.Cursor;import android.content.ContentValues;
public class SQLiteDatabase {
 public static final int CONFLICT_REPLACE=5;String path;public SQLiteDatabase(String p){path=p;}
 static String enc(String s){return Base64.getEncoder().encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 List<String> run(String sql,String[] a){try{List<String> cmd=new ArrayList<>(Arrays.asList("python3",System.getProperty("bridge"),path,enc(sql)));if(a!=null)for(String s:a)cmd.add(enc(s));Process p=new ProcessBuilder(cmd).redirectErrorStream(true).start();BufferedReader br=new BufferedReader(new InputStreamReader(p.getInputStream()));List<String> lines=new ArrayList<>();String line;while((line=br.readLine())!=null)lines.add(line);if(p.waitFor()!=0)throw new RuntimeException(sql+lines);return lines;}catch(Exception e){throw new RuntimeException(e);}}
 public Cursor rawQuery(String sql,String[] a){List<String[]> rows=new ArrayList<>();for(String line:run(sql,a)){String[] values=line.split("\\t",-1);for(int i=0;i<values.length;i++)values[i]=values[i].equals("~")?null:new String(Base64.getDecoder().decode(values[i]),java.nio.charset.StandardCharsets.UTF_8);rows.add(values);}return new Cursor(rows);}
 public void execSQL(String sql){run(sql,null);}public void execSQL(String sql,Object[] a){run(sql,Arrays.stream(a).map(Object::toString).toArray(String[]::new));}
 public int update(String table,ContentValues v,String where,String[] a){List<String> keys=new ArrayList<>(),args=new ArrayList<>();v.forEach((k,x)->{keys.add(k+"=?");args.add(x.toString());});args.addAll(Arrays.asList(a));return Integer.parseInt(run("UPDATE "+table+" SET "+String.join(",",keys)+" WHERE "+where,args.toArray(new String[0])).get(0));}
 public long insertOrThrow(String table,Object unused,ContentValues v){return insertWithOnConflict(table,unused,v,0);}public long insertWithOnConflict(String table,Object unused,ContentValues v,int conflict){String names=String.join(",",v.keySet()),marks=String.join(",",Collections.nCopies(v.size(),"?"));run("INSERT "+(conflict==5?"OR REPLACE ":"")+"INTO "+table+" ("+names+") VALUES ("+marks+")",v.values().stream().map(Object::toString).toArray(String[]::new));return 1;}
}''')
 write('me/minis/ledger/LedgerDb.java','''package me.minis.ledger;public class LedgerDb{static String hash(String s){try{return Hex.encode(java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")));}catch(Exception e){throw new RuntimeException(e);}}}''')
 # Production classes run unchanged, with SQLite statements executed by local Python sqlite3.
 for name in ['ReceiptDedup','CaptureDedup','PaymentPageDb','IdentityEvidence']:
  write('me/minis/ledger/'+name+'.java',(src/(name+'.java')).read_text())
 write('me/minis/ledger/Dedup368Test.java',(r/'validation/Dedup368Test.java').read_text())
 files=list(t.rglob('*.java'));subprocess.run(['javac','-encoding','UTF-8','-cp',str(r/'validation/classes'),'-d',str(t),*map(str,files)],check=True)
 subprocess.run(['java','-Dbridge='+str(t/'bridge.py'),'-cp',str(t)+':'+str(r/'validation/classes'),'me.minis.ledger.Dedup368Test',str(t/'test.db')],check=True)
print('Production ingestion gates with real local SQLite SQL via test adapter; not Android runtime/transactions')
