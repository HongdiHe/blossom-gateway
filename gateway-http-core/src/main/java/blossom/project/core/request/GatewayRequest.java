package blossom.project.core.request;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import blossom.project.common.constant.BasicConst;
import blossom.project.common.utils.TimeUtil;
import com.google.common.collect.Lists;
import com.jayway.jsonpath.JsonPath;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.apache.commons.lang3.StringUtils;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;

import java.nio.charset.Charset;
import java.util.*;

public class GatewayRequest implements IGatewayRequest {
    private static final Logger log = LoggerFactory.getLogger(GatewayRequest.class);


    /**
     * The unique ID of the service.
     */
    private String uniqueId;

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    /**
     * The time when the request enters the gateway.
     */
    private final long beginTime;

    /**
     * The character set which won't change.
     */
    private final Charset charset;

    /**
     * The client's IP address, mainly used for flow control and black/white lists.
     */
    private final String clientIp;

    /**
     * The address of the request: IP:port.
     */
    private final String host;

    /**
     * The path of the request: /XXX/XXX/XX.
     */
    private final String path;

    /**
     * URI: Uniform Resource Identifier, /XXX/XXX/XXX?attr1=value&attr2=value2.
     * URL: Uniform Resource Locator, which is just a subset implementation of URI.
     */
    private final String uri;

    /**
     * The HTTP method of the request, such as POST, PUT, GET.
     */
    private final HttpMethod method;

    /**
     * The format of the request.
     */
    private final String contentType;

    /**
     * The header information of the request.
     */
    private final HttpHeaders headers;

    /**
     * The query string decoder for parsing parameters.
     */
    private final QueryStringDecoder queryStringDecoder;

    /**
     * The FullHttpRequest object.
     */
    private final FullHttpRequest fullHttpRequest;

    /**
     * The body of the request.
     */
    private String body;

    private long userId;

    /**
     * The map of request cookies.
     */
    private Map<String, io.netty.handler.codec.http.cookie.Cookie> cookieMap;

    /**
     * The parameter combination defined for POST requests.
     */
    private Map<String, List<String>> postParameters;

    /****** Modifiable request variables ***************************************/
    /**
     * The modifiable scheme, default is http://.
     */
    private String modifyScheme;

    private String modifyHost;

    private String modifyPath;

    /**
     * The HTTP request builder for constructing downstream requests.
     */
    private final RequestBuilder requestBuilder;

