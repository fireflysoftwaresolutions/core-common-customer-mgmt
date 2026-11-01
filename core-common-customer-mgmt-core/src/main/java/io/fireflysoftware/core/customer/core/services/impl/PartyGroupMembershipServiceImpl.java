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
import io.fireflysoftware.core.customer.core.mappers.PartyGroupMembershipMapper;
import io.fireflysoftware.core.customer.core.services.PartyGroupMembershipService;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyGroupMembershipDTO;
import io.fireflysoftware.core.customer.models.entities.PartyGroupMembership;
import io.fireflysoftware.core.customer.models.repositories.PartyGroupMembershipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class PartyGroupMembershipServiceImpl implements PartyGroupMembershipService {

    @Autowired
    private PartyGroupMembershipRepository repository;

    @Autowired
    private PartyGroupMembershipMapper mapper;

    @Override
    public Mono<PaginationResponse<PartyGroupMembershipDTO>> filterPartyGroupMemberships(UUID partyId, FilterRequest<PartyGroupMembershipDTO> filterRequest) {
        PartyScopeImpl.scope(filterRequest, PartyGroupMembershipDTO::new, PartyGroupMembershipDTO::setPartyId, partyId);
        return FilterUtils
                .createFilter(
                        PartyGroupMembership.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<PartyGroupMembershipDTO> createPartyGroupMembership(UUID partyId, PartyGroupMembershipDTO partyGroupMembershipDTO) {
        partyGroupMembershipDTO.setPartyId(partyId);
        return Mono.just(partyGroupMembershipDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PartyGroupMembershipDTO> updatePartyGroupMembership(UUID partyId, UUID partyGroupMembershipId, PartyGroupMembershipDTO partyGroupMembershipDTO) {
        return repository.findById(partyGroupMembershipId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party group membership not found with ID: " + partyGroupMembershipId)))
                .flatMap(existingPartyGroupMembership -> PartyScopeImpl.requireOwner(
                                partyId, existingPartyGroupMembership.getPartyId(), existingPartyGroupMembership,
                                "Party group membership", partyGroupMembershipId)
                        .flatMap(owned -> {
                            PartyGroupMembership updatedPartyGroupMembership = mapper.toEntity(partyGroupMembershipDTO);
                            updatedPartyGroupMembership.setPartyGroupMembershipId(partyGroupMembershipId);
                            updatedPartyGroupMembership.setPartyId(partyId);
                            return repository.save(updatedPartyGroupMembership);
                        }))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deletePartyGroupMembership(UUID partyId, UUID partyGroupMembershipId) {
        return repository.findById(partyGroupMembershipId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party group membership not found with ID: " + partyGroupMembershipId)))
                .flatMap(partyGroupMembership -> PartyScopeImpl.requireOwner(
                                partyId, partyGroupMembership.getPartyId(), partyGroupMembership,
                                "Party group membership", partyGroupMembershipId)
                        .flatMap(owned -> repository.deleteById(partyGroupMembershipId)));
    }

    @Override
    public Mono<PartyGroupMembershipDTO> getPartyGroupMembershipById(UUID partyId, UUID partyGroupMembershipId) {
        return repository.findById(partyGroupMembershipId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party group membership not found with ID: " + partyGroupMembershipId)))
                .flatMap(partyGroupMembership -> PartyScopeImpl.requireOwner(
                        partyId, partyGroupMembership.getPartyId(), partyGroupMembership,
                        "Party group membership", partyGroupMembershipId))
                .map(mapper::toDTO);
    }
}