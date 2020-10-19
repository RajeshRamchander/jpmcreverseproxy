package com.jpmc.sagemaker.studio.dagger;

import com.jpmc.sagemaker.studio.server.HealthCheckServer;
import com.jpmc.sagemaker.studio.server.ReverseProxyServer;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = { StudioReverseProxyServiceModule.class })
public interface StudioReverseProxyServiceComponent {

    HealthCheckServer getHealthCheckServer();

    ReverseProxyServer getReverseProxyServer();
}
