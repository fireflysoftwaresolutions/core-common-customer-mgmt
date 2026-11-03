package io.fireflysoftware.core.customer.web.config;

import io.fireflysoftware.core.customer.core.services.PartyService;
import io.fireflysoftware.core.customer.web.services.CurrentPartyAccessService;
import io.fireflysoftware.core.customer.web.services.impl.CurrentPartyAccessServiceImpl;
import org.fireflyframework.security.adapter.opa.OpaPolicyDecisionAdapter;
import org.fireflyframework.security.rs.ResourceServerProperties;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;
import java.net.URI;
import java.time.Clock;
import java.util.Set;

@Configuration(proxyBeanMethods=false)
@Profile("!openapi-gen")
public class CustomerAuthorityConfiguration {
    @Bean public PolicyDecisionPort customerPolicyDecisionPort(
            @Value("${firefly.customer.authority.policy-url}") URI endpoint,
            @Value("${firefly.customer.authority.policy-path}") String path){
        requireHttps(endpoint);
        if(path==null||!path.matches("[A-Za-z0-9_/-]{1,160}")||path.startsWith("/")||path.contains(".."))
            throw new IllegalArgumentException("Explicit Customer current policy decision path required");
        return new OpaPolicyDecisionAdapter(WebClient.builder().baseUrl(endpoint.toString()).build(),path);
    }

    @Bean public CurrentPartyAccessService currentPartyAccessService(PartyService owner,SecurityContextPort contexts,
            PolicyDecisionPort policies,ResourceServerProperties receiver,
            @Value("${firefly.customer.authority.allowed-client-ids}") Set<String> clients,
            @Value("${firefly.security.resource-server.enabled:true}") boolean enabled){
        if(!enabled||!receiver.isStrictPolicy()||!receiver.isTenantRequired()||!receiver.getPermitMatchers().isEmpty()
            ||!receiver.getTenantClaimPaths().equals(java.util.List.of("tenant_id"))
            ||!receiver.getScopeClaimPaths().equals(java.util.List.of("scope"))
            ||receiver.getAudiences().isEmpty()||receiver.getIssuer()==null||receiver.getJwkSetUri()==null)
            throw new IllegalArgumentException("Strict current Customer issuer, audience, tenant and scope receiver required");
        requireHttps(URI.create(receiver.getIssuer()));requireHttps(URI.create(receiver.getJwkSetUri()));
        return new CurrentPartyAccessServiceImpl(owner,contexts,policies,Clock.systemUTC(),Set.of(receiver.getIssuer()),
            Set.copyOf(receiver.getAudiences()),clients);
    }
    private static void requireHttps(URI value){
        if(value==null||!"https".equals(value.getScheme())||value.getHost()==null||value.getUserInfo()!=null
            ||value.getFragment()!=null||value.getQuery()!=null)
            throw new IllegalArgumentException("Explicit HTTPS Customer authority endpoint required");
    }
}
