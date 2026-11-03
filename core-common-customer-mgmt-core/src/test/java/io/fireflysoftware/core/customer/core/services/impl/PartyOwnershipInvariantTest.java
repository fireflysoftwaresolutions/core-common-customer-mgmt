package io.fireflysoftware.core.customer.core.services.impl;

import io.fireflysoftware.core.customer.core.mappers.PartyMapper;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyDTO;
import io.fireflysoftware.core.customer.models.entities.Party;
import io.fireflysoftware.core.customer.models.repositories.PartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PartyOwnershipInvariantTest {
    private static final UUID PARTY = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 1, 1, 12, 0);
    private PartyRepository repository;
    private PartyServiceImpl service;

    @BeforeEach void setup() {
        repository = mock(PartyRepository.class);
        service = new PartyServiceImpl();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "mapper", PartyMapper.INSTANCE);
        when(repository.findById(PARTY)).thenReturn(Mono.just(Party.builder()
                .partyId(PARTY).tenantId(TENANT).createdAt(CREATED).updatedAt(CREATED).build()));
        when(repository.save(any(Party.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }
    private void rejects(PartyDTO request) {
        StepVerifier.create(service.updateParty(PARTY, request)).expectError(IllegalArgumentException.class).verify();
        verify(repository, never()).save(any());
    }
    @Test void ownerRejectsTenantReplacement() { rejects(PartyDTO.builder().tenantId(OTHER).build()); }
    @Test void ownerRejectsMissingTenant() { rejects(new PartyDTO()); }
    @Test void ownerRejectsConflictingResourceIdentity() {
        rejects(PartyDTO.builder().tenantId(TENANT).partyId(OTHER).build());
    }
    @Test void callerCannotSetLastModifiedTimestamp() {
        var request = PartyDTO.builder().tenantId(TENANT).updatedAt(CREATED.plusYears(50)).build();
        StepVerifier.create(service.updateParty(PARTY, request))
                .assertNext(updated -> assertEquals(CREATED, updated.getUpdatedAt())).verifyComplete();
    }
    @Test void updatePreservesOriginalCreationTimeAndOwnership() {
        var request = PartyDTO.builder().tenantId(TENANT).preferredLanguage("en-US")
                .createdAt(CREATED.plusDays(1)).build();
        StepVerifier.create(service.updateParty(PARTY, request)).assertNext(updated -> {
            assertEquals(PARTY, updated.getPartyId());
            assertEquals(TENANT, updated.getTenantId());
            assertEquals(CREATED, updated.getCreatedAt());
            assertEquals("en-US", updated.getPreferredLanguage());
        }).verifyComplete();
    }
}
