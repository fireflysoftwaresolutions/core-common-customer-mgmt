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
import io.fireflysoftware.core.customer.core.mappers.IdentityDocumentMapper;
import io.fireflysoftware.core.customer.core.services.IdentityDocumentService;
import io.fireflysoftware.core.customer.interfaces.dtos.IdentityDocumentDTO;
import io.fireflysoftware.core.customer.models.entities.IdentityDocument;
import io.fireflysoftware.core.customer.models.repositories.IdentityDocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class IdentityDocumentServiceImpl implements IdentityDocumentService {

    @Autowired
    private IdentityDocumentRepository repository;

    @Autowired
    private IdentityDocumentMapper mapper;

    @Override
    public Mono<PaginationResponse<IdentityDocumentDTO>> filterIdentityDocuments(UUID partyId, FilterRequest<IdentityDocumentDTO> filterRequest) {
        if (filterRequest.getFilters() == null) {
            filterRequest.setFilters(new IdentityDocumentDTO());
        }
        filterRequest.getFilters().setPartyId(partyId);
        return FilterUtils
                .createFilter(
                        IdentityDocument.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<IdentityDocumentDTO> createIdentityDocument(UUID partyId, IdentityDocumentDTO identityDocumentDTO) {
        identityDocumentDTO.setPartyId(partyId);
        return Mono.just(identityDocumentDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<IdentityDocumentDTO> updateIdentityDocument(UUID partyId, UUID identityDocumentId, IdentityDocumentDTO identityDocumentDTO) {
        return repository.findById(identityDocumentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Identity document not found with ID: " + identityDocumentId)))
                .flatMap(existingIdentityDocument -> PartyScopeImpl.requireOwner(
                                partyId, existingIdentityDocument.getPartyId(), existingIdentityDocument,
                                "Identity document", identityDocumentId)
                        .flatMap(owned -> {
                            IdentityDocument updatedIdentityDocument = mapper.toEntity(identityDocumentDTO);
                            updatedIdentityDocument.setIdentityDocumentId(identityDocumentId);
                            updatedIdentityDocument.setPartyId(partyId);
                            return repository.save(updatedIdentityDocument);
                        }))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteIdentityDocument(UUID partyId, UUID identityDocumentId) {
        return repository.findById(identityDocumentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Identity document not found with ID: " + identityDocumentId)))
                .flatMap(identityDocument -> PartyScopeImpl.requireOwner(partyId, identityDocument.getPartyId(), identityDocument, "Identity document", identityDocumentId)
                        .flatMap(owned -> repository.deleteById(identityDocumentId)));
    }

    @Override
    public Mono<IdentityDocumentDTO> getIdentityDocumentById(UUID partyId, UUID identityDocumentId) {
        return repository.findById(identityDocumentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Identity document not found with ID: " + identityDocumentId)))
                .flatMap(identityDocument -> PartyScopeImpl.requireOwner(partyId, identityDocument.getPartyId(), identityDocument, "Identity document", identityDocumentId))
                .map(mapper::toDTO);
    }
}