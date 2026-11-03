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

import io.fireflysoftware.core.customer.core.services.PartyProfiles;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.customer.core.mappers.LegalEntityMapper;
import io.fireflysoftware.core.customer.core.services.LegalEntityService;
import io.fireflysoftware.core.customer.interfaces.dtos.LegalEntityDTO;
import io.fireflysoftware.core.customer.models.entities.LegalEntity;
import io.fireflysoftware.core.customer.models.repositories.LegalEntityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class LegalEntityServiceImpl implements LegalEntityService {

    @Autowired
    private LegalEntityRepository repository;

    @Autowired
    private LegalEntityMapper mapper;

    @Autowired
    private PartyProfiles partyProfiles;

    @Override
    public Mono<PaginationResponse<LegalEntityDTO>> filterLegalEntities(UUID partyId, FilterRequest<LegalEntityDTO> filterRequest) {
        PartyScopeImpl.scope(filterRequest, LegalEntityDTO::new, LegalEntityDTO::setPartyId, partyId);
        return FilterUtils
                .createFilter(
                        LegalEntity.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<LegalEntityDTO> createLegalEntity(UUID partyId, LegalEntityDTO legalEntityDTO) {
        legalEntityDTO.setPartyId(partyId);
        return partyProfiles.requireOrganizationSlot(partyId)
                .then(Mono.fromSupplier(() -> mapper.toEntity(legalEntityDTO)))
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<LegalEntityDTO> updateLegalEntity(UUID partyId, UUID legalEntityId, LegalEntityDTO legalEntityDTO) {
        return repository.findById(legalEntityId)
                .switchIfEmpty(Mono.error(new RuntimeException("Legal entity not found with ID: " + legalEntityId)))
                .flatMap(existingLegalEntity -> {
                    // Validate that the natural person belongs to the specified party
                    if (!partyId.equals(existingLegalEntity.getPartyId())) {
                        return Mono.error(new RuntimeException("Legal entity with ID " + legalEntityId + " does not belong to party " + partyId));
                    }
                    mapper.updateEntityFromDto(legalEntityDTO, existingLegalEntity);
                    existingLegalEntity.setPartyId(partyId);
                    return repository.save(existingLegalEntity);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteLegalEntity(UUID partyId, UUID legalEntityId) {
        return repository.findById(legalEntityId)
                .switchIfEmpty(Mono.error(new RuntimeException("Legal entity not found with ID: " + legalEntityId)))
                .flatMap(legalEntity -> PartyScopeImpl.requireOwner(partyId, legalEntity.getPartyId(), legalEntity, "Legal entity", legalEntityId)
                        .flatMap(owned -> repository.deleteById(legalEntityId)));
    }

    @Override
    public Mono<LegalEntityDTO> getLegalEntityById(UUID partyId, UUID legalEntityId) {
        return repository.findById(legalEntityId)
                .switchIfEmpty(Mono.error(new RuntimeException("Legal entity not found with ID: " + legalEntityId)))
                .flatMap(legalEntity -> PartyScopeImpl.requireOwner(partyId, legalEntity.getPartyId(), legalEntity, "Legal entity", legalEntityId))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<LegalEntityDTO> getLegalEntityByPartyId(UUID partyId) {
        return repository.findByPartyId(partyId)
                .map(mapper::toDTO)
                .next()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Legal entity not found for partyId: " + partyId)));
    }
}
