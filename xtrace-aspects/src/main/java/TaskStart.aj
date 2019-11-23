import edu.brown.cs.systems.xtrace.XTrace;

public aspect TaskStart
{
    pointcut onTableCall(): call(* org.apache.hadoop.hbase.client.Table.*(..));
    //pointcut onTableCall(): call(public * org.apache.hadoop.hbase.client.HTable.*(..));
    pointcut onAdminCall(): call(* org.apache.hadoop.hbase.client.Admin.*(..));

    pointcut onClient(): onTableCall() || onAdminCall();

    before(): onClient(){
        XTrace.startTask(true);
        String mname = thisJoinPoint.toShortString();
        XTrace.getDefaultLogger().tag(mname, mname);
        XTrace.getDefaultLogger().log("test");
    }


}
