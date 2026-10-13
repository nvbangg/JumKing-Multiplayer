package jumpking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import jumpking.identity.AuthRateLimiter;
import jumpking.identity.SessionAuthority;
import jumpking.network.ApiErrors;
import jumpking.network.HttpBoundaryFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.filter.CorsFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.Customizer;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SecuritySettings.class)
public class SecurityConfiguration {
    @Bean Clock applicationClock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean UserDetailsService userDetailsService(JdbcTemplate jdbc) {
        return email -> jdbc.query("SELECT email,password_hash FROM users WHERE type='ACCOUNT' AND email=?",
            (row, index) -> User.withUsername(row.getString("email")).password(row.getString("password_hash")).roles("ACCOUNT").build(), email)
            .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
    @Bean ServletListenerRegistrationBean<SessionAuthority> sessionListener(SessionAuthority authority) {
        return new ServletListenerRegistrationBean<>(authority);
    }
    @Bean HttpSessionCsrfTokenRepository csrfTokens() { return new HttpSessionCsrfTokenRepository(); }
    @Bean CorsFilter corsFilter(SecuritySettings settings, ObjectMapper json) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(settings.allowedOrigins()); cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Accept", "Content-Type", "X-CSRF-TOKEN")); cors.setMaxAge(600L);
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", cors);
        var filter = new CorsFilter(source);
        filter.setCorsProcessor(new DefaultCorsProcessor() {
            @Override protected void rejectRequest(org.springframework.http.server.ServerHttpResponse response) throws java.io.IOException {
                response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                response.getHeaders().setCacheControl("no-store");
                json.writeValue(response.getBody(), ApiErrors.body("ORIGIN_DENIED", "Origin is not allowed")); response.flush();
            }
        });
        return filter;
    }
    @Bean FilterRegistrationBean<CorsFilter> corsRegistration(CorsFilter filter) {
        var registration = new FilterRegistrationBean<>(filter); registration.setEnabled(false); return registration;
    }
    @Bean SecurityFilterChain security(HttpSecurity http, SecuritySettings settings, SessionAuthority sessions,
            AuthRateLimiter limits, ObjectMapper json, HttpSessionCsrfTokenRepository csrf) throws Exception {
        http.cors(Customizer.withDefaults())
            .csrf(configuration -> configuration.csrfTokenRepository(csrf))
            .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(requests -> requests
                .requestMatchers("/api/community/**", "/api/solo/**", "/api/auth/guest-history/**").hasRole("ACCOUNT")
                .anyRequest().permitAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> ApiErrors.write(response, json, 401, "UNAUTHENTICATED", "Sign in to continue"))
                .accessDeniedHandler((request, response, error) -> ApiErrors.write(response, json, 403,
                    error instanceof org.springframework.security.web.csrf.CsrfException ? "CSRF" : "FORBIDDEN", "This request is not allowed")))
            .addFilterBefore(new HttpBoundaryFilter(settings, sessions, limits, json), CsrfFilter.class);
        return http.build();
    }
}
