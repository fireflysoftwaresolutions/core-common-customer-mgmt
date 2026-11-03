package io.fireflysoftware.core.customer.core.services;

import reactor.core.publisher.Mono;
import java.util.UUID;

public interface PartyProfiles {
    Mono<Void> requirePersonSlot(UUID partyId);
    Mono<Void> requireOrganizationSlot(UUID partyId);
}
