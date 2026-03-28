package blossom.project.core.netty.processor;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import com.lmax.disruptor.dsl.ProducerType;

import blossom.project.common.enums.ResponseCode;
import blossom.project.core.Config;
import blossom.project.core.context.HttpRequestWrapper;
import blossom.project.core.disruptor.EventListener;
import blossom.project.core.disruptor.ParallelQueueHandler;
import blossom.project.core.helper.ResponseHelper;

/**
 * DisruptorNettyCoreProcessor is a Netty processor that uses Disruptor to improve performance.
 * This processor acts as a caching layer, asynchronously processing HTTP requests through Disruptor
 * to reduce the burden on the Netty core processor.
 */
public class DisruptorNettyCoreProcessor implements NettyProcessor {
    private static final Logger log = LoggerFactory.getLogger(DisruptorNettyCoreProcessor.class);


    /**
     * The prefix for the thread name.
     */
    private static final String THREAD_NAME_PREFIX = "gateway-queue-";

    private Config config;

    /**
     * Although Disruptor serves as a cache, the Netty core processor is still required for processing.
     */
    private NettyCoreProcessor nettyCoreProcessor;

    /**
     * The handler class for processing requests using Disruptor.
     */
    private ParallelQueueHandler<HttpRequestWrapper> parallelQueueHandler;

    /**
     * Constructor to initialize the DisruptorNettyCoreProcessor.
     *
     * @param config             The configuration object.
     * @param nettyCoreProcessor The Netty core processor instance.
     */
    public DisruptorNettyCoreProcessor(Config config, NettyCoreProcessor nettyCoreProcessor) {
        this.config = config;
        this.nettyCoreProcessor = nettyCoreProcessor;

        // Create and configure the processing queue using Disruptor.
        ParallelQueueHandler.Builder<HttpRequestWrapper> builder = new ParallelQueueHandler.Builder<HttpRequestWrapper>()
                .setBufferSize(config.getBufferSize())
                .setThreads(config.getProcessThread())
                .setProducerType(ProducerType.MULTI)
                .setNamePrefix(THREAD_NAME_PREFIX)
                .setWaitStrategy(config.getWaitStrategy());

        // Set the event listener processing class.
        BatchEventListenerProcessor batchEventListenerProcessor = new BatchEventListenerProcessor();
        builder.setListener(batchEventListenerProcessor);
        this.parallelQueueHandler = builder.build();
    }

    /**
     * Process the HTTP request by adding it to the Disruptor processing queue.
     *
     * @param wrapper The HttpRequestWrapper object that wraps the HTTP request.
     */
    @Override
    public void process(HttpRequestWrapper wrapper) {
        this.parallelQueueHandler.add(wrapper);
    }

    /**
     * The event listener processing class that handles events fetched from the Disruptor processing queue.
     */
    public class BatchEventListenerProcessor implements EventListener<HttpRequestWrapper> {

        @Override
        public void onEvent(HttpRequestWrapper event) {
            // Use the Netty core processor to handle the event.
            nettyCoreProcessor.process(event);
        }

        @Override
        public void onException(Throwable ex, long sequence, HttpRequestWrapper event) {
            HttpRequest request = event.getRequest();
            ChannelHandlerContext ctx = event.getCtx();

            try {
                log.error("BatchEventListenerProcessor onException: Failed to write back the request, request:{}, errMsg:{} ", request, ex.getMessage(), ex);

                // Construct the response object.
                FullHttpResponse fullHttpResponse = ResponseHelper.getHttpResponse(ResponseCode.INTERNAL_ERROR);

                if (!HttpUtil.isKeepAlive(request)) {
                    ctx.writeAndFlush(fullHttpResponse).addListener(ChannelFutureListener.CLOSE);
                } else {
                    fullHttpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                    ctx.writeAndFlush(fullHttpResponse);
                }
            } catch (Exception e) {
                log.error("BatchEventListenerProcessor onException: Failed to write back the request, request:{}, errMsg:{} ", request, e.getMessage(), e);
            }
        }
    }

    /**
     * Start the DisruptorNettyCoreProcessor, which starts the processing queue.
     */
    @Override
    public void start() {
        parallelQueueHandler.start();
    }

    /**
     * Shut down the DisruptorNettyCoreProcessor, which closes the processing queue.
     */
    @Override
    public void shutDown() {
        parallelQueueHandler.shutDown();
    }
}