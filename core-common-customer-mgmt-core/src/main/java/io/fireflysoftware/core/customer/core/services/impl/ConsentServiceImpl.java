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
import io.fireflysoftware.core.customer.core.mappers.ConsentMapper;
import io.fireflysoftware.core.customer.core.services.ConsentService;
import io.fireflysoftware.core.customer.interfaces.dtos.ConsentDTO;
import io.fireflysoftware.core.customer.models.entities.Consent;
import io.fireflysoftware.core.customer.models.repositories.ConsentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class ConsentServiceImpl implements ConsentService {

    @Autowired
    private ConsentRepository repository;

    @Autowired
    private ConsentMapper mapper;

    @Override
    public Mono<PaginationResponse<ConsentDTO>> filterConsents(UUID partyId, FilterRequest<ConsentDTO> filterRequest) {
        PartyScopeImpl.scope(filterRequest, ConsentDTO::new, ConsentDTO::setPartyId, partyId);
        return FilterUtils
                .createFilter(
                        Consent.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<ConsentDTO> createConsent(UUID partyId, ConsentDTO consentDTO) {
        consentDTO.setPartyId(partyId);
        return Mono.just(consentDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<ConsentDTO> updateConsent(UUID partyId, UUID consentId, ConsentDTO consentDTO) {
        return repository.findById(consentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Consent not found with ID: " + consentId)))
                .flatMap(existingConsent -> PartyScopeImpl.requireOwner(partyId, existingConsent.getPartyId(), existingConsent, "Consent", consentId)
                        .flatMap(owned -> {
                            Consent updatedConsent = mapper.toEntity(consentDTO);
                            updatedConsent.setConsentId(consentId);
                            updatedConsent.setPartyId(partyId);
                            return repository.save(updatedConsent);
                        }))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteConsent(UUID partyId, UUID consentId) {
        return repository.findById(consentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Consent not found with ID: " + consentId)))
                .flatMap(consent -> PartyScopeImpl.requireOwner(partyId, consent.getPartyId(), consent, "Consent", consentId)
                        .flatMap(owned -> repository.deleteById(consentId)));
    }

    @Override
    public Mono<ConsentDTO> getConsentById(UUID partyId, UUID consentId) {
        return repository.findById(consentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Consent not found with ID: " + consentId)))
                .flatMap(consent -> PartyScopeImpl.requireOwner(partyId, consent.getPartyId(), consent, "Consent", consentId))
                .map(mapper::toDTO);
    }
}