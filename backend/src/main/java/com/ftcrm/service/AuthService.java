package com.ftcrm.service;

import com.ftcrm.common.ApiException;
import com.ftcrm.common.SideChannelGuard;
import com.ftcrm.domain.AppUser;
import com.ftcrm.domain.Role;
import com.ftcrm.repo.AppUserRepository;
import com.ftcrm.security.AuthUser;
import com.ftcrm.security.JwtService;
import com.ftcrm.time.TimeSource;
import com.ftcrm.web.dto.AuthDtos.AuthResponse;
import com.ftcrm.web.dto.AuthDtos.LoginRequest;
import com.ftcrm.web.dto.AuthDtos.RegisterRequest;
import com.ftcrm.web.dto.AuthDtos.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final TimeSource time;
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit, TimeSource time) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.time = time;
        this.dummyHash = encoder.encode("unused-dummy-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String displayName = request.displayName().trim();
        SideChannelGuard.reject(displayName, "姓名");
        if (displayName.isEmpty() || displayName.length() > 40) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "姓名不合法");
        }
        if (!acceptable(request.password())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "密码不合法");
        }
        if (users.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "用户名已存在");
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(request.password()));
        user.setDisplayName(displayName);
        user.setRole(Role.SALESPERSON);
        user.setCreatedAt(time.now());
        user = users.save(user);
        audit.record(AuthUser.from(user), "REGISTER", "USER", user.getId(), null, Map.of());
        return tokenFor(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        AppUser user = users.findByUsername(username).orElse(null);
        boolean matches = encoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) {
            audit.recordAnonymous(username, "LOGIN_FAILED", "USER");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "用户名或密码错误");
        }
        audit.record(AuthUser.from(user), "LOGIN", "USER", user.getId(), null, Map.of());
        return tokenFor(user);
    }

    public UserResponse me(AuthUser actor) {
        return toUser(users.findById(actor.id()).orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录")));
    }

    private AuthResponse tokenFor(AppUser user) {
        return new AuthResponse(jwt.issue(user), toUser(user));
    }

    private static UserResponse toUser(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                SideChannelGuard.display(user.getDisplayName()),
                user.getRole().name(),
                user.getRole().getLabel());
    }

    private static boolean acceptable(String password) {
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < password.length(); ) {
            int cp = password.codePointAt(i);
            if (Character.isLetter(cp)) {
                letter = true;
            }
            if (Character.isDigit(cp)) {
                digit = true;
            }
            i += Character.charCount(cp);
        }
        return letter && digit;
    }
}
