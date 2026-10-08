package com.corporate.travel.security;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.JdkClientHttpRequestFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

/**
 * HTTP client settings for service-to-service calls (OPA, Keycloak, other services).
 *
 * <p>Boot builds every {@code RestClient.Builder} from this factory. The JDK client defaults to
 * HTTP/2 and attempts an h2c upgrade on plain-HTTP requests; some servers accept the upgrade and
 * then reset the stream on requests with a body. Internal calls are plain HTTP/1.1.</p>
 */
@Configuration(proxyBeanMethods = false)
public class InternalHttpClientConfig {

    @Bean
    public ClientHttpRequestFactoryBuilder<?> clientHttpRequestFactoryBuilder() {
        return http11();
    }

    /** JDK HttpClient pinned to HTTP/1.1. Also used directly where no Boot builder is available. */
    public static JdkClientHttpRequestFactoryBuilder http11() {
        return ClientHttpRequestFactoryBuilder.jdk()
            .withHttpClientCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1));
    }
}
