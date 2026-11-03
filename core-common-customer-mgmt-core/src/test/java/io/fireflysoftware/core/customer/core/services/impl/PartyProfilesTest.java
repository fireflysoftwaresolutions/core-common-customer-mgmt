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

import io.fireflysoftware.core.customer.interfaces.enums.PartyKind;
import io.fireflysoftware.core.customer.models.entities.NaturalPerson;
import io.fireflysoftware.core.customer.models.entities.Party;
import io.fireflysoftware.core.customer.models.repositories.LegalEntityRepository;
import io.fireflysoftware.core.customer.models.repositories.NaturalPersonRepository;
import io.fireflysoftware.core.customer.models.repositories.PartyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyProfilesTest {

    @Mock
    private PartyRepository parties;

    @Mock
    private NaturalPersonRepository persons;

    @Mock
    private LegalEntityRepository organizations;

    @InjectMocks
    private PartyProfilesImpl profiles;

    private final UUID partyId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");

    @Test
    void requirePersonSlot_rejectsAnOrganization() {
        Party party = new Party();
        party.setPartyId(partyId);
        party.setPartyKind(PartyKind.ORGANIZATION);
        when(parties.findById(partyId)).thenReturn(Mono.just(party));

        StepVerifier.create(profiles.requirePersonSlot(partyId))
                .expectErrorMatches(error -> error.getMessage().contains("cannot hold a person profile"))
                .verify();
    }

    @Test
    void requireOrganizationSlot_rejectsAPartyThatAlreadyHasAPerson() {
        Party party = new Party();
        party.setPartyId(partyId);
        party.setPartyKind(PartyKind.ORGANIZATION);
        when(parties.findById(partyId)).thenReturn(Mono.just(party));
        when(persons.findByPartyId(partyId)).thenReturn(Flux.just(new NaturalPerson()));

        StepVerifier.create(profiles.requireOrganizationSlot(partyId))
                .expectErrorMatches(error -> error.getMessage().contains("already has a person profile"))
                .verify();
    }

    @Test
    void requirePersonSlot_rejectsASecondPersonProfile() {
        Party party = new Party();
        party.setPartyId(partyId);
        party.setPartyKind(PartyKind.INDIVIDUAL);
        when(parties.findById(partyId)).thenReturn(Mono.just(party));
        when(organizations.findByPartyId(partyId)).thenReturn(Flux.empty());
        when(persons.findByPartyId(partyId)).thenReturn(Flux.just(new NaturalPerson()));

        StepVerifier.create(profiles.requirePersonSlot(partyId))
                .expectErrorMatches(error -> error.getMessage().contains("already has a person profile"))
                .verify();
    }

    @Test
    void requireOrganizationSlot_acceptsAnEmptyOrganizationParty() {
        Party party = new Party();
        party.setPartyId(partyId);
        party.setPartyKind(PartyKind.ORGANIZATION);
        when(parties.findById(partyId)).thenReturn(Mono.just(party));
        when(persons.findByPartyId(partyId)).thenReturn(Flux.empty());
        when(organizations.findByPartyId(partyId)).thenReturn(Flux.empty());

        StepVerifier.create(profiles.requireOrganizationSlot(partyId))
                .verifyComplete();
    }

    @Test
    void requireOrganizationSlot_messageUsesAPersonWhenTheOtherProfileExists() {
        Party party = new Party();
        party.setPartyKind(PartyKind.ORGANIZATION);
        when(parties.findById(partyId)).thenReturn(Mono.just(party));
        when(persons.findByPartyId(partyId)).thenReturn(Flux.just(new NaturalPerson()));

        StepVerifier.create(profiles.requireOrganizationSlot(partyId))
                .expectErrorMatches(error -> error.getMessage().contains("a person profile"))
                .verify();
    }
}
