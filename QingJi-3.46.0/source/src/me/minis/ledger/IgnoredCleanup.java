package me.minis.ledger;
import android.app.job.*;
import android.content.*;
import android.os.SystemClock;
import java.util.concurrent.*;
/** Inexact, no alarm/wake lock, no new permission; UI access also enforces expiry. */
public final class IgnoredCleanup extends JobService {
 private static final int ID=31801;
 private static final ExecutorService worker=Executors.newSingleThreadExecutor();
 private static boolean queued=false;private static long lastCheck=-1;
 private volatile JobParameters current;
 public static void start(Context c){Context app=c.getApplicationContext();try{JobScheduler scheduler=(JobScheduler)app.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(scheduler.getPendingJob(ID)==null)scheduler.schedule(new JobInfo.Builder(ID,new ComponentName(app,IgnoredCleanup.class)).setPeriodic(6*60*60*1000L).build());}catch(Exception e){PowerDetail.count("cleanup_schedule_error");}request(app);}
 public static synchronized void request(Context c){long now=SystemClock.elapsedRealtime();if(queued||(lastCheck>=0&&now>=lastCheck&&now-lastCheck<60*60*1000L))return;queued=true;Context app=c.getApplicationContext();worker.execute(()->{boolean ok=clean(app);synchronized(IgnoredCleanup.class){queued=false;if(ok)lastCheck=SystemClock.elapsedRealtime();}});}
 private static boolean clean(Context c){ServiceNodeDiagnostic.cleanup(c);try(LedgerDb db=new LedgerDb(c)){db.cleanupIgnored(System.currentTimeMillis());LedgerPlans.check(c,db);BackupVault.due(c,db);return true;}catch(Exception e){PowerDetail.count("cleanup_error");return false;}}
 public boolean onStartJob(JobParameters p){current=p;worker.execute(()->{boolean ok=clean(getApplicationContext());if(current==p){current=null;jobFinished(p,!ok);}});return true;}
 public boolean onStopJob(JobParameters p){if(current==p)current=null;return true;}
}
