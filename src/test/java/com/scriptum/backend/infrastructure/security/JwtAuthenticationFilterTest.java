package com.scriptum.backend.infrastructure.security;

import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationFilter")
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private static final String EMAIL = "user@example.invalid";
    private static final String TOKEN = "a-signed-token";

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notes");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    private static UserDetails userDetails() {
        return new User(EMAIL, "hashed-password", List.of(new SimpleGrantedAuthority("USER")));
    }

    private static Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("continues the chain unauthenticated when no Authorization header is present")
    void continuesUnauthenticatedWithoutAuthorizationHeader() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(currentAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    @DisplayName("continues the chain unauthenticated when the scheme is not Bearer")
    void continuesUnauthenticatedForNonBearerScheme() throws Exception {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNzd29yZA==");

        filter.doFilter(request, response, chain);

        assertThat(currentAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    @DisplayName("populates the SecurityContext from a valid Bearer token")
    void populatesSecurityContextForValidToken() throws Exception {
        request.addHeader("Authorization", "Bearer " + TOKEN);
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractEmail(TOKEN)).thenReturn(EMAIL);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(userDetails());

        filter.doFilter(request, response, chain);

        Authentication authentication = currentAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(EMAIL);
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("USER");
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getDetails()).isInstanceOf(WebAuthenticationDetails.class);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("leaves the context empty and skips the user lookup when the token fails validation")
    void leavesContextEmptyForInvalidToken() throws Exception {
        request.addHeader("Authorization", "Bearer " + TOKEN);
        when(jwtService.isTokenValid(TOKEN)).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(currentAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
        // An invalid token must not cost a database round trip.
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    @DisplayName("continues the chain unauthenticated when parsing the subject throws")
    void continuesUnauthenticatedWhenSubjectParsingThrows() throws Exception {
        request.addHeader("Authorization", "Bearer " + TOKEN);
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractEmail(TOKEN)).thenThrow(new MalformedJwtException("not a jwt"));

        filter.doFilter(request, response, chain);

        assertThat(currentAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    @DisplayName("continues the chain unauthenticated when the token subject is not a known user")
    void continuesUnauthenticatedForUnknownSubject() throws Exception {
        request.addHeader("Authorization", "Bearer " + TOKEN);
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractEmail(TOKEN)).thenReturn("ghost@example.invalid");
        when(userDetailsService.loadUserByUsername("ghost@example.invalid"))
                .thenThrow(new UsernameNotFoundException("User not found"));

        filter.doFilter(request, response, chain);

        assertThat(currentAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    @DisplayName("continues the chain unauthenticated when the token carries no subject")
    void continuesUnauthenticatedWhenSubjectIsNull() throws Exception {
        request.addHeader("Authorization", "Bearer " + TOKEN);
        when(jwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(jwtService.extractEmail(TOKEN)).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertThat(currentAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    @DisplayName("does not overwrite an authentication already present in the context")
    void doesNotOverwriteExistingAuthentication() throws Exception {
        Authentication preexisting = new UsernamePasswordAuthenticationToken(
                "already@example.invalid", null, List.of(new SimpleGrantedAuthority("USER")));
        SecurityContextHolder.getContext().setAuthentication(preexisting);
        request.addHeader("Authorization", "Bearer " + TOKEN);

        filter.doFilter(request, response, chain);

        assertThat(currentAuthentication()).isSameAs(preexisting);
        assertThat(chain.getRequest()).isSameAs(request);
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    @DisplayName("treats a bare \"Bearer\" header with no token as unauthenticated")
    void treatsBearerWithoutTokenAsUnauthenticated() throws Exception {
        request.addHeader("Authorization", "Bearer");

        filter.doFilter(request, response, chain);

        assertThat(currentAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
        verifyNoInteractions(jwtService, userDetailsService);
    }
}
