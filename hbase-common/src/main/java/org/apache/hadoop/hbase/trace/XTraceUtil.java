package org.apache.hadoop.hbase.trace;

import edu.brown.cs.systems.xtrace.XTrace;
import edu.brown.cs.systems.xtrace.logging.NullLogger;
import edu.brown.cs.systems.xtrace.logging.XTraceLogger;
import org.apache.yetus.audience.InterfaceAudience;

@InterfaceAudience.Private
public class XTraceUtil {

    public static boolean checkBaggageForNull(Object baggage){
        if(baggage == null){
            onBaggageIsNull();
            return false;
        }
        return true;
    }

    private static void onBaggageIsNull(){
        XTrace.startTask(true);
        XTrace.getDefaultLogger().tag("NULL BAGGAGE","NULL BAGGAGE");
        XTrace.getDefaultLogger().log("NULL BAGGAGE HERE");

    }

    private static final boolean TRACE_DEBUG_LOGGING = true;
    private static XTraceLogger d_logger;

    public synchronized static XTraceLogger getDebugLogger() {
        if (d_logger == null) {
            if (TRACE_DEBUG_LOGGING) {
                d_logger = XTrace.getLogger("TraceDebug");
            }else {
                d_logger = new NullLogger();
            }
        }
        return d_logger;
    }

    private static XTraceLogger c_logger;
    public synchronized  static XTraceLogger getContentLogger(){
        if(c_logger == null) {
            c_logger = XTrace.getDefaultLogger();
        }
        return c_logger;
    }
}
