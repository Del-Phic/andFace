package com.andface.backend.security;

import com.andface.backend.dto.response.Responses.*;
import com.andface.backend.entity.UserAccount;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
  private final JwtEncoder encoder;
  private final String issuer;
  private final long ttl;

  public TokenService(
      JwtEncoder encoder,
      @Value("${andface.jwt.issuer}") String issuer,
      @Value("${andface.jwt.ttl-seconds}") long ttl) {
    this.encoder = encoder;
    this.issuer = issuer;
    this.ttl = ttl;
  }

  public TokenView issue(UserAccount user) {
    Instant now = Instant.now();
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(ttl))
            .build();
    String token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new TokenView(token, "Bearer", ttl, UserView.of(user));
  }
}
