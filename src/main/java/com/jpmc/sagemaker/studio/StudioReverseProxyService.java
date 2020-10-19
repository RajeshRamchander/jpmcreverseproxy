package com.jpmc.sagemaker.studio;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import com.jpmc.sagemaker.studio.dagger.DaggerStudioReverseProxyServiceComponent;
import com.jpmc.sagemaker.studio.dagger.StudioReverseProxyServiceComponent;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class StudioReverseProxyService {

    private static final int TEN_SECONDS = (int) TimeUnit.SECONDS.toMillis(10);

    public static void main(final String[] args) throws Throwable {
        log.info("Starting with args {}", Arrays.toString(args));
        verifyArguments(args);

        final StudioReverseProxyServiceComponent component = DaggerStudioReverseProxyServiceComponent.create();

        try {
            component.getHealthCheckServer().startServer();
            component.getReverseProxyServer().startServer();
            log.info("All servers successfully bound to port and started.");
        } catch (final Exception exception) {
            log.error("Error occurred while starting Server", exception);
            throw exception;
        } finally {
            component.getHealthCheckServer().stopServer();
            component.getReverseProxyServer().stopServer();
        }
    }

    private static void verifyArguments(final String[] args) throws InterruptedException {
        boolean hasRealm = false;
        boolean hasDomain = false;
        boolean hasRoot = false;

        for (String arg : args) {
            if (arg.startsWith("--realm=")) {
                hasRealm = true;
            } else if (arg.startsWith("--domain=")) {
                hasDomain = true;
            } else if (arg.startsWith("--root=")) {
                hasRoot = true;
            }
        }

        if (hasRealm && hasDomain && hasRoot) {
            return;
        } else {
            System.out.println("The service cannot determine what environment it is running in and will shut down.");
            System.out.println("If you are trying to run from an Eclipse workspace, add the following");
            System.out.println("program arguments to your launch configuration: ");
            System.out.println("");
            System.out.println("--domain=desktop --realm=us-east-1 --root=build/private");
            Thread.sleep(TEN_SECONDS); // Wait a while to avoid flapping at full speed (in case this happens in Apollo)
            System.exit(0);
        }
    }
}
