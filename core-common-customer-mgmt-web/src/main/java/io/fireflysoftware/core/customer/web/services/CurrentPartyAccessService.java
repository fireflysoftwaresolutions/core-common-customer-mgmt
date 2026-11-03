package io.fireflysoftware.core.customer.web.services;

import io.fireflysoftware.core.customer.interfaces.dtos.PartyDTO;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface CurrentPartyAccessService {
    Mono<PartyDTO> createParty(PartyDTO party);
    Mono<PartyDTO> getPartyById(UUID partyId);
    Mono<PartyDTO> updateParty(UUID partyId, PartyDTO party);
    Mono<Void> deleteParty(UUID partyId);
}
