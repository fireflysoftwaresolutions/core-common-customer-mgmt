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

import io.fireflysoftware.core.customer.core.mappers.PartyIndustryClassificationMapper;
import io.fireflysoftware.core.customer.core.services.PartyIndustryClassificationService;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyIndustryClassificationDTO;
import io.fireflysoftware.core.customer.models.entities.PartyIndustryClassification;
import io.fireflysoftware.core.customer.models.repositories.PartyIndustryClassificationRepository;
import io.fireflysoftware.core.customer.models.repositories.PartyRepository;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@Transactional
public class PartyIndustryClassificationServiceImpl implements PartyIndustryClassificationService {

    @Autowired
    private PartyIndustryClassificationRepository repository;

    @Autowired
    private PartyRepository parties;

    @Autowired
    private PartyIndustryClassificationMapper mapper;

    @Override
    public Mono<PaginationResponse<PartyIndustryClassificationDTO>> filter(UUID partyId, FilterRequest<PartyIndustryClassificationDTO> filterRequest) {
        PartyScopeImpl.scope(filterRequest, PartyIndustryClassificationDTO::new, PartyIndustryClassificationDTO::setPartyId, partyId);
        return FilterUtils.createFilter(PartyIndustryClassification.class, mapper::toDTO).filter(filterRequest);
    }

    @Override
    public Mono<PartyIndustryClassificationDTO> create(UUID partyId, PartyIndustryClassificationDTO dto) {
        dto.setPartyId(partyId);
        if (dto.getIsPrimary() == null) {
            dto.setIsPrimary(Boolean.FALSE);
        }
        return requireParty(partyId)
                .then(Mono.defer(() -> Boolean.TRUE.equals(dto.getIsPrimary())
                        ? clearPrimary(partyId)
                        : Mono.empty()))
                .then(Mono.fromSupplier(() -> mapper.toEntity(dto)))
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PartyIndustryClassificationDTO> update(UUID partyId, UUID classificationId, PartyIndustryClassificationDTO dto) {
        return repository.findById(classificationId)
                .switchIfEmpty(Mono.error(new RuntimeException(
                        "Industry classification not found with ID: " + classificationId)))
                .flatMap(existing -> PartyScopeImpl.requireOwner(
                                partyId, existing.getPartyId(), existing, "Industry classification", classificationId)
                        .flatMap(owned -> {
                            dto.setPartyId(partyId);
                            if (dto.getIsPrimary() == null) {
                                dto.setIsPrimary(owned.getIsPrimary());
                            }
                            Mono<Void> clear = Boolean.TRUE.equals(dto.getIsPrimary())
                                    ? clearPrimary(partyId)
                                    : Mono.empty();
                            return clear.then(Mono.fromSupplier(() -> {
                                PartyIndustryClassification updated = mapper.toEntity(dto);
                                updated.setPartyIndustryClassificationId(classificationId);
                                updated.setPartyId(partyId);
                                return updated;
                            })).flatMap(repository::save);
                        }))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> delete(UUID partyId, UUID classificationId) {
        return repository.findById(classificationId)
                .switchIfEmpty(Mono.error(new RuntimeException(
                        "Industry classification not found with ID: " + classificationId)))
                .flatMap(existing -> PartyScopeImpl.requireOwner(
                                partyId, existing.getPartyId(), existing, "Industry classification", classificationId)
                        .flatMap(owned -> repository.deleteById(classificationId)));
    }

    @Override
    public Mono<PartyIndustryClassificationDTO> get(UUID partyId, UUID classificationId) {
        return repository.findById(classificationId)
                .switchIfEmpty(Mono.error(new RuntimeException(
                        "Industry classification not found with ID: " + classificationId)))
                .flatMap(existing -> PartyScopeImpl.requireOwner(
                        partyId, existing.getPartyId(), existing, "Industry classification", classificationId))
                .map(mapper::toDTO);
    }

    private Mono<Void> requireParty(UUID partyId) {
        return parties.findById(partyId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party not found with ID: " + partyId)))
                .then();
    }

    private Mono<Void> clearPrimary(UUID partyId) {
        return repository.findByPartyIdAndIsPrimary(partyId, Boolean.TRUE)
                .flatMap(row -> {
                    row.setIsPrimary(Boolean.FALSE);
                    return repository.save(row);
                })
                .then();
    }
}
