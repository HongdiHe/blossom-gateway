package blossom.project.core.request;

import org.asynchttpclient.Request;
import io.netty.handler.codec.http.cookie.Cookie;

public interface IGatewayRequest {

    /**
     * Set the modified host name.
     *
     * @param host The new host name to be set.
     */
    void setModifyHost(String host);

    /**
     * Get the modified host name.
     *
     * @return The modified host name.
     */
    String getModifyHost();

    /**
     * Set the modified path.
     *
     * @param path The new path to be set.
     */
    void setModifyPath(String path);

    /**
     * Get the modified path.
     *
     * @return The modified path.
     */
    String getModifyPath();

    /**
     * Add a header to the request.
     *
     * @param name  The name of the header (CharSequence).
     * @param value The value of the header.
     */
    void addHeader(CharSequence name, String value);

    /**
     * Set a header value for the request. If the header already exists, it will update the value;
     * if not, it will add the header.
     *
     * @param name  The name of the header (CharSequence).
     * @param value The value of the header.
     */
    void setHeader(CharSequence name, String value);

    /**
     * Add a query parameter for a GET request.
     *
     * @param name  The name of the query parameter.
     * @param value The value of the query parameter.
     */
    void addQueryParam(String name, String value);

    /**
     * Add a form parameter for a POST request.
     *
     * @param name  The name of the form parameter.
     * @param value The value of the form parameter.
     */
    void addFormParam(String name, String value);

    /**
     * Add a new cookie or replace an existing cookie in the request.
     *
     * @param cookie The Cookie object to be added or replaced.
     */
    void addOrReplaceCookie(Cookie cookie);

    /**
     * Set the request timeout value in milliseconds.
     *
     * @param requestTimeout The timeout value in milliseconds.
     */
    void setRequestTimeout(int requestTimeout);

    /**
     * Get the final request URL by combining the modified scheme, host, and path.
     *
     * @return The final request URL as a string.
     */
    String getFinalUrl();

    /**
     * Build the final Request object with all the configured parameters (headers, parameters, cookies, etc.).
     *
     * @return The constructed Request object.
     */
    Request build();
}