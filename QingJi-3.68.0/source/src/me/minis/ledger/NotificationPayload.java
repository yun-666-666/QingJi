package me.minis.ledger;

import android.app.Notification;
import android.os.*;
import java.util.*;

/** Each MessagingStyle message retains its own timestamp and sender; never join transactions. */
final class NotificationPayload {
 final String title,text,key;final long time;
 NotificationPayload(String title,String text,String key,long time){this.title=title;this.text=text;this.key=key;this.time=time;}
 static List<NotificationPayload> read(Notification n,String key,long posted){
  List<NotificationPayload> out=new ArrayList<>();if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return out;Bundle b=n.extras;if(b==null)return out;
  String title=String.valueOf(b.getCharSequence(Notification.EXTRA_TITLE,""));
  Parcelable[] bundles=b.getParcelableArray(Notification.EXTRA_MESSAGES);
  if(bundles!=null){
   List<Notification.MessagingStyle.Message> messages=Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles);
   for(Notification.MessagingStyle.Message message:messages){
    if(message.getText()==null||message.getTimestamp()<=0)continue;
    String sender=message.getSender()==null?title:message.getSender().toString();
    out.add(new NotificationPayload(sender,sender+"\n"+message.getText(),key+":message:"+message.getTimestamp(),message.getTimestamp()));
   }
   if(!out.isEmpty())return out;
  }
  CharSequence big=b.getCharSequence(Notification.EXTRA_BIG_TEXT),plain=b.getCharSequence(Notification.EXTRA_TEXT);
  CharSequence body=big!=null&&big.length()>0?big:plain;
  CharSequence[] lines=b.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
  if((body==null||body.length()==0)&&lines!=null){
   // Preserve the previous latest-line behavior when no per-message time is available.
   if(lines.length>0&&lines[lines.length-1]!=null)out.add(new NotificationPayload(title,title+"\n"+lines[lines.length-1],key,posted));
  }else if(body!=null){
   LinkedHashSet<String> parts=new LinkedHashSet<>();parts.add(title);parts.add(body.toString());CharSequence sub=b.getCharSequence(Notification.EXTRA_SUB_TEXT);if(sub!=null)parts.add(sub.toString());
   out.add(new NotificationPayload(title,String.join("\n",parts),key,posted));
  }
  return out;
 }
}
