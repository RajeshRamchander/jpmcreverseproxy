package com.jpmc.sagemaker.studio.dagger;

import com.jpmc.sagemaker.studio.health.PingHandler;
import com.jpmc.sagemaker.studio.health.PingInitializer;
import com.jpmc.sagemaker.studio.reverseproxy.ReverseProxyInitializer;
import dagger.Module;
import dagger.Provides;

@Module
public class StudioReverseProxyServiceModule {

    @Provides
    public PingHandler pingHandler() {
        return new PingHandler();
    }

    @Provides
    public PingInitializer pingInitializer(final PingHandler pingHandler) {
        return new PingInitializer(pingHandler);
    }

    @Provides
    public ReverseProxyInitializer reverseProxyInitializer() {
        return new ReverseProxyInitializer();
    }
}
