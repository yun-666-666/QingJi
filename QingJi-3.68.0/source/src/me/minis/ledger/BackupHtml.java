package me.minis.ledger;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.Base64;
/** HTML wrapper carries only ciphertext. Android reads payload; it NEVER executes imported HTML. */
public final class BackupHtml {
 static final byte[] HEAD="<!doctype html><!--QJHTML1-->\n".getBytes(StandardCharsets.US_ASCII);
 static final byte[] MARK="<script id=\"qj-payload\" type=\"application/octet-stream\">".getBytes(StandardCharsets.US_ASCII);
 static final byte[] TAIL="</script>\n</body></html>\n".getBytes(StandardCharsets.US_ASCII);
 static final int MAX_TEMPLATE=65536;
 public static OutputStream encrypt(OutputStream out,InputStream template,String code)throws IOException{
  out.write(HEAD);try(InputStream src=template){byte[] b=new byte[4096];int n,total=0;while((n=src.read(b))!=-1){if((total+=n)>MAX_TEMPLATE)throw new IOException("备份页面模板过大");out.write(b,0,n);}}
  out.write(MARK);OutputStream base=Base64.getEncoder().wrap(new FilterOutputStream(out){public void write(byte[] b,int off,int len)throws IOException{out.write(b,off,len);}public void close()throws IOException{flush();}});OutputStream encrypted=BackupCrypto.encrypt(base,code);
  final OutputStream target=out;return new FilterOutputStream(encrypted){boolean closed;public void write(byte[] b,int off,int len)throws IOException{out.write(b,off,len);}public void close()throws IOException{if(closed)return;closed=true;out.close();BackupHtml.finish(target);}};
 }
 private static void finish(OutputStream out)throws IOException{out.write(TAIL);out.flush();out.close();}
 public static InputStream payload(InputStream in)throws IOException{
  PushbackInputStream p=new PushbackInputStream(in,HEAD.length);byte[] b=new byte[HEAD.length];int n=0,r;while(n<b.length&&(r=p.read(b,n,b.length-n))>0)n+=r;
  if(n!=b.length||!java.util.Arrays.equals(b,HEAD)){if(n>0)p.unread(b,0,n);return p;}
  int matched=0,count=0;while(matched<MARK.length){int c=p.read();if(c<0||++count>MAX_TEMPLATE+MARK.length)throw new IOException("备份页面缺少密文");if(c==(MARK[matched]&255))matched++;else matched=c==(MARK[0]&255)?1:0;}
  InputStream base=new InputStream(){long size;boolean done;public int read()throws IOException{if(done)return -1;int c=p.read();if(c=='<'){for(int i=1;i<TAIL.length;i++)if(p.read()!=(TAIL[i]&255))throw new IOException("备份页面尾部损坏");if(p.read()!=-1)throw new IOException("备份页面含多余内容");done=true;return -1;}if(c<0)throw new IOException("备份页面不完整");if(++size>(BackupStream.MAX_BYTES+1024*1024)*4/3+4)throw new IOException("备份过大");if(!(c>='A'&&c<='Z'||c>='a'&&c<='z'||c>='0'&&c<='9'||c=='+'||c=='/'||c=='='))throw new IOException("备份密文编码不合法");return c;}public void close()throws IOException{p.close();}};
  InputStream decoded=Base64.getDecoder().wrap(base);return new FilterInputStream(decoded){boolean checked;private int check(int n)throws IOException{if(n==-1&&!checked){checked=true;if(base.read()!=-1)throw new IOException("备份尾部存在多余密文");}return n;}public int read()throws IOException{return check(super.read());}public int read(byte[] b,int off,int len)throws IOException{return check(super.read(b,off,len));}};
 }
}
