package com.jpmc.sagemaker.studio.server;

import com.jpmc.sagemaker.studio.reverseproxy.ReverseProxyInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;

/**
 * ReverseProxyServer class bootstraps the Netty Server to proxy the
 * HTTP/Websocket requests
 */
@Log4j2
public class ReverseProxyServer implements StudioServer {

    private static final int INET_PORT = 8081;

    private final ReverseProxyInitializer reverseProxyInitializer;
    private Channel channel;

    @Inject
    public ReverseProxyServer(final ReverseProxyInitializer reverseProxyInitializer) {
        this.reverseProxyInitializer = reverseProxyInitializer;
    }

    @Override
    public void startServer() throws InterruptedException {
        log.debug("Starting a reverse proxy server.");
        final ServerBootstrap serverBootstrap = new ServerBootstrap();
        // TODO: Likely need to tune the number of threads per worker group
        // Default is number of available processors * 2
        final EventLoopGroup bossGroup = new NioEventLoopGroup();
        final EventLoopGroup workerGroup = new NioEventLoopGroup();

        serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                .childHandler(reverseProxyInitializer);

        channel = serverBootstrap.bind(INET_PORT).sync().channel();
        log.info("Started reverse proxy server on port {}.", INET_PORT);
    }

    @Override
    public void stopServer() throws InterruptedException {
        if (channel != null) {
            channel.closeFuture().sync();
            log.info("Stopped reverse proxy server.");
        }
    }
}
