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

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.customer.core.mappers.PartyRelationshipMapper;
import io.fireflysoftware.core.customer.core.services.PartyRelationshipService;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyAncestorDTO;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyRelationshipDTO;
import io.fireflysoftware.core.customer.interfaces.enums.PartyKind;
import io.fireflysoftware.core.customer.interfaces.enums.RelationshipKind;
import io.fireflysoftware.core.customer.models.entities.Party;
import io.fireflysoftware.core.customer.models.entities.PartyRelationship;
import io.fireflysoftware.core.customer.models.repositories.PartyRelationshipRepository;
import io.fireflysoftware.core.customer.models.repositories.PartyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class PartyRelationshipServiceImpl implements PartyRelationshipService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @Autowired
    private PartyRelationshipRepository repository;

    @Autowired
    private PartyRepository parties;

    @Autowired
    private PartyRelationshipMapper mapper;

    @Override
    public Mono<PaginationResponse<PartyRelationshipDTO>> filterPartyRelationships(FilterRequest<PartyRelationshipDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        PartyRelationship.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<PartyRelationshipDTO> createPartyRelationship(PartyRelationshipDTO partyRelationshipDTO) {
        return prepare(null, partyRelationshipDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PartyRelationshipDTO> updatePartyRelationship(UUID partyRelationshipId, PartyRelationshipDTO partyRelationshipDTO) {
        return repository.findById(partyRelationshipId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party relationship not found with ID: " + partyRelationshipId)))
                .flatMap(existing -> prepare(partyRelationshipId, partyRelationshipDTO)
                        .map(mapper::toEntity)
                        .flatMap(updated -> {
                            updated.setPartyRelationshipId(partyRelationshipId);
                            return repository.save(updated);
                        }))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deletePartyRelationship(UUID partyRelationshipId) {
        return repository.findById(partyRelationshipId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party relationship not found with ID: " + partyRelationshipId)))
                .flatMap(partyRelationship -> repository.deleteById(partyRelationshipId));
    }

    @Override
    public Mono<PartyRelationshipDTO> getPartyRelationshipById(UUID partyRelationshipId) {
        return repository.findById(partyRelationshipId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party relationship not found with ID: " + partyRelationshipId)))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<List<PartyRelationshipDTO>> directOwners(UUID legalEntityPartyId) {
        return requireLegalEntity(legalEntityPartyId)
                .thenMany(repository.findByTargetPartyIdAndRelationshipKind(legalEntityPartyId, RelationshipKind.OWNERSHIP))
                .map(mapper::toDTO)
                .collectList();
    }

    @Override
    public Mono<List<PartyRelationshipDTO>> directSubsidiaries(UUID legalEntityPartyId) {
        return requireLegalEntity(legalEntityPartyId)
                .then(hierarchyEdges(null))
                .map(edges -> PartyHierarchyImpl.directChildren(legalEntityPartyId, edges).stream()
                        .map(mapper::toDTO)
                        .toList());
    }

    @Override
    public Mono<List<PartyAncestorDTO>> ancestors(UUID legalEntityPartyId, int depth) {
        if (depth < 1) {
            return Mono.error(new RuntimeException("Ancestor depth must be at least 1"));
        }
        return requireLegalEntity(legalEntityPartyId)
                .then(hierarchyEdges(null))
                .map(edges -> PartyHierarchyImpl.ancestors(legalEntityPartyId, edges, depth));
    }

    private Mono<PartyRelationshipDTO> prepare(UUID existingId, PartyRelationshipDTO dto) {
        if (dto.getRelationshipKind() == null) {
            return Mono.error(new RuntimeException("Relationship kind is required"));
        }
        if (dto.getSourcePartyId() == null || dto.getTargetPartyId() == null) {
            return Mono.error(new RuntimeException("Source party and target party are required"));
        }
        if (dto.getSourcePartyId().equals(dto.getTargetPartyId())) {
            return Mono.error(new RuntimeException("A party cannot have a relationship with itself"));
        }
        if (dto.getOwnershipPercentage() != null
                && (dto.getOwnershipPercentage().compareTo(BigDecimal.ZERO) < 0
                || dto.getOwnershipPercentage().compareTo(ONE_HUNDRED) > 0)) {
            return Mono.error(new RuntimeException("Ownership percentage must be between 0 and 100"));
        }
        if (dto.getEffectiveFrom() != null && dto.getEffectiveTo() != null
                && dto.getEffectiveTo().isBefore(dto.getEffectiveFrom())) {
            return Mono.error(new RuntimeException("Effective to must not be before effective from"));
        }
        if (dto.getEffectiveFrom() == null) {
            dto.setEffectiveFrom(LocalDateTime.now());
        }
        if (dto.getActive() == null) {
            dto.setActive(Boolean.TRUE);
        }
        return Mono.zip(
                        parties.findById(dto.getSourcePartyId())
                                .switchIfEmpty(Mono.error(new RuntimeException(
                                        "Party not found with ID: " + dto.getSourcePartyId()))),
                        parties.findById(dto.getTargetPartyId())
                                .switchIfEmpty(Mono.error(new RuntimeException(
                                        "Party not found with ID: " + dto.getTargetPartyId()))))
                .flatMap(tuple -> {
                    Party source = tuple.getT1();
                    Party target = tuple.getT2();
                    String rule = kindRule(dto.getRelationshipKind(), source.getPartyKind(), target.getPartyKind());
                    if (rule != null) {
                        return Mono.error(new RuntimeException(rule));
                    }
                    UUID tenant = dto.getTenantId() != null ? dto.getTenantId() : source.getTenantId();
                    if (tenant == null || !tenant.equals(source.getTenantId()) || !tenant.equals(target.getTenantId())) {
                        return Mono.error(new RuntimeException(
                                "Source and target parties must belong to the relationship tenant"));
                    }
                    dto.setTenantId(tenant);
                    if (!PartyHierarchyImpl.isHierarchyEdge(dto.getRelationshipKind(), source.getPartyKind())) {
                        return Mono.just(dto);
                    }
                    return hierarchyEdges(existingId)
                            .flatMap(edges -> {
                                if (PartyHierarchyImpl.wouldCycle(dto.getSourcePartyId(), dto.getTargetPartyId(), edges)) {
                                    return Mono.error(new RuntimeException(
                                            "The relationship would create a cycle"));
                                }
                                return Mono.just(dto);
                            });
                });
    }

    private Mono<Party> requireLegalEntity(UUID partyId) {
        return parties.findById(partyId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party not found with ID: " + partyId)))
                .flatMap(party -> party.getPartyKind() == PartyKind.ORGANIZATION
                        ? Mono.just(party)
                        : Mono.error(new RuntimeException("Party " + partyId + " is not a legal entity")));
    }

    private Mono<List<PartyRelationship>> hierarchyEdges(UUID ignoreId) {
        return repository.findByRelationshipKind(RelationshipKind.PARENT)
                .concatWith(repository.findByRelationshipKind(RelationshipKind.OWNERSHIP))
                .filter(row -> ignoreId == null || !ignoreId.equals(row.getPartyRelationshipId()))
                .collectList()
                .flatMap(rows -> {
                    List<UUID> ownershipSources = rows.stream()
                            .filter(row -> row.getRelationshipKind() == RelationshipKind.OWNERSHIP)
                            .map(PartyRelationship::getSourcePartyId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();
                    Mono<Map<UUID, PartyKind>> kinds = ownershipSources.isEmpty()
                            ? Mono.just(Map.of())
                            : parties.findAllById(ownershipSources)
                            .collectMap(Party::getPartyId, Party::getPartyKind);
                    return kinds.map(kindByParty -> rows.stream()
                            .filter(row -> PartyHierarchyImpl.isHierarchyEdge(
                                    row.getRelationshipKind(), kindByParty.get(row.getSourcePartyId())))
                            .toList());
                });
    }

    private static String kindRule(RelationshipKind kind, PartyKind source, PartyKind target) {
        return switch (kind) {
            case OWNERSHIP, CONTROL -> target == PartyKind.ORGANIZATION
                    ? null
                    : "Ownership and control apply to a legal entity";
            case PARENT -> source == PartyKind.ORGANIZATION && target == PartyKind.ORGANIZATION
                    ? null
                    : "A parent relationship is between legal entities";
            case OFFICER -> source == PartyKind.INDIVIDUAL && target == PartyKind.ORGANIZATION
                    ? null
                    : "An officer is a natural person of a legal entity";
        };
    }
}
