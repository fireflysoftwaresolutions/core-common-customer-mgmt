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
import io.fireflysoftware.core.customer.core.mappers.NaturalPersonMapper;
import io.fireflysoftware.core.customer.core.services.NaturalPersonService;
import io.fireflysoftware.core.customer.interfaces.dtos.NaturalPersonDTO;
import io.fireflysoftware.core.customer.models.entities.NaturalPerson;
import io.fireflysoftware.core.customer.models.repositories.NaturalPersonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class NaturalPersonServiceImpl implements NaturalPersonService {

    @Autowired
    private NaturalPersonRepository repository;

    @Autowired
    private NaturalPersonMapper mapper;

    @Autowired
    private PartyProfiles partyProfiles;

    @Override
    public Mono<PaginationResponse<NaturalPersonDTO>> filterNaturalPersons(UUID partyId, FilterRequest<NaturalPersonDTO> filterRequest) {
        PartyScopeImpl.scope(filterRequest, NaturalPersonDTO::new, NaturalPersonDTO::setPartyId, partyId);
        return FilterUtils
                .createFilter(
                        NaturalPerson.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<NaturalPersonDTO> createNaturalPerson(UUID partyId, NaturalPersonDTO naturalPersonDTO) {
        return requireName(naturalPersonDTO)
                .then(partyProfiles.requirePersonSlot(partyId))
                .then(Mono.fromSupplier(() -> {
                    naturalPersonDTO.setPartyId(partyId);
                    return mapper.toEntity(naturalPersonDTO);
                }))
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<NaturalPersonDTO> updateNaturalPerson(UUID partyId, UUID naturalPersonId, NaturalPersonDTO naturalPersonDTO) {
        return repository.findById(naturalPersonId)
                .switchIfEmpty(Mono.error(new RuntimeException("Natural person not found with ID: " + naturalPersonId)))
                .flatMap(existingNaturalPerson -> {
                    // Validate that the natural person belongs to the specified party
                    if (!partyId.equals(existingNaturalPerson.getPartyId())) {
                        return Mono.error(new RuntimeException("Natural person with ID " + naturalPersonId + " does not belong to party " + partyId));
                    }
                    mapper.updateEntityFromDto(naturalPersonDTO, existingNaturalPerson);
                    existingNaturalPerson.setPartyId(partyId);
                    if (blank(existingNaturalPerson.getGivenName()) && blank(existingNaturalPerson.getFamilyName())) {
                        return Mono.error(new RuntimeException("A natural person needs a given name or a family name"));
                    }
                    return repository.save(existingNaturalPerson);
                })
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> deleteNaturalPerson(UUID partyId, UUID naturalPersonId) {
        return repository.findById(naturalPersonId)
                .switchIfEmpty(Mono.error(new RuntimeException("Natural person not found with ID: " + naturalPersonId)))
                .flatMap(naturalPerson -> {
                    // Validate that the natural person belongs to the specified party
                    if (!partyId.equals(naturalPerson.getPartyId())) {
                        return Mono.error(new RuntimeException("Natural person with ID " + naturalPersonId + " does not belong to party " + partyId));
                    }
                    return repository.deleteById(naturalPersonId);
                });
    }

    @Override
    public Mono<NaturalPersonDTO> getNaturalPersonById(UUID partyId, UUID naturalPersonId) {
        return repository.findById(naturalPersonId)
                .switchIfEmpty(Mono.error(new RuntimeException("Natural person not found with ID: " + naturalPersonId)))
                .flatMap(naturalPerson -> {
                    // Validate that the natural person belongs to the specified party
                    if (!partyId.equals(naturalPerson.getPartyId())) {
                        return Mono.error(new RuntimeException("Natural person with ID " + naturalPersonId + " does not belong to party " + partyId));
                    }
                    return Mono.just(mapper.toDTO(naturalPerson));
                });
    }

    @Override
    public Mono<NaturalPersonDTO> getNaturalPersonByPartyId(UUID partyId) {
        return repository.findByPartyId(partyId)
                .map(mapper::toDTO)
                .next()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Natural person not found for partyId: " + partyId)));
    }

    private static Mono<Void> requireName(NaturalPersonDTO dto) {
        if (blank(dto.getGivenName()) && blank(dto.getFamilyName())) {
            return Mono.error(new RuntimeException("A natural person needs a given name or a family name"));
        }
        return Mono.empty();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
