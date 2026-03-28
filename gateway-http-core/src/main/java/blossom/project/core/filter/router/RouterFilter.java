package blossom.project.core.filter.router;

import blossom.project.common.config.Rule;
import blossom.project.common.enums.ResponseCode;
import blossom.project.common.exception.ConnectException;
import blossom.project.common.exception.ResponseException;
import blossom.project.core.ConfigLoader;
import blossom.project.core.context.GatewayContext;
import blossom.project.core.filter.Filter;
import blossom.project.core.filter.FilterAspect;
import blossom.project.core.helper.AsyncHttpHelper;
import blossom.project.core.helper.ResponseHelper;
import blossom.project.core.response.GatewayResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.apache.commons.lang3.StringUtils;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static blossom.project.common.constant.FilterConst.*;

@FilterAspect(id = ROUTER_FILTER_ID, name = ROUTER_FILTER_NAME, order = ROUTER_FILTER_ORDER)
public class RouterFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(RouterFilter.class);
    private static final Logger accessLog = LoggerFactory.getLogger("accessLog");

    /**
     * 缓存每个服务的 CircuitBreaker 实例，同一个服务共享同一个断路器。
     * key = serviceUniqueId, value = CircuitBreaker instance
     * 如果每次请求都 new 一个，单个实例只经历一次调用，永远不会累积失败率，熔断永远不会触发。
     */
    private static final ConcurrentHashMap<String, CircuitBreaker> circuitBreakerCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, TimeLimiter> timeLimiterCache = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService timeLimiterScheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "gw-timelimiter");
                t.setDaemon(true);
                return t;
            });

    @Override
    public void doFilter(GatewayContext gatewayContext) throws Exception {
        //首先获取熔断降级的配置
        Optional<Rule.HystrixConfig> circuitBreakerConfig = getCircuitBreakerConfig(gatewayContext);
        //如果存在对应配置就走熔断降级的逻辑
        if (circuitBreakerConfig.isPresent()) {
            routeWithCircuitBreaker(gatewayContext, circuitBreakerConfig);
        } else {
            route(gatewayContext, circuitBreakerConfig);
        }
    }

    /**
     * 获取circuit breaker的配置
     *
     * @param gatewayContext
     * @return
     */
    private static Optional<Rule.HystrixConfig> getCircuitBreakerConfig(GatewayContext gatewayContext) {
        Rule rule = gatewayContext.getRule();
        Optional<Rule.HystrixConfig> circuitBreakerConfig =
                rule.getHystrixConfigs().stream().filter(c -> StringUtils.equals(c.getPath(),
                        gatewayContext.getRequest().getPath())).findFirst();
        return circuitBreakerConfig;
    }

    /**
     * 正常异步路由逻辑
     *whenComplete方法:
     *
     * whenComplete是一个非异步的完成方法。
     * 当CompletableFuture的执行完成或者发生异常时，它提供了一个回调。
     * 这个回调将在CompletableFuture执行的相同线程中执行。这意味着，如果CompletableFuture的操作是阻塞的，那么回调也会在同一个阻塞的线程中执行。
     * 在这段代码中，如果whenComplete为true，则在future完成时使用whenComplete方法。这意味着complete方法将在future所在的线程中被调用。
     * whenCompleteAsync方法:
     *
     * whenCompleteAsync是异步的完成方法。
     * 它也提供了一个在CompletableFuture执行完成或者发生异常时执行的回调。
     * 与whenComplete不同，这个回调将在不同的线程中异步执行。通常情况下，它将在默认的ForkJoinPool中的某个线程上执行，除非提供了自定义的Executor。
     * 在代码中，如果whenComplete为false，则使用whenCompleteAsync。这意味着complete方法将在不同的线程中异步执行。
     * @param gatewayContext
     * @param cbConfig 熔断配置（可选）
     * @return
     */
    private CompletableFuture<Response> route(GatewayContext gatewayContext,
                                              Optional<Rule.HystrixConfig> cbConfig) {
        Request request = gatewayContext.getRequest().build();
        //执行具体的请求 并得到一个CompleatableFuture对象用于帮助我们执行后续的处理
        CompletableFuture<Response> future = AsyncHttpHelper.getInstance().executeRequest(request);
        boolean whenComplete = ConfigLoader.getConfig().isWhenComplete();
        if (whenComplete) {
            future.whenComplete((response, throwable) -> {
                complete(request, response, throwable, gatewayContext, cbConfig);
            });
        } else {
            future.whenCompleteAsync((response, throwable) -> {
                complete(request, response, throwable, gatewayContext, cbConfig);
            });
        }
        return future;
    }

    /**
     * 根据提供的GatewayContext和Resilience4j配置，执行路由操作，并在熔断时执行降级逻辑。
     * 熔断会发生在：
     * 当 CircuitBreaker 命令的执行时间超过配置的超时时间。
     * 当 CircuitBreaker 命令的执行出现异常或错误。
     * 当连续请求失败率达到配置的阈值。
     * @param gatewayContext
     * @param circuitBreakerConfig
     */
    private void routeWithCircuitBreaker(GatewayContext gatewayContext, Optional<Rule.HystrixConfig> circuitBreakerConfig) {
        Rule.HystrixConfig config = circuitBreakerConfig.get();
        String uniqueId = gatewayContext.getUniqueId();

        // 从缓存获取或创建 CircuitBreaker 实例（同一个服务共享，才能累积失败率触发熔断）
        CircuitBreaker circuitBreaker = circuitBreakerCache.computeIfAbsent(uniqueId, id -> {
            CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                    .failureRateThreshold((float) config.getFailureRateThreshold())
                    .waitDurationInOpenState(Duration.ofSeconds(config.getWaitDurationInOpenState()))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .minimumNumberOfCalls(5)
                    .build();
            return CircuitBreaker.of(id, cbConfig);
        });

        // 从缓存获取或创建 TimeLimiter 实例
        TimeLimiter timeLimiter = timeLimiterCache.computeIfAbsent(uniqueId, id -> {
            TimeLimiterConfig tlConfig = TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofMillis(config.getTimeoutInMilliseconds()))
                    .cancelRunningFuture(true)
                    .build();
            return TimeLimiter.of(id + "-tl", tlConfig);
        });

        try {
            // Fast-fail: if circuit breaker is OPEN, reject immediately without making the call
            if (!circuitBreaker.tryAcquirePermission()) {
                handleCircuitBreakerFailure(gatewayContext, circuitBreakerConfig,
                        CallNotPermittedException.createCallNotPermittedException(circuitBreaker));
                return;
            }

            long startNanos = System.nanoTime();

            // Execute route asynchronously (non-blocking)
            CompletableFuture<Response> routeFuture = route(gatewayContext, circuitBreakerConfig);

            // Apply TimeLimiter for timeout protection (non-blocking)
            timeLimiter.executeCompletionStage(timeLimiterScheduler, () -> routeFuture)
                    .toCompletableFuture()
                    .whenComplete((response, throwable) -> {
                        long durationNanos = System.nanoTime() - startNanos;
                        if (throwable != null) {
                            // Record failure in circuit breaker
                            circuitBreaker.onError(durationNanos, TimeUnit.NANOSECONDS, throwable);
                            handleCircuitBreakerFailure(gatewayContext, circuitBreakerConfig, throwable);
                        } else {
                            // Record success in circuit breaker
                            circuitBreaker.onSuccess(durationNanos, TimeUnit.NANOSECONDS);
                        }
                    });

        } catch (CallNotPermittedException e) {
            // Circuit breaker is open, use fallback
            handleCircuitBreakerFailure(gatewayContext, circuitBreakerConfig, e);
        } catch (Exception e) {
            // Other exceptions
            handleCircuitBreakerFailure(gatewayContext, circuitBreakerConfig, e);
        }
    }

    /**
     * Handle circuit breaker failures and execute fallback logic
     */
    private void handleCircuitBreakerFailure(GatewayContext gatewayContext,
                                            Optional<Rule.HystrixConfig> circuitBreakerConfig,
                                            Throwable throwable) {
        // Check if it's caused by timeout
        if (throwable instanceof TimeoutException) {
            gatewayContext.setResponse(GatewayResponse.buildGatewayResponse(
                    ResponseCode.GATEWAY_FALLBACK));
        } else {
            // Other types of circuit breaking
            String fallbackResp = circuitBreakerConfig.get().getFallbackResponse();
            if (fallbackResp == null) {
                gatewayContext.setResponse(GatewayResponse.buildGatewayResponse(ResponseCode.GATEWAY_FALLBACK));
            } else {
                gatewayContext.setResponse(fallbackResp);
            }
        }

        // IMPORTANT: Ensure we don't double-write if the main execution path also tries to write
        if (!gatewayContext.isWritten() && !gatewayContext.isCompleted()) {
            gatewayContext.written();
            ResponseHelper.writeResponse(gatewayContext);
        }
    }


    private void complete(Request request, Response response, Throwable throwable, GatewayContext gatewayContext,
                          Optional<Rule.HystrixConfig> cbConfig) {
        //请求已经处理完毕 释放请求资源
        gatewayContext.releaseRequest();
        //获取网关上下文规则
        Rule rule = gatewayContext.getRule();
        //获取请求重试次数
        int currentRetryTimes = gatewayContext.getCurrentRetryTimes();
        int confRetryTimes = rule.getRetryConfig().getTimes();
        //判断是否出现异常 如果是 进行重试
        if ((throwable instanceof TimeoutException || throwable instanceof IOException) &&
                currentRetryTimes <= confRetryTimes && !cbConfig.isPresent()) {
            //请求重试
            doRetry(gatewayContext, currentRetryTimes);
            return;
        }

        try {
            //之前出现了异常 执行异常返回逻辑
            if (Objects.nonNull(throwable)) {
                String url = request.getUrl();
                if (throwable instanceof TimeoutException) {
                    log.warn("complete time out {}", url);
                    gatewayContext.setThrowable(new ResponseException(ResponseCode.REQUEST_TIMEOUT));
                    gatewayContext.setResponse(GatewayResponse.buildGatewayResponse(ResponseCode.REQUEST_TIMEOUT));
                } else {
                    gatewayContext.setThrowable(new ConnectException(throwable, gatewayContext.getUniqueId(), url,
                            ResponseCode.HTTP_RESPONSE_ERROR));
                    gatewayContext.setResponse(GatewayResponse.buildGatewayResponse(ResponseCode.HTTP_RESPONSE_ERROR));
                }
            } else {
                //没有出现异常直接正常返回
                gatewayContext.setResponse(GatewayResponse.buildGatewayResponse(response));
            }
        } catch (Throwable t) {
            gatewayContext.setThrowable(new ResponseException(ResponseCode.INTERNAL_ERROR));
            gatewayContext.setResponse(GatewayResponse.buildGatewayResponse(ResponseCode.INTERNAL_ERROR));
            log.error("complete error", t);
        } finally {
            // 防止与 handleCircuitBreakerFailure 双写：只有首次写入才执行
            if (!gatewayContext.isWritten()) {
                gatewayContext.written();
                ResponseHelper.writeResponse(gatewayContext);
            }

            //增加日志记录（需要空指针保护：熔断/超时场景下 futureResponse 可能为 null）
            try {
                accessLog.info("{} {} {} {} {} {} {}",
                        System.currentTimeMillis() - gatewayContext.getRequest().getBeginTime(),
                        gatewayContext.getRequest().getClientIp(),
                        gatewayContext.getRequest().getUniqueId(),
                        gatewayContext.getRequest().getMethod(),
                        gatewayContext.getRequest().getPath(),
                        gatewayContext.getResponse().getHttpResponseStatus().code(),
                        gatewayContext.getResponse().getFutureResponse() != null
                                ? gatewayContext.getResponse().getFutureResponse().getResponseBodyAsBytes().length
                                : 0);
            } catch (Exception e) {
                // 日志记录失败不应影响请求处理
                log.debug("access log write failed", e);
            }
        }
    }


    private void doRetry(GatewayContext gatewayContext, int retryTimes) {
        log.debug("Current retry times: {}", retryTimes);
        gatewayContext.setCurrentRetryTimes(retryTimes + 1);
        try {
            //调用路由过滤器方法再次进行请求重试
            doFilter(gatewayContext);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
