package blossom.project.core.helper;

import blossom.project.common.config.*;
import blossom.project.common.constant.BasicConst;
import blossom.project.common.constant.GatewayConst;
import blossom.project.common.exception.ResponseException;
import blossom.project.core.DynamicConfigManager;
import blossom.project.core.context.GatewayContext;
import blossom.project.core.request.GatewayRequest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static blossom.project.common.enums.ResponseCode.PATH_NO_MATCHED;

public class RequestHelper {
    /**
     * Create and initialize a GatewayContext object based on the given FullHttpRequest and ChannelHandlerContext.
     * This method builds a GatewayRequest, retrieves the service definition, sets up the service invoker,
     * fetches the relevant rule, and finally constructs the GatewayContext.
     *
     * @param request The FullHttpRequest object representing the incoming HTTP request.
     * @param ctx     The ChannelHandlerContext for the current channel.
     * @return A fully initialized GatewayContext object.
     */
    public static GatewayContext doContext(FullHttpRequest request, ChannelHandlerContext ctx) {

        // Build the GatewayRequest object
        GatewayRequest gateWayRequest = doRequest(request, ctx);

        // Retrieve the service definition information (ServiceDefinition) based on the uniqueId in the request object
        String uniqueId = gateWayRequest.getUniqueId();
        ServiceDefinition serviceDefinition = null;

        if (uniqueId != null) {
            serviceDefinition = DynamicConfigManager.getInstance().getServiceDefinition(uniqueId);
        } else {
            // Fallback: try to find matching rule by path
            for (Rule rule : DynamicConfigManager.getInstance().getRuleMap().values()) {
                boolean matched = false;
                if (rule.getPaths() != null && rule.getPaths().contains(gateWayRequest.getPath())) {
                    matched = true;
                } else if (StringUtils.isNotEmpty(rule.getPrefix()) && gateWayRequest.getPath().startsWith(rule.getPrefix())) {
                    matched = true;
                }

                if (matched) {
                    // Find service definition by serviceId
                    for (ServiceDefinition sd : DynamicConfigManager.getInstance().getServiceDefinitionMap().values()) {
                        if (sd.getServiceId().equals(rule.getServiceId())) {
                            serviceDefinition = sd;
                            break;
                        }
                    }
                    if (serviceDefinition != null) {
                        break;
                    }
                }
            }
        }

        if (serviceDefinition == null) {
            throw new ResponseException(PATH_NO_MATCHED);
        }

        // Ensure request has the correct uniqueId (if it was null)
        if (gateWayRequest.getUniqueId() == null) {
            gateWayRequest.setUniqueId(serviceDefinition.getUniqueId());
        }

        // Set up the service invoker according to the request object and get the corresponding rule
        ServiceInvoker serviceInvoker = new HttpServiceInvoker();
        serviceInvoker.setInvokerPath(gateWayRequest.getPath());
        serviceInvoker.setTimeout(500);

        // Fetch the rule based on the request object and the service ID of the service definition
        Rule rule = getRule(gateWayRequest, serviceDefinition.getServiceId());

        // Construct the GatewayContext object
        GatewayContext gatewayContext = new GatewayContext(serviceDefinition.getProtocol(), ctx,
                HttpUtil.isKeepAlive(request), gateWayRequest, rule, 0);

        // Once the service discovery is completed later, this part needs to be made dynamic - and also in the load balancing algorithm implementation
        // gatewayContext.getRequest().setModifyHost("127.0.0.1:8080");

        return gatewayContext;
    }

    /**
     * Build a GatewayRequest object from the given FullHttpRequest and ChannelHandlerContext.
     * This method extracts necessary information such as uniqueId, host, method, uri, clientIp, contentType, and charset
     * from the request and related context to create the GatewayRequest.
     *
     * @param fullHttpRequest The FullHttpRequest object.
     * @param ctx             The ChannelHandlerContext for the current channel.
     * @return A constructed GatewayRequest object.
     */
    private static GatewayRequest doRequest(FullHttpRequest fullHttpRequest, ChannelHandlerContext ctx) {

        HttpHeaders headers = fullHttpRequest.headers();
        // Extract the essential uniqueId attribute from the header
        String uniqueId = headers.get(GatewayConst.UNIQUE_ID);

        String host = headers.get(HttpHeaderNames.HOST);
        HttpMethod method = fullHttpRequest.method();
        String uri = fullHttpRequest.uri();
        String clientIp = getClientIp(ctx, fullHttpRequest);
        String contentType = HttpUtil.getMimeType(fullHttpRequest) == null ? null :
                HttpUtil.getMimeType(fullHttpRequest).toString();
        Charset charset = HttpUtil.getCharset(fullHttpRequest, StandardCharsets.UTF_8);

        GatewayRequest gatewayRequest = new GatewayRequest(uniqueId, charset, clientIp, host, uri, method,
                contentType, headers, fullHttpRequest);

        return gatewayRequest;
    }

    /**
     * Get the client's IP address.
     * First, it tries to get the IP from the "X-Forwarded-For" header. If not available there,
     * it retrieves the IP from the channel's remote address.
     *
     * @param ctx     The ChannelHandlerContext for the current channel.
     * @param request The FullHttpRequest object.
     * @return The client's IP address as a string.
     */
    private static String getClientIp(ChannelHandlerContext ctx, FullHttpRequest request) {
        String xForwardedValue = request.headers().get(BasicConst.HTTP_FORWARD_SEPARATOR);

        String clientIp = null;
        if (StringUtils.isNotEmpty(xForwardedValue)) {
            List<String> values = Arrays.asList(xForwardedValue.split(", "));
            if (values.size() >= 1 && StringUtils.isNotBlank(values.get(0))) {
                clientIp = values.get(0);
            }
        }
        if (clientIp == null) {
            InetSocketAddress inetSocketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            clientIp = inetSocketAddress.getAddress().getHostAddress();
        }
        return clientIp;
    }

    /**
     * Retrieve the Rule object based on the given GatewayRequest and service ID.
     * It first tries to get the rule by a specific key composed of service ID and request path.
     * If not found, it searches for a rule whose prefix matches the start of the request path.
     *
     * @param gateWayRequest The GatewayRequest object.
     * @param serviceId      The ID of the service.
     * @return The Rule object if found, otherwise throws a ResponseException.
     */
    private static Rule getRule(GatewayRequest gateWayRequest, String serviceId) {
        String key = serviceId + "." + gateWayRequest.getPath();
        Rule rule = DynamicConfigManager.getInstance().getRuleByPath(key);

        if (rule != null) {
            return rule;
        }
        return DynamicConfigManager.getInstance().getRuleByServiceId(serviceId).stream()
                .filter(r -> gateWayRequest.getPath().startsWith(r.getPrefix())).findAny()
                .orElseThrow(() -> new ResponseException(PATH_NO_MATCHED));
    }
}