package android.os;
public final class Handler {
    private static final class Task {final Runnable action;final long time;Task(Runnable action,long time){this.action=action;this.time=time;}}
    private static final java.util.List<Task> tasks=new java.util.ArrayList<>();
    private static long now;
    static long nowMillis(){return now;}
    public Handler(Looper ignored){}
    public void removeCallbacks(Runnable action){tasks.removeIf(t->t.action==action);}
    public boolean postDelayed(Runnable action,long delay){tasks.add(new Task(action,now+delay));return true;}
    public static void advance(long delta){
        long end=now+delta;
        while(true){Task next=null;for(Task task:tasks)if(task.time<=end&&(next==null||task.time<next.time))next=task;
            if(next==null)break;tasks.remove(next);now=next.time;next.action.run();}
        now=end;
    }
}
