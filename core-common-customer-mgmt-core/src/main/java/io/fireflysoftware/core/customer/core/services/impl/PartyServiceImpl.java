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
import io.fireflysoftware.core.customer.core.mappers.PartyMapper;
import io.fireflysoftware.core.customer.core.services.PartyService;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyDTO;
import io.fireflysoftware.core.customer.models.entities.Party;
import io.fireflysoftware.core.customer.models.repositories.PartyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class PartyServiceImpl implements PartyService {

    @Autowired
    private PartyRepository repository;

    @Autowired
    private PartyMapper mapper;

    @Override
    public Mono<PaginationResponse<PartyDTO>> filterParties(FilterRequest<PartyDTO> filterRequest) {
        return FilterUtils
                .createFilter(
                        Party.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<PartyDTO> createParty(PartyDTO partyDTO) {
        return Mono.just(partyDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PartyDTO> updateParty(UUID partyId, PartyDTO partyDTO) {
        return repository.findById(partyId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party not found with ID: " + partyId)))
                .flatMap(existingParty -> {
                    if (partyDTO == null || partyDTO.getTenantId() == null
                            || !partyDTO.getTenantId().equals(existingParty.getTenantId())
                            || (partyDTO.getPartyId() != null && !partyId.equals(partyDTO.getPartyId()))) {
                        return Mono.error(new IllegalArgumentException("Party identity and tenant cannot change"));
                    }
                    Party updatedParty = mapper.toEntity(partyDTO);
                    updatedParty.setPartyId(partyId);
                    updatedParty.setTenantId(existingParty.getTenantId());
                    updatedParty.setCreatedAt(existingParty.getCreatedAt());
                    updatedParty.setUpdatedAt(existingParty.getUpdatedAt());
                    return repository.save(updatedParty);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteParty(UUID partyId) {
        return repository.findById(partyId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party not found with ID: " + partyId)))
                .flatMap(party -> repository.deleteById(partyId));
    }

    @Override
    public Mono<PartyDTO> getPartyById(UUID partyId) {
        return repository.findById(partyId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party not found with ID: " + partyId)))
                .map(mapper::toDTO);
    }
}
