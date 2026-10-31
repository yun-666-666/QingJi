package me.minis.ledger;
/** All application packages may be checked; exclude only OS surfaces and our own output. */
public final class PaymentApps {
 public static boolean allowed(String p){return p!=null&&!p.isEmpty()&&!p.equals("android")&&!p.equals("com.android.systemui")&&!p.equals("com.android.settings")&&!p.equals("com.bjbyhd.screenreader_huawei.biller")&&!p.equals("com.openminis.app");}
}
