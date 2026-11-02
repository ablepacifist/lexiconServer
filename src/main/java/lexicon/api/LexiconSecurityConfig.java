package lexicon.api;

import lexicon.logic.PlayerManagerService;
import lexicon.logic.RememberMeManagerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.session.SessionManagementFilter;
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

    // hasRole() adds the ROLE_ prefix itself
    private static final String ROLE_USER = "USER";
    private static final String ROLE_SERVICE = "SERVICE";

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

    // Shared secret the GTW Mumble bridge sends as X-Service-Token. Empty = service access off.
    @Value("${lexicon.service-token:}")
    private String serviceToken;

    // Transition switch for deploying the bridge: while true, the routes the GTW bridge used to call
    // without credentials stay open. Set LEXICON_LEGACY_OPEN_ROUTES=false (or remove it) once the
    // bridge sends X-Service-Token.
    @Value("${lexicon.security.legacy-open-routes:false}")
    private boolean legacyOpenRoutes;

    @Value("${app.update.public-metadata:false}")
    private boolean appUpdatePublicMetadata;

    @Value("${app.update.public-download:false}")
    private boolean appUpdatePublicDownload;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsSource,
                                           RememberMeManagerService rememberMe,
                                           PlayerManagerService players) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .maximumSessions(10) // Allow multiple sessions per user
            )
            .authorizeHttpRequests(auth -> {
                // Sign-in and the pages that run before it
                auth.requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/logout", "/api/auth/me").permitAll();
                auth.requestMatchers("/api/auth/sso/**").permitAll();
                auth.requestMatchers("/api/health", "/api/info").permitAll();
                if (appUpdatePublicMetadata) {
                    auth.requestMatchers("/api/app/version").permitAll();
                }
                if (appUpdatePublicDownload) {
                    auth.requestMatchers("/api/app/download/**").permitAll();
                }

                // Loaded by <audio>/<video>/<img> tags and EventSource, which cannot send the Android
                // shell's Authorization header, so they have to stay readable without a login.
                auth.requestMatchers(HttpMethod.GET,
                        "/api/media/stream/**", "/api/stream/**",
                        "/api/avatar/image/**", "/api/chat/files/**",
                        "/api/livestream/updates", "/api/notifications/stream",
                        "/api/playlists/import-progress/**").permitAll();

                // Event polls are shared by link and voted on by guests (a browser-generated voterKey, no account).
                // Creating events and polls still needs a login.
                auth.requestMatchers(HttpMethod.GET, "/api/events/*", "/api/events/*/polls/*").permitAll();
                auth.requestMatchers(HttpMethod.PUT, "/api/events/*/polls/*/votes").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/events/*/polls/*/options").permitAll();

                // The GTW Mumble bridge (X-Service-Token) may use only what it needs; a signed-in player may use anything
                // else below. No player client calls these directly (players reach them through the bridge), so
                // they are bridge-only: storing chat messages, chat uploads, and creating notifications or pushes for anyone.
                String[] bridgeOnly = {"/api/messages/**", "/api/chat/upload",
                        "/api/push/send", "/api/push/send-bulk"};
                String[] bridgeAndPlayers = {"/api/chat/**", "/api/avatar/**", "/api/push/**",
                        "/api/notifications/**", "/api/livestream/**"};
                String[] bridgeReads = {"/api/players", "/api/players/username/*", "/api/media/**", "/api/playlists/**"};
                if (legacyOpenRoutes) {
                    auth.requestMatchers(bridgeOnly).permitAll();
                    auth.requestMatchers(bridgeAndPlayers).permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/api/notifications").permitAll();
                    auth.requestMatchers(HttpMethod.GET, bridgeReads).permitAll();
                } else {
                    auth.requestMatchers(bridgeOnly).hasRole(ROLE_SERVICE);
                    auth.requestMatchers(HttpMethod.POST, "/api/notifications").hasRole(ROLE_SERVICE);
                    auth.requestMatchers(bridgeAndPlayers).hasAnyRole(ROLE_USER, ROLE_SERVICE);
                    auth.requestMatchers(HttpMethod.GET, bridgeReads).hasAnyRole(ROLE_USER, ROLE_SERVICE);
                }

                // Everything else under /api needs a signed-in player
                auth.requestMatchers("/api/**").hasRole(ROLE_USER);
                auth.anyRequest().permitAll();
            })
            // Unauthenticated callers of a protected route get 401 (the site and app already handle that), not 403
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            // After SessionManagementFilter so Spring's concurrent-session control stays out of Lexicon's own sessions
            .addFilterAfter(new ServiceTokenAuthFilter(serviceToken), SessionManagementFilter.class)
            .addFilterAfter(new RememberMeSessionFilter(rememberMe, players), ServiceTokenAuthFilter.class)
            .addFilterAfter(new SessionAuthFilter(), RememberMeSessionFilter.class)
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