package me.minis.ledger;
import java.io.*;
final class BackupFailure {
 static final class Read extends IOException {Read(IOException cause){super("源文件读取失败",cause);}}
 static final class Stage extends IOException {Stage(Throwable cause){super("临时存储处理失败",cause);}}
 static final class Format extends IOException {Format(Throwable cause){super(cause.getMessage()==null?"备份格式不完整":cause.getMessage(),cause);}}
 static InputStream source(InputStream in){return new FilterInputStream(in){public int read()throws IOException{try{return in.read();}catch(IOException e){throw new Read(e);}}public int read(byte[] b,int off,int len)throws IOException{try{return in.read(b,off,len);}catch(IOException e){throw new Read(e);}}};}
}
