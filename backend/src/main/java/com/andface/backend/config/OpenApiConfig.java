package com.andface.backend.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
  @Bean
  org.springdoc.core.customizers.OperationCustomizer errorResponses() {
    return (operation, handler) -> {
      for (var error :
          java.util.Map.of(
                  "400",
                  "Invalid fields or filters",
                  "401",
                  "Invalid credentials or JWT",
                  "403",
                  "Access denied",
                  "404",
                  "User, device or log not found",
                  "409",
                  "Duplicate or conflicting data")
              .entrySet()) {
        operation
            .getResponses()
            .addApiResponse(
                error.getKey(),
                new io.swagger.v3.oas.models.responses.ApiResponse()
                    .description(error.getValue())
                    .content(
                        new io.swagger.v3.oas.models.media.Content()
                            .addMediaType(
                                "application/json",
                                new io.swagger.v3.oas.models.media.MediaType()
                                    .schema(
                                        new io.swagger.v3.oas.models.media.ObjectSchema()
                                            .addProperty(
                                                "status",
                                                new io.swagger.v3.oas.models.media.IntegerSchema())
                                            .addProperty(
                                                "code",
                                                new io.swagger.v3.oas.models.media.StringSchema())
                                            .addProperty(
                                                "message",
                                                new io.swagger.v3.oas.models.media.StringSchema())
                                            .addProperty(
                                                "timestamp",
                                                new io.swagger.v3.oas.models.media
                                                    .DateTimeSchema())))));
      }
      return operation;
    };
  }

  @Bean
  OpenAPI openAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("AndFace Results API")
                .version("1.0.0")
                .description(
                    "Account-scoped client-reported face authentication logs; no server face"
                        + " analysis. Dates use UTC, to is inclusive, successRate is 0..1."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
  }
}
