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
import io.fireflysoftware.core.customer.core.mappers.PoliticallyExposedPersonMapper;
import io.fireflysoftware.core.customer.core.services.PoliticallyExposedPersonService;
import io.fireflysoftware.core.customer.interfaces.dtos.PoliticallyExposedPersonDTO;
import io.fireflysoftware.core.customer.models.entities.PoliticallyExposedPerson;
import io.fireflysoftware.core.customer.models.repositories.PoliticallyExposedPersonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class PoliticallyExposedPersonServiceImpl implements PoliticallyExposedPersonService {

    @Autowired
    private PoliticallyExposedPersonRepository repository;

    @Autowired
    private PoliticallyExposedPersonMapper mapper;

    @Override
    public Mono<PaginationResponse<PoliticallyExposedPersonDTO>> filterPoliticallyExposedPersons(UUID partyId, FilterRequest<PoliticallyExposedPersonDTO> filterRequest) {
        PartyScopeImpl.scope(filterRequest, PoliticallyExposedPersonDTO::new, PoliticallyExposedPersonDTO::setPartyId, partyId);
        return FilterUtils
                .createFilter(
                        PoliticallyExposedPerson.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<PoliticallyExposedPersonDTO> createPoliticallyExposedPerson(UUID partyId, PoliticallyExposedPersonDTO politicallyExposedPersonDTO) {
        politicallyExposedPersonDTO.setPartyId(partyId);
        return Mono.just(politicallyExposedPersonDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PoliticallyExposedPersonDTO> updatePoliticallyExposedPerson(UUID partyId, UUID politicallyExposedPersonId, PoliticallyExposedPersonDTO politicallyExposedPersonDTO) {
        return repository.findById(politicallyExposedPersonId)
                .switchIfEmpty(Mono.error(new RuntimeException("Politically exposed person not found with ID: " + politicallyExposedPersonId)))
                .flatMap(existingPoliticallyExposedPerson -> PartyScopeImpl.requireOwner(
                                partyId, existingPoliticallyExposedPerson.getPartyId(), existingPoliticallyExposedPerson,
                                "Politically exposed person", politicallyExposedPersonId)
                        .flatMap(owned -> {
                            PoliticallyExposedPerson updatedPoliticallyExposedPerson = mapper.toEntity(politicallyExposedPersonDTO);
                            updatedPoliticallyExposedPerson.setPepId(politicallyExposedPersonId);
                            updatedPoliticallyExposedPerson.setPartyId(partyId);
                            return repository.save(updatedPoliticallyExposedPerson);
                        }))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deletePoliticallyExposedPerson(UUID partyId, UUID politicallyExposedPersonId) {
        return repository.findById(politicallyExposedPersonId)
                .switchIfEmpty(Mono.error(new RuntimeException("Politically exposed person not found with ID: " + politicallyExposedPersonId)))
                .flatMap(politicallyExposedPerson -> PartyScopeImpl.requireOwner(
                                partyId, politicallyExposedPerson.getPartyId(), politicallyExposedPerson,
                                "Politically exposed person", politicallyExposedPersonId)
                        .flatMap(owned -> repository.deleteById(politicallyExposedPersonId)));
    }

    @Override
    public Mono<PoliticallyExposedPersonDTO> getPoliticallyExposedPersonById(UUID partyId, UUID politicallyExposedPersonId) {
        return repository.findById(politicallyExposedPersonId)
                .switchIfEmpty(Mono.error(new RuntimeException("Politically exposed person not found with ID: " + politicallyExposedPersonId)))
                .flatMap(politicallyExposedPerson -> PartyScopeImpl.requireOwner(
                        partyId, politicallyExposedPerson.getPartyId(), politicallyExposedPerson,
                        "Politically exposed person", politicallyExposedPersonId))
                .map(mapper::toDTO);
    }
}