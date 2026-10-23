package me.minis.ledger;
import java.io.*;import java.security.*;import java.util.*;import javax.crypto.*;import javax.crypto.spec.*;
/** Versioned bounded authenticated chunks. No plaintext temp export and no hardcoded password. */
public final class BackupCrypto {
 private static final byte[] MAGIC={'Q','J','B','K',1};private static final int CHUNK=32768;
 public static String newCode(){byte[] b=new byte[32];new SecureRandom().nextBytes(b);return hex(b);}
 static String hex(byte[] b){return Hex.encode(b);}
 static byte[] key(String text){String s=text.replace("-","").replace(" ","").trim();if(!s.matches("[0-9a-fA-F]{64}"))throw new IllegalArgumentException("恢复码应为64位字符，请完整粘贴");byte[] b=new byte[32];for(int i=0;i<32;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
 static byte[] crypt(int mode,byte[] key,byte[] prefix,int seq,boolean end,byte[] data,int len)throws IOException{try{byte[] iv=new byte[12];System.arraycopy(prefix,0,iv,0,8);for(int i=0;i<4;i++)iv[8+i]=(byte)(seq>>>(24-8*i));Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));c.updateAAD(MAGIC);c.updateAAD(new byte[]{(byte)(end?1:0)});return c.doFinal(data,0,len);}catch(GeneralSecurityException e){throw new IOException("恢复码不正确，或备份已损坏／被修改；未恢复任何记录",e);}}
 public static OutputStream encrypt(OutputStream out,String code)throws IOException{return new Encrypted(out,key(code));}
 public static InputStream decrypt(InputStream in,String code)throws IOException{return new Decrypted(in,key(code));}
 public static boolean encrypted(byte[] b){return Arrays.equals(b,MAGIC);}
 public static final class Encrypted extends OutputStream{
  final DataOutputStream out;final byte[] key,prefix=new byte[8],buffer=new byte[CHUNK];int used,seq;boolean closed;
  Encrypted(OutputStream o,byte[] k)throws IOException{out=new DataOutputStream(o);key=k;new SecureRandom().nextBytes(prefix);out.write(MAGIC);out.write(prefix);}
  void block(boolean end)throws IOException{if(seq==Integer.MAX_VALUE)throw new IOException("备份过大");byte[] b=crypt(Cipher.ENCRYPT_MODE,key,prefix,seq++,end,buffer,used);out.writeInt(end?-b.length:b.length);out.write(b);used=0;}
  public void write(int n)throws IOException{if(closed)throw new IOException("备份已关闭");buffer[used++]=(byte)n;if(used==CHUNK)block(false);}
  public void write(byte[] b,int off,int len)throws IOException{if(closed)throw new IOException("备份已关闭");while(len>0){int n=Math.min(len,CHUNK-used);System.arraycopy(b,off,buffer,used,n);used+=n;off+=n;len-=n;if(used==CHUNK)block(false);}}
  public void flush()throws IOException{out.flush();}
  public void close()throws IOException{if(closed)return;closed=true;try{if(used>0)block(false);block(true);out.flush();}finally{Arrays.fill(key,(byte)0);Arrays.fill(buffer,(byte)0);out.close();}}
 }
 static final class Decrypted extends InputStream{
  final DataInputStream in;final byte[] key,prefix=new byte[8];byte[] current=new byte[0];int pos,seq;boolean done;
  Decrypted(InputStream i,byte[] k)throws IOException{in=new DataInputStream(i);key=k;byte[] head=new byte[5];in.readFully(head);if(!encrypted(head))throw new IOException("不是轻记加密备份");in.readFully(prefix);}
  boolean next()throws IOException{if(done)return false;int n;try{n=in.readInt();}catch(EOFException e){throw new IOException("加密备份不完整，未恢复任何记录",e);}boolean end=n<0;if(n==Integer.MIN_VALUE||n==0||Math.abs(n)<16||Math.abs(n)>CHUNK+16||seq==Integer.MAX_VALUE)throw new IOException("加密备份块不合法");byte[] b=new byte[Math.abs(n)];in.readFully(b);current=crypt(Cipher.DECRYPT_MODE,key,prefix,seq++,end,b,b.length);pos=0;if(end){if(current.length!=0||in.read()!=-1)throw new IOException("加密备份尾部不合法");done=true;return false;}if(current.length==0)throw new IOException("加密备份空数据块");return true;}
  public int read()throws IOException{while(pos==current.length)if(!next())return -1;return current[pos++]&255;}
  public int read(byte[] b,int off,int len)throws IOException{if(len==0)return 0;while(pos==current.length)if(!next())return -1;int n=Math.min(len,current.length-pos);System.arraycopy(current,pos,b,off,n);pos+=n;return n;}
  public void close()throws IOException{Arrays.fill(key,(byte)0);Arrays.fill(current,(byte)0);in.close();}
 }
}
