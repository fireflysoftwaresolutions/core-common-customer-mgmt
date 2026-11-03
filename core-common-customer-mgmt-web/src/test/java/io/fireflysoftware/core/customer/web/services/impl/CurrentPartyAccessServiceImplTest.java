package io.fireflysoftware.core.customer.web.services.impl;

import io.fireflysoftware.core.customer.core.services.PartyService;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyDTO;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import java.time.*;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CurrentPartyAccessServiceImplTest {
    private static final Instant NOW=Instant.parse("2026-11-02T12:00:00Z");
    private static final UUID TENANT=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PARTY=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER=UUID.fromString("00000000-0000-0000-0000-000000000003");
    private PartyService owner;
    private SecurityContextPort contexts;
    private PolicyDecisionPort policies;
    private CurrentPartyAccessServiceImpl service;
    @BeforeEach void setup(){
        owner=mock(PartyService.class);contexts=mock(SecurityContextPort.class);policies=mock(PolicyDecisionPort.class);
        service=new CurrentPartyAccessServiceImpl(owner,contexts,policies,Clock.fixed(NOW,ZoneOffset.UTC),Set.of("https://issuer.example"),Set.of("customer"),Set.of("registered-client"));
    }
    private SecurityPrincipal principal(String scope){
        return SecurityPrincipal.builder().issuer("https://issuer.example").subject("current-staff").tenantId(TENANT.toString()).scopes(Set.of(scope)).authorities(Set.of())
            .claims(Map.of("tenant_id",TENANT.toString(),"exp",NOW.plusSeconds(60),"nbf",NOW.minusSeconds(1),"aud",List.of("customer"),"principal_kind","STAFF","staff_actor_id",OTHER.toString(),"client_id","registered-client")).build();
    }
    private void current(SecurityPrincipal actor){when(contexts.currentPrincipal()).thenReturn(Mono.just(actor));}
    private void permitted(){when(policies.authorize(any(),anyString(),anyString(),anyMap())).thenReturn(Mono.just(Decision.permit()));}
    private PartyDTO request(){return PartyDTO.builder().tenantId(TENANT).sourceSystem("CONTROLLED").build();}
    private void denied(Mono<?> result){StepVerifier.create(result).expectErrorMatches(e->e instanceof ResponseStatusException status&&status.getStatusCode().value()==403).verify();}
    @Test void absentPrincipalCannotCreate(){when(contexts.currentPrincipal()).thenReturn(Mono.empty());denied(service.createParty(request()));verifyNoInteractions(owner,policies);}
    @Test void wrongIssuerCannotCreate(){current(principal("customer:parties:create").toBuilder().issuer("https://other.example").build());denied(service.createParty(request()));verifyNoInteractions(owner,policies);}
    @Test void wrongScopeCannotCreate(){current(principal("customer:parties:read"));denied(service.createParty(request()));verifyNoInteractions(owner,policies);}
    @Test void wrongBodyTenantCannotCreate(){current(principal("customer:parties:create"));var body=request();body.setTenantId(OTHER);denied(service.createParty(body));verifyNoInteractions(owner,policies);}
    @Test void callerSuppliedPartyIdCannotCreate(){current(principal("customer:parties:create"));var body=request();body.setPartyId(PARTY);denied(service.createParty(body));verifyNoInteractions(owner,policies);}
    @Test void expiredPrincipalCannotCreate(){var actor=principal("customer:parties:create");var claims=new HashMap<>(actor.claims());claims.put("exp",NOW);current(actor.toBuilder().claims(claims).build());denied(service.createParty(request()));verifyNoInteractions(owner,policies);}
    @Test void wrongAudienceCannotCreate(){var actor=principal("customer:parties:create");var claims=new HashMap<>(actor.claims());claims.put("aud",List.of("other"));current(actor.toBuilder().claims(claims).build());denied(service.createParty(request()));verifyNoInteractions(owner,policies);}
    @Test void inconsistentSignedTenantCannotCreate(){var actor=principal("customer:parties:create");var claims=new HashMap<>(actor.claims());claims.put("tenant_id",OTHER.toString());current(actor.toBuilder().claims(claims).build());denied(service.createParty(request()));verifyNoInteractions(owner,policies);}
    @Test void unregisteredClientCannotCreate(){var actor=principal("customer:parties:create");var claims=new HashMap<>(actor.claims());claims.put("client_id","other-client");current(actor.toBuilder().claims(claims).build());denied(service.createParty(request()));verifyNoInteractions(owner,policies);}
    @Test void futureNotBeforeCannotCreate(){var actor=principal("customer:parties:create");var claims=new HashMap<>(actor.claims());claims.put("nbf",NOW.plusSeconds(1));current(actor.toBuilder().claims(claims).build());denied(service.createParty(request()));verifyNoInteractions(owner,policies);}
    @Test void deniedCurrentPolicyCannotCreate(){current(principal("customer:parties:create"));when(policies.authorize(any(),anyString(),anyString(),anyMap())).thenReturn(Mono.just(Decision.deny("current denial")));denied(service.createParty(request()));verifyNoInteractions(owner);}
    @Test void emptyCurrentPolicyCannotCreate(){current(principal("customer:parties:create"));when(policies.authorize(any(),anyString(),anyString(),anyMap())).thenReturn(Mono.empty());denied(service.createParty(request()));verifyNoInteractions(owner);}
    @Test void unavailableCurrentPolicyCannotCreate(){current(principal("customer:parties:create"));when(policies.authorize(any(),anyString(),anyString(),anyMap())).thenReturn(Mono.error(new IllegalStateException("unavailable")));denied(service.createParty(request()));verifyNoInteractions(owner);}
    @Test void authorizedCreationCallsOriginalOwnerOnce(){current(principal("customer:parties:create"));permitted();var body=request();var persisted=request();persisted.setPartyId(PARTY);when(owner.createParty(any())).thenReturn(Mono.just(persisted));StepVerifier.create(service.createParty(body)).expectNext(persisted).verifyComplete();verify(owner).createParty(body);verify(policies).authorize(any(),eq("customer.parties.create"),eq("tenant/"+TENANT+"/parties"),argThat(c->TENANT.toString().equals(c.get("tenantId"))&&"STAFF".equals(c.get("actorKind"))));}
    @Test void persistedWrongTenantCannotEscapeRead(){current(principal("customer:parties:read"));permitted();var row=request();row.setPartyId(PARTY);row.setTenantId(OTHER);when(owner.getPartyById(PARTY)).thenReturn(Mono.just(row));denied(service.getPartyById(PARTY));}
    @Test void wrongScopeCannotReadOwner(){current(principal("customer:parties:create"));denied(service.getPartyById(PARTY));verifyNoInteractions(owner,policies);}
    @Test void currentReadReauthorizesExactPersistedIdentity(){current(principal("customer:parties:read"));permitted();var row=request();row.setPartyId(PARTY);when(owner.getPartyById(PARTY)).thenReturn(Mono.just(row));StepVerifier.create(service.getPartyById(PARTY)).expectNext(row).verifyComplete();verify(owner).getPartyById(PARTY);verify(policies,times(2)).authorize(any(),eq("customer.parties.read"),eq("tenant/"+TENANT+"/parties/"+PARTY),anyMap());}
    @Test void revocationBeforeReadResponseSuppressesDto(){current(principal("customer:parties:read"));when(policies.authorize(any(),anyString(),anyString(),anyMap())).thenReturn(Mono.just(Decision.permit()),Mono.just(Decision.deny("revoked")));var row=request();row.setPartyId(PARTY);when(owner.getPartyById(PARTY)).thenReturn(Mono.just(row));denied(service.getPartyById(PARTY));}
}
