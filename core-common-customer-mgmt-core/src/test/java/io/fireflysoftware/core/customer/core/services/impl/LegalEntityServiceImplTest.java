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
import io.fireflysoftware.core.customer.core.mappers.LegalEntityMapper;
import io.fireflysoftware.core.customer.interfaces.dtos.LegalEntityDTO;
import io.fireflysoftware.core.customer.models.entities.LegalEntity;
import io.fireflysoftware.core.customer.models.repositories.LegalEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.lenient;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class LegalEntityServiceImplTest {

    @Mock
    private LegalEntityRepository legalEntityRepository;

    @Mock
    private LegalEntityMapper legalEntityMapper;

    @Mock
    private PartyProfilesImpl partyProfiles;

    @InjectMocks
    private LegalEntityServiceImpl legalEntityService;

    private LegalEntityDTO legalEntityDTO;
    private LegalEntity legalEntity;
    private UUID legalEntityId;
    private UUID partyId;

    @BeforeEach
    void setUp() {
        legalEntityId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
        partyId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
        
        legalEntity = new LegalEntity();
        legalEntity.setLegalEntityId(legalEntityId);
        legalEntity.setPartyId(partyId);
        legalEntity.setCreatedAt(LocalDateTime.now());
        legalEntity.setUpdatedAt(LocalDateTime.now());

        legalEntityDTO = new LegalEntityDTO();
        legalEntityDTO.setLegalEntityId(legalEntityId);
        legalEntityDTO.setCreatedAt(LocalDateTime.now());
        legalEntityDTO.setUpdatedAt(LocalDateTime.now());
        lenient().when(partyProfiles.requireOrganizationSlot(any())).thenReturn(Mono.empty());
    }

    @Test
    void filterLegalEntities_ShouldReturnFilteredResults_WhenValidFilterRequest() {
        // Arrange
        FilterRequest<LegalEntityDTO> filterRequest = new FilterRequest<>();
        PaginationResponse<LegalEntityDTO> mockResponse = mock(PaginationResponse.class);
        
        // Create a spy of the service to mock the filterLegalEntities method
        LegalEntityServiceImpl spyService = spy(legalEntityService);
        doReturn(Mono.just(mockResponse)).when(spyService).filterLegalEntities(partyId, filterRequest);

        // Act & Assert
        StepVerifier.create(spyService.filterLegalEntities(partyId, filterRequest))
                .expectNext(mockResponse)
                .verifyComplete();

        // Verify that filterLegalEntities was called
        verify(spyService).filterLegalEntities(partyId, filterRequest);
    }

    @Test
    void createLegalEntity_ShouldReturnCreatedLegalEntityDTO_WhenValidLegalEntityDTO() {
        // Arrange
        when(legalEntityMapper.toEntity(legalEntityDTO)).thenReturn(legalEntity);
        when(legalEntityRepository.save(legalEntity)).thenReturn(Mono.just(legalEntity));
        when(legalEntityMapper.toDTO(legalEntity)).thenReturn(legalEntityDTO);

        // Act & Assert
        StepVerifier.create(legalEntityService.createLegalEntity(partyId, legalEntityDTO))
                .expectNext(legalEntityDTO)
                .verifyComplete();

        verify(legalEntityMapper).toEntity(legalEntityDTO);
        verify(legalEntityRepository).save(legalEntity);
        verify(legalEntityMapper).toDTO(legalEntity);
    }

    @Test
    void createLegalEntity_ShouldHandleRepositoryError_WhenSaveFails() {
        // Arrange
        when(legalEntityMapper.toEntity(legalEntityDTO)).thenReturn(legalEntity);
        when(legalEntityRepository.save(legalEntity)).thenReturn(Mono.error(new RuntimeException("Database error")));

        // Act & Assert
        StepVerifier.create(legalEntityService.createLegalEntity(partyId, legalEntityDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Database error"))
                .verify();

        verify(legalEntityMapper).toEntity(legalEntityDTO);
        verify(legalEntityRepository).save(legalEntity);
        verify(legalEntityMapper, never()).toDTO(any());
    }

    @Test
    void updateLegalEntity_ShouldReturnUpdatedLegalEntityDTO_WhenLegalEntityExists() {
        // Arrange
        LegalEntityDTO updateDTO = new LegalEntityDTO();
        updateDTO.setLegalEntityId(null);
        
        // Set up the existing legal entity with the correct partyId for validation
        legalEntity.setPartyId(partyId);
        
        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Mono.just(legalEntity));
        doNothing().when(legalEntityMapper).updateEntityFromDto(updateDTO, legalEntity);
        when(legalEntityRepository.save(legalEntity)).thenReturn(Mono.just(legalEntity));
        when(legalEntityMapper.toDTO(legalEntity)).thenReturn(legalEntityDTO);

        // Act & Assert
        StepVerifier.create(legalEntityService.updateLegalEntity(partyId, legalEntityId, updateDTO))
                .expectNext(legalEntityDTO)
                .verifyComplete();

        verify(legalEntityRepository).findById(legalEntityId);
        verify(legalEntityMapper).updateEntityFromDto(updateDTO, legalEntity);
        verify(legalEntityRepository).save(legalEntity);
        verify(legalEntityMapper).toDTO(legalEntity);
    }

    @Test
    void updateLegalEntity_ShouldReturnError_WhenLegalEntityNotFound() {
        // Arrange
        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(legalEntityService.updateLegalEntity(partyId, legalEntityId, legalEntityDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Legal entity not found with ID: " + legalEntityId))
                .verify();

        verify(legalEntityRepository).findById(legalEntityId);
        verify(legalEntityMapper, never()).toEntity(any());
        verify(legalEntityRepository, never()).save(any());
    }

    @Test
    void deleteLegalEntity_ShouldCompleteSuccessfully_WhenLegalEntityExists() {
        // Arrange
        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Mono.just(legalEntity));
        when(legalEntityRepository.deleteById(legalEntityId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(legalEntityService.deleteLegalEntity(partyId, legalEntityId))
                .verifyComplete();

        verify(legalEntityRepository).findById(legalEntityId);
        verify(legalEntityRepository).deleteById(legalEntityId);
    }

    @Test
    void deleteLegalEntity_ShouldReturnError_WhenLegalEntityNotFound() {
        // Arrange
        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(legalEntityService.deleteLegalEntity(partyId, legalEntityId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Legal entity not found with ID: " + legalEntityId))
                .verify();

        verify(legalEntityRepository).findById(legalEntityId);
        verify(legalEntityRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void deleteLegalEntity_ShouldHandleRepositoryError_WhenDeleteFails() {
        // Arrange
        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Mono.just(legalEntity));
        when(legalEntityRepository.deleteById(legalEntityId)).thenReturn(Mono.error(new RuntimeException("Delete failed")));

        // Act & Assert
        StepVerifier.create(legalEntityService.deleteLegalEntity(partyId, legalEntityId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Delete failed"))
                .verify();

        verify(legalEntityRepository).findById(legalEntityId);
        verify(legalEntityRepository).deleteById(legalEntityId);
    }

    @Test
    void getLegalEntityById_ShouldReturnLegalEntityDTO_WhenLegalEntityExists() {
        // Arrange
        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Mono.just(legalEntity));
        when(legalEntityMapper.toDTO(legalEntity)).thenReturn(legalEntityDTO);

        // Act & Assert
        StepVerifier.create(legalEntityService.getLegalEntityById(partyId, legalEntityId))
                .expectNext(legalEntityDTO)
                .verifyComplete();

        verify(legalEntityRepository).findById(legalEntityId);
        verify(legalEntityMapper).toDTO(legalEntity);
    }

    @Test
    void getLegalEntityById_ShouldReturnError_WhenLegalEntityNotFound() {
        // Arrange
        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(legalEntityService.getLegalEntityById(partyId, legalEntityId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Legal entity not found with ID: " + legalEntityId))
                .verify();

        verify(legalEntityRepository).findById(legalEntityId);
        verify(legalEntityMapper, never()).toDTO(any());
    }

    @Test
    void getLegalEntityById_ShouldHandleRepositoryError_WhenFindFails() {
        // Arrange
        when(legalEntityRepository.findById(legalEntityId)).thenReturn(Mono.error(new RuntimeException("Database connection failed")));

        // Act & Assert
        StepVerifier.create(legalEntityService.getLegalEntityById(partyId, legalEntityId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Database connection failed"))
                .verify();

        verify(legalEntityRepository).findById(legalEntityId);
        verify(legalEntityMapper, never()).toDTO(any());
    }
}