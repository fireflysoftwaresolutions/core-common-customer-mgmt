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


package io.fireflysoftware.core.customer.interfaces.dtos;

import io.fireflysoftware.core.customer.interfaces.enums.PartyKind;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Data Transfer Object for Party entity representing the supertype for all customer types.
 * Used for transferring party data between application layers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartyDTO {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID partyId;
    
    @NotNull(message = "Party kind is required")
    private PartyKind partyKind;

    @org.fireflyframework.utils.annotations.FilterableId
    @NotNull(message = "Tenant ID is required")
    private UUID tenantId;

    @Size(max = 10, message = "Preferred language must not exceed 10 characters")
    private String preferredLanguage;

    @Size(max = 100, message = "Source system must not exceed 100 characters")
    private String sourceSystem;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}