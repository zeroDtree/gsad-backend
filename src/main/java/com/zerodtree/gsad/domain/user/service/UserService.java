package com.zerodtree.gsad.domain.user.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.user.api.ChangePasswordRequest;
import com.zerodtree.gsad.domain.user.api.LoginRequest;
import com.zerodtree.gsad.domain.user.model.UserStatus;
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
    private final UserPasswordService userPasswordService;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(readOnly = true)
    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid credentials");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Account is inactive");
        }
        var roles = AuthorityUtils.parseRoles(user.getRoles());
        String token = jwtTokenProvider.generateToken(user.getEmail(), roles, user.getId(), user.getPassword());
        return new LoginResult(token, user.getEmail(), roles);
    }

    @Transactional
    public LoginResult changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Not authenticated"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Account is inactive");
        }
        userPasswordService.assertMatches(user, request.currentPassword());
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "New password must differ from current password");
        }
        userPasswordService.applyPassword(user, request.newPassword());
        userRepository.save(user);
        var roles = AuthorityUtils.parseRoles(user.getRoles());
        String token = jwtTokenProvider.generateToken(user.getEmail(), roles, user.getId(), user.getPassword());
        return new LoginResult(token, user.getEmail(), roles);
    }
}
