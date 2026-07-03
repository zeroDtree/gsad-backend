package com.zerodtree.gsad.domain.user.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.application.service.LinuxUsernameResolver;
import com.zerodtree.gsad.domain.user.persistence.User;
import com.zerodtree.gsad.domain.user.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final LinuxUsernameResolver linuxUsernameResolver;

    public void applyImportProfile(
            User user,
            String linuxUsername,
            String displayName,
            String studentId,
            String cohort) {
        setLinuxUsername(user, linuxUsername);
        setStudentId(user, studentId);
        user.setDisplayName(blankToNull(displayName));
        user.setCohort(blankToNull(cohort));
    }

    public void applyPatchProfile(
            User user,
            String linuxUsername,
            String displayName,
            String cohort) {
        if (linuxUsername != null) {
            setLinuxUsername(user, linuxUsername);
        }
        if (displayName != null) {
            user.setDisplayName(blankToNull(displayName));
        }
        if (cohort != null) {
            user.setCohort(blankToNull(cohort));
        }
    }

    private void setLinuxUsername(User user, String linuxUsername) {
        String validated = linuxUsernameResolver.validateAndReturn(linuxUsername.trim());
        if (validated.equals(user.getLinuxUsername())) {
            return;
        }
        if (userRepository.existsByLinuxUsernameAndIdNot(validated, user.getId())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "linux_username already exists");
        }
        user.setLinuxUsername(validated);
    }

    private void setStudentId(User user, String studentId) {
        String normalized = blankToNull(studentId);
        if (normalized == null && user.getStudentId() == null) {
            return;
        }
        if (normalized != null && normalized.equals(user.getStudentId())) {
            return;
        }
        if (normalized != null && userRepository.existsByStudentIdAndIdNot(normalized, user.getId())) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "student_id already exists");
        }
        user.setStudentId(normalized);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
