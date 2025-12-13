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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsSource) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsSource))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/api/health", "/api/info").permitAll()
                .requestMatchers("/api/test/**").permitAll()  // Allow access to test endpoints
                .requestMatchers("/api/players/**").permitAll()  // Allow access to player endpoints for testing
                .requestMatchers("/api/media/**").permitAll()  // Allow access to media endpoints for testing
                .requestMatchers("/api/playlists/**").permitAll()  // Allow access to playlist endpoints
                .requestMatchers("/api/playback/**").permitAll()  // Allow access to playback position endpoints
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
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
        
        // Log the configured origins for debugging
        System.out.println("=== CORS Configuration ===");
        System.out.println("Configured origins: " + origins);
        System.out.println("========================");
        
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Range", "Accept-Ranges", "Content-Length", "Content-Type"));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}