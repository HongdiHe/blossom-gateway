package blossom.project.core.filter.flow;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import blossom.project.core.util.JedisUtil;
public class RedisCountLimiter {
    private static final Logger log = LoggerFactory.getLogger(RedisCountLimiter.class);


    protected JedisUtil jedisUtil;

    public RedisCountLimiter(JedisUtil jedisUtil) {
        this.jedisUtil = jedisUtil;
    }

    private static final int SUCCESS_RESULT = 1;
    private static final int FAILED_RESULT = 0;

    public boolean doFlowControl(String key, int limit, int expire) {
        try {
            Object object = jedisUtil.executeScript(key, limit, expire);
            if (object == null) {
                // 如果 Redis 执行失败（比如连接不上），为了避免误杀请求，这里选择放行
                // 或者根据业务需求选择抛出异常（fail-fast）
                log.warn("Redis executeScript failed (returned null), bypassing flow control for key: {}", key);
                return true; 
            }
            Long result = Long.valueOf(object.toString());
            if (FAILED_RESULT == result) {
                return false;
            }
        } catch (Exception e) {
            log.error("分布式限流发送错误: {}", e.getMessage());
            // 出现异常时，也选择放行，避免网关因为 Redis 故障而不可用
            return true;
        }
        return true;
    }


}
