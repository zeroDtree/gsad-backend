package com.zerodtree.gsad.domain.user.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.user.api.AuthResponse;
import com.zerodtree.gsad.domain.user.api.LoginRequest;
import com.zerodtree.gsad.domain.user.api.RegisterRequest;
import com.zerodtree.gsad.domain.user.persistence.User;
import com.zerodtree.gsad.domain.user.persistence.UserRepository;
import com.zerodtree.gsad.security.AuthorityUtils;
import com.zerodtree.gsad.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "Email already registered");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRoles("");
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid credentials");
        }
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        var roles = AuthorityUtils.parseRoles(user.getRoles());
        String token = jwtTokenProvider.generateToken(user.getEmail(), roles, user.getId());
        return new AuthResponse(token, user.getEmail(), roles);
    }
}
