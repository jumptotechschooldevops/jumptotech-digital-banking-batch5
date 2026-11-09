package com.jumptotech.banking.auth;

import com.jumptotech.banking.auth.dto.AuthUserDto;
import com.jumptotech.banking.auth.dto.LoginRequest;
import com.jumptotech.banking.auth.dto.RegisterRequest;
import com.jumptotech.banking.common.exception.InvalidCredentialsException;
import com.jumptotech.banking.customer.CustomerProfileService;
import com.jumptotech.banking.customer.dto.CustomerProfileDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final CustomerProfileService customerProfileService;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthService authService, AuthenticationManager authenticationManager,
                           CustomerProfileService customerProfileService) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.customerProfileService = customerProfileService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthUserDto> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        AuthUserDto created = authService.register(request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthUserDto> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String ip = httpRequest.getRemoteAddr();
        authService.assertNotLocked(request.email());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException e) {
            authService.onLoginFailure(request.email(), ip);
            throw new InvalidCredentialsException();
        }

        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        authService.onLoginSuccess(principal.getUserId(), ip);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        CustomerProfileDto profile = customerProfileService.getMyProfile(principal.getUserId());
        return ResponseEntity.ok(new AuthUserDto(principal.getUserId(), profile.email(), principal.getRole(),
                profile.firstName(), profile.lastName()));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserDto> me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        CustomerProfileDto profile = customerProfileService.getMyProfile(principal.getUserId());
        return ResponseEntity.ok(new AuthUserDto(principal.getUserId(), profile.email(), principal.getRole(),
                profile.firstName(), profile.lastName()));
    }
}
