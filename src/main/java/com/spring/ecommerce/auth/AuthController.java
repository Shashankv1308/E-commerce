package com.spring.ecommerce.auth;

import com.spring.ecommerce.auth.dto.AuthResponse;
import com.spring.ecommerce.auth.dto.LoginRequest;
import com.spring.ecommerce.auth.dto.SignupRequest;
import com.spring.ecommerce.auth.dto.SignupResponse;
import com.spring.ecommerce.security.JwtService;

import jakarta.validation.Valid;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController 
{

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthService authService;

    public AuthController(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            AuthService authService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.authService = authService;
    }

    @PostMapping("/register")
    public SignupResponse register(
            @RequestBody @Valid SignupRequest request
    ) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) 
    {

        Authentication authentication =
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    request.getEmail(),
                    request.getPassword()
                )
            );

        String token = jwtService.generateToken(authentication.getName());

        AuthResponse response = new AuthResponse();
        response.setToken(token);

        return response;
    }
}
