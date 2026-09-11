package com.andface.backend.config;

import com.andface.backend.common.ApiError;
import com.andface.backend.repository.UserRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecretKeySpec jwtKey(@Value("${andface.jwt.secret}") String secret) {
    byte[] bytes = Base64.getDecoder().decode(secret);
    if (bytes.length < 32)
      throw new IllegalArgumentException("JWT_SECRET must be base64 of at least 32 random bytes");
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKeySpec key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKeySpec key, @Value("${andface.jwt.issuer}") String issuer) {
    var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, UserRepository users, ObjectMapper mapper)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(c -> c.disable())
        .formLogin(f -> f.disable())
        .httpBasic(b -> b.disable())
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        org.springframework.http.HttpMethod.POST,
                        "/api/users/register",
                        "/api/users/login")
                    .permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            o ->
                o.jwt(
                        j ->
                            j.jwtAuthenticationConverter(
                                jwt -> {
                                  try {
                                    var user =
                                        users
                                            .findById(UUID.fromString(jwt.getSubject()))
                                            .orElseThrow(
                                                () ->
                                                    new org.springframework.security.oauth2.core
                                                        .OAuth2AuthenticationException(
                                                        "invalid_token"));
                                    return new JwtAuthenticationToken(
                                        jwt,
                                        List.of(
                                            new SimpleGrantedAuthority(
                                                "ROLE_" + user.getRole().name())));
                                  } catch (IllegalArgumentException e) {
                                    throw new org.springframework.security.oauth2.core
                                        .OAuth2AuthenticationException("invalid_token");
                                  }
                                }))
                    .authenticationEntryPoint(
                        (req, res, e) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          mapper.writeValue(
                              res.getOutputStream(),
                              ApiError.of(401, "UNAUTHORIZED", "Valid access token required"));
                        }))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          mapper.writeValue(
                              res.getOutputStream(),
                              ApiError.of(401, "UNAUTHORIZED", "Valid access token required"));
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/json");
                          mapper.writeValue(
                              res.getOutputStream(),
                              ApiError.of(403, "FORBIDDEN", "Access denied"));
                        }));
    return http.build();
  }
}
