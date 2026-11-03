package io.fireflysoftware.core.customer.web.services;

import io.fireflysoftware.core.customer.interfaces.dtos.PartyDTO;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface CurrentPartyAccessService {
    Mono<org.fireflyframework.core.queries.PaginationResponse<PartyDTO>> filterParties(org.fireflyframework.core.filters.FilterRequest<PartyDTO> request);
    Mono<PartyDTO> createParty(PartyDTO party);
    Mono<PartyDTO> getPartyById(UUID partyId);
    Mono<PartyDTO> updateParty(UUID partyId, PartyDTO party);
    Mono<Void> deleteParty(UUID partyId);
}
