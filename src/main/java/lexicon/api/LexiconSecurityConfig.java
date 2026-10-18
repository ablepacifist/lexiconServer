package lexicon.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Security configuration for Lexicon
 * Following similar pattern to Alchemy but adapted for media sharing
 */
@Configuration
@EnableWebSecurity
public class LexiconSecurityConfig {

    @Value("${cors.allowed.origins:}")
    private String allowedOrigins;

    @Value("${cors.allowed.origin-patterns:}")
    private String allowedOriginPatterns;

    // Derived automatically into origin patterns as http://HOST:* below, only
    // when set - so a bare LAN IP or the PlayIt tunnel host never has to be
    // hard-coded here. See the root .env.example for LAN_HOST/PLAYIT_HOST.
    @Value("${cors.lan-host:}")
    private String lanHost;

    @Value("${cors.playit-host:}")
    private String playitHost;

    @Value("${app.update.public-metadata:false}")
    private boolean appUpdatePublicMetadata;

    @Value("${app.update.public-download:false}")
    private boolean appUpdatePublicDownload;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .maximumSessions(10) // Allow multiple sessions per user
            )
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout", "/api/auth/me").permitAll();
                auth.requestMatchers("/api/auth/sso/**").permitAll();
                auth.requestMatchers("/api/health", "/api/info").permitAll();
                if (appUpdatePublicMetadata) {
                    auth.requestMatchers("/api/app/version").permitAll();
                }
                if (appUpdatePublicDownload) {
                    auth.requestMatchers("/api/app/download/**").permitAll();
                }
                auth.requestMatchers("/api/test/**").permitAll();  // Allow access to test endpoints
                auth.requestMatchers("/api/players/**").permitAll();  // Allow access to player endpoints for testing
                auth.requestMatchers("/api/media/**").permitAll();  // Allow access to media endpoints for testing
                auth.requestMatchers("/api/playlists/**").permitAll();  // Allow access to playlist endpoints
                auth.requestMatchers("/api/playback/**").permitAll();  // Allow access to playback position endpoints
                auth.requestMatchers("/api/livestream/**").permitAll();  // Allow access to live stream endpoints
                auth.requestMatchers("/api/stream/**").permitAll();  // Allow access to streaming endpoints
                auth.requestMatchers("/api/download-queue/**").permitAll();  // Allow async download queue
                auth.requestMatchers("/api/messages/**").permitAll();  // Allow message endpoints (Mumble bridge)
                auth.requestMatchers("/api/chat/**").permitAll();     // Allow chat file upload/serving (Mumble bridge)
                auth.requestMatchers("/api/avatar/**").permitAll();   // Allow avatar proxy endpoints (Mumble bridge)
                auth.requestMatchers("/api/push/**").permitAll();     // Allow push notification endpoints
                auth.requestMatchers("/api/notifications/**").permitAll();  // Allow notification endpoints (Mumble bridge + frontend SSE)
                auth.requestMatchers("/api/events/**").permitAll();  // Allow events & polls endpoints (public voting; creation gated client-side)
                auth.requestMatchers("/api/app/**").authenticated();
                auth.requestMatchers("/api/**").authenticated();
                auth.anyRequest().permitAll();
            })
            .formLogin(form -> form.disable());

        return http.build();
    }

    @Bean
    @Primary
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);

        // Exact origins from CORS_ALLOWED_ORIGINS.
        List<String> exactOrigins = splitCsv(allowedOrigins);

        // Wildcard patterns from CORS_ALLOWED_ORIGIN_PATTERNS, plus LAN_HOST
        // and PLAYIT_HOST derived automatically when those keys are set.
        // Spring's setAllowedOriginPatterns uses * as a wildcard (NOT regex);
        // it internally wraps patterns in \Q...\E and only expands * to .*
        List<String> originPatterns = splitCsv(allowedOriginPatterns);
        if (lanHost != null && !lanHost.isBlank()) {
            originPatterns.add("http://" + lanHost.trim() + ":*");
        }
        if (playitHost != null && !playitHost.isBlank()) {
            originPatterns.add("http://" + playitHost.trim() + ":*");
        }

        // Log the configured origins for debugging
        System.out.println("=== CORS Configuration ===");
        System.out.println("Exact origins: " + exactOrigins);
        System.out.println("Origin patterns: " + originPatterns);
        System.out.println("========================");

        // Set both exact origins and patterns
        if (!exactOrigins.isEmpty()) {
            config.setAllowedOrigins(exactOrigins);
        }
        if (!originPatterns.isEmpty()) {
            config.setAllowedOriginPatterns(originPatterns);
        }

        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS","HEAD"));
        config.setAllowedHeaders(List.of("*"));
        // X-Mobile-Token carries a rotated bearer token back to the Android app —
        // it must be exposed or the WebView cannot read it off the response
        config.setExposedHeaders(List.of("Content-Range", "Accept-Ranges", "Content-Length", "Content-Type", "Cache-Control", "X-Accel-Buffering", "X-Mobile-Token"));
        config.setMaxAge(3600L); // Cache preflight requests for 1 hour
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Comma-separated property value -&gt; trimmed, non-empty entries. */
    private static List<String> splitCsv(String csv) {
        List<String> result = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return result;
        }
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}