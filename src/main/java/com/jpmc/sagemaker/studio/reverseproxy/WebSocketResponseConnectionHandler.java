package com.jpmc.sagemaker.studio.reverseproxy;

import com.jpmc.sagemaker.studio.utils.HttpUtils;
import com.jpmc.sagemaker.studio.utils.NettyUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;
import lombok.extern.log4j.Log4j2;

/**
 * Channel Handler to send the WS Frames back from Backend server to the Browser
 */
@Log4j2

public class WebSocketResponseConnectionHandler extends ChannelInboundHandlerAdapter {

    private final WebSocketClientHandshaker webSocketClientHandshaker;
    private final Channel frontendChannel;
    private final HttpRequest originalUpgradeRequest;
    private final String frontendWebsocketURL;

    public WebSocketResponseConnectionHandler(final Channel frontendChannel,
            final WebSocketClientHandshaker webSocketClientHandshaker, final HttpRequest originalUpgradeRequest,
            final String frontendWebsocketURL) {
        this.frontendChannel = frontendChannel;
        this.webSocketClientHandshaker = webSocketClientHandshaker;
        this.originalUpgradeRequest = originalUpgradeRequest;
        this.frontendWebsocketURL = frontendWebsocketURL;
    }

    @Override
    public void channelActive(final ChannelHandlerContext channelHandlerContext) throws Exception {
        log.info("[STEP WS 2a]  Initiating the backend WS Handshake.");
        webSocketClientHandshaker.handshake(channelHandlerContext.channel());
        log.info("[STEP WS 2a]  Backend Handshake Initiated.");
        channelHandlerContext.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext channelHandlerContext, final Object message) throws Exception {
        log.debug("WS Response Received {}", message);
        if (!webSocketClientHandshaker.isHandshakeComplete()) {
            if (!(message instanceof FullHttpResponse)) {
                throw new Exception("Didn't receive a FullHttpResponse message to complete handshake");
            }

            completeFrontendWsHandshake().addListener(future -> {
                if (future.isSuccess()) {
                    log.info("[STEP WS 4] Finalizing the backend WS handshake...");
                    webSocketClientHandshaker.finishHandshake(channelHandlerContext.channel(),
                            (FullHttpResponse) message);
                    log.info(
                            "[STEP WS 4] Finalized the backend WS handshake.  Ready to proxy messages in both directions!");
                } else {
                    log.info("Since the frontend handshake didn't work, closing the backend context.");
                    NettyUtils.closeOnFlush(channelHandlerContext.channel());
                }
            });
            return;
        }

        // After the handshake, there shouldn't be any Http responses anymore but rather
        // WS frames
        if (message instanceof FullHttpResponse) {
            final FullHttpResponse response = (FullHttpResponse) message;
            throw new Exception("Unexpected FullHttpResponse (Status=" + response.status() + ", content="
                    + response.content().toString(CharsetUtil.UTF_8) + ')');
        }

        frontendChannel.writeAndFlush(message).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext channelHandlerContext, final Throwable cause) {
        log.error("An exception is thrown: ", cause);
        NettyUtils.closeOnFlush(channelHandlerContext.channel());
    }

    private ChannelFuture completeFrontendWsHandshake() {
        log.info("Frontend WS Handshake for URL {}", frontendWebsocketURL);
        final WebSocketServerHandshaker handshaker = new WebSocketServerHandshakerFactory(frontendWebsocketURL, null,
                true).newHandshaker(originalUpgradeRequest);

        final ChannelFuture channelFuture;

        if (handshaker == null) {
            channelFuture = WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(frontendChannel);
        } else {
            log.info("[STEP WS 3] Finalizing Frontend Handshake with Browser");
            channelFuture = handshaker.handshake(frontendChannel, originalUpgradeRequest,
                    HttpUtils.createResponseHeadersFromRequestHeaders(originalUpgradeRequest.headers()),
                    frontendChannel.newPromise());
            log.info("[STEP WS 3] Frontend Handshake with Browser finalized");
        }
        return channelFuture;
    }
}
