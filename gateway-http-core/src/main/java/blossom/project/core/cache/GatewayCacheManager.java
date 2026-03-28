package blossom.project.core.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.ConcurrentHashMap;


public class GatewayCacheManager {

    public GatewayCacheManager() {
    }

    /**
     * global cache, double level cache
     */
    private final ConcurrentHashMap<String, Cache<String, ?>> cacheMap = new ConcurrentHashMap<>();


    private static class SingletonInstance {
        private static final GatewayCacheManager INSTANCE = new GatewayCacheManager();
    }

    public static GatewayCacheManager getInstance() {
        return SingletonInstance.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    public <V> Cache<String, V> create(String cacheId) {
        Cache<String, V> cache = Caffeine.newBuilder().build();
        cacheMap.put(cacheId, cache);
        return (Cache<String, V>) cacheMap.get(cacheId);
    }

    @SuppressWarnings("unchecked")
    public <V> Cache<String, V> getCache(String cacheId) {
        return (Cache<String, V>) cacheMap.get(cacheId);
    }

    public void remove(String cacheId, String key) {
        Cache<String, ?> cache = cacheMap.get(cacheId);
        if (cache != null) {
            cache.invalidate(key);
        }
    }

    public void remove(String cacheId) {
        Cache<String, ?> cache = cacheMap.get(cacheId);
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    public <V> void removeAll() {
        cacheMap.values().forEach(cache -> cache.invalidateAll());
    }

}
