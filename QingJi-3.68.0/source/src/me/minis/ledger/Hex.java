package me.minis.ledger;
/** Exact lowercase byte encoding. No shared mutable digest or Formatter state. */
final class Hex {
 private static final String DIGITS="0123456789abcdef";
 static String encode(byte[] bytes){char[] out=new char[bytes.length*2];for(int i=0;i<bytes.length;i++){int n=bytes[i]&255;out[i*2]=DIGITS.charAt(n>>>4);out[i*2+1]=DIGITS.charAt(n&15);}return new String(out);}
}
