import edu.brown.cs.systems.xtrace.XTrace;


public aspect DiscoverControlFlow
{

    private static final boolean ENABLED = false;
    pointcut onHBaseMethod(): execution(* org.apache.hadoop.hbase..*(..)) && if(ENABLED);

    before(): onHBaseMethod() {
       XTrace.getDefaultLogger().log("DISCOVER call: ");
   }

}