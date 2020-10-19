package com.jpmc.sagemaker.studio.utils;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Utility Methods useful in the HTTP Context
 */
public class HttpUtils {

    private static final String HTTP_HEADERS_SERVER = "Server";
    private static final String HTTP_HEADERS_SEC_WEBSOCKET_EXTENSIONS = "permessage-deflate";

    /**
     * Util method to convert the HTTPS SET-COOKIE cookies to HTTP SET-COOKIE
     * cookies ClientCookieDecoder is used to decode the cookies and
     * ServerCookieEncoder is used to encode the cookies
     * 
     * @param response HttpResponse Object
     */
    public static void convertHttpsToHttpCookie(final FullHttpResponse response) {
        final List<String> setCookies = response.headers().getAll(HttpHeaderNames.SET_COOKIE);
        if (setCookies != null && !setCookies.isEmpty()) {
            response.headers().remove(HttpHeaderNames.SET_COOKIE);
            final List<Cookie> httpCookies = new LinkedList<>();
            for (String cookieS : setCookies) {
                final Cookie secureCookie = ClientCookieDecoder.STRICT.decode(cookieS);
                final Cookie httpCookie = createHttpCookieFromSecureCookie(secureCookie);
                if (httpCookie != null)
                    httpCookies.add(httpCookie);
            }
            for (Cookie cookie : httpCookies) {
                response.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
            }
        }
    }

    /**
     * Util method to create ResponseHeaders from RequestHeaders ServerCookieDecoder
     * is used to decode the cookies and ServerCookieEncoder is used to encode the
     * cookies
     * 
     * @param requestHeaders HttpHeaders Object
     * @return Response HTTPHeaders Object
     */
    public static HttpHeaders createResponseHeadersFromRequestHeaders(final HttpHeaders requestHeaders) {
        final DefaultHttpHeaders responseHeaders = new DefaultHttpHeaders();
        final List<String> cookies = requestHeaders.getAll(HttpHeaderNames.COOKIE);

        if (cookies != null && !cookies.isEmpty()) {
            final List<Cookie> httpCookies = new LinkedList<>();

            for (String cookieS : cookies) {
                final Set<Cookie> cookieSet = ServerCookieDecoder.STRICT.decode(cookieS);
                for (Cookie cookie : cookieSet) {
                    final Cookie httpCookie = createHttpCookieFromSecureCookie(cookie);
                    if (httpCookie != null)
                        httpCookies.add(httpCookie);
                }
            }
            for (Cookie cookie : httpCookies) {
                responseHeaders.add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
            }
        }

        responseHeaders.add(HttpHeaderNames.SERVER, HTTP_HEADERS_SERVER);
        responseHeaders.add(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS, HTTP_HEADERS_SEC_WEBSOCKET_EXTENSIONS);

        return responseHeaders;
    }

    private static Cookie createHttpCookieFromSecureCookie(final Cookie secureCookie) {
        if (!secureCookie.name().equals("Path") || secureCookie.value() != null) {
            final Cookie cookieOutput = new DefaultCookie(secureCookie.name(), secureCookie.value());
            cookieOutput.setHttpOnly(true);
            cookieOutput.setPath("/");
            return cookieOutput;
        }
        return null;
    }
}
