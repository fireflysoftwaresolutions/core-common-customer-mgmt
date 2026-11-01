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

import io.fireflysoftware.core.customer.interfaces.dtos.PartyAncestorDTO;
import io.fireflysoftware.core.customer.interfaces.enums.PartyKind;
import io.fireflysoftware.core.customer.interfaces.enums.RelationshipKind;
import io.fireflysoftware.core.customer.models.entities.PartyRelationship;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Parent and legal-entity ownership edges form the hierarchy.
 * A natural person who owns a company is an owner, not a parent in that chain.
 */
final class PartyHierarchyImpl {

    static final int MAX_DEPTH = 25;

    private PartyHierarchyImpl() {
    }

    static boolean isHierarchyEdge(RelationshipKind kind, PartyKind sourceKind) {
        if (kind == RelationshipKind.PARENT) {
            return true;
        }
        return kind == RelationshipKind.OWNERSHIP && sourceKind == PartyKind.ORGANIZATION;
    }

    static boolean wouldCycle(UUID parent, UUID child, List<PartyRelationship> edges) {
        Map<UUID, List<UUID>> parentsOf = new HashMap<>();
        for (PartyRelationship edge : edges) {
            parentsOf.computeIfAbsent(edge.getTargetPartyId(), key -> new ArrayList<>())
                    .add(edge.getSourcePartyId());
        }
        Set<UUID> seen = new HashSet<>();
        ArrayDeque<UUID> stack = new ArrayDeque<>();
        stack.push(parent);
        int guard = 0;
        while (!stack.isEmpty()) {
            if (++guard > 10_000) {
                return true;
            }
            UUID current = stack.pop();
            if (!seen.add(current)) {
                continue;
            }
            for (UUID ancestor : parentsOf.getOrDefault(current, List.of())) {
                if (child.equals(ancestor)) {
                    return true;
                }
                stack.push(ancestor);
            }
        }
        return false;
    }

    static List<PartyRelationship> directChildren(UUID partyId, List<PartyRelationship> edges) {
        Map<UUID, PartyRelationship> byTarget = new LinkedHashMap<>();
        for (PartyRelationship edge : edges) {
            if (!partyId.equals(edge.getSourcePartyId())) {
                continue;
            }
            PartyRelationship current = byTarget.get(edge.getTargetPartyId());
            if (current == null || edge.getRelationshipKind() == RelationshipKind.PARENT) {
                byTarget.put(edge.getTargetPartyId(), edge);
            }
        }
        return List.copyOf(byTarget.values());
    }

    static List<PartyAncestorDTO> ancestors(UUID partyId, List<PartyRelationship> edges, int depth) {
        int cap = Math.min(Math.max(depth, 1), MAX_DEPTH);
        Map<UUID, List<PartyRelationship>> parentsOf = new HashMap<>();
        for (PartyRelationship edge : edges) {
            parentsOf.computeIfAbsent(edge.getTargetPartyId(), key -> new ArrayList<>()).add(edge);
        }
        for (List<PartyRelationship> parents : parentsOf.values()) {
            parents.sort(Comparator.comparingInt(edge ->
                    edge.getRelationshipKind() == RelationshipKind.PARENT ? 0 : 1));
        }
        List<PartyAncestorDTO> found = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        seen.add(partyId);
        ArrayDeque<UUID> level = new ArrayDeque<>();
        level.add(partyId);
        for (int distance = 1; distance <= cap && !level.isEmpty(); distance++) {
            ArrayDeque<UUID> next = new ArrayDeque<>();
            for (UUID current : level) {
                for (PartyRelationship edge : parentsOf.getOrDefault(current, List.of())) {
                    if (!seen.add(edge.getSourcePartyId())) {
                        continue;
                    }
                    found.add(PartyAncestorDTO.builder()
                            .partyId(edge.getSourcePartyId())
                            .distance(distance)
                            .partyRelationshipId(edge.getPartyRelationshipId())
                            .relationshipKind(edge.getRelationshipKind())
                            .build());
                    next.add(edge.getSourcePartyId());
                }
            }
            level = next;
        }
        return found;
    }
}
