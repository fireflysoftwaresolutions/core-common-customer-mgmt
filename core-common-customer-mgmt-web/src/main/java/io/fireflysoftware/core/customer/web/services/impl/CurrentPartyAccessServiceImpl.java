package io.fireflysoftware.core.customer.web.services.impl;

import io.fireflysoftware.core.customer.core.services.PartyService;
import io.fireflysoftware.core.customer.interfaces.dtos.PartyDTO;
import io.fireflysoftware.core.customer.web.services.CurrentPartyAccessService;
import org.fireflyframework.security.api.domain.SecurityPrincipal;
import org.fireflyframework.security.spi.PolicyDecisionPort;
import org.fireflyframework.security.spi.SecurityContextPort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import java.time.*;
import java.util.*;

public final class CurrentPartyAccessServiceImpl implements CurrentPartyAccessService {
    private final PartyService owner;
    private final SecurityContextPort contexts;
    private final PolicyDecisionPort policies;
    private final Clock clock;
    private final Set<String> issuers;
    private final Set<String> audiences;
    private final Set<String> clients;

    public CurrentPartyAccessServiceImpl(PartyService owner,SecurityContextPort contexts,PolicyDecisionPort policies,
            Clock clock,Set<String> issuers,Set<String> audiences,Set<String> clients){
        this.owner=Objects.requireNonNull(owner);this.contexts=Objects.requireNonNull(contexts);
        this.policies=Objects.requireNonNull(policies);this.clock=Objects.requireNonNull(clock);
        this.issuers=Set.copyOf(issuers);this.audiences=Set.copyOf(audiences);this.clients=Set.copyOf(clients);
        if(this.issuers.isEmpty()||this.audiences.isEmpty()||this.clients.isEmpty()
            ||this.issuers.stream().anyMatch(String::isBlank)||this.audiences.stream().anyMatch(String::isBlank)
            ||this.clients.stream().anyMatch(String::isBlank))throw new IllegalArgumentException("Explicit Customer issuer, audience and client registrations required");
    }

    @Override public Mono<PartyDTO> createParty(PartyDTO party){
        return Mono.defer(()->{
            if(party==null||party.getPartyId()!=null||party.getCreatedAt()!=null||party.getUpdatedAt()!=null)throw denied();
            UUID tenant=identifier(party.getTenantId());
            return contexts.currentPrincipal().switchIfEmpty(Mono.error(denied())).flatMap(actor->{
                current(actor,tenant,null,"create");
                return authorize(actor,tenant,null,"create").then(Mono.defer(()->{
                    current(actor,tenant,null,"create");return owner.createParty(party);
                })).map(created->{
                    current(actor,tenant,null,"create");
                    if(created==null||created.getPartyId()==null||!tenant.equals(created.getTenantId()))throw denied();
                    return created;
                });
            });
        }).timeout(Duration.ofSeconds(5));
    }

    @Override public Mono<PartyDTO> getPartyById(UUID partyId){
        return Mono.defer(()->{
            UUID id=identifier(partyId);
            return contexts.currentPrincipal().switchIfEmpty(Mono.error(denied())).flatMap(actor->{
                UUID tenant=tenant(actor);current(actor,tenant,id,"read");
                return authorize(actor,tenant,id,"read").then(Mono.defer(()->{
                    current(actor,tenant,id,"read");return owner.getPartyById(id);
                })).flatMap(party->{
                    if(party==null||!id.equals(party.getPartyId())||!tenant.equals(party.getTenantId()))return Mono.error(denied());
                    return authorize(actor,tenant,id,"read").thenReturn(party);
                });
            });
        }).timeout(Duration.ofSeconds(5));
    }

    @Override public Mono<PartyDTO> updateParty(UUID partyId,PartyDTO party){
        return Mono.defer(()->{
            UUID id=identifier(partyId);
            if(party==null||(party.getPartyId()!=null&&!id.equals(party.getPartyId())))throw denied();
            UUID requestedTenant=identifier(party.getTenantId());
            return mutationAuthority(id,"update").flatMap(actor->{
                UUID tenant=tenant(actor);
                if(!tenant.equals(requestedTenant))return Mono.error(denied());
                return Mono.defer(()->{
                    current(actor,tenant,id,"update");
                    return owner.updateParty(id,party);
                }).map(updated->{
                    current(actor,tenant,id,"update");
                    if(!id.equals(updated.getPartyId())||!tenant.equals(updated.getTenantId()))throw denied();
                    return updated;
                });
            });
        }).timeout(Duration.ofSeconds(5));
    }

