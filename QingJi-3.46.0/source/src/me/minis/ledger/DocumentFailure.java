package me.minis.ledger;
import java.io.*;
public final class DocumentFailure {
 public static String message(Throwable e,String phase){for(Throwable t=e;t!=null;t=t.getCause()){String s=String.valueOf(t.getMessage());if(t instanceof android.database.sqlite.SQLiteFullException||s.contains("ENOSPC")||s.contains("No space"))return "设备空间不足：请释放本机空间后重试。导入事务失败不会部分记账。";if(t instanceof SecurityException)return "文件访问权限失效：请重新选择文件或保存位置。";}
  if(e instanceof BackupFailure.Format||e instanceof org.json.JSONException)return "备份格式或内容不合法："+e.getMessage()+"。请选择轻记导出的完整 JSON 备份。";
  if(e instanceof BackupFailure.Stage)phase="stage";if(e instanceof BackupFailure.Read)phase="read";
  if(phase.equals("read"))return "源文件读取失败：请确认文件已下载到本机且能正常打开，再重新选择。";
  if(phase.equals("write"))return "目标文件写入失败：请检查保存位置权限和剩余空间。目标可能是不完整文件，请删除后重新导出。";
  if(phase.equals("stage"))return "本机临时文件处理失败：请检查可用空间后重试。";
  return "恢复失败，本次账本更改未提交："+(e.getMessage()==null?"请重新选择完整备份":e.getMessage());
 }
}
