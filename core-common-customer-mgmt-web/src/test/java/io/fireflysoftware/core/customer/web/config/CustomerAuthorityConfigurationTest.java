package io.fireflysoftware.core.customer.web.config;

import io.fireflysoftware.core.customer.core.services.PartyService;
import org.fireflyframework.security.rs.ResourceServerProperties;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CustomerAuthorityConfigurationTest {
    private final CustomerAuthorityConfiguration configuration=new CustomerAuthorityConfiguration();
    private ResourceServerProperties receiver(){var value=new ResourceServerProperties();value.setStrictPolicy(true);value.setTenantRequired(true);value.setTenantClaimPaths(List.of("tenant_id"));value.setScopeClaimPaths(List.of("scope"));value.setIssuer("https://issuer.example");value.setJwkSetUri("https://issuer.example/jwks");value.setAudiences(List.of("customer"));return value;}
    private Object build(ResourceServerProperties receiver,boolean enabled){return configuration.currentPartyAccessService(mock(PartyService.class),mock(SecurityContextPort.class),mock(PolicyDecisionPort.class),receiver,Set.of("registered-client"),enabled);}
    @Test void explicitSecureReceiverBuildsFunctionalService(){assertNotNull(build(receiver(),true));}
    @Test void disabledReceiverCannotBoot(){assertThrows(IllegalArgumentException.class,()->build(receiver(),false));}
    @Test void publicPermitMatcherCannotBoot(){var value=receiver();value.setPermitMatchers(List.of("/**"));assertThrows(IllegalArgumentException.class,()->build(value,true));}
    @Test void nonStrictPolicyCannotBoot(){var value=receiver();value.setStrictPolicy(false);assertThrows(IllegalArgumentException.class,()->build(value,true));}
    @Test void missingAudienceCannotBoot(){var value=receiver();value.setAudiences(List.of());assertThrows(IllegalArgumentException.class,()->build(value,true));}
    @Test void optionalTenantCannotBoot(){var value=receiver();value.setTenantRequired(false);assertThrows(IllegalArgumentException.class,()->build(value,true));}
    @Test void alternateTenantProjectionCannotBoot(){var value=receiver();value.setTenantClaimPaths(List.of("header_tenant"));assertThrows(IllegalArgumentException.class,()->build(value,true));}
    @Test void insecureJwksCannotBoot(){var value=receiver();value.setJwkSetUri("http://issuer.example/jwks");assertThrows(IllegalArgumentException.class,()->build(value,true));}
    @Test void unavailableIssuerConfigurationCannotBoot(){var value=receiver();value.setIssuer(null);assertThrows(IllegalArgumentException.class,()->build(value,true));}
    @Test void currentPolicyRejectsInsecureEndpoint(){assertThrows(IllegalArgumentException.class,()->configuration.customerPolicyDecisionPort(URI.create("http://pdp.example"),"customer/allow"));}
    @Test void currentPolicyRejectsTraversal(){assertThrows(IllegalArgumentException.class,()->configuration.customerPolicyDecisionPort(URI.create("https://pdp.example"),"../allow"));}
    @Test void releasedOpaAdapterRemainsTheCurrentPolicyPort(){assertInstanceOf(org.fireflyframework.security.adapter.opa.OpaPolicyDecisionAdapter.class,configuration.customerPolicyDecisionPort(URI.create("https://pdp.example"),"customer/allow"));}
}
