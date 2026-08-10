package com.elatusdev.pokedex.identity.web.config;

import com.elatusdev.pokedex.identity.web.security.JwtAuthenticationFilter;
import com.elatusdev.pokedex.identity.web.security.LoginRateLimitFilter;
import com.elatusdev.pokedex.identity.web.security.ProblemDetailAccessDeniedHandler;
import com.elatusdev.pokedex.identity.web.security.ProblemDetailEntryPoint;
import com.elatusdev.pokedex.identity.web.security.SecurityProperties;
import com.elatusdev.pokedex.identity.web.security.SessionCheckFilter;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

// Deny by default. Public routes are an enumerated allow-list, so an endpoint someone
// forgets to think about is protected rather than exposed, and the chain terminates in
// .anyRequest().authenticated() — asserted by ArchUnit SB-PA4.
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    // You cannot authenticate in order to authenticate.
    private static final String[] PUBLIC_CREDENTIAL_ENDPOINTS = {
        "/v1/security/register", "/v1/security/login", "/v1/security/token/refresh"
    };

    // The contract is a published artifact (ADR-0008): consumers fetch it, the Phase 7 gate
    // diffs it with an unauthenticated curl, and quickstart.md advertises both URLs. It is
    // public by design, and it carries no data — only the shape of the API.
    private static final String[] PUBLIC_CONTRACT_ENDPOINTS = {
        "/v3/api-docs.yaml", "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"
    };

    private final JwtAuthenticationFilter jwtAuthentication;
    private final SessionCheckFilter sessionCheck;
    private final LoginRateLimitFilter loginRateLimit;
    private final ProblemDetailEntryPoint entryPoint;
    private final ProblemDetailAccessDeniedHandler accessDenied;
    private final SecurityProperties properties;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthentication,
            SessionCheckFilter sessionCheck,
            LoginRateLimitFilter loginRateLimit,
            ProblemDetailEntryPoint entryPoint,
            ProblemDetailAccessDeniedHandler accessDenied,
            SecurityProperties properties) {
        this.jwtAuthentication = jwtAuthentication;
        this.sessionCheck = sessionCheck;
        this.loginRateLimit = loginRateLimit;
        this.entryPoint = entryPoint;
        this.accessDenied = accessDenied;
        this.properties = properties;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // bearer tokens carry the identity, so there is no server-side HTTP session
                // for CSRF to attack and none for the container to replicate
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling ->
                        handling.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied))
                // rate limit, then verify the signature, then confirm the session — the
                // order is load-bearing and matches docs/diagrams/auth-filter-chain.md
                .addFilterBefore(loginRateLimit, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthentication, LoginRateLimitFilter.class)
                .addFilterAfter(sessionCheck, JwtAuthenticationFilter.class)
                .authorizeHttpRequests(routes -> routes
                        // an unmatched path is forwarded to /error by MVC, and that internal
                        // forward was being authorized a second time — turning every honest
                        // 404 into a 401. The ERROR dispatch is server-initiated and cannot
                        // be requested from outside, and the original status is preserved.
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_CREDENTIAL_ENDPOINTS)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_CONTRACT_ENDPOINTS)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        // read-only reference data. Only GET: naming /v1/pokedex/** here
                        // would open every mutation on the same paths
                        .requestMatchers(HttpMethod.GET, "/v1/pokedex/pokemon/**", "/v1/pokedex/local/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .build();
    }

    // an explicit origin allow-list, never "*" — with credentials in play a wildcard makes
    // every site on the internet a trusted caller
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.corsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
