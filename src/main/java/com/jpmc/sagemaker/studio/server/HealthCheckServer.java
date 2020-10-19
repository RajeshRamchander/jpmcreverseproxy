package com.jpmc.sagemaker.studio.server;

import com.jpmc.sagemaker.studio.health.PingInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;

/**
 * HealthCheckServer class bootstraps the Netty Server for deep_ping request and
 * uses HttpRequestDecoder and PingHandler as the Channel Inbound Handler and
 * HttpResponseEncoder as Channel Outbound Handler
 */
@Log4j2
public class HealthCheckServer implements StudioServer {

    private static final int INET_PORT = 8080;
    private PingInitializer pingInitializer;
    private Channel channel;

    @Inject
    public HealthCheckServer(final PingInitializer pingInitializer) {
        this.pingInitializer = pingInitializer;
    }

    @Override
    public void startServer() throws InterruptedException {
        final ServerBootstrap serverBootstrap = new ServerBootstrap();
        final EventLoopGroup bossGroup = new NioEventLoopGroup();
        final EventLoopGroup workerGroup = new NioEventLoopGroup();

        serverBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                .childHandler(pingInitializer);

        channel = serverBootstrap.bind(INET_PORT).sync().channel();
        log.info("Started health check server on port {}", INET_PORT);
    }

    @Override
    public void stopServer() throws InterruptedException {
        if (channel != null) {
            channel.closeFuture().sync();
            log.info("Stopped health check server");
        }
    }
}
