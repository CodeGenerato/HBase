import edu.brown.cs.systems.xtrace.XTrace;
import edu.brown.cs.systems.baggage.Baggage;
import edu.brown.cs.systems.xtrace.XTraceBaggageInterface;


public aspect TaskStart
{
    pointcut onAdminCall(): within(org.apache.hadoop.hbase.client.HBaseAdmin) && execution(public * *(..));
    pointcut onTableCall(): within(org.apache.hadoop.hbase.client.HTable) && execution(public * *(..));

    pointcut onClient(): onAdminCall() || onTableCall();

    Object around(): onClient() {
       if (!XTraceBaggageInterface.hasTaskID()) {
         XTrace.startTask(true);
         String mname = thisJoinPoint.toShortString();
         XTrace.getDefaultLogger().tag(mname, mname);
         try {
           return proceed();
         } finally {
           Baggage.discard();
         }
       } else {
         return proceed();
       }
     }

}
