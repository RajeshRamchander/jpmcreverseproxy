package com.jpmc.sagemaker.studio.health;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import lombok.extern.log4j.Log4j2;

/**
 * Channel Inbound Handler that processes the Ping HTTPRequest and send the 200
 * HTTPResponse
 */
@ChannelHandler.Sharable
@Log4j2
public class PingHandler extends SimpleChannelInboundHandler<HttpRequest> {

    private static final String DEEP_PING_URI = "/deep_ping";

    @Override
    protected void channelRead0(final ChannelHandlerContext channelHandlerContext, final HttpRequest httpRequest)
            throws Exception {
        log.info("Ping Request Received: {}.", httpRequest);
        if (DEEP_PING_URI.equals(httpRequest.uri())) {
            handlePing(channelHandlerContext, httpRequest);
        } else {
            channelHandlerContext.writeAndFlush(
                    buildHttpResponse(HttpResponseStatus.NOT_FOUND, Unpooled.EMPTY_BUFFER).retainedDuplicate());
        }
    }

    private void handlePing(final ChannelHandlerContext channelHandlerContext, final HttpRequest httpRequest) {
        log.debug("Handling Ping for request: {}.", httpRequest);
        final HttpResponse response = buildHttpResponse(HttpResponseStatus.OK, "healthy").retainedDuplicate();

        channelHandlerContext.channel().writeAndFlush(response).addListener((ChannelFuture channelFuture) -> {
            if (channelFuture.isSuccess()) {
                log.info("Successfully responded to ping.");
            } else if (channelFuture.isCancelled()) {
                log.warn("Ping response write to channel was cancelled.");
            } else {
                log.warn("Ping response failed, closing connection");
            }
            channelFuture.channel().close();
        });
    }

    public static DefaultFullHttpResponse buildHttpResponse(final HttpResponseStatus httpResponseStatus,
            final String content) {
        final ByteBuf bytes = Unpooled.copiedBuffer(content.getBytes(CharsetUtil.UTF_8));
        return buildHttpResponse(httpResponseStatus, bytes);
    }

    public static DefaultFullHttpResponse buildHttpResponse(final HttpResponseStatus httpResponseStatus,
            final ByteBuf content) {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, httpResponseStatus, content,
                new DefaultHttpHeaders().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                        .set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes()),
                EmptyHttpHeaders.INSTANCE);
    }
}
