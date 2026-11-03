package io.fireflysoftware.core.customer.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.fireflysoftware.core.customer.sdk.api.EmailContactsApi;
import io.fireflysoftware.core.customer.sdk.invoker.ApiClient;
import io.fireflysoftware.core.customer.sdk.invoker.auth.HttpBearerAuth;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class CustomerBearerGeneratedContractTest {
    @Test void packagedCompatibilityResourceIsTheExactFullProducer()throws Exception{
        try(var producer=getClass().getResourceAsStream("/openapi.yml");var compatibility=getClass().getResourceAsStream("/api-spec/openapi.yml")){
            assertNotNull(producer);assertNotNull(compatibility);assertArrayEquals(producer.readAllBytes(),compatibility.readAllBytes());
        }
    }
    @Test void allOriginalOperationsRetainTheActualReceiverBearerContract()throws Exception{
        try(var input=getClass().getResourceAsStream("/openapi.yml");var baseline=getClass().getResourceAsStream("/original-customer-operations.txt")){
            assertNotNull(input);assertNotNull(baseline);var root=new ObjectMapper(new YAMLFactory()).readTree(input);var expected=new TreeSet<>(new String(baseline.readAllBytes(),StandardCharsets.UTF_8).lines().toList());
            assertEquals(85,expected.size());var actual=new TreeSet<String>();var paths=root.path("paths").fields();
            while(paths.hasNext()){
                var path=paths.next();var methods=path.getValue().fields();
                while(methods.hasNext()){
                    var method=methods.next();if(!Set.of("get","post","put","delete","patch","options","head").contains(method.getKey()))continue;
                    actual.add(method.getKey()+" "+path.getKey()+" "+method.getValue().path("operationId").asText());
                    var security=method.getValue().has("security")?method.getValue().path("security"):root.path("security");
                    assertTrue(security.isArray()&&security.size()==1&&security.get(0).has("customerBearer"),path.getKey()+" "+method.getKey());
                }
            }
            assertEquals(expected,actual);assertEquals("bearer",root.path("components").path("securitySchemes").path("customerBearer").path("scheme").asText());
        }
    }
    @Test void genuineNonPartyGeneratedApiSendsTheExactNamedBearer(){
        var observed=new AtomicReference<String>();var transport=ApiClient.buildWebClientBuilder().exchangeFunction(request->{observed.set(request.headers().getFirst(HttpHeaders.AUTHORIZATION));return Mono.just(ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE,MediaType.APPLICATION_JSON_VALUE).body("{}").build());}).build();
        var client=new ApiClient(transport).setBasePath("https://controlled.invalid");((HttpBearerAuth)client.getAuthentication("customerBearer")).setBearerToken("controlled-contract-token");
        new EmailContactsApi(client).getEmailContactByIdWithHttpInfo(UUID.fromString("00000000-0000-0000-0000-000000000001"),UUID.fromString("00000000-0000-0000-0000-000000000002"),null).block(Duration.ofSeconds(5));
        assertEquals("Bearer controlled-contract-token",observed.get());
    }
}
