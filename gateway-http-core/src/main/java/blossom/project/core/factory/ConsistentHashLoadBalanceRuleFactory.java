package blossom.project.core.factory;

import blossom.project.core.filter.loadbalance.ConsistentHashLoadBalanceRule;

import java.util.concurrent.ConcurrentHashMap;

/**
 * This factory class is responsible for managing and providing instances of the ConsistentHashLoadBalanceRule.
 * It uses a ConcurrentHashMap to cache the instances of the load balancing rule for different service IDs.
 */
public class ConsistentHashLoadBalanceRuleFactory {
    // A ConcurrentHashMap to store instances of ConsistentHashLoadBalanceRule,
    // with service ID as the key and the rule instance as the value.
    private static final ConcurrentHashMap<String, ConsistentHashLoadBalanceRule> ruleMap = new ConcurrentHashMap<>();

    // Private constructor to prevent external instantiation of this factory class.
    // This ensures that the class can only be used through its static methods.
    private ConsistentHashLoadBalanceRuleFactory() {
    }

    /**
     * Retrieves an instance of the ConsistentHashLoadBalanceRule for the given service ID.
     * If an instance for the specified service ID does not exist in the cache (ruleMap),
     * it will create a new instance using the static getInstance method of ConsistentHashLoadBalanceRule.
     *
     * @param serviceId The ID of the service for which the load balancing rule instance is needed.
     * @return An instance of the ConsistentHashLoadBalanceRule for the given service ID.
     */
    public static ConsistentHashLoadBalanceRule getInstance(String serviceId) {
        return ruleMap.computeIfAbsent(serviceId, ConsistentHashLoadBalanceRule::getInstance);
    }
}