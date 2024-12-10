package com.dedalus.uks.ChecksService.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "checks")
public class ChecksConfig {

    private TerminologyServer terminologyServer;
    private FhirServer fhirServer;

    public FhirServer getFhirServer() {
        return fhirServer;
    }
    public void setFhirServer(FhirServer fhirServer) {
        this.fhirServer = fhirServer;
    }

    public TerminologyServer getTerminologyServer() {
        return terminologyServer;
    }

    public void setTerminologyServer(TerminologyServer terminologyServer) {
        this.terminologyServer = terminologyServer;
    }

    public interface IFhirRestServer {
        String getEndpoint();
        String getAuthenticationEndpoint();
        String getClientId();
        String getClientSecret();
    }

    
    public static class FhirServer implements IFhirRestServer {
        private String endpoint;
        private String authenticationEndpoint;
        private String client_id;
        private String client_secret;

       public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
        public String getEndpoint() {
            return endpoint;
        }

        public void setAuthenticationEndpoint(String authenticationEndpoint) {
            this.authenticationEndpoint = authenticationEndpoint;
        }
        public String getAuthenticationEndpoint() {
            return authenticationEndpoint;
        }

        public void setClientId(String client_id) {
            this.client_id = client_id;
        }
        public String getClientId() {
            return client_id;
        }

        public void setClientSecret(String client_secret) {
            this.client_secret = client_secret;
        }
        public String getClientSecret() {
            return client_secret;
        }   
    }
    public static class TerminologyServer implements IFhirRestServer  {
        private String endpoint;
        private String authenticationEndpoint;
        private String client_id;
        private String client_secret;

       public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
        public String getEndpoint() {
            return endpoint;
        }

        public void setAuthenticationEndpoint(String authenticationEndpoint) {
            this.authenticationEndpoint = authenticationEndpoint;
        }
        public String getAuthenticationEndpoint() {
            return authenticationEndpoint;
        }

        public void setClientId(String client_id) {
            this.client_id = client_id;
        }
        public String getClientId() {
            return client_id;
        }

        public void setClientSecret(String client_secret) {
            this.client_secret = client_secret;
        }
        public String getClientSecret() {
            return client_secret;
        }   
    }


    
}
