package org.apache.hadoop.hbase.trace;

import org.apache.yetus.audience.InterfaceAudience;

@InterfaceAudience.Private
public class XTraceUtil {

    public static boolean checkBaggageForNull(Object baggage) {
        if (baggage == null) {
            onBaggageIsNull();
            return false;
        }
        return true;
    }

    private static void onBaggageIsNull() {
//        XTrace.startTask(true);
//        XTraceUtil.getDebugLogger().tag("NULL BAGGAGE","NULL BAGGAGE");
//        XTraceUtil.getDebugLogger().log("NULL BAGGAGE HERE");

    }

    private static final boolean TRACE_DEBUG_LOGGING = true;
//    private static XTraceLogger d_logger;

    public synchronized static DummyLogger getDebugLogger() {
        return new DummyLogger();
    }

    public synchronized static DummyLogger getContentLogger() {
        return new DummyLogger();

    }

}

