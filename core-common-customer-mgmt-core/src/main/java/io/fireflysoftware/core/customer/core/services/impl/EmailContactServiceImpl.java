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
import io.fireflysoftware.core.customer.core.mappers.EmailContactMapper;
import io.fireflysoftware.core.customer.core.services.EmailContactService;
import io.fireflysoftware.core.customer.interfaces.dtos.EmailContactDTO;
import io.fireflysoftware.core.customer.models.entities.EmailContact;
import io.fireflysoftware.core.customer.models.repositories.EmailContactRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Service
@Transactional
public class EmailContactServiceImpl implements EmailContactService {

    @Autowired
    private EmailContactRepository repository;

    @Autowired
    private EmailContactMapper mapper;

    @Override
    public Mono<PaginationResponse<EmailContactDTO>> filterEmailContacts(UUID partyId, FilterRequest<EmailContactDTO> filterRequest) {
        if (filterRequest.getFilters() == null) {
            filterRequest.setFilters(new EmailContactDTO());
        }
        filterRequest.getFilters().setPartyId(partyId);
        return FilterUtils
                .createFilter(
                        EmailContact.class,
                        mapper::toDTO
                )
                .filter(filterRequest);
    }

    @Override
    public Mono<EmailContactDTO> createEmailContact(UUID partyId, EmailContactDTO emailContactDTO) {
        emailContactDTO.setPartyId(partyId);
        return Mono.just(emailContactDTO)
                .map(mapper::toEntity)
                .flatMap(repository::save)
                .map(mapper::toDTO);
    }

    @Override
    public Mono<EmailContactDTO> updateEmailContact(UUID partyId, UUID emailContactId, EmailContactDTO emailContactDTO) {
        return repository.findById(emailContactId)
                .switchIfEmpty(Mono.error(new RuntimeException("Email contact not found with ID: " + emailContactId)))
                .flatMap(existingEmailContact -> {
                    // Validate that the natural person belongs to the specified party
                    if (!partyId.equals(existingEmailContact.getPartyId())) {
                        return Mono.error(new RuntimeException("Email contact with ID " + emailContactId + " does not belong to party " + partyId));
                    }
                    mapper.updateEntityFromDto(emailContactDTO, existingEmailContact);
                    existingEmailContact.setPartyId(partyId);
                    return repository.save(existingEmailContact);
                })
                .map(mapper::toDTO);
    }


    @Override
    public Mono<Void> deleteEmailContact(UUID partyId, UUID emailContactId) {
        return repository.findById(emailContactId)
                .switchIfEmpty(Mono.error(new RuntimeException("Email contact not found with ID: " + emailContactId)))
                .flatMap(emailContact -> PartyScopeImpl.requireOwner(partyId, emailContact.getPartyId(), emailContact, "Email contact", emailContactId)
                        .flatMap(owned -> repository.deleteById(emailContactId)));
    }

    @Override
    public Mono<EmailContactDTO> getEmailContactById(UUID partyId, UUID emailContactId) {
        return repository.findById(emailContactId)
                .switchIfEmpty(Mono.error(new RuntimeException("Email contact not found with ID: " + emailContactId)))
                .flatMap(emailContact -> PartyScopeImpl.requireOwner(partyId, emailContact.getPartyId(), emailContact, "Email contact", emailContactId))
                .map(mapper::toDTO);
    }
}