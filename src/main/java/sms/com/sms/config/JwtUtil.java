package sms.com.sms.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import sms.com.sms.enums.UserRole;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
public class JwtUtil {

    private String SECRET_KEY = "RbthOApwUHf33z9v2uR/eNpNUvv3zczUh/ffaMJx8YU="; // Use a strong secret key
    private final Set<String> invalidatedTokens = ConcurrentHashMap.newKeySet();

    public String generateToken(String username, UserRole role) {
        
    Map<String, Object> claims = new HashMap<>();
    claims.put("role", role); // 👈 Add the role as a claim

    return Jwts.builder()
            .setClaims(claims)             // Include claims
            .setSubject(username)          // Typically the phone number
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 hours
            .signWith(SignatureAlgorithm.HS256, SECRET_KEY)
            .compact();
}


    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) { //  Fixed
        String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token) && !isTokenBlacklisted(token));
    }

    public void invalidateToken(String token) {
        if (token != null && !token.isBlank()) {
            invalidatedTokens.add(token);
        }
    }

    public boolean isTokenBlacklisted(String token) {
        return token != null && !token.isBlank() && invalidatedTokens.contains(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }


}
