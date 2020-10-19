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
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import java.net.URI;
import java.util.Arrays;

/**
 * Channel Inbound Handler to process the WS request and send it to Downstream
 * Server
 */
@Log4j2
public class WebSocketRequestConnectionHandler extends ChannelInboundHandlerAdapter {

    @Setter
    private Channel backendWSChannel;
    @Setter
    private String frontendWebsocketURL;
    private final HttpRequest request;

    public WebSocketRequestConnectionHandler(final HttpRequest request, final String frontendWebsocketURL) {
        this.request = request;
        this.frontendWebsocketURL = frontendWebsocketURL;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext channelHandlerContext) throws Exception {
        if (backendWSChannel == null) {
            final ChannelFuture backendWSChannelFuture = createBackendWSChannel(channelHandlerContext.channel());
            backendWSChannel = backendWSChannelFuture.channel();
            backendWSChannelFuture.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext channelHandlerContext, final Object message) throws Exception {
        log.debug("WS Request Received: {}.", message);
        sendMessageToBackendServer(message);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext channelHandlerContext, final Throwable cause) {
        log.error("An exception is thrown:", cause);
        NettyUtils.closeOnFlush(channelHandlerContext.channel());
    }

    private ChannelFuture createBackendWSChannel(final Channel frontendChannel) throws Exception {
        // TODO:For Phase 0:Update this to map to previously created Sagemaker N/B.
        // TODO:For Phase 1:Update this to map to Customer's N/B server based on Routing
        // Logic
        final URI jupyterURI = BackendServerURLUtils.getJupyterURI(request);
        log.debug("Creating Backend WS Channel for {}", jupyterURI);

        final String versionFromHeader = request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_VERSION);
        final WebSocketVersion version = Arrays.stream(WebSocketVersion.values())
                .filter(wsv -> !WebSocketVersion.UNKNOWN.equals(wsv))
                .filter(wsv -> wsv.toHttpHeaderValue().equals(versionFromHeader)).findFirst()
                .orElseThrow(() -> new WebSocketHandshakeException(
                        "Protocol version " + versionFromHeader + " not supported."));

        final String expectedSubprotocol = request.headers().get(HttpHeaderNames.SEC_WEBSOCKET_PROTOCOL);

        // Required for HTTPS request
        // TODO: Remove InsecureTrustManagerFactory once we have backend encryption
        // enabled
        final SslContext sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();

        // WS Upgrade request was failing due to the present of "Origin" in the request
        // https://sage.amazon.com/questions/869751
        request.headers().remove(HttpHeaderNames.ORIGIN);

        return new Bootstrap().group(frontendChannel.eventLoop()).channel(frontendChannel.getClass())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel channel) throws Exception {
                        channel.pipeline().addLast(sslCtx.newHandler(frontendChannel.alloc()));
                        channel.pipeline().addLast(new HttpClientCodec());
                        channel.pipeline().addLast(
                                new HttpObjectAggregator(ReverseProxyInitializer.HTTP_MAX_CONTENT_LENGTH_BYTES));
                        channel.pipeline().addLast(new WebSocketResponseConnectionHandler(frontendChannel,
                                WebSocketClientHandshakerFactory.newHandshaker(jupyterURI, version, expectedSubprotocol,
                                        true, request.headers(), ReverseProxyInitializer.HTTP_MAX_CONTENT_LENGTH_BYTES),
                                request, frontendWebsocketURL));
                    }
                })
                // Currently connecting to Personal Sagemaker's Jupyter Server
                // TODO:For Phase 1:Update this to map to Customer's N/B server based on Routing
                // Logic
                .connect(jupyterURI.getHost(), jupyterURI.getPort());
    }

    private void sendMessageToBackendServer(final Object message) {
        log.debug("Sending WS message to Backend Server: {}.", message);
        backendWSChannel.writeAndFlush(message).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }
}
