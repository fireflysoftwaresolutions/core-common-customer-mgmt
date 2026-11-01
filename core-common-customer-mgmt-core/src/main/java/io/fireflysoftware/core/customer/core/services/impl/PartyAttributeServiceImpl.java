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

import io.fireflysoftware.core.customer.core.mappers.PartyAttributeMapper;
import io.fireflysoftware.core.customer.core.services.PartyAttributeService;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyAttributeDTO;
import io.fireflysoftware.core.customer.models.entities.PartyAttribute;
import io.fireflysoftware.core.customer.models.repositories.PartyAttributeRepository;
import io.fireflysoftware.core.customer.models.repositories.PartyRepository;
import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.filters.FilterUtils;
import org.fireflyframework.core.queries.PaginationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class PartyAttributeServiceImpl implements PartyAttributeService {

    @Autowired
    private PartyAttributeRepository repository;

    @Autowired
    private PartyRepository parties;

    @Autowired
    private PartyAttributeMapper mapper;

    @Override
    public Mono<PaginationResponse<PartyAttributeDTO>> filter(UUID partyId, FilterRequest<PartyAttributeDTO> filterRequest) {
        PartyScopeImpl.scope(filterRequest, PartyAttributeDTO::new, PartyAttributeDTO::setPartyId, partyId);
        return FilterUtils.createFilter(PartyAttribute.class, mapper::toDTO).filter(filterRequest);
    }

    @Override
    public Mono<PartyAttributeDTO> create(UUID partyId, PartyAttributeDTO dto) {
        dto.setPartyId(partyId);
        String invalid = validate(dto);
        if (invalid != null) {
            return Mono.error(new RuntimeException(invalid));
        }
        if (dto.getEffectiveFrom() == null) {
            dto.setEffectiveFrom(LocalDateTime.now());
        }
        return parties.findById(partyId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party not found with ID: " + partyId)))
                .then(Mono.fromSupplier(() -> mapper.toEntity(dto)))
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<PartyAttributeDTO> update(UUID partyId, UUID attributeId, PartyAttributeDTO dto) {
        dto.setPartyId(partyId);
        String invalid = validate(dto);
        if (invalid != null) {
            return Mono.error(new RuntimeException(invalid));
        }
        return repository.findById(attributeId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party attribute not found with ID: " + attributeId)))
                .flatMap(existing -> PartyScopeImpl.requireOwner(partyId, existing.getPartyId(), existing, "Party attribute", attributeId)
                        .flatMap(owned -> {
                            if (dto.getEffectiveFrom() == null) {
                                dto.setEffectiveFrom(owned.getEffectiveFrom());
                            }
                            PartyAttribute updated = mapper.toEntity(dto);
                            updated.setPartyAttributeId(attributeId);
                            updated.setPartyId(partyId);
                            return repository.save(updated);
                        }))
                .map(mapper::toDTO);
    }

    @Override
    public Mono<Void> delete(UUID partyId, UUID attributeId) {
        return repository.findById(attributeId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party attribute not found with ID: " + attributeId)))
                .flatMap(existing -> PartyScopeImpl.requireOwner(partyId, existing.getPartyId(), existing, "Party attribute", attributeId)
                        .flatMap(owned -> repository.deleteById(attributeId)));
    }

    @Override
    public Mono<PartyAttributeDTO> get(UUID partyId, UUID attributeId) {
        return repository.findById(attributeId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party attribute not found with ID: " + attributeId)))
                .flatMap(existing -> PartyScopeImpl.requireOwner(partyId, existing.getPartyId(), existing, "Party attribute", attributeId))
                .map(mapper::toDTO);
    }

    static String validate(PartyAttributeDTO dto) {
        if (dto.getAttributeKey() == null || dto.getAttributeKey().isBlank()) {
            return "Attribute key is required";
        }
        if (dto.getAttributeType() == null) {
            return "Attribute type is required";
        }
        if (dto.getAttributeValue() == null || dto.getAttributeValue().isBlank()) {
            return "Attribute value is required";
        }
        if (dto.getCurrencyCode() != null && !dto.getCurrencyCode().matches("^[A-Z]{3}$")) {
            return "Currency must be a 3-letter ISO code";
        }
        if (dto.getEffectiveFrom() != null && dto.getEffectiveTo() != null
                && dto.getEffectiveTo().isBefore(dto.getEffectiveFrom())) {
            return "Effective to must not be before effective from";
        }
        try {
            switch (dto.getAttributeType()) {
                case NUMBER -> new BigDecimal(dto.getAttributeValue().trim());
                case DATE -> LocalDate.parse(dto.getAttributeValue().trim());
                case BOOLEAN -> {
                    String value = dto.getAttributeValue().trim();
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        return "Boolean attribute value must be true or false";
                    }
                }
                case STRING -> {
                    // free text
                }
            }
        } catch (RuntimeException ex) {
            return "Attribute value does not match type " + dto.getAttributeType();
        }
        return null;
    }
}
