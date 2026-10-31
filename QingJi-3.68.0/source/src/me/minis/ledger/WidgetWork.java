package me.minis.ledger;
import java.util.concurrent.Executor;
/** At most one running pass and one replaceable latest pass; never queues every tap. */
final class WidgetWork {
 interface Job{void run(long version);}
 private final Executor executor;private Job next;private Runnable finish;private boolean running;private long version;
 WidgetWork(Executor executor){this.executor=executor;}
 synchronized boolean current(long v){return version==v;}
 synchronized void submit(Job job,Runnable done){version++;if(finish!=null)finish.run();next=job;finish=done;if(!running){running=true;executor.execute(this::drain);}}
 private void drain(){for(;;){Job job;Runnable done;long v;synchronized(this){if(next==null){running=false;return;}job=next;done=finish;next=null;finish=null;v=version;}try{job.run(v);}catch(RuntimeException ignored){}finally{if(done!=null)done.run();}}}
}
