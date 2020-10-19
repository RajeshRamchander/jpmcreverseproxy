package com.jpmc.sagemaker.studio.server;

/**
 * Interface for the Studio Servers
 */
public interface StudioServer {

    void startServer() throws InterruptedException;

    void stopServer() throws InterruptedException;
}
