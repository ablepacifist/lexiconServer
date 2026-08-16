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
import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for Lexicon
 * Following similar pattern to Alchemy but adapted for media sharing
 */
@Configuration
@EnableWebSecurity
public class LexiconSecurityConfig {

    @Value("${cors.allowed.origins:http://localhost:3000}")
    private String allowedOrigins;

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
        
        // Parse comma-separated allowed origins from environment variable
        List<String> origins = new ArrayList<>();
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            origins.addAll(Arrays.asList(allowedOrigins.split(",")));
        }
        
        // Always add localhost for development
        if (!origins.contains("http://localhost:3000")) {
            origins.add("http://localhost:3000");
        }
        if (!origins.contains("http://localhost:3001")) {
            origins.add("http://localhost:3001");
        }
        
        // Use origin patterns for wildcards support
        // Spring's setAllowedOriginPatterns uses * as wildcard (NOT regex)
        // Spring internally wraps patterns in \Q...\E and only expands * to .*
        List<String> originPatterns = new ArrayList<>();
        List<String> exactOrigins = new ArrayList<>();
        
        for (String origin : origins) {
            if (origin.contains("*")) {
                // Already has wildcard - use as pattern directly (don't convert to regex)
                originPatterns.add(origin);
            } else {
                exactOrigins.add(origin);
            }
        }
        
        // Add common playit patterns (use * wildcard, NOT regex .*)
        originPatterns.add("http://147.185.221.24:*");
        originPatterns.add("https://147.185.221.24:*");
        originPatterns.add("http://209.25.140.16:*");
        originPatterns.add("https://209.25.140.16:*");
        originPatterns.add("http://*.playit.pub:*");
        originPatterns.add("https://*.playit.pub:*");
        originPatterns.add("http://*.with.playit.plus:*");
        originPatterns.add("https://*.with.playit.plus:*");
        
        // Add custom domain patterns (Cloudflare tunnel)
        originPatterns.add("https://alex-dyakin.com");
        originPatterns.add("https://*.alex-dyakin.com");
        
        // Mumble Bridge origins
        originPatterns.add("http://localhost:3080");
        originPatterns.add("https://voice.alex-dyakin.com");
        originPatterns.add("https://mumble.alex-dyakin.com");

        // Android (Capacitor) app — the WebView serves the bundled build from a
        // local origin, so its fetches are still subject to CORS
        originPatterns.add("capacitor://localhost");
        originPatterns.add("https://localhost");
        originPatterns.add("http://localhost");
        
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
}