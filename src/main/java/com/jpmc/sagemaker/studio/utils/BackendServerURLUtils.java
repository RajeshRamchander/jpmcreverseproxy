package com.jpmc.sagemaker.studio.utils;

import io.netty.handler.codec.http.HttpRequest;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Util class to provide the Sagemaker's Presigned URL information This is
 * temporary and replaced with Handler after Routing Logic implementation
 */
public class BackendServerURLUtils {

    private static final String SAGEMAKER_PRESIGNED_URL_HOST = "d-wvneonphlfwk.studio.us-east-1.sagemaker.aws";
    private static final int SAGEMAKER_PRESIGNED_URL_PORT = 443;

    public static String getRemoteHost() {
        return SAGEMAKER_PRESIGNED_URL_HOST;
    }

    public static int getRemotePort() {
        return SAGEMAKER_PRESIGNED_URL_PORT;
    }

    public static URI getJupyterURI(final HttpRequest request) throws URISyntaxException {
        final StringBuilder uriBuilder = new StringBuilder("wss://").append(SAGEMAKER_PRESIGNED_URL_HOST).append(":")
                .append(SAGEMAKER_PRESIGNED_URL_PORT).append(new URI(request.uri()).getPath());

        return new URI(uriBuilder.toString());
    }
}
