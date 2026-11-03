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

package io.fireflysoftware.core.customer.web.openapi;

import org.fireflyframework.web.openapi.EnableOpenApiGen;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Minimal Spring Boot application used only during the Maven build to expose
 * the OpenAPI spec via Springdoc. It scans the production controllers so that
 * Springdoc discovers their {@code @RestController} annotations and
 * {@code @OpenAPIDefinition} metadata.
 *
 * <p>This class lives under {@code src/test/java} and is never packaged into
 * the production artifact. It is activated by the {@code generate-openapi}
 * Maven profile with {@code useTestClasspath=true}.
 */
@EnableOpenApiGen
@SecurityScheme(name = "customerBearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@OpenAPIDefinition(info = @Info(title = "${spring.application.name}", version = "${spring.application.version}",
        description = "${spring.application.description}"), security = @SecurityRequirement(name = "customerBearer"))
@ComponentScan(basePackages = "io.fireflysoftware.core.customer.web.controllers")
public class OpenApiGenApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpenApiGenApplication.class, args);
    }
}
