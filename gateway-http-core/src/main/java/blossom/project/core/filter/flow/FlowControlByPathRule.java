package blossom.project.core.filter.flow;

import blossom.project.common.config.Rule;
import blossom.project.common.enums.ResponseCode;
import blossom.project.common.exception.LimitedException;
import blossom.project.core.util.JedisUtil;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static blossom.project.common.constant.FilterConst.*;

public class FlowControlByPathRule implements GatewayFlowControlRule {
    private String serviceId;

    private String path;

    private RedisCountLimiter redisCountLimiter;

    private static final String LIMIT_MESSAGE = "您的请求过于频繁,请稍后重试";

    public FlowControlByPathRule(String serviceId, String path, RedisCountLimiter redisCountLimiter) {
        this.serviceId = serviceId;
        this.path = path;
        this.redisCountLimiter = redisCountLimiter;
    }

    /**
     * Map for storing path-flow control rules.
     */
    private static ConcurrentHashMap<String, FlowControlByPathRule> servicePathMap = new ConcurrentHashMap<>();

    /**
     * Obtain specific flow control rule filters by service ID and path.
     */
    public static FlowControlByPathRule getInstance(String serviceId, String path) {
        StringBuffer buffer = new StringBuffer();
        String key = buffer.append(serviceId).append(".").append(path).toString();
        FlowControlByPathRule flowControlByPathRule = servicePathMap.get(key);

        if (flowControlByPathRule == null) {
            flowControlByPathRule = new FlowControlByPathRule(serviceId, path, new RedisCountLimiter(new JedisUtil()));
            servicePathMap.put(key, flowControlByPathRule);
        }
        return flowControlByPathRule;
    }

    @Override
    public void doFlowControlFilter(Rule.FlowControlConfig flowControlConfig, String serviceId) {
        if (flowControlConfig == null || StringUtils.isEmpty(serviceId) || StringUtils.isEmpty(flowControlConfig.getConfig())) {
            return;
        }

        Map<String, Integer> configMap = JSON.parseObject(flowControlConfig.getConfig(), Map.class);

        if (!configMap.containsKey(FLOW_CTL_LIMIT_DURATION) || !configMap.containsKey(FLOW_CTL_LIMIT_PERMITS)) {
            return;
        }

        double duration = configMap.get(FLOW_CTL_LIMIT_DURATION);
        double permits = configMap.get(FLOW_CTL_LIMIT_PERMITS);
        StringBuffer buffer = new StringBuffer();

        boolean flag = false;
        String key = buffer.append(serviceId).append(".").append(path).toString();

        if (FLOW_CTL_MODEL_DISTRIBUTED.equalsIgnoreCase(flowControlConfig.getModel())) {
            flag = redisCountLimiter.doFlowControl(key, (int) permits, (int) duration);
        } else {
            GuavaCountLimiter guavaCountLimiter = GuavaCountLimiter.getInstance(serviceId, flowControlConfig);
            if (guavaCountLimiter == null) {
                throw new RuntimeException("获取单机限流工具类为空");
            }
            double count = Math.ceil(permits / duration);
            flag = guavaCountLimiter.acquire((int) count);
        }
        if (!flag) {
            throw new LimitedException(ResponseCode.FLOW_CONTROL_ERROR);
        }
    }
}
