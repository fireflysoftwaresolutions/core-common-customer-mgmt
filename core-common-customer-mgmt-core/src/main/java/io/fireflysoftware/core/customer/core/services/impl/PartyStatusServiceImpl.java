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
import io.fireflysoftware.core.customer.core.mappers.PartyStatusMapper;
import io.fireflysoftware.core.customer.core.services.PartyStatusService;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyStatusDTO;
import io.fireflysoftware.core.customer.interfaces.enums.StatusCode;
import io.fireflysoftware.core.customer.models.entities.PartyStatus;
import io.fireflysoftware.core.customer.models.repositories.PartyStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PartyStatusServiceImpl implements PartyStatusService {

    @Autowired
    private PartyStatusRepository repository;

    @Autowired
    private PartyStatusMapper mapper;

    @Override
    public Mono<PaginationResponse<PartyStatusDTO>> filterPartyStatuses(UUID partyId, FilterRequest<PartyStatusDTO> filterRequest) {
        PartyScopeImpl.scope(filterRequest, PartyStatusDTO::new, PartyStatusDTO::setPartyId, partyId);
        return FilterUtils
                .createFilter(
                        PartyStatus.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<PartyStatusDTO> createPartyStatus(UUID partyId, PartyStatusDTO partyStatusDTO) {
        partyStatusDTO.setPartyId(partyId);
        if (partyStatusDTO.getValidFrom() == null) {
            partyStatusDTO.setValidFrom(LocalDateTime.now());
        }
        return repository.findByPartyIdAndValidToIsNull(partyId)
                .flatMap(open -> {
                    open.setValidTo(partyStatusDTO.getValidFrom());
                    return repository.save(open);
                })
                .then(Mono.fromSupplier(() -> mapper.toEntity(partyStatusDTO)))
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PartyStatusDTO> updatePartyStatus(UUID partyId, PartyStatusDTO partyStatusDTO) {
        return repository.findByPartyIdAndValidToIsNull(partyId)
                .collectList()
                .flatMap(open -> applyUpdate(partyId, partyStatusDTO, open))
                .map(mapper::toDTO);
    }

    private Mono<PartyStatus> applyUpdate(UUID partyId, PartyStatusDTO partyStatusDTO, List<PartyStatus> open) {
        if (open.isEmpty()) {
            return Mono.error(new RuntimeException("Party status not found for party ID: " + partyId));
        }
        if (open.size() > 1) {
            return Mono.error(new RuntimeException("Party " + partyId + " has more than one open status"));
        }
        PartyStatus existing = open.get(0);
        StatusCode incoming = partyStatusDTO.getStatusCode();
        if (incoming != null && incoming != existing.getStatusCode()) {
            LocalDateTime at = partyStatusDTO.getValidFrom() != null ? partyStatusDTO.getValidFrom() : LocalDateTime.now();
            existing.setValidTo(at);
            PartyStatus next = mapper.toEntity(partyStatusDTO);
            next.setPartyStatusId(null);
            next.setPartyId(partyId);
            next.setValidFrom(at);
            next.setValidTo(null);
            return repository.save(existing).then(repository.save(next));
        }
        mapper.updateEntityFromDto(partyStatusDTO, existing);
        existing.setPartyId(partyId);
        return repository.save(existing);
    }

    @Override
    public Mono<Void> deletePartyStatus(UUID partyId, UUID partyStatusId) {
        return repository.findById(partyStatusId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party status not found with ID: " + partyStatusId)))
                .flatMap(partyStatus -> PartyScopeImpl.requireOwner(partyId, partyStatus.getPartyId(), partyStatus, "Party status", partyStatusId)
                        .flatMap(owned -> repository.deleteById(partyStatusId)));
    }

    @Override
    public Mono<PartyStatusDTO> getPartyStatusById(UUID partyId, UUID partyStatusId) {
        return repository.findById(partyStatusId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party status not found with ID: " + partyStatusId)))
                .flatMap(partyStatus -> PartyScopeImpl.requireOwner(partyId, partyStatus.getPartyId(), partyStatus, "Party status", partyStatusId))
                .map(mapper::toDTO);
    }
}
