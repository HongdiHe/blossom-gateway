package blossom.project.core;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import blossom.project.common.config.ServiceDefinition;
import blossom.project.common.config.ServiceInstance;
import blossom.project.common.utils.NetUtils;
import blossom.project.common.utils.TimeUtil;
import blossom.project.config.center.api.ConfigCenter;
import blossom.project.register.center.api.RegisterCenter;
import blossom.project.register.center.api.RegisterCenterListener;
import com.alibaba.fastjson.JSON;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import static blossom.project.common.constant.BasicConst.COLON_SEPARATOR;

public class Bootstrap {
    private static final Logger log = LoggerFactory.getLogger(Bootstrap.class);

    public static void main(String[] args) {
        // Load the core static configuration of the gateway
        Config config = ConfigLoader.getInstance().load(args);
        log.info("Gateway port: {}", config.getPort());

        // Initialize plugins
        // Initialize the configuration center manager, connect to the configuration center, and listen for the addition, modification, and deletion of configurations
        ServiceLoader<ConfigCenter> serviceLoader = ServiceLoader.load(ConfigCenter.class);
        final ConfigCenter configCenter = serviceLoader.findFirst().orElseThrow(() -> {
            log.error("not found ConfigCenter impl");
            return new RuntimeException("not found ConfigCenter impl");
        });

        // Get the configuration from the configuration center
        configCenter.init(config.getRegistryAddress(), config.getEnv());
        configCenter.subscribeRulesChange(rules -> DynamicConfigManager.getInstance()
                .putAllRule(rules));

        // Connect to the registration center and load the instances in the registration center locally
        final RegisterCenter registerCenter = registerAndSubscribe(config);

        // Start the container
        Container container = new Container(config);
        container.start();

        // Gracefully shut down the service
        // Called when receiving a kill signal
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            registerCenter.deregister(buildGatewayServiceDefinition(config),
                    buildGatewayServiceInstance(config));
            container.shutdown();
        }));
    }

    private static RegisterCenter registerAndSubscribe(Config config) {
        ServiceLoader<RegisterCenter> serviceLoader = ServiceLoader.load(RegisterCenter.class);
        final RegisterCenter registerCenter = serviceLoader.findFirst().orElseThrow(() -> {
            log.error("not found RegisterCenter impl");
            return new RuntimeException("not found RegisterCenter impl");
        });
        registerCenter.init(config.getRegistryAddress(), config.getEnv());

        // Construct the gateway service definition and service instance
        ServiceDefinition serviceDefinition = buildGatewayServiceDefinition(config);
        ServiceInstance serviceInstance = buildGatewayServiceInstance(config);

        // Register
        registerCenter.register(serviceDefinition, serviceInstance);

        // Subscribe
        registerCenter.subscribeAllServices(new RegisterCenterListener() {
            @Override
            public void onChange(ServiceDefinition serviceDefinition, Set<ServiceInstance> serviceInstanceSet) {
                ServiceDefinition sd = serviceDefinition;
                if (sd == null && !serviceInstanceSet.isEmpty()) {
                    // Try to recover from instance metadata
                    ServiceInstance instance = serviceInstanceSet.iterator().next();
                    String uniqueId = instance.getUniqueId();
                    if (uniqueId != null) {
                        sd = new ServiceDefinition();
                        sd.setUniqueId(uniqueId);
                        sd.setServiceId(uniqueId.split(":")[0]);
                        sd.setEnvType(config.getEnv());
                    }
                }

                if (sd == null) {
                    log.warn("serviceDefinition is null, ignore change event");
                    return;
                }
                log.info("refresh service and instance: {} {}", sd.getUniqueId(),
                        JSON.toJSON(serviceInstanceSet));
                DynamicConfigManager manager = DynamicConfigManager.getInstance();
                // Add the service instances affected by this change event to the corresponding service instance collection again
                manager.addServiceInstance(sd.getUniqueId(), serviceInstanceSet);
                // Modify the corresponding service definition when the change occurs
                manager.putServiceDefinition(sd.getUniqueId(), sd);
            }
        });
        return registerCenter;
    }

    private static ServiceInstance buildGatewayServiceInstance(Config config) {
        String localIp = NetUtils.getLocalIp();
        int port = config.getPort();
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setServiceInstanceId(localIp + COLON_SEPARATOR + port);
        serviceInstance.setIp(localIp);
        serviceInstance.setPort(port);
        serviceInstance.setRegisterTime(TimeUtil.currentTimeMillis());
        return serviceInstance;
    }

    private static ServiceDefinition buildGatewayServiceDefinition(Config config) {
        ServiceDefinition serviceDefinition = new ServiceDefinition();
        serviceDefinition.setInvokerMap(Map.of());
        serviceDefinition.setUniqueId(config.getApplicationName());
        serviceDefinition.setServiceId(config.getApplicationName());
        serviceDefinition.setEnvType(config.getEnv());
        return serviceDefinition;
    }
}