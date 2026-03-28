package blossom.project.core.filter.auth;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import blossom.project.common.enums.ResponseCode;
import blossom.project.common.exception.ResponseException;
import blossom.project.core.context.GatewayContext;
import blossom.project.core.filter.Filter;
import blossom.project.core.filter.FilterAspect;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.impl.DefaultClaims;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

import static blossom.project.common.constant.FilterConst.*;

@FilterAspect(id = AUTH_FILTER_ID, name = AUTH_FILTER_NAME, order = AUTH_FILTER_ORDER)
public class AuthFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);

    /**
     * 加密密钥
     */
    private static final String SECRET_KEY = System.getenv().getOrDefault("GATEWAY_JWT_SECRET", "default-secret-change-in-production");

    /**
     * Cookie key. Obtain this key from the corresponding cookie. What is stored is our token information.
     */
    private static final String COOKIE_NAME = "blossomgateway-jwt";

    @Override
    public void doFilter(GatewayContext ctx) throws Exception {
        if (ctx.getRule().getFilterConfig(AUTH_FILTER_ID) == null) {
            return;
        }
        Optional<String> cookieValue = Optional.ofNullable(ctx.getRequest().getCookie(COOKIE_NAME)).map(cookie -> cookie.value());

        String token = cookieValue.orElse(null);
        if (StringUtils.isBlank(token)) {
            throw new ResponseException(ResponseCode.UNAUTHORIZED);
        }

        try {
            long userId = parseUserId(token);
            ctx.getRequest().setUserId(userId);
        } catch (Exception e) {
            throw new ResponseException(ResponseCode.UNAUTHORIZED);
        }

    }

    private long parseUserId(String token) {
        Jwt jwt = Jwts.parser().setSigningKey(SECRET_KEY).parse(token);
        return Long.parseLong(((DefaultClaims) jwt.getBody()).getSubject());
    }
}
