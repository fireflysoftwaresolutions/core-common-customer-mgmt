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
import io.fireflysoftware.core.customer.models.repositories.LegalEntityRepository;
import io.fireflysoftware.core.customer.models.repositories.NaturalPersonRepository;
import io.fireflysoftware.core.customer.models.repositories.PartyRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * A party is either a person or an organization, never both and never the other kind.
 */
@Component("partyProfiles")
public class PartyProfilesImpl {

    private final PartyRepository parties;
    private final NaturalPersonRepository persons;
    private final LegalEntityRepository organizations;

    public PartyProfilesImpl(PartyRepository parties,
                         NaturalPersonRepository persons,
                         LegalEntityRepository organizations) {
        this.parties = parties;
        this.persons = persons;
        this.organizations = organizations;
    }

    public Mono<Void> requirePersonSlot(UUID partyId) {
        return require(partyId, PartyKind.INDIVIDUAL, "a person profile", "an organization profile");
    }

    public Mono<Void> requireOrganizationSlot(UUID partyId) {
        return require(partyId, PartyKind.ORGANIZATION, "an organization profile", "a person profile");
    }

    private Mono<Void> require(UUID partyId, PartyKind kind, String profile, String otherProfile) {
        return parties.findById(partyId)
                .switchIfEmpty(Mono.error(new RuntimeException("Party not found with ID: " + partyId)))
                .flatMap(party -> {
                    if (party.getPartyKind() != kind) {
                        return Mono.error(new RuntimeException(
                                "Party " + partyId + " is " + party.getPartyKind() + " and cannot hold " + profile));
                    }
                    Mono<Boolean> otherExists = Mono.defer(() -> kind == PartyKind.INDIVIDUAL
                            ? organizations.findByPartyId(partyId).hasElements()
                            : persons.findByPartyId(partyId).hasElements());
                    Mono<Boolean> sameExists = Mono.defer(() -> kind == PartyKind.INDIVIDUAL
                            ? persons.findByPartyId(partyId).hasElements()
                            : organizations.findByPartyId(partyId).hasElements());
                    return otherExists.flatMap(hasOther -> {
                        if (hasOther) {
                            return Mono.error(new RuntimeException(
                                    "Party " + partyId + " already has " + otherProfile));
                        }
                        return sameExists;
                    }).flatMap(hasSame -> {
                        if (hasSame) {
                            return Mono.error(new RuntimeException(
                                    "Party " + partyId + " already has " + profile));
                        }
                        return Mono.empty();
                    });
                });
    }
}
