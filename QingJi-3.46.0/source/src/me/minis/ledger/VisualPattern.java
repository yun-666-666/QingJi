package me.minis.ledger;

/** Small contrast features of a known success label, not OCR and not an amount detector. */
public final class VisualPattern {
 public static final int WIDTH=48,HEIGHT=16,SIZE=WIDTH*HEIGHT;
 public static String encode(int[] pixels){
  if(pixels.length!=SIZE)return "";int min=255,max=0;int[] gray=new int[SIZE];
  for(int i=0;i<SIZE;i++){int p=pixels[i];gray[i]=(((p>>16)&255)*30+((p>>8)&255)*59+(p&255)*11)/100;min=Math.min(min,gray[i]);max=Math.max(max,gray[i]);}
  if(max-min<45)return "";StringBuilder out=new StringBuilder(SIZE);int ink=0;
  for(int value:gray){boolean dark=value<(min+max)/2;out.append(dark?'1':'0');if(dark)ink++;}
  return ink<SIZE/20||ink>SIZE*19/20?"":out.toString();
 }
 public static boolean matches(String a,String b){
  if(a.length()!=SIZE||b.length()!=SIZE)return false;int different=0,ink=0,shared=0;
  for(int i=0;i<SIZE;i++){char x=a.charAt(i),y=b.charAt(i);if(x!=y)different++;if(x=='1'||y=='1')ink++;if(x=='1'&&y=='1')shared++;}
  return different<=SIZE/25&&ink>0&&shared*100>=ink*88;
 }
}
