package com.jpmc.sagemaker.studio.reverseproxy;

import com.jpmc.sagemaker.studio.utils.BackendServerURLUtils;
import com.jpmc.sagemaker.studio.utils.NettyUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.util.Arrays;

/**
 * Channel Inbound Handler to process the HTTP request and send it to Downstream
 * Server
 */
@Log4j2
public class HttpRequestConnectionHandler extends ChannelInboundHandlerAdapter {

    private static final String UPGRADE = "Upgrade";
    private static final String WEBSOCKET = "WebSocket";
    private static final String WEBSOCKET_HANDLER = "webSocketRequestConnectionHandler";

    @Setter
    private Channel backendHttpChannel;

    @Override
    public void channelActive(final ChannelHandlerContext channelHandlerContext) {
        log.debug("HTTP Frontend Channel is Active");
        channelHandlerContext.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext channelHandlerContext, final Object message) throws Exception {
        if (!(message instanceof FullHttpRequest)) {
            throw new IllegalStateException("Something went wrong. Received a non-FullHttpRequest " + message);
        }

        final FullHttpRequest request = (FullHttpRequest) message;
        final HttpHeaders headers = request.headers();

        log.debug("HTTP Request Received: {}.", request);

        final String frontendWebsocketURL = getWebSocketURL(request);

        // HTTP Request to Sagemaker Presigned URL was failing due to Host pointing to
        // Proxy URL endpoint.
        // It should be same as Sagemaker's Presigned URL endpoint. Services usually
        // reject requests(400 Bad Request) having
        // an inappropriate Host: header as this is often a sign of an SSL
        // man-in-the-middle attack.
        // https://stackoverflow.com/questions/43156023/what-is-http-host-header
        // https://sage.amazon.com/questions/866200
        request.headers().set(HttpHeaderNames.HOST, BackendServerURLUtils.getRemoteHost());

        if (isWebSocketUpgradeRequest(headers)) {
            log.info("[STEP WS 1] Detected HTTP upgrade WS request.");
            channelHandlerContext.pipeline().replace(this, WEBSOCKET_HANDLER,
                    new WebSocketRequestConnectionHandler(request, frontendWebsocketURL));
            return;
        } else if (backendHttpChannel == null) {
            log.debug("Creating a BackendHTTP Channel.");
            final ChannelFuture backendHttPChannelFuture = createBackendHttpChannel(channelHandlerContext.channel());
            backendHttpChannel = backendHttPChannelFuture.channel();

            backendHttPChannelFuture.addListener((ChannelFutureListener) futureListener -> {
                if (futureListener.isSuccess()) {
                    // There is no connection caching at the moment.
                    // RFC 2616 HTTP/1.1 section 14.10 says:
                    // HTTP/1.1 applications that do not support persistent
                    // connections MUST include the "close" connection
                    // option in every message
                    // https://tools.ietf.org/html/rfc2616#section-14.10
                    HttpUtil.setKeepAlive(request, false);
                    sendMessageToBackendServer(request);
                } else {
                    log.warn("Connection failed for request {}", request);
                    // Close the connection if the connection attempt has failed.
                    channelHandlerContext.channel().close();
                }
            });

        } else if (backendHttpChannel.isActive()) {
            sendMessageToBackendServer(request);
        } else {
            throw new IllegalStateException("Backend HTTP Channel is present but not active");
        }
    }

    private ChannelFuture createBackendHttpChannel(final Channel frontendChannel) throws Exception {
        // Required for HTTPS request
        // TODO: Remove InsecureTrustManagerFactory once we have backend encryption
        // enabled
        final SslContext sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        return new Bootstrap().group(frontendChannel.eventLoop()).channel(frontendChannel.getClass())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel channel) throws Exception {
                        channel.pipeline().addLast(sslCtx.newHandler(frontendChannel.alloc()));
                        // Equivalent to an HttpRequestEncoder and HttpResponseDecoder
                        // This needs to be the inverse of the ReverseProxyInitializer as incoming
                        // request to this server
                        // are already decoded as an HttpRequest and outbound needs to be converted back
                        // to bytes
                        channel.pipeline().addLast(new HttpClientCodec());
                        channel.pipeline().addLast(
                                new HttpObjectAggregator(ReverseProxyInitializer.HTTP_MAX_CONTENT_LENGTH_BYTES));
                        channel.pipeline().addLast(new HttpResponseConnectionHandler(frontendChannel));
                    }
                }).option(ChannelOption.AUTO_READ, false)
                // Currently connecting to Personal Sagemaker's Jupyter Server
                // TODO:For Phase 1:Update this to map to Customer's N/B server based on Routing
                // Logic
                .connect(BackendServerURLUtils.getRemoteHost(), BackendServerURLUtils.getRemotePort());
    }

    @Override
    public void channelInactive(final ChannelHandlerContext channelHandlerContext) {
        log.debug("HTTP Frontend Channel is Inactive");
        if (backendHttpChannel != null) {
            NettyUtils.closeOnFlush(backendHttpChannel);
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext channelHandlerContext, final Throwable cause) {
        log.error("An exception is thrown:", cause);
        NettyUtils.closeOnFlush(channelHandlerContext.channel());
    }

    private void sendMessageToBackendServer(final FullHttpRequest message) {
        log.debug("Sending HTTP message to Backend Server: {}.", message);
        backendHttpChannel.writeAndFlush(message).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    private static boolean isWebSocketUpgradeRequest(final HttpHeaders headers) {
        return headers.getAll(HttpHeaderNames.CONNECTION).stream()
                .anyMatch(h -> Arrays.asList(h.split(",")).stream().anyMatch(v -> v.trim().equalsIgnoreCase(UPGRADE)))
                && WEBSOCKET.equalsIgnoreCase(headers.get(HttpHeaderNames.UPGRADE));
    }

    private String getWebSocketURL(FullHttpRequest request) {
        // TODO: Need to support WSS for non-desktop mode testing
        return "ws://" + request.headers().get("Host") + request.uri();
    }
}
