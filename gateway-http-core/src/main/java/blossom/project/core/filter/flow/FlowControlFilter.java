package blossom.project.core.filter.flow;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import blossom.project.common.config.Rule;
import blossom.project.core.context.GatewayContext;
import blossom.project.core.filter.Filter;
import blossom.project.core.filter.FilterAspect;
import java.util.Iterator;
import java.util.Set;

import static blossom.project.common.constant.FilterConst.*;

@FilterAspect(id = FLOW_CTL_FILTER_ID,
        name = FLOW_CTL_FILTER_NAME,
        order = FLOW_CTL_FILTER_ORDER)
public class FlowControlFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(FlowControlFilter.class);

    @Override
    public void doFilter(GatewayContext ctx) throws Exception {
        Rule rule = ctx.getRule();
        if (rule != null) {

            Set<Rule.FlowControlConfig> flowControlConfigs = rule.getFlowControlConfigs();
            if (flowControlConfigs == null || flowControlConfigs.isEmpty()) {
                return;
            }
            Iterator iterator = flowControlConfigs.iterator();
            Rule.FlowControlConfig flowControlConfig;
            while (iterator.hasNext()) {
                GatewayFlowControlRule flowControlRule = null;
                flowControlConfig = (Rule.FlowControlConfig) iterator.next();
                if (flowControlConfig == null) {
                    continue;
                }
                //http-server/ping
                String path = ctx.getRequest().getPath();
                if (flowControlConfig.getType().equalsIgnoreCase(FLOW_CTL_TYPE_PATH)
                        && path.equals(flowControlConfig.getValue())) {
                    flowControlRule = FlowControlByPathRule.getInstance(rule.getServiceId(), path);
                } else if (flowControlConfig.getType().equalsIgnoreCase(FLOW_CTL_TYPE_SERVICE)) {

                }
                if (flowControlRule != null) {
                    flowControlRule.doFlowControlFilter(flowControlConfig, rule.getServiceId());
                }
            }
        }
    }
}
