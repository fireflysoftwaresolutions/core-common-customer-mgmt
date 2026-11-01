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
import io.fireflysoftware.core.customer.core.mappers.PartyProviderMapper;
import io.fireflysoftware.core.customer.core.services.PartyProviderService;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyProviderDTO;
import io.fireflysoftware.core.customer.models.entities.PartyProvider;
import io.fireflysoftware.core.customer.models.repositories.PartyProviderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class PartyProviderServiceImpl implements PartyProviderService {

    @Autowired
    private PartyProviderRepository repository;

    @Autowired
    private PartyProviderMapper mapper;

    @Override
    public Mono<PaginationResponse<PartyProviderDTO>> filterPartyProviders(UUID partyId, FilterRequest<PartyProviderDTO> filterRequest) {
        PartyScopeImpl.scope(filterRequest, PartyProviderDTO::new, PartyProviderDTO::setPartyId, partyId);
        return FilterUtils
                .createFilter(
                        PartyProvider.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<PartyProviderDTO> createPartyProvider(UUID partyId, PartyProviderDTO partyProviderDTO) {
        partyProviderDTO.setPartyId(partyId);
        return Mono.just(partyProviderDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PartyProviderDTO> updatePartyProvider(UUID partyId, UUID partyProviderId, PartyProviderDTO partyProviderDTO) {
        return repository.findById(partyProviderId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party provider not found with ID: " + partyProviderId)))
                .flatMap(existingPartyProvider -> PartyScopeImpl.requireOwner(
                                partyId, existingPartyProvider.getPartyId(), existingPartyProvider,
                                "Party provider", partyProviderId)
                        .flatMap(owned -> {
                            PartyProvider updatedPartyProvider = mapper.toEntity(partyProviderDTO);
                            updatedPartyProvider.setPartyProviderId(partyProviderId);
                            updatedPartyProvider.setPartyId(partyId);
                            return repository.save(updatedPartyProvider);
                        }))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deletePartyProvider(UUID partyId, UUID partyProviderId) {
        return repository.findById(partyProviderId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party provider not found with ID: " + partyProviderId)))
                .flatMap(partyProvider -> PartyScopeImpl.requireOwner(partyId, partyProvider.getPartyId(), partyProvider, "Party provider", partyProviderId)
                        .flatMap(owned -> repository.deleteById(partyProviderId)));
    }

    @Override
    public Mono<PartyProviderDTO> getPartyProviderById(UUID partyId, UUID partyProviderId) {
        return repository.findById(partyProviderId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party provider not found with ID: " + partyProviderId)))
                .flatMap(partyProvider -> PartyScopeImpl.requireOwner(partyId, partyProvider.getPartyId(), partyProvider, "Party provider", partyProviderId))
                .map(mapper::toDTO);
    }
}