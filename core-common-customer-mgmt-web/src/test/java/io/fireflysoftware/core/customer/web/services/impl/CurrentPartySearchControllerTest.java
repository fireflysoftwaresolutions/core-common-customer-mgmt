package io.fireflysoftware.core.customer.web.services.impl;

import io.fireflysoftware.core.customer.core.services.PartyService;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyDTO;
import io.fireflysoftware.core.customer.web.controllers.PartyController;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.PaginationResponse;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CurrentPartySearchControllerTest {
    private static final Instant NOW=Instant.parse("2026-11-03T03:00:00Z");
    private static final UUID TENANT=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PARTY=UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER=UUID.fromString("00000000-0000-0000-0000-000000000003");
    private PartyService owner;private SecurityContextPort contexts;private PolicyDecisionPort policies;private PartyController controller;
    @BeforeEach void setup(){
        owner=mock(PartyService.class);contexts=mock(SecurityContextPort.class);policies=mock(PolicyDecisionPort.class);
        controller=new PartyController(new CurrentPartyAccessServiceImpl(owner,contexts,policies,Clock.fixed(NOW,ZoneOffset.UTC),Set.of("https://issuer.example"),Set.of("customer"),Set.of("registered-client")));
        when(contexts.currentPrincipal()).thenReturn(Mono.just(actor("filter")));
        when(policies.authorize(any(),anyString(),anyString(),anyMap())).thenReturn(Mono.just(Decision.permit()));
        when(owner.filterParties(any())).thenReturn(Mono.just(page(TENANT)));
    }
    private SecurityPrincipal actor(String scope){return SecurityPrincipal.builder().issuer("https://issuer.example").subject("staff").tenantId(TENANT.toString()).scopes(Set.of("customer:parties:"+scope)).authorities(Set.of()).claims(Map.of("tenant_id",TENANT.toString(),"exp",NOW.plusSeconds(60),"aud",List.of("customer"),"principal_kind","STAFF","staff_actor_id",OTHER.toString(),"client_id","registered-client")).build();}
    private PaginationResponse<PartyDTO> page(UUID tenant){return new PaginationResponse<>(List.of(PartyDTO.builder().partyId(PARTY).tenantId(tenant).build()),1,1,0);}
    private FilterRequest<PartyDTO> request(){return new FilterRequest<>();}
    private void forbidden(FilterRequest<PartyDTO> request){StepVerifier.create(controller.filterParties(request)).expectErrorMatches(e->e instanceof ResponseStatusException s&&s.getStatusCode().value()==403).verify();}
    @Test void unsignedSearchCannotReadRowsOrCounts(){when(contexts.currentPrincipal()).thenReturn(Mono.empty());forbidden(request());verifyNoInteractions(owner);}
    @Test void IndividualReadScopeCannotEnumerateParties(){when(contexts.currentPrincipal()).thenReturn(Mono.just(actor("read")));forbidden(request());verifyNoInteractions(owner);}
    @Test void humanIdentityCannotEnumerateParties(){var staff=actor("filter");var claims=new HashMap<>(staff.claims());claims.put("principal_kind","HUMAN");claims.put("party_id",PARTY.toString());claims.remove("staff_actor_id");when(contexts.currentPrincipal()).thenReturn(Mono.just(staff.toBuilder().claims(claims).build()));forbidden(request());verifyNoInteractions(owner);}
    @Test void callerCannotSelectAnotherTenant(){var request=request();request.setFilters(PartyDTO.builder().tenantId(OTHER).build());forbidden(request);verifyNoInteractions(owner);}
    @Test void deniedPolicyCannotReadRowsOrCounts(){when(policies.authorize(any(),anyString(),anyString(),anyMap())).thenReturn(Mono.just(Decision.deny("denied")));forbidden(request());verifyNoInteractions(owner);}
    @Test void tenantConstraintIsBoundBeforeExecutingFilter(){var request=request();when(owner.filterParties(any())).thenAnswer(invocation->{FilterRequest<PartyDTO> actual=invocation.getArgument(0);assertEquals(TENANT,actual.getFilters().getTenantId());return Mono.just(page(TENANT));});StepVerifier.create(controller.filterParties(request)).assertNext(response->{assertEquals(1,response.getBody().getTotalElements());assertEquals("no-store",response.getHeaders().getCacheControl());assertEquals(List.of("Authorization"),response.getHeaders().getVary());}).verifyComplete();verify(policies,times(2)).authorize(any(),eq("customer.parties.filter"),eq("tenant/"+TENANT+"/parties"),anyMap());}
    @Test void unexpectedForeignRowsCannotEscape(){when(owner.filterParties(any())).thenReturn(Mono.just(page(OTHER)));forbidden(request());}
    @Test void revocationBeforeResponseSuppressesRowsAndCounts(){when(policies.authorize(any(),anyString(),anyString(),anyMap())).thenReturn(Mono.just(Decision.permit()),Mono.just(Decision.deny("revoked")));forbidden(request());}
    @Test void emptyOwnedSearchStillRequiresCurrentPolicy(){when(owner.filterParties(any())).thenReturn(Mono.just(new PaginationResponse<>(List.of(),0,0,0)));StepVerifier.create(controller.filterParties(request())).assertNext(response->assertEquals(0,response.getBody().getTotalElements())).verifyComplete();verify(policies,times(2)).authorize(any(),eq("customer.parties.filter"),anyString(),anyMap());}
    @Test void realFrameworkCriteriaRetainsMandatoryTenantConstraint(){
        var request=request();request.setFilters(PartyDTO.builder().tenantId(TENANT).build());
        var previous=(org.springframework.data.r2dbc.core.R2dbcEntityTemplate)org.springframework.test.util.ReflectionTestUtils.getField(
                org.fireflyframework.core.filters.FilterUtils.class,"entityTemplate");
        org.fireflyframework.core.filters.FilterUtils.initializeTemplate(mock(org.springframework.data.r2dbc.core.R2dbcEntityTemplate.class));
        try {
            var criteria=org.fireflyframework.core.filters.FilterUtils.createFilter(
                    io.fireflysoftware.core.customer.models.entities.Party.class, value->value).getCriteria(request);
            assertFalse(criteria.isEmpty());assertTrue(criteria.toString().contains("tenantId"));
            assertTrue(criteria.toString().contains(TENANT.toString()));
        } finally {org.fireflyframework.core.filters.FilterUtils.initializeTemplate(previous);}
    }
}