    /**
     * Constructor.
     *
     * @param uniqueId        The unique ID of the service.
     * @param charset         The character set.
     * @param clientIp        The client's IP address.
     * @param host            The address of the request.
     * @param uri             The URI of the request.
     * @param method          The HTTP method of the request.
     * @param contentType     The format of the request.
     * @param headers         The header information of the request.
     * @param fullHttpRequest The FullHttpRequest object.
     */
    public GatewayRequest(String uniqueId, Charset charset, String clientIp, String host, String uri, HttpMethod method, String contentType, HttpHeaders headers, FullHttpRequest fullHttpRequest) {
        this.uniqueId = uniqueId;
        this.beginTime = TimeUtil.currentTimeMillis();
        this.charset = charset;
        this.clientIp = clientIp;
        this.host = host;
        this.uri = uri;
        this.method = method;
        this.contentType = contentType;
        this.headers = headers;
        this.fullHttpRequest = fullHttpRequest;
        this.queryStringDecoder = new QueryStringDecoder(uri, charset);
        this.path = queryStringDecoder.path();
        this.modifyHost = host;
        this.modifyPath = path;

        this.modifyScheme = BasicConst.HTTP_PREFIX_SEPARATOR;
        this.requestBuilder = new RequestBuilder();
        this.requestBuilder.setMethod(getMethod().name());
        this.requestBuilder.setHeaders(getHeaders());
        this.requestBuilder.setQueryParams(queryStringDecoder.parameters());

        ByteBuf contentBuffer = fullHttpRequest.content();
        if (Objects.nonNull(contentBuffer)) {
            this.requestBuilder.setBody(contentBuffer.nioBuffer());
        }
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public long getBeginTime() {
        return beginTime;
    }

    public Charset getCharset() {
        return charset;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getHost() {
        return host;
    }

    public String getPath() {
        return path;
    }

    public String getUri() {
        return uri;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public String getContentType() {
        return contentType;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public QueryStringDecoder getQueryStringDecoder() {
        return queryStringDecoder;
    }

    public FullHttpRequest getFullHttpRequest() {
        return fullHttpRequest;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public Map<String, io.netty.handler.codec.http.cookie.Cookie> getCookieMap() {
        return cookieMap;
    }

    public Map<String, List<String>> getPostParameters() {
        return postParameters;
    }

    /**
     * Get the body of the request.
     * If the body is empty, it will be retrieved from the fullHttpRequest content.
     *
     * @return The body of the request.
     */
    public String getBody() {
        if (StringUtils.isEmpty(body)) {
            body = fullHttpRequest.content().toString(charset);
        }
        return body;
    }

    /**
     * Get a specific cookie by its name.
     * If the cookie map hasn't been initialized, it will be initialized first.
     *
     * @param name The name of the cookie.
     * @return The cookie object if found, otherwise null.
     */
    public io.netty.handler.codec.http.cookie.Cookie getCookie(String name) {
        if (cookieMap == null) {
            cookieMap = new HashMap<String, io.netty.handler.codec.http.cookie.Cookie>();
            String cookieStr = getHeaders().get(HttpHeaderNames.COOKIE);
            if (StringUtils.isBlank(cookieStr)) {
                return null;
            }
            Set<io.netty.handler.codec.http.cookie.Cookie> cookies = ServerCookieDecoder.STRICT.decode(cookieStr);
            for (io.netty.handler.codec.http.cookie.Cookie cookie : cookies) {
                cookieMap.put(cookie.name(), cookie); // Use cookie.name() instead of the passed name here to store correctly
            }
        }
        return cookieMap.get(name);
    }

    /**
     * Get multiple values of a specific query parameter by its name.
     *
     * @param name The name of the query parameter.
     * @return A list of parameter values if found, otherwise null.
     */
    public List<String> getQueryParametersMultiple(String name) {
        return queryStringDecoder.parameters().get(name);
    }

    /**
     * Get multiple values of a specific parameter for a POST request by its name.
     * It will handle different types of POST requests (form data or JSON).
     *
     * @param name The name of the parameter.
     * @return A list of parameter values if found, otherwise null.
     */
    public List<String> getPostParametersMultiples(String name) {
        String body = getBody();
        if (isFormPost()) {
            if (postParameters == null) {
                QueryStringDecoder paramDecoder = new QueryStringDecoder(body, false);
                postParameters = paramDecoder.parameters();
            }
            if (postParameters == null || postParameters.isEmpty()) {
                return null;
            } else {
                return postParameters.get(name);
            }
        } else if (isJsonPost()) {
            try {
                return Lists.newArrayList(JsonPath.read(body, name).toString());
            } catch (Exception e) {
                log.error("JsonPath parsing failed, JsonPath:{},Body:{},", name, body, e);
            }
        }
        return null;
    }

    @Override
    public void setModifyHost(String modifyHost) {
        this.modifyHost = modifyHost;
    }

    @Override
    public String getModifyHost() {
        return modifyHost;
    }

    @Override
    public void setModifyPath(String modifyPath) {
        this.modifyPath = modifyPath;
    }

    @Override
    public String getModifyPath() {
        return modifyPath;
    }

    @Override
    public void addHeader(CharSequence name, String value) {
        requestBuilder.addHeader(name, value);
    }

    @Override
    public void setHeader(CharSequence name, String value) {
        requestBuilder.setHeader(name, value);
    }

    @Override
    public void addQueryParam(String name, String value) {
        requestBuilder.addQueryParam(name, value);
    }

    @Override
    public void addFormParam(String name, String value) {
        if (isFormPost()) {
            requestBuilder.addFormParam(name, value);
        }
    }

    @Override
    public void addOrReplaceCookie(io.netty.handler.codec.http.cookie.Cookie cookie) {
        requestBuilder.addOrReplaceCookie(cookie);
    }

    @Override
    public void setRequestTimeout(int requestTimeout) {
        requestBuilder.setRequestTimeout(requestTimeout);
    }

    @Override
    public String getFinalUrl() {
        return modifyScheme + modifyHost + modifyPath;
    }

    @Override
    public Request build() {
        requestBuilder.setUrl(getFinalUrl());
        // Set the user ID for downstream services to use.
        requestBuilder.addHeader("userId", String.valueOf(userId));
        return requestBuilder.build();
    }

    /**
     * Check if the request is a form-based POST request.
     * It checks the HTTP method and the content type.
     *
     * @return true if it's a form-based POST request, otherwise false.
     */
    public boolean isFormPost() {
        return HttpMethod.POST.equals(method) &&
                (contentType.startsWith(HttpHeaderValues.FORM_DATA.toString()) ||
                        contentType.startsWith(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString()));
    }

    /**
     * Check if the request is a JSON-based POST request.
     * It checks the HTTP method and the content type.
     *
     * @return true if it's a JSON-based POST request, otherwise false.
     */
    public boolean isJsonPost() {
        return HttpMethod.POST.equals(method) && contentType.startsWith(HttpHeaderValues.APPLICATION_JSON.toString());
    }
}
