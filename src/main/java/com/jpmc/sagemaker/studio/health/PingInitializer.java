package com.jpmc.sagemaker.studio.health;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

import javax.inject.Inject;

public class PingInitializer extends ChannelInitializer<SocketChannel> {

    private PingHandler pingHandler;

    @Inject
    public PingInitializer(final PingHandler pingHandler) {
        this.pingHandler = pingHandler;
    }

    @Override
    protected void initChannel(final SocketChannel socketChannel) throws Exception {
        socketChannel.pipeline().addLast(new HttpRequestDecoder());
        socketChannel.pipeline().addLast(new HttpResponseEncoder());
        socketChannel.pipeline().addLast(pingHandler);
    }
}
