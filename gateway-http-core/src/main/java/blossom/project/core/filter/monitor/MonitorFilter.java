package blossom.project.core.filter.monitor;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import blossom.project.core.context.GatewayContext;
import blossom.project.core.filter.Filter;
import blossom.project.core.filter.FilterAspect;
import io.micrometer.core.instrument.Timer;
import static blossom.project.common.constant.FilterConst.*;

@FilterAspect(id=MONITOR_FILTER_ID,
        name = MONITOR_FILTER_NAME,
        order = MONITOR_FILTER_ORDER)
public class MonitorFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(MonitorFilter.class);

    @Override
    public void doFilter(GatewayContext ctx) throws Exception {
        ctx.setTimerSample(Timer.start());
    }
}
