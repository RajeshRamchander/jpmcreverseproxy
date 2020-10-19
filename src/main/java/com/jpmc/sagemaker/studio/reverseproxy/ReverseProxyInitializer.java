package com.jpmc.sagemaker.studio.reverseproxy;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import lombok.extern.log4j.Log4j2;

/**
 * ReverseProxyInitializer initializes the Channel Pipeline for the Reverse
 * Proxy Server
 */
@Log4j2
public class ReverseProxyInitializer extends ChannelInitializer<SocketChannel> {

    // TODO: Update this with the Ideal value(not sure right now what it should be)
    public static final int HTTP_MAX_CONTENT_LENGTH_BYTES = 200 * 1024 * 1024;

    @Override
    protected void initChannel(final SocketChannel socketChannel) throws Exception {
        log.debug("Initiating a Channel Pipeline for ReverseProxy.");

        // Equivalent to an HttpRequestDecoder and HttpResponseEncoder
        socketChannel.pipeline().addLast(new HttpServerCodec());
        // TODO: What should maxContentLength be?
        // Are large notebook files still loadable? Otherwise might need to tweak this
        // setting
        socketChannel.pipeline().addLast(new HttpObjectAggregator(HTTP_MAX_CONTENT_LENGTH_BYTES));
        // Business logic to handle incoming connections from the browser/client
        socketChannel.pipeline().addLast(new HttpRequestConnectionHandler());

    }
}
