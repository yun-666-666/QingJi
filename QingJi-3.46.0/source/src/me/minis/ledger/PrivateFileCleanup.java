package me.minis.ledger;
import java.io.File;
/** No ledger/export paths; only explicitly named app-owned diagnostic/cache files. */
public final class PrivateFileCleanup {
 public static int prune(File dir,long now,long age,int max){
  File[] files=dir.listFiles(f->f.isFile()&&f.getName().startsWith("nodes-")&&f.getName().endsWith(".json"));if(files==null)return 0;
  java.util.Arrays.sort(files,java.util.Comparator.comparingLong(File::lastModified));int keep=files.length,removed=0;
  for(File f:files)if(now-f.lastModified()>=age||f.lastModified()>now+age||keep>max){if(f.delete()){keep--;removed++;}}
  return removed;
 }
 public static void staleTransfers(File dir,long now){File[] files=dir.listFiles();if(files==null)return;for(File f:files)if(f.isFile()&&(f.getName().startsWith("qingji-import-")||f.getName().startsWith("qingji-export-"))&&now-f.lastModified()>86400000L)f.delete();}
}
