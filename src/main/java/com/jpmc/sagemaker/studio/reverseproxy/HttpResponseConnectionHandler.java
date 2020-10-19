package com.jpmc.sagemaker.studio.reverseproxy;

import com.jpmc.sagemaker.studio.utils.HttpUtils;
import com.jpmc.sagemaker.studio.utils.NettyUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import lombok.extern.log4j.Log4j2;

/**
 * Channel Handler to send the response back from Backend server to the Browser
 */
@Log4j2
public class HttpResponseConnectionHandler extends ChannelInboundHandlerAdapter {

    private final Channel frontendChannel;

    public HttpResponseConnectionHandler(final Channel frontendChannel) {
        this.frontendChannel = frontendChannel;
    }

    @Override
    public void channelActive(final ChannelHandlerContext channelHandlerContext) {
        log.debug("HTTP Backend Channel is Active");
        channelHandlerContext.read();
    }

    @Override
    public void channelRead(final ChannelHandlerContext channelHandlerContext, final Object message) {
        if (!(message instanceof FullHttpResponse)) {
            throw new IllegalStateException("Something went wrong. Received a non-FullHttpResponse " + message);
        }

        final FullHttpResponse response = (FullHttpResponse) message;

        log.debug("HTTP Response received {}", response);

        // Currently we are only supporting http but the response coming back from
        // Backend Server contains https cookies
        // Need to convert https SET-Cookie to http SET-Cookie
        // TODO: Remove this once we start supporting HTTPS
        HttpUtils.convertHttpsToHttpCookie(response);

        frontendChannel.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext channelHandlerContext) {
        log.debug("HTTP Backend Channel is Inactive");
        NettyUtils.closeOnFlush(frontendChannel);
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext channelHandlerContext, final Throwable cause) {
        log.error("An exception is thrown: ", cause);
        NettyUtils.closeOnFlush(channelHandlerContext.channel());
    }
}
