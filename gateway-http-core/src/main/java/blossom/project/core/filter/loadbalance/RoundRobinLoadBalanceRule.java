package blossom.project.core.filter.loadbalance;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import blossom.project.core.DynamicConfigManager;
import blossom.project.common.config.ServiceInstance;
import blossom.project.common.exception.NotFoundException;
import blossom.project.core.context.GatewayContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static blossom.project.common.enums.ResponseCode.SERVICE_INSTANCE_NOT_FOUND;

public class RoundRobinLoadBalanceRule implements LoadBalanceGatewayRule {
    private static final Logger log = LoggerFactory.getLogger(RoundRobinLoadBalanceRule.class);


    private AtomicInteger position = new AtomicInteger(1);

    private final String serviceId;


    public RoundRobinLoadBalanceRule(String serviceId) {
        this.serviceId = serviceId;
    }

    private static ConcurrentHashMap<String, RoundRobinLoadBalanceRule> serviceMap = new ConcurrentHashMap<>();

    public static RoundRobinLoadBalanceRule getInstance(String serviceId) {
        RoundRobinLoadBalanceRule loadBalanceRule = serviceMap.get(serviceId);
        if (loadBalanceRule == null) {
            loadBalanceRule = new RoundRobinLoadBalanceRule(serviceId);
            serviceMap.put(serviceId, loadBalanceRule);
        }
        return loadBalanceRule;
    }

    @Override
    public ServiceInstance choose(GatewayContext ctx,boolean gray) {
        return choose(ctx.getUniqueId(),gray);
    }

    @Override
    public ServiceInstance choose(String serviceId,boolean gray) {
        Set<ServiceInstance> serviceInstanceSet =
                DynamicConfigManager.getInstance().getServiceInstanceByUniqueId(serviceId,gray);
        if (serviceInstanceSet.isEmpty()) {
            log.warn("No instance available for:{} gray:{}", serviceId, gray);
            throw new NotFoundException(SERVICE_INSTANCE_NOT_FOUND);
        }
        List<ServiceInstance> instances = new ArrayList<ServiceInstance>(serviceInstanceSet);
        if (instances.isEmpty()) {
            log.warn("No instance available for service:{}", serviceId);
            return null;
        } else {
            int pos = Math.abs(this.position.incrementAndGet());
            return instances.get(pos % instances.size());
        }
    }
}
