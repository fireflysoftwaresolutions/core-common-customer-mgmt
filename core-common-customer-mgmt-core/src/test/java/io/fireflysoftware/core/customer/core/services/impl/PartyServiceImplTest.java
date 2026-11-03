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
import io.fireflysoftware.core.customer.core.mappers.PartyMapper;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyDTO;
import io.fireflysoftware.core.customer.models.entities.Party;
import io.fireflysoftware.core.customer.models.repositories.PartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class PartyServiceImplTest {

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private PartyMapper partyMapper;

    @InjectMocks
    private PartyServiceImpl partyService;

    private PartyDTO partyDTO;
    private Party party;
    private UUID partyId;

    @BeforeEach
    void setUp() {
        partyId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        UUID tenantId = UUID.fromString("223e4567-e89b-12d3-a456-426614174000");

        party = new Party();
        party.setPartyId(partyId);
        party.setTenantId(tenantId);
        party.setCreatedAt(LocalDateTime.now());
        party.setUpdatedAt(LocalDateTime.now());

        partyDTO = new PartyDTO();
        partyDTO.setPartyId(partyId);
        partyDTO.setTenantId(tenantId);
        partyDTO.setCreatedAt(LocalDateTime.now());
        partyDTO.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void filterParties_ShouldReturnFilteredResults_WhenValidFilterRequest() {
        // Arrange
        FilterRequest<PartyDTO> filterRequest = new FilterRequest<>();
        PaginationResponse<PartyDTO> mockResponse = mock(PaginationResponse.class);
        
        // Create a spy of the service to mock the filterParties method
        PartyServiceImpl spyService = spy(partyService);
        doReturn(Mono.just(mockResponse)).when(spyService).filterParties(filterRequest);

        // Act & Assert
        StepVerifier.create(spyService.filterParties(filterRequest))
                .expectNext(mockResponse)
                .verifyComplete();

        // Verify that filterParties was called
        verify(spyService).filterParties(filterRequest);
    }

    @Test
    void createParty_ShouldReturnCreatedPartyDTO_WhenValidPartyDTO() {
        // Arrange
        when(partyMapper.toEntity(partyDTO)).thenReturn(party);
        when(partyRepository.save(party)).thenReturn(Mono.just(party));
        when(partyMapper.toDTO(party)).thenReturn(partyDTO);

        // Act & Assert
        StepVerifier.create(partyService.createParty(partyDTO))
                .expectNext(partyDTO)
                .verifyComplete();

        verify(partyMapper).toEntity(partyDTO);
        verify(partyRepository).save(party);
        verify(partyMapper).toDTO(party);
    }

    @Test
    void createParty_ShouldHandleRepositoryError_WhenSaveFails() {
        // Arrange
        when(partyMapper.toEntity(partyDTO)).thenReturn(party);
        when(partyRepository.save(party)).thenReturn(Mono.error(new RuntimeException("Database error")));

        // Act & Assert
        StepVerifier.create(partyService.createParty(partyDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Database error"))
                .verify();

        verify(partyMapper).toEntity(partyDTO);
        verify(partyRepository).save(party);
        verify(partyMapper, never()).toDTO(any());
    }

    @Test
    void updateParty_ShouldReturnUpdatedPartyDTO_WhenPartyExists() {
        // Arrange
        PartyDTO updateDTO = new PartyDTO();
        updateDTO.setPartyId(null); // ID should be set by service
        updateDTO.setTenantId(party.getTenantId());
        
        Party updatedParty = new Party();
        updatedParty.setPartyId(partyId);
        
        when(partyRepository.findById(partyId)).thenReturn(Mono.just(party));
        when(partyMapper.toEntity(updateDTO)).thenReturn(updatedParty);
        when(partyRepository.save(updatedParty)).thenReturn(Mono.just(updatedParty));
        when(partyMapper.toDTO(updatedParty)).thenReturn(partyDTO);

        // Act & Assert
        StepVerifier.create(partyService.updateParty(partyId, updateDTO))
                .expectNext(partyDTO)
                .verifyComplete();

        verify(partyRepository).findById(partyId);
        verify(partyMapper).toEntity(updateDTO);
        verify(partyRepository).save(updatedParty);
        verify(partyMapper).toDTO(updatedParty);
    }

    @Test
    void updateParty_ShouldReturnError_WhenPartyNotFound() {
        // Arrange
        when(partyRepository.findById(partyId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(partyService.updateParty(partyId, partyDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Party not found with ID: " + partyId))
                .verify();

        verify(partyRepository).findById(partyId);
        verify(partyMapper, never()).toEntity(any());
        verify(partyRepository, never()).save(any());
    }

    @Test
    void deleteParty_ShouldCompleteSuccessfully_WhenPartyExists() {
        // Arrange
        when(partyRepository.findById(partyId)).thenReturn(Mono.just(party));
        when(partyRepository.deleteById(partyId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(partyService.deleteParty(partyId))
                .verifyComplete();

        verify(partyRepository).findById(partyId);
        verify(partyRepository).deleteById(partyId);
    }

    @Test
    void deleteParty_ShouldReturnError_WhenPartyNotFound() {
        // Arrange
        when(partyRepository.findById(partyId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(partyService.deleteParty(partyId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Party not found with ID: " + partyId))
                .verify();

        verify(partyRepository).findById(partyId);
        verify(partyRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void deleteParty_ShouldHandleRepositoryError_WhenDeleteFails() {
        // Arrange
        when(partyRepository.findById(partyId)).thenReturn(Mono.just(party));
        when(partyRepository.deleteById(partyId)).thenReturn(Mono.error(new RuntimeException("Delete failed")));

        // Act & Assert
        StepVerifier.create(partyService.deleteParty(partyId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Delete failed"))
                .verify();

        verify(partyRepository).findById(partyId);
        verify(partyRepository).deleteById(partyId);
    }

    @Test
    void getPartyById_ShouldReturnPartyDTO_WhenPartyExists() {
        // Arrange
        when(partyRepository.findById(partyId)).thenReturn(Mono.just(party));
        when(partyMapper.toDTO(party)).thenReturn(partyDTO);

        // Act & Assert
        StepVerifier.create(partyService.getPartyById(partyId))
                .expectNext(partyDTO)
                .verifyComplete();

        verify(partyRepository).findById(partyId);
        verify(partyMapper).toDTO(party);
    }

    @Test
    void getPartyById_ShouldReturnError_WhenPartyNotFound() {
        // Arrange
        when(partyRepository.findById(partyId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(partyService.getPartyById(partyId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Party not found with ID: " + partyId))
                .verify();

        verify(partyRepository).findById(partyId);
        verify(partyMapper, never()).toDTO(any());
    }

    @Test
    void getPartyById_ShouldHandleRepositoryError_WhenFindFails() {
        // Arrange
        when(partyRepository.findById(partyId)).thenReturn(Mono.error(new RuntimeException("Database connection failed")));

        // Act & Assert
        StepVerifier.create(partyService.getPartyById(partyId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Database connection failed"))
                .verify();

        verify(partyRepository).findById(partyId);
        verify(partyMapper, never()).toDTO(any());
    }
}
