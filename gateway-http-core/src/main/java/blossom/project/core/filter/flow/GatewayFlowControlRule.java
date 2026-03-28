package blossom.project.core.filter.flow;

import blossom.project.common.config.Rule;

public interface GatewayFlowControlRule {

    /**
     * filter
     * @param flowControlConfig
     * @param serviceId
     */
    void doFlowControlFilter(Rule.FlowControlConfig flowControlConfig, String serviceId);
}
