package io.fireflysoftware.core.customer.web.services.impl;

import io.fireflysoftware.core.customer.core.services.PartyService;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyDTO;
import io.fireflysoftware.core.customer.web.controllers.PartyController;
import org.fireflyframework.security.api.domain.Decision;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CurrentPartyMutationControllerTest {
    private static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PARTY = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private PartyService owner;
    private SecurityContextPort contexts;
    private PolicyDecisionPort policies;
    private PartyController controller;

    @BeforeEach void setup() {
        owner = mock(PartyService.class);
        contexts = mock(SecurityContextPort.class);
        policies = mock(PolicyDecisionPort.class);
        var service = new CurrentPartyAccessServiceImpl(owner, contexts, policies,
                Clock.fixed(NOW, ZoneOffset.UTC), Set.of("https://issuer.example"),
                Set.of("customer"), Set.of("registered-client"));
        controller = new PartyController(owner, service);
        when(contexts.currentPrincipal()).thenReturn(Mono.empty());
        when(policies.authorize(any(), anyString(), anyString(), anyMap())).thenReturn(Mono.just(Decision.permit()));
        when(owner.getPartyById(PARTY)).thenReturn(Mono.just(row(TENANT)));
        when(owner.updateParty(eq(PARTY), any())).thenReturn(Mono.just(row(TENANT)));
        when(owner.deleteParty(PARTY)).thenReturn(Mono.empty());
    }

    private PartyDTO row(UUID tenant) { return PartyDTO.builder().partyId(PARTY).tenantId(tenant).build(); }
    private PartyDTO body() { return PartyDTO.builder().tenantId(TENANT).preferredLanguage("en-US").build(); }
    private SecurityPrincipal actor(String operation) {
        return SecurityPrincipal.builder().issuer("https://issuer.example").subject("staff")
                .tenantId(TENANT.toString()).scopes(Set.of("customer:parties:" + operation)).authorities(Set.of())
                .claims(Map.of("tenant_id", TENANT.toString(), "exp", NOW.plusSeconds(60),
                        "aud", List.of("customer"), "principal_kind", "STAFF",
                        "staff_actor_id", OTHER.toString(), "client_id", "registered-client")).build();
    }
    private void current(String operation) { when(contexts.currentPrincipal()).thenReturn(Mono.just(actor(operation))); }
    private void forbidden(Mono<?> value) {
        StepVerifier.create(value).expectErrorMatches(error -> error instanceof ResponseStatusException status
                && status.getStatusCode().value() == 403).verify();
    }
    private void noMutation() {
        verify(owner, never()).updateParty(any(), any());
        verify(owner, never()).deleteParty(any(UUID.class));
    }

    @Test void unauthenticatedUpdateCannotReachPersistence() {
        forbidden(controller.updateParty(PARTY, body())); noMutation();
    }
    @Test void readScopeCannotAuthorizeUpdate() {
        current("read"); forbidden(controller.updateParty(PARTY, body())); noMutation();
    }
    @Test void requestCannotMovePartyToAnotherTenant() {
        current("update"); var request = body(); request.setTenantId(OTHER);
        forbidden(controller.updateParty(PARTY, request)); noMutation();
    }
    @Test void persistedForeignTenantCannotBeUpdated() {
        current("update"); when(owner.getPartyById(PARTY)).thenReturn(Mono.just(row(OTHER)));
        forbidden(controller.updateParty(PARTY, body())); noMutation();
    }
    @Test void humanIdentityCannotUpdateEvenWithStaffScope() {
        var staff = actor("update"); var claims = new HashMap<>(staff.claims());
        claims.put("principal_kind", "HUMAN"); claims.put("party_id", PARTY.toString()); claims.remove("staff_actor_id");
        when(contexts.currentPrincipal()).thenReturn(Mono.just(staff.toBuilder().claims(claims).build()));
        forbidden(controller.updateParty(PARTY, body())); noMutation();
    }
    @Test void revocationAfterOwnerReadPreventsUpdate() {
        current("update");
        when(policies.authorize(any(), anyString(), anyString(), anyMap()))
                .thenReturn(Mono.just(Decision.permit()), Mono.just(Decision.deny("revoked")));
        forbidden(controller.updateParty(PARTY, body())); noMutation();
    }
    @Test void authorizedUpdateReturnsUncacheableOwnedRow() {
        current("update");
        StepVerifier.create(controller.updateParty(PARTY, body())).assertNext(response -> {
            assertEquals(200, response.getStatusCode().value());
            assertEquals(TENANT, response.getBody().getTenantId());
            assertEquals("no-store", response.getHeaders().getCacheControl());
        }).verifyComplete();
        verify(owner).updateParty(eq(PARTY), any());
        verify(policies, atLeast(2)).authorize(any(), eq("customer.parties.update"),
                eq("tenant/" + TENANT + "/parties/" + PARTY), anyMap());
    }
    @Test void readModifyWriteAcceptsReturnedServerTimestamps() {
        current("update"); var request = row(TENANT);
        request.setCreatedAt(java.time.LocalDateTime.of(2026, 1, 1, 12, 0));
        request.setUpdatedAt(request.getCreatedAt());
        StepVerifier.create(controller.updateParty(PARTY, request))
                .assertNext(response -> assertEquals(200, response.getStatusCode().value())).verifyComplete();
    }
    @Test void unauthenticatedDeleteCannotReachPersistence() {
        forbidden(controller.deleteParty(PARTY)); noMutation();
    }
    @Test void persistedForeignTenantCannotBeDeleted() {
        current("delete"); when(owner.getPartyById(PARTY)).thenReturn(Mono.just(row(OTHER)));
        forbidden(controller.deleteParty(PARTY)); noMutation();
    }
    @Test void revocationAfterOwnerReadPreventsDelete() {
        current("delete");
        when(policies.authorize(any(), anyString(), anyString(), anyMap()))
                .thenReturn(Mono.just(Decision.permit()), Mono.just(Decision.deny("revoked")));
        forbidden(controller.deleteParty(PARTY)); noMutation();
    }
    @Test void authorizedDeleteInvokesOwnedOperationExactlyOnce() {
        current("delete");
        StepVerifier.create(controller.deleteParty(PARTY)).assertNext(response -> {
            assertEquals(204, response.getStatusCode().value());
            assertEquals("no-store", response.getHeaders().getCacheControl());
        }).verifyComplete();
        verify(owner).deleteParty(PARTY);
        verify(policies, atLeast(2)).authorize(any(), eq("customer.parties.delete"),
                eq("tenant/" + TENANT + "/parties/" + PARTY), anyMap());
    }
}
