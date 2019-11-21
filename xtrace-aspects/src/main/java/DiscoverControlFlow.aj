import edu.brown.cs.systems.xtrace.XTrace;
import org.aspectj.lang.annotation.*;

@Aspect
public class DiscoverControlFlow
{

    @Pointcut("execution(* org.apache.hadoop.hbase..*(..))")
    public void hbaseMethod() {}

    @Before("hbaseMethod()")
    public void logDiscover() {
        XTrace.getDefaultLogger().log("DISCOVER call: ");
   }

}