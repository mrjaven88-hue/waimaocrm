package com.ftcrm.web;

import com.ftcrm.service.AuthService;
import com.ftcrm.web.dto.AuthDtos.AuthResponse;
import com.ftcrm.web.dto.AuthDtos.LoginRequest;
import com.ftcrm.web.dto.AuthDtos.RegisterRequest;
import com.ftcrm.web.dto.AuthDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final CurrentUser current;

    public AuthController(AuthService auth, CurrentUser current) {
        this.auth = auth;
        this.current = current;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return auth.me(current.require());
    }
}
