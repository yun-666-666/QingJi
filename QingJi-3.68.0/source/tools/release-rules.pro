# Conservative release: shrink and rename, but do not rewrite program logic.
-dontoptimize
# WebView bridge annotations are required; no production reflection uses generic/enclosing metadata.
-keepattributes *Annotation*
-keep public class me.minis.ledger.MainActivity { public <init>(); }
-keep public class me.minis.ledger.LedgerApp { public <init>(); }
-keep public class me.minis.ledger.PaymentListener { public <init>(); }
-keep public class me.minis.ledger.PaymentScreenService { public <init>(); }
-keep public class me.minis.ledger.IgnoredCleanup { public <init>(); }
-keep public class me.minis.ledger.SummaryNotice { public <init>(); }
-keep public class me.minis.ledger.LedgerWidget { public <init>(); }
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keepclassmembers class * extends android.app.Activity { protected void onActivityResult(int,int,android.content.Intent); }
