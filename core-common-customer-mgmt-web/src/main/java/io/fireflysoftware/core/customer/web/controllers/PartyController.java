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


package io.fireflysoftware.core.customer.web.controllers;

import org.fireflyframework.core.filters.FilterRequest;
import org.fireflyframework.core.queries.PaginationResponse;
import io.fireflysoftware.core.customer.web.services.CurrentPartyAccessService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@SecurityRequirement(name = "customerBearer")
@RequestMapping("/api/v1/parties")
@RequiredArgsConstructor
@Validated
@Tag(
    name = "Parties", 
    description = "API for managing parties - the main customer entities in the system"
)
public class PartyController {

    private final CurrentPartyAccessService currentPartyAccessService;

    @PostMapping("/filter")
    @Operation(
        summary = "Filter parties",
        description = "Retrieve a paginated list of parties based on filtering criteria"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "Successfully retrieved filtered parties"
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid filter request",
            content = @Content
        )
    })
    public Mono<ResponseEntity<PaginationResponse<PartyDTO>>> filterParties(
            @Parameter(description = "Filter criteria for parties", required = true)
            @Valid @RequestBody FilterRequest<PartyDTO> filterRequest) {
        return currentPartyAccessService.filterParties(filterRequest)
                .map(page -> ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                        .varyBy("Authorization").body(page));
    }

    @PostMapping
    @Operation(
        summary = "Create party",
        description = "Create a new party in the system"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201", 
            description = "Party successfully created",
            content = @Content(schema = @Schema(implementation = PartyDTO.class))
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid party data",
            content = @Content
        )
    })
    public Mono<ResponseEntity<PartyDTO>> createParty(
            @Parameter(description = "Party data to create", required = true)
            @Valid @RequestBody PartyDTO partyDTO) {
        return currentPartyAccessService.createParty(partyDTO)
                .map(party -> ResponseEntity.status(HttpStatus.CREATED).cacheControl(org.springframework.http.CacheControl.noStore())
                        .varyBy("Authorization").body(party));
    }

    @GetMapping("/{partyId}")
    @Operation(
        summary = "Get party by ID",
        description = "Retrieve a specific party by its unique identifier"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "Party found",
            content = @Content(schema = @Schema(implementation = PartyDTO.class))
        ),
        @ApiResponse(
            responseCode = "404", 
            description = "Party not found",
            content = @Content
        )
    })
    public Mono<ResponseEntity<PartyDTO>> getPartyById(
            @Parameter(description = "Unique identifier of the party", required = true)
            @PathVariable UUID partyId) {
        return currentPartyAccessService.getPartyById(partyId)
                .map(party -> ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                        .varyBy("Authorization").body(party));
    }

    @PutMapping("/{partyId}")
    @Operation(
        summary = "Update party",
        description = "Update an existing party with new information"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200", 
            description = "Party successfully updated",
            content = @Content(schema = @Schema(implementation = PartyDTO.class))
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Invalid party data",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "404", 
            description = "Party not found",
            content = @Content
        )
    })
    public Mono<ResponseEntity<PartyDTO>> updateParty(
            @Parameter(description = "Unique identifier of the party", required = true)
            @PathVariable UUID partyId,
            @Parameter(description = "Updated party data", required = true)
            @Valid @RequestBody PartyDTO partyDTO) {
        return currentPartyAccessService.updateParty(partyId, partyDTO)
                .map(party -> ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                        .varyBy("Authorization").body(party));
    }

    @DeleteMapping("/{partyId}")
    @Operation(
        summary = "Delete party",
        description = "Delete a party from the system"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "204", 
            description = "Party successfully deleted"
        ),
        @ApiResponse(
            responseCode = "404", 
            description = "Party not found",
            content = @Content
        )
    })
    public Mono<ResponseEntity<Void>> deleteParty(
            @Parameter(description = "Unique identifier of the party", required = true)
            @PathVariable UUID partyId) {
        return currentPartyAccessService.deleteParty(partyId)
                .then(Mono.just(ResponseEntity.noContent().cacheControl(org.springframework.http.CacheControl.noStore())
                        .varyBy("Authorization").build()));
    }
}
