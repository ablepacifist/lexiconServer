package lexicon.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

import java.util.List;

/**
 * Security configuration for Lexicon
 * Following similar pattern to Alchemy but adapted for media sharing
 */
@Configuration
@EnableWebSecurity
public class LexiconSecurityConfig {

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
        config.setAllowedOrigins(List.of(
            "http://localhost:3000",                            // local dev
            "http://localhost:3001",                            // local dev alt port
            "http://127.0.0.1:3000",                           // local dev
            "http://127.0.0.1:3001",                           // local dev alt port
            "http://group-net.gl.at.ply.gg:58938",             // Frontend tunnel
            "http://147.185.221.211:58938",                     // Bilbo PC frontend URL
            "http://see-recover.gl.at.ply.gg:36567"             // Gateway tunnel
        ));

        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}