    @Override public Mono<Void> deleteParty(UUID partyId){
        return Mono.defer(()->{
            UUID id=identifier(partyId);
            return mutationAuthority(id,"delete").flatMap(actor->Mono.defer(()->{
                current(actor,tenant(actor),id,"delete");
                return owner.deleteParty(id);
            }));
        }).timeout(Duration.ofSeconds(5));
    }

    private Mono<SecurityPrincipal> mutationAuthority(UUID id,String operation){
        return contexts.currentPrincipal().switchIfEmpty(Mono.error(denied())).flatMap(actor->{
            UUID tenant=tenant(actor);current(actor,tenant,id,operation);
            return authorize(actor,tenant,id,operation).then(Mono.defer(()->{
                current(actor,tenant,id,operation);
                return owner.getPartyById(id);
            })).switchIfEmpty(Mono.error(denied())).flatMap(party->{
                if(!id.equals(party.getPartyId())||!tenant.equals(party.getTenantId()))return Mono.error(denied());
                return authorize(actor,tenant,id,operation).thenReturn(actor);
            });
        });
    }

    private Mono<Void> authorize(SecurityPrincipal actor,UUID tenant,UUID party,String operation){
        return Mono.defer(()->{
            current(actor,tenant,party,operation);
            var context=new HashMap<String,Object>();
            context.put("tenantId",tenant.toString());context.put("actorTenantId",actor.tenantId());
            context.put("actorIssuer",actor.issuer());context.put("actorSubject",actor.subject());
            context.put("actorKind",actor.claims().get("principal_kind"));context.put("clientId",actor.claims().get("client_id"));
            if(party!=null)context.put("partyId",party.toString());
            if(actor.claims().get("staff_actor_id")instanceof String staff)context.put("staffActorId",staff);
            String resource="tenant/"+tenant+"/parties"+(party==null?"":"/"+party);
            return policies.authorize(actor,"customer.parties."+operation,resource,Map.copyOf(context))
                .filter(decision->decision.granted()&&decision.obligations().isEmpty())
                .switchIfEmpty(Mono.error(denied())).doOnNext(decision->current(actor,tenant,party,operation)).then();
        }).timeout(Duration.ofSeconds(5)).onErrorMap(error->denied());
    }

    private void current(SecurityPrincipal actor,UUID tenant,UUID party,String operation){
        if(actor==null||!issuers.contains(actor.issuer())||actor.subject()==null||actor.subject().isBlank()
            ||!tenant.equals(tenant(actor))||!Objects.equals(actor.claims().get("tenant_id"),actor.tenantId())
            ||!actor.hasScope("customer:parties:"+operation)||!clients.contains(actor.claims().get("client_id")))throw denied();
        Instant now=clock.instant();
        if(!(actor.claims().get("exp")instanceof Instant expiry)||!now.isBefore(expiry))throw denied();
        Object notBefore=actor.claims().get("nbf");
        if(notBefore!=null&&(!(notBefore instanceof Instant before)||now.isBefore(before)))throw denied();
        Object claim=actor.claims().get("aud");Collection<?> actual=claim instanceof String value?List.of(value):claim instanceof Collection<?> values?values:List.of();
        if(actual.stream().noneMatch(audiences::contains))throw denied();
        String kind=Objects.toString(actor.claims().get("principal_kind"),"");
        if("STAFF".equals(kind)){
            if(!(actor.claims().get("staff_actor_id")instanceof String value))throw denied();parse(value);
        }else if("HUMAN".equals(kind)&&"read".equals(operation)&&party!=null){
            if(!(actor.claims().get("party_id")instanceof String value)||!party.equals(parse(value))||actor.claims().containsKey("staff_actor_id"))throw denied();
        }else throw denied();
    }
    private static UUID tenant(SecurityPrincipal actor){if(actor==null)return null;return parse(actor.tenantId());}
    private static UUID parse(String value){try{UUID id=UUID.fromString(value);if(!id.toString().equals(value))throw denied();return identifier(id);}catch(IllegalArgumentException|NullPointerException error){throw denied();}}
    private static UUID identifier(UUID value){if(value==null||value.equals(new UUID(0,0)))throw denied();return value;}
    private static ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Current Customer party authority required");}
}
