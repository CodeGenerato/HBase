import edu.brown.cs.systems.xtrace.XTrace;
import edu.brown.cs.systems.baggage.Baggage;
import edu.brown.cs.systems.xtrace.XTraceBaggageInterface;


public aspect TaskStart
{
    //pointcut onTableCall(): call(* org.apache.hadoop.hbase.client.Table.*(..));
    //pointcut onTableCall(): call(public * org.apache.hadoop.hbase.client.HTable.*(..));
    //pointcut onAdminCall(): call(* org.apache.hadoop.hbase.client.Admin.*(..));
    pointcut onAdminCall(): call(public void org.apache.hadoop.hbase.client.HBaseAdmin.*(..));

    pointcut onClient(): onAdminCall();

   // before(): onClient(){
//      if(!XTraceBaggageInterface.hasTaskID()){
//            XTrace.startTask(true);
//            String mname = thisJoinPoint.toShortString();
//            XTrace.getDefaultLogger().tag(mname, mname);
//            XTrace.getDefaultLogger().log("test before");
//       }
  //  }

    // after(): onClient(){
         //   Baggage.discard();
   //  }

     void around(): onClient() {
      if(!XTraceBaggageInterface.hasTaskID()){
                 XTrace.startTask(true);
                 String mname = thisJoinPoint.toShortString();
                 XTrace.getDefaultLogger().tag(mname, mname);
                 XTrace.getDefaultLogger().log("test before");
                 try{
                 proceed();
                 }
                 finally{
                 XTrace.getDefaultLogger().log("test after");
                 Baggage.discard();
                 }
       }
       else{
         proceed();
       }
     }



}
