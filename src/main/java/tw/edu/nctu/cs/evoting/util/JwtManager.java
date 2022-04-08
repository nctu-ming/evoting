package tw.edu.nctu.cs.evoting.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

public class JwtManager {
    private static final Logger logger = LoggerFactory.getLogger(JwtManager.class);

    private static final String CLAIM_NAME_UNIQUIFIER = "uniquer";

    private final String issuer;
    private final Algorithm algorithm;
    private final int validSeconds;

    private final JWTVerifier verifier;

    public static JwtManager EVotingJwtManager() {
        return new JwtManager(
                "evoting",
                Algorithm.HMAC256("evoting-secret-key"),
                3600,
                30);
    }

    JwtManager(String issuer, Algorithm algorithm, int validSeconds, int leewaySeconds) {
        this.issuer = issuer;
        this.algorithm = algorithm;
        this.validSeconds = validSeconds;

        verifier = JWT.require(algorithm)
                .withIssuer(issuer)
                .acceptLeeway(leewaySeconds)
                .build();
    }

    public String nextToken() {
        final Instant now = Instant.now();
        final int un2 = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE) & 0x7fffffff;
        return JWT.create()
                .withIssuer(issuer)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(validSeconds, ChronoUnit.SECONDS)))
                .withClaim(CLAIM_NAME_UNIQUIFIER, un2)
                .sign(algorithm);
    }

    public boolean validateID(String id) {
        try {
            // Verifier will check whether its issuer is me and also check whether it has been expired or not.
            verifier.verify(id);
            return true;
        } catch (Throwable cause) {
            logger.trace("JWT token validation failed", cause);
            return false;
        }
    }
}
