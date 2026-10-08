package com.corporate.travel.bff.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties
public class BffProperties {

    private Keycloak keycloak = new Keycloak();
    private Services services = new Services();
    private Delegation delegation = new Delegation();

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

    @Data
    public static class Delegation {
        /**
         * Resource servers the BFF calls on the subject's behalf while delegation is active.
         * One audience-scoped token is exchanged per entry, because each service only accepts
         * tokens whose {@code aud} names it.
         */
        private List<String> audiences = new ArrayList<>(List.of("travel-service", "expense-service"));
    }
}
