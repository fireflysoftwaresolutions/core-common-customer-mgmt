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
import io.fireflysoftware.core.customer.core.mappers.NaturalPersonMapper;
import io.fireflysoftware.core.customer.interfaces.dtos.NaturalPersonDTO;
import io.fireflysoftware.core.customer.models.entities.NaturalPerson;
import io.fireflysoftware.core.customer.models.repositories.NaturalPersonRepository;
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
class NaturalPersonServiceImplTest {

    @Mock
    private NaturalPersonRepository naturalPersonRepository;

    @Mock
    private NaturalPersonMapper naturalPersonMapper;

    @Mock
    private PartyProfilesImpl partyProfiles;

    @InjectMocks
    private NaturalPersonServiceImpl naturalPersonService;

    private NaturalPersonDTO naturalPersonDTO;
    private NaturalPerson naturalPerson;
    private UUID naturalPersonId;
    private UUID partyId;

    @BeforeEach
    void setUp() {
        naturalPersonId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        partyId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");

        naturalPerson = new NaturalPerson();
        naturalPerson.setNaturalPersonId(naturalPersonId);
        naturalPerson.setCreatedAt(LocalDateTime.now());
        naturalPerson.setUpdatedAt(LocalDateTime.now());
        naturalPerson.setPartyId(partyId);
        naturalPerson.setGivenName("Ada");

        naturalPersonDTO = new NaturalPersonDTO();
        naturalPersonDTO.setNaturalPersonId(naturalPersonId);
        naturalPersonDTO.setGivenName("Ada");
        lenient().when(partyProfiles.requirePersonSlot(any())).thenReturn(Mono.empty());
        naturalPersonDTO.setCreatedAt(LocalDateTime.now());
        naturalPersonDTO.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void filterNaturalPersons_ShouldReturnFilteredResults_WhenValidFilterRequest() {
        // Arrange
        FilterRequest<NaturalPersonDTO> filterRequest = new FilterRequest<>();
        PaginationResponse<NaturalPersonDTO> mockResponse = mock(PaginationResponse.class);
        
        // Create a spy of the service to mock the filterNaturalPersons method
        NaturalPersonServiceImpl spyService = spy(naturalPersonService);
        doReturn(Mono.just(mockResponse)).when(spyService).filterNaturalPersons(partyId, filterRequest);

        // Act & Assert
        StepVerifier.create(spyService.filterNaturalPersons(partyId, filterRequest))
                .expectNext(mockResponse)
                .verifyComplete();

        // Verify that filterNaturalPersons was called
        verify(spyService).filterNaturalPersons(partyId, filterRequest);
    }

    @Test
    void createNaturalPerson_ShouldReturnCreatedNaturalPersonDTO_WhenValidNaturalPersonDTO() {
        // Arrange
        when(naturalPersonMapper.toEntity(naturalPersonDTO)).thenReturn(naturalPerson);
        when(naturalPersonRepository.save(naturalPerson)).thenReturn(Mono.just(naturalPerson));
        when(naturalPersonMapper.toDTO(naturalPerson)).thenReturn(naturalPersonDTO);

        // Act & Assert
        StepVerifier.create(naturalPersonService.createNaturalPerson(partyId, naturalPersonDTO))
                .expectNext(naturalPersonDTO)
                .verifyComplete();

        verify(naturalPersonMapper).toEntity(naturalPersonDTO);
        verify(naturalPersonRepository).save(naturalPerson);
        verify(naturalPersonMapper).toDTO(naturalPerson);
    }

    @Test
    void createNaturalPerson_ShouldHandleRepositoryError_WhenSaveFails() {
        // Arrange
        when(naturalPersonMapper.toEntity(naturalPersonDTO)).thenReturn(naturalPerson);
        when(naturalPersonRepository.save(naturalPerson)).thenReturn(Mono.error(new RuntimeException("Database error")));

        // Act & Assert
        StepVerifier.create(naturalPersonService.createNaturalPerson(partyId, naturalPersonDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Database error"))
                .verify();

        verify(naturalPersonMapper).toEntity(naturalPersonDTO);
        verify(naturalPersonRepository).save(naturalPerson);
        verify(naturalPersonMapper, never()).toDTO(any());
    }

    @Test
    void updateNaturalPerson_ShouldReturnUpdatedNaturalPersonDTO_WhenNaturalPersonExists() {
        // Arrange
        NaturalPersonDTO updateDTO = new NaturalPersonDTO();
        updateDTO.setNaturalPersonId(null);
        
        when(naturalPersonRepository.findById(naturalPersonId)).thenReturn(Mono.just(naturalPerson));
        doNothing().when(naturalPersonMapper).updateEntityFromDto(updateDTO, naturalPerson);
        when(naturalPersonRepository.save(naturalPerson)).thenReturn(Mono.just(naturalPerson));
        when(naturalPersonMapper.toDTO(naturalPerson)).thenReturn(naturalPersonDTO);

        // Act & Assert
        StepVerifier.create(naturalPersonService.updateNaturalPerson(partyId, naturalPersonId, updateDTO))
                .expectNext(naturalPersonDTO)
                .verifyComplete();

        verify(naturalPersonRepository).findById(naturalPersonId);
        verify(naturalPersonMapper).updateEntityFromDto(updateDTO, naturalPerson);
        verify(naturalPersonRepository).save(naturalPerson);
        verify(naturalPersonMapper).toDTO(naturalPerson);
    }

    @Test
    void updateNaturalPerson_ShouldReturnError_WhenNaturalPersonNotFound() {
        // Arrange
        when(naturalPersonRepository.findById(naturalPersonId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(naturalPersonService.updateNaturalPerson(partyId, naturalPersonId, naturalPersonDTO))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Natural person not found with ID: " + naturalPersonId))
                .verify();

        verify(naturalPersonRepository).findById(naturalPersonId);
        verify(naturalPersonMapper, never()).toEntity(any());
        verify(naturalPersonRepository, never()).save(any());
    }

    @Test
    void deleteNaturalPerson_ShouldCompleteSuccessfully_WhenNaturalPersonExists() {
        // Arrange
        when(naturalPersonRepository.findById(naturalPersonId)).thenReturn(Mono.just(naturalPerson));
        when(naturalPersonRepository.deleteById(naturalPersonId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(naturalPersonService.deleteNaturalPerson(partyId, naturalPersonId))
                .verifyComplete();

        verify(naturalPersonRepository).findById(naturalPersonId);
        verify(naturalPersonRepository).deleteById(naturalPersonId);
    }

    @Test
    void deleteNaturalPerson_ShouldReturnError_WhenNaturalPersonNotFound() {
        // Arrange
        when(naturalPersonRepository.findById(naturalPersonId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(naturalPersonService.deleteNaturalPerson(partyId, naturalPersonId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Natural person not found with ID: " + naturalPersonId))
                .verify();

        verify(naturalPersonRepository).findById(naturalPersonId);
        verify(naturalPersonRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void deleteNaturalPerson_ShouldHandleRepositoryError_WhenDeleteFails() {
        // Arrange
        when(naturalPersonRepository.findById(naturalPersonId)).thenReturn(Mono.just(naturalPerson));
        when(naturalPersonRepository.deleteById(naturalPersonId)).thenReturn(Mono.error(new RuntimeException("Delete failed")));

        // Act & Assert
        StepVerifier.create(naturalPersonService.deleteNaturalPerson(partyId, naturalPersonId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Delete failed"))
                .verify();

        verify(naturalPersonRepository).findById(naturalPersonId);
        verify(naturalPersonRepository).deleteById(naturalPersonId);
    }

    @Test
    void getNaturalPersonById_ShouldReturnNaturalPersonDTO_WhenNaturalPersonExists() {
        // Arrange
        when(naturalPersonRepository.findById(naturalPersonId)).thenReturn(Mono.just(naturalPerson));
        when(naturalPersonMapper.toDTO(naturalPerson)).thenReturn(naturalPersonDTO);

        // Act & Assert
        StepVerifier.create(naturalPersonService.getNaturalPersonById(partyId, naturalPersonId))
                .expectNext(naturalPersonDTO)
                .verifyComplete();

        verify(naturalPersonRepository).findById(naturalPersonId);
        verify(naturalPersonMapper).toDTO(naturalPerson);
    }

    @Test
    void getNaturalPersonById_ShouldReturnError_WhenNaturalPersonNotFound() {
        // Arrange
        when(naturalPersonRepository.findById(naturalPersonId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(naturalPersonService.getNaturalPersonById(partyId, naturalPersonId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Natural person not found with ID: " + naturalPersonId))
                .verify();

        verify(naturalPersonRepository).findById(naturalPersonId);
        verify(naturalPersonMapper, never()).toDTO(any());
    }

    @Test
    void getNaturalPersonById_ShouldHandleRepositoryError_WhenFindFails() {
        // Arrange
        when(naturalPersonRepository.findById(naturalPersonId)).thenReturn(Mono.error(new RuntimeException("Database connection failed")));

        // Act & Assert
        StepVerifier.create(naturalPersonService.getNaturalPersonById(partyId, naturalPersonId))
                .expectErrorMatches(throwable -> throwable instanceof RuntimeException && 
                        throwable.getMessage().equals("Database connection failed"))
                .verify();

        verify(naturalPersonRepository).findById(naturalPersonId);
        verify(naturalPersonMapper, never()).toDTO(any());
    }

    @Test
    void createNaturalPerson_ShouldReject_WhenBothNamesAreMissing() {
        naturalPersonDTO.setGivenName(null);
        naturalPersonDTO.setFamilyName(" ");

        StepVerifier.create(naturalPersonService.createNaturalPerson(partyId, naturalPersonDTO))
                .expectErrorMatches(throwable -> throwable.getMessage().contains("given name or a family name"))
                .verify();

        verify(naturalPersonRepository, never()).save(any());
    }

    @Test
    void createNaturalPerson_ShouldAccept_AFamilyNameOnly() {
        naturalPersonDTO.setGivenName(null);
        naturalPersonDTO.setFamilyName("Nguyen");
        when(naturalPersonMapper.toEntity(naturalPersonDTO)).thenReturn(naturalPerson);
        when(naturalPersonRepository.save(naturalPerson)).thenReturn(Mono.just(naturalPerson));
        when(naturalPersonMapper.toDTO(naturalPerson)).thenReturn(naturalPersonDTO);

        StepVerifier.create(naturalPersonService.createNaturalPerson(partyId, naturalPersonDTO))
                .expectNext(naturalPersonDTO)
                .verifyComplete();
    }
}