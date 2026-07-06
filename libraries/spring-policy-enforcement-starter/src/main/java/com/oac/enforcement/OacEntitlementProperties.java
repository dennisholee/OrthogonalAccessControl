package com.oac.enforcement;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the OAC entitlement enforcement.
 *
 * Configured via application.yml:
 * <pre>
 * oac:
 *   enforcement:
 *     contract-paths:
 *       - classpath:order-service-api.yaml
 *     pdp-url: http://pdp:8080
 *     identity:
 *       user-header: X-User-Id
 *       service-header: X-Service-Id
 *     fail-closed: true
 * </pre>
 */
@ConfigurationProperties(prefix = "oac.enforcement")
public class OacEntitlementProperties {

    /** OpenAPI contract YAML files to parse for x-oac-entitlement vendor extensions */
    private List<String> contractPaths = new ArrayList<>(List.of("classpath:api.yaml"));

    /** PDP base URL for CheckPermission calls */
    private String pdpUrl = "http://localhost:8080";

    /** If true, missing/unparseable contracts fail startup. If false, start without enforcement */
    private boolean failClosed = true;

    /** Identity header configuration */
    private Identity identity = new Identity();

    public List<String> getContractPaths() { return contractPaths; }
    public void setContractPaths(List<String> contractPaths) { this.contractPaths = contractPaths; }

    public String getPdpUrl() { return pdpUrl; }
    public void setPdpUrl(String pdpUrl) { this.pdpUrl = pdpUrl; }

    public boolean isFailClosed() { return failClosed; }
    public void setFailClosed(boolean failClosed) { this.failClosed = failClosed; }

    public Identity getIdentity() { return identity; }
    public void setIdentity(Identity identity) { this.identity = identity; }

    public static class Identity {
        private String userHeader = "X-User-Id";
        private String serviceHeader = "X-Service-Id";

        public String getUserHeader() { return userHeader; }
        public void setUserHeader(String userHeader) { this.userHeader = userHeader; }

        public String getServiceHeader() { return serviceHeader; }
        public void setServiceHeader(String serviceHeader) { this.serviceHeader = serviceHeader; }
    }
}