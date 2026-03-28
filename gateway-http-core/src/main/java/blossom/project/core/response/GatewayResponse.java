package blossom.project.core.response;

import blossom.project.common.enums.ResponseCode;
import blossom.project.common.utils.JSONUtil;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.codec.http.*;
import org.asynchttpclient.Response;


public class GatewayResponse {

    private HttpHeaders responseHeaders = new DefaultHttpHeaders();

    private final HttpHeaders extraResponseHeaders = new DefaultHttpHeaders();

    private String content;

    /**
     * Asynchronous return object
     */
    private Response futureResponse;

    private HttpResponseStatus httpResponseStatus;


    public GatewayResponse() {

    }

    public HttpHeaders getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(HttpHeaders responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public HttpHeaders getExtraResponseHeaders() {
        return extraResponseHeaders;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Response getFutureResponse() {
        return futureResponse;
    }

    public void setFutureResponse(Response futureResponse) {
        this.futureResponse = futureResponse;
    }

    public HttpResponseStatus getHttpResponseStatus() {
        return httpResponseStatus;
    }

    public void setHttpResponseStatus(HttpResponseStatus httpResponseStatus) {
        this.httpResponseStatus = httpResponseStatus;
    }

    public void putHeader(CharSequence key, CharSequence val) {
        responseHeaders.add(key, val);
    }

    public static GatewayResponse buildGatewayResponse(Response futureResponse) {
        GatewayResponse response = new GatewayResponse();
        response.setFutureResponse(futureResponse);
        response.setHttpResponseStatus(HttpResponseStatus.valueOf(futureResponse.getStatusCode()));
        return response;
    }

    public static GatewayResponse buildGatewayResponse(ResponseCode code, Object... args) {
        ObjectNode objectNode = JSONUtil.createObjectNode();
        objectNode.put(JSONUtil.STATUS, code.getStatus().code());
        objectNode.put(JSONUtil.CODE, code.getCode());
        objectNode.put(JSONUtil.MESSAGE, code.getMessage());

        GatewayResponse response = new GatewayResponse();
        response.setHttpResponseStatus(code.getStatus());
        response.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");
        response.setContent(JSONUtil.toJSONString(objectNode));

        return response;
    }

    public static GatewayResponse buildGatewayResponse(Object data) {
        ObjectNode objectNode = JSONUtil.createObjectNode();

        if (data instanceof ResponseCode) {
            ResponseCode code = (ResponseCode) data;
            objectNode.put(JSONUtil.STATUS, code.getStatus().code());
            objectNode.put(JSONUtil.CODE, code.getCode());
            objectNode.putPOJO(JSONUtil.DATA, code.getMessage());
        } else {

            objectNode.put(JSONUtil.STATUS, ResponseCode.SUCCESS.getStatus().code());
            objectNode.put(JSONUtil.CODE, ResponseCode.SUCCESS.getCode());
            objectNode.putPOJO(JSONUtil.DATA, data);
        }


        GatewayResponse response = new GatewayResponse();
        response.setHttpResponseStatus(ResponseCode.SUCCESS.getStatus());
        response.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON + ";charset=utf-8");
        response.setContent(JSONUtil.toJSONString(objectNode));
        return response;
    }

}
