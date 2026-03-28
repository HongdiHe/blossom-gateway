package blossom.project.core;

import com.lmax.disruptor.*;

/**
 * Config
 */

public class Config {
    private int port = 8888;

    private int prometheusPort = 18000;

    private String applicationName = "api-gateway";

    private String registryAddress = "127.0.0.1:8848";

    private String env = "dev";

    //netty

    private int eventLoopGroupBossNum = 1;

    //private int eventLoopGroupWokerNum = Runtime.getRuntime().availableProcessors();
    private int eventLoopGroupWokerNum = Runtime.getRuntime().availableProcessors();

    private int maxContentLength = 64 * 1024 * 1024;

    // default async
    private boolean whenComplete = true;

    //	Http Async params:
    private int httpConnectTimeout = 30 * 1000;

    private int httpRequestTimeout = 30 * 1000;

    private int httpMaxRequestRetry = 2;

    private int httpMaxConnections = 10000;

    private int httpConnectionsPerHost = 8000;

    private int httpPooledConnectionIdleTimeout = 60 * 1000;

    private String defaultBufferType = "default";
    private String parallelBufferType = "parallel";

    private int bufferSize = 1024 * 16;

    private int processThread = Runtime.getRuntime().availableProcessors();

    private String waitStrategy ="blocking";

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getPrometheusPort() {
        return prometheusPort;
    }

    public void setPrometheusPort(int prometheusPort) {
        this.prometheusPort = prometheusPort;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getRegistryAddress() {
        return registryAddress;
    }

    public void setRegistryAddress(String registryAddress) {
        this.registryAddress = registryAddress;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public int getEventLoopGroupBossNum() {
        return eventLoopGroupBossNum;
    }

    public void setEventLoopGroupBossNum(int eventLoopGroupBossNum) {
        this.eventLoopGroupBossNum = eventLoopGroupBossNum;
    }

    public int getEventLoopGroupWokerNum() {
        return eventLoopGroupWokerNum;
    }

    public void setEventLoopGroupWokerNum(int eventLoopGroupWokerNum) {
        this.eventLoopGroupWokerNum = eventLoopGroupWokerNum;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public boolean isWhenComplete() {
        return whenComplete;
    }

    public void setWhenComplete(boolean whenComplete) {
        this.whenComplete = whenComplete;
    }

    public int getHttpConnectTimeout() {
        return httpConnectTimeout;
    }

    public void setHttpConnectTimeout(int httpConnectTimeout) {
        this.httpConnectTimeout = httpConnectTimeout;
    }

    public int getHttpRequestTimeout() {
        return httpRequestTimeout;
    }

    public void setHttpRequestTimeout(int httpRequestTimeout) {
        this.httpRequestTimeout = httpRequestTimeout;
    }

    public int getHttpMaxRequestRetry() {
        return httpMaxRequestRetry;
    }

    public void setHttpMaxRequestRetry(int httpMaxRequestRetry) {
        this.httpMaxRequestRetry = httpMaxRequestRetry;
    }

    public int getHttpMaxConnections() {
        return httpMaxConnections;
    }

    public void setHttpMaxConnections(int httpMaxConnections) {
        this.httpMaxConnections = httpMaxConnections;
    }

    public int getHttpConnectionsPerHost() {
        return httpConnectionsPerHost;
    }

    public void setHttpConnectionsPerHost(int httpConnectionsPerHost) {
        this.httpConnectionsPerHost = httpConnectionsPerHost;
    }

    public int getHttpPooledConnectionIdleTimeout() {
        return httpPooledConnectionIdleTimeout;
    }

    public void setHttpPooledConnectionIdleTimeout(int httpPooledConnectionIdleTimeout) {
        this.httpPooledConnectionIdleTimeout = httpPooledConnectionIdleTimeout;
    }

    public String getDefaultBufferType() {
        return defaultBufferType;
    }

    public void setDefaultBufferType(String defaultBufferType) {
        this.defaultBufferType = defaultBufferType;
    }

    public String getParallelBufferType() {
        return parallelBufferType;
    }

    public void setParallelBufferType(String parallelBufferType) {
        this.parallelBufferType = parallelBufferType;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public int getProcessThread() {
        return processThread;
    }

    public void setProcessThread(int processThread) {
        this.processThread = processThread;
    }

    public void setWaitStrategy(String waitStrategy) {
        this.waitStrategy = waitStrategy;
    }

    /**
     * strategy pattern
     *
     * @return
     */
    public WaitStrategy getWaitStrategy(){
        switch (waitStrategy){
            case "blocking":
                return  new BlockingWaitStrategy();
            case "busySpin":
                return  new BusySpinWaitStrategy();
            case "yielding":
                return  new YieldingWaitStrategy();
            case "sleeping":
                return  new SleepingWaitStrategy();
            default:
                return new BlockingWaitStrategy();
        }
    }
}
