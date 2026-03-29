package com.corporate.travel.bff.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties
public class BffProperties {

    private Keycloak keycloak = new Keycloak();
    private Services services = new Services();

    @Data
    public static class Keycloak {
        private String url;
        private String realm;
        private String clientId;
        private String clientSecret;
    }

    @Data
    public static class Services {
        private String travelServiceUrl;
        private String expenseServiceUrl;
        private String delegationServiceUrl;
        private String consentServiceUrl;
    }
}
