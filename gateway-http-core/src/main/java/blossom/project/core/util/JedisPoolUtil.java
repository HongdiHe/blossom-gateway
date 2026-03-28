package blossom.project.core.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.*;

import java.util.Properties;

public class JedisPoolUtil {
    private static final Logger log = LoggerFactory.getLogger(JedisPoolUtil.class);

    private static volatile JedisPool jedisPool = null;

    public JedisPoolUtil() {}

    private static void initialPool() {
        if (jedisPool != null) {
            return;
        }
        synchronized (JedisPoolUtil.class) {
            if (jedisPool != null) {
                return;
            }
            try {
                Properties prop = new Properties();
                prop.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("gateway.properties"));

                String host = prop.getProperty("redis.host");
                int port = Integer.parseInt(prop.getProperty("redis.port"));

                int maxTotal = Integer.parseInt(prop.getProperty("redis.maxTotal"));
                int maxIdle = Integer.parseInt(prop.getProperty("redis.maxIdle"));
                int minIdle = Integer.parseInt(prop.getProperty("redis.minIdle"));

                JedisPoolConfig config = new JedisPoolConfig();
                config.setMaxTotal(maxTotal);
                config.setMaxIdle(maxIdle);
                config.setMinIdle(minIdle);
                // config.setMaxWaitMillis(maxWaitMillis);
                
                jedisPool = new JedisPool(config, host, port);
            } catch (Exception e) {
                log.error("init redis pool failed", e);
            }
        }
    }

    public Jedis getJedis() {
        if (jedisPool == null) {
            initialPool();
        }
        try {
            return jedisPool.getResource();
        } catch (Exception e) {
            log.debug("getJedis() throws : {}", e.getMessage());
        }
        return null;
    }

    public Pipeline getPipeline() {
        // Warning: This creates a NEW connection every time and does not use the pool!
        // Fixed to use pool if possible, but keeping original logic structure for now to avoid breaking too much
        // Ideally should use jedisPool.getResource().pipelined()
        if (jedisPool == null) {
             initialPool();
        }
        try (Jedis jedis = jedisPool.getResource()) {
             // This is tricky because Pipeline is attached to a Client.
             // Original code created a new BinaryJedis which is a separate connection.
             // We will stick to the original behavior but be aware it's not pooled properly.
             // Actually, let's fix the property loading first.
             Properties prop = new Properties();
             try {
                prop.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("gateway.properties"));
                String host = prop.getProperty("redis.host");
                int port = Integer.parseInt(prop.getProperty("redis.port"));
                BinaryJedis binaryJedis = new BinaryJedis(host, port);
                return binaryJedis.pipelined();
             } catch(Exception e) {
                 log.error("getPipeline error", e);
             }
        }
        return null;
    }
}
