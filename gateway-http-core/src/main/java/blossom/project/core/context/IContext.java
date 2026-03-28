package blossom.project.core.context;

import blossom.project.common.config.Rule;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.function.Consumer;

public interface IContext {

    /**
     * The status indicating that a request is currently being executed.
     */
    int RUNNING = 0;
    /**
     * The status indicating that the request has ended and the response is being written back.
     */
    int WRITTEN = 1;
    /**
     * The status set after the response is successfully written back. In the case of Netty, it is after ctx.WriteAndFlush(response).
     */
    int COMPLETED = 2;
    /**
     * The status indicating that the entire gateway request has been completed and ended completely.
     */
    int TERMINATED = -1;

    /**
     * set context running
     */
    void running();

    /**
     * set context written
     */
    void written();

    /**
     * set context completed
     */
    void completed();

    void terminated();

    boolean isRunning();

    boolean isWritten();

    boolean isCompleted();

    boolean isTerminated();

    String getProtocol();

    Rule getRule();

    Object getRequest();

    Object getResponse();

    Throwable getThrowable();

    Object getAttribute(Map<String, Object> key);

    void setRule();

    void setResponse();

    void setThrowable(Throwable throwable);

    void setAttribute(String key, Object obj);

    ChannelHandlerContext getNettyCtx();

    boolean isKeepAlive();

    void releaseRequest();

    void setCompletedCallBack(Consumer<IContext> consumer);

    void invokeCompletedCallBack();

}
