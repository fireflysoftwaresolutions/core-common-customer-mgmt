/*
 * Copyright 2025 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package io.fireflysoftware.core.customer.core.services.impl;

import io.fireflysoftware.core.customer.core.mappers.PartyRelationshipMapper;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyAncestorDTO;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyRelationshipDTO;
import io.fireflysoftware.core.customer.interfaces.enums.PartyKind;
import io.fireflysoftware.core.customer.interfaces.enums.RelationshipKind;
import io.fireflysoftware.core.customer.models.entities.Party;
import io.fireflysoftware.core.customer.models.entities.PartyRelationship;
import io.fireflysoftware.core.customer.models.repositories.PartyRelationshipRepository;
import io.fireflysoftware.core.customer.models.repositories.PartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyRelationshipHierarchyTest {

    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock
    private PartyRelationshipRepository repository;

    @Mock
    private PartyRepository parties;

    @Mock
    private PartyRelationshipMapper mapper;

    @InjectMocks
    private PartyRelationshipServiceImpl service;

    private UUID person;
    private UUID holding;
    private UUID subsidiary;
    private UUID grandchild;

    @BeforeEach
    void parties() {
        person = UUID.randomUUID();
        holding = UUID.randomUUID();
        subsidiary = UUID.randomUUID();
        grandchild = UUID.randomUUID();
        lenient().when(parties.findById(person)).thenReturn(Mono.just(party(person, PartyKind.INDIVIDUAL)));
        lenient().when(parties.findById(holding)).thenReturn(Mono.just(party(holding, PartyKind.ORGANIZATION)));
        lenient().when(parties.findById(subsidiary)).thenReturn(Mono.just(party(subsidiary, PartyKind.ORGANIZATION)));
        lenient().when(parties.findById(grandchild)).thenReturn(Mono.just(party(grandchild, PartyKind.ORGANIZATION)));
        lenient().when(mapper.toEntity(any())).thenAnswer(invocation -> {
            PartyRelationshipDTO dto = invocation.getArgument(0);
            return PartyRelationship.builder()
                    .sourcePartyId(dto.getSourcePartyId())
                    .targetPartyId(dto.getTargetPartyId())
                    .relationshipKind(dto.getRelationshipKind())
                    .ownershipPercentage(dto.getOwnershipPercentage())
                    .tenantId(dto.getTenantId())
                    .build();
        });
        lenient().when(mapper.toDTO(any())).thenAnswer(invocation -> {
            PartyRelationship entity = invocation.getArgument(0);
            return PartyRelationshipDTO.builder()
                    .partyRelationshipId(entity.getPartyRelationshipId())
                    .sourcePartyId(entity.getSourcePartyId())
                    .targetPartyId(entity.getTargetPartyId())
                    .relationshipKind(entity.getRelationshipKind())
                    .ownershipPercentage(entity.getOwnershipPercentage())
                    .tenantId(entity.getTenantId())
                    .build();
        });
        lenient().when(repository.save(any())).thenAnswer(invocation -> {
            PartyRelationship entity = invocation.getArgument(0);
            if (entity.getPartyRelationshipId() == null) {
                entity.setPartyRelationshipId(UUID.randomUUID());
            }
            return Mono.just(entity);
        });
    }

    @Test
    void personOwnsCompany() {
        PartyRelationshipDTO request = ownership(person, subsidiary, new BigDecimal("40"));

        StepVerifier.create(service.createPartyRelationship(request))
                .assertNext(saved -> {
                    assertEquals(person, saved.getSourcePartyId());
                    assertEquals(subsidiary, saved.getTargetPartyId());
                    assertEquals(RelationshipKind.OWNERSHIP, saved.getRelationshipKind());
                    assertEquals(new BigDecimal("40"), saved.getOwnershipPercentage());
                })
                .verifyComplete();

        PartyRelationship stored = storedOwnership(person, subsidiary, new BigDecimal("40"));
        when(repository.findByTargetPartyIdAndRelationshipKind(subsidiary, RelationshipKind.OWNERSHIP))
                .thenReturn(Flux.just(stored));

        StepVerifier.create(service.directOwners(subsidiary))
                .assertNext(owners -> {
                    assertEquals(1, owners.size());
                    assertEquals(person, owners.get(0).getSourcePartyId());
                })
                .verifyComplete();
    }

    @Test
    void companyOwnsCompanyAndIsItsParent() {
        stubHierarchy();
        PartyRelationshipDTO request = ownership(holding, subsidiary, new BigDecimal("80"));

        StepVerifier.create(service.createPartyRelationship(request))
                .assertNext(saved -> assertEquals(subsidiary, saved.getTargetPartyId()))
                .verifyComplete();

        PartyRelationship edge = storedOwnership(holding, subsidiary, new BigDecimal("80"));
        when(repository.findByRelationshipKind(RelationshipKind.PARENT)).thenReturn(Flux.empty());
        when(repository.findByRelationshipKind(RelationshipKind.OWNERSHIP)).thenReturn(Flux.just(edge));
        when(parties.findAllById(anyIterable())).thenReturn(Flux.just(party(holding, PartyKind.ORGANIZATION)));

        StepVerifier.create(service.directSubsidiaries(holding))
                .assertNext(children -> {
                    assertEquals(1, children.size());
                    assertEquals(subsidiary, children.get(0).getTargetPartyId());
                })
                .verifyComplete();

        StepVerifier.create(service.ancestors(subsidiary, 25))
                .assertNext(ancestors -> {
                    assertEquals(1, ancestors.size());
                    assertEquals(holding, ancestors.get(0).getPartyId());
                    assertEquals(1, ancestors.get(0).getDistance());
                    assertEquals(RelationshipKind.OWNERSHIP, ancestors.get(0).getRelationshipKind());
                })
                .verifyComplete();
    }

    @Test
    void severalOwnersDoNotNeedToSumToOneHundred() {
        PartyRelationship first = storedOwnership(person, subsidiary, new BigDecimal("60"));
        PartyRelationship second = storedOwnership(holding, subsidiary, new BigDecimal("60"));
        when(repository.findByTargetPartyIdAndRelationshipKind(subsidiary, RelationshipKind.OWNERSHIP))
                .thenReturn(Flux.just(first, second));

        StepVerifier.create(service.directOwners(subsidiary))
                .assertNext(owners -> {
                    assertEquals(2, owners.size());
                    assertEquals(new BigDecimal("120"), owners.stream()
                            .map(PartyRelationshipDTO::getOwnershipPercentage)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                })
                .verifyComplete();
    }

    @Test
    void cycleIsRejected() {
        PartyRelationship holdingOwnsSubsidiary = storedOwnership(holding, subsidiary, new BigDecimal("100"));
        when(repository.findByRelationshipKind(RelationshipKind.PARENT)).thenReturn(Flux.empty());
        when(repository.findByRelationshipKind(RelationshipKind.OWNERSHIP)).thenReturn(Flux.just(holdingOwnsSubsidiary));
        when(parties.findAllById(anyIterable())).thenReturn(Flux.just(party(holding, PartyKind.ORGANIZATION)));

        StepVerifier.create(service.createPartyRelationship(ownership(subsidiary, holding, new BigDecimal("10"))))
                .expectErrorMatches(error -> error.getMessage().contains("cycle"))
                .verify();

        verify(repository, never()).save(any());
    }

    @Test
    void selfLinkIsRejected() {
        PartyRelationshipDTO request = ownership(holding, holding, null);

        StepVerifier.create(service.createPartyRelationship(request))
                .expectErrorMatches(error -> error.getMessage().contains("itself"))
                .verify();

        verify(repository, never()).save(any());
        verify(parties, never()).findById(any(UUID.class));
    }

    @Test
    void parentChainReportsSubsidiariesAndAncestorsUpToTheRequestedDepth() {
        PartyRelationship holdingToSubsidiary = parent(holding, subsidiary);
        PartyRelationship subsidiaryToGrandchild = parent(subsidiary, grandchild);
        when(repository.findByRelationshipKind(RelationshipKind.PARENT))
                .thenReturn(Flux.just(holdingToSubsidiary, subsidiaryToGrandchild));
        when(repository.findByRelationshipKind(RelationshipKind.OWNERSHIP)).thenReturn(Flux.empty());

        StepVerifier.create(service.directSubsidiaries(holding))
                .assertNext(children -> {
                    assertEquals(1, children.size());
                    assertEquals(subsidiary, children.get(0).getTargetPartyId());
                })
                .verifyComplete();

        StepVerifier.create(service.ancestors(grandchild, 1))
                .assertNext(ancestors -> {
                    assertEquals(1, ancestors.size());
                    assertEquals(subsidiary, ancestors.get(0).getPartyId());
                    assertEquals(1, ancestors.get(0).getDistance());
                })
                .verifyComplete();

        StepVerifier.create(service.ancestors(grandchild, 100))
                .assertNext(ancestors -> {
                    assertEquals(List.of(subsidiary, holding), ancestors.stream().map(PartyAncestorDTO::getPartyId).toList());
                    assertEquals(List.of(1, 2), ancestors.stream().map(PartyAncestorDTO::getDistance).toList());
                    assertTrue(ancestors.stream().allMatch(ancestor -> ancestor.getDistance() <= PartyHierarchyImpl.MAX_DEPTH));
                })
                .verifyComplete();
    }

    @Test
    void percentageOutsideZeroToOneHundredIsRejected() {
        StepVerifier.create(service.createPartyRelationship(ownership(person, subsidiary, new BigDecimal("100.01"))))
                .expectErrorMatches(error -> error.getMessage().contains("0 and 100"))
                .verify();

        verify(repository, never()).save(any());
    }

    private void stubHierarchy() {
        when(repository.findByRelationshipKind(RelationshipKind.PARENT)).thenReturn(Flux.empty());
        when(repository.findByRelationshipKind(RelationshipKind.OWNERSHIP)).thenReturn(Flux.empty());
    }

    private PartyRelationshipDTO ownership(UUID source, UUID target, BigDecimal percentage) {
        return PartyRelationshipDTO.builder()
                .sourcePartyId(source)
                .targetPartyId(target)
                .relationshipKind(RelationshipKind.OWNERSHIP)
                .ownershipPercentage(percentage)
                .tenantId(TENANT)
                .build();
    }

    private static Party party(UUID id, PartyKind kind) {
        return Party.builder().partyId(id).partyKind(kind).tenantId(TENANT).build();
    }

    private static PartyRelationship storedOwnership(UUID source, UUID target, BigDecimal percentage) {
        return PartyRelationship.builder()
                .partyRelationshipId(UUID.randomUUID())
                .sourcePartyId(source)
                .targetPartyId(target)
                .relationshipKind(RelationshipKind.OWNERSHIP)
                .ownershipPercentage(percentage)
                .tenantId(TENANT)
                .active(true)
                .build();
    }

    private static PartyRelationship parent(UUID source, UUID target) {
        return PartyRelationship.builder()
                .partyRelationshipId(UUID.randomUUID())
                .sourcePartyId(source)
                .targetPartyId(target)
                .relationshipKind(RelationshipKind.PARENT)
                .tenantId(TENANT)
                .active(true)
                .build();
    }
}
