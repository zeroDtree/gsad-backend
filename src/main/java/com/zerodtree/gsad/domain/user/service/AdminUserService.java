package com.zerodtree.gsad.domain.user.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.common.PageResult;
import com.zerodtree.gsad.domain.application.model.AuditStatus;
import com.zerodtree.gsad.domain.application.persistence.ApplicationRepository;
import com.zerodtree.gsad.domain.application.service.ApplicationService;
import com.zerodtree.gsad.domain.user.api.AdminUserVO;
import com.zerodtree.gsad.domain.user.api.BulkDeleteUsersRequest;
import com.zerodtree.gsad.domain.user.api.BulkDeleteUsersResponse;
import com.zerodtree.gsad.domain.user.api.BulkDisableUsersResponse;
import com.zerodtree.gsad.domain.user.api.BulkEnableUsersResponse;
import com.zerodtree.gsad.domain.user.api.BulkUserActionRequest;
import com.zerodtree.gsad.domain.user.api.BulkUserError;
import com.zerodtree.gsad.domain.user.api.DeleteAdminUserResponse;
import com.zerodtree.gsad.domain.user.api.UpdateAdminUserRequest;
import com.zerodtree.gsad.domain.user.model.UserStatus;
import com.zerodtree.gsad.domain.user.persistence.User;
import com.zerodtree.gsad.domain.user.persistence.UserRepository;
import com.zerodtree.gsad.security.AuthorityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final int BULK_MAX_SIZE = 500;

    private static final List<AuditStatus> ACTIVE_ACCESS_STATUSES =
            List.of(AuditStatus.ACTIVE, AuditStatus.REVOKING);

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;

    @Transactional(readOnly = true)
    public PageResult<AdminUserVO> list(String cohort, String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);

        UserStatus userStatus = parseStatusFilter(status);
        String cohortFilter = StringUtils.hasText(cohort) ? cohort.trim() : null;

        Page<User> resultPage = userRepository.findFiltered(userStatus, cohortFilter, pageable);
        List<AdminUserVO> items = resultPage.getContent().stream()
                .map(this::toVo)
                .toList();

        return PageResult.of(items, resultPage.getTotalElements(), safePage, safeSize);
    }

    @Transactional
    public AdminUserVO update(Long id, UpdateAdminUserRequest request) {
        User user = requireUser(id);
        if (request.displayName() != null) {
            user.setDisplayName(blankToNull(request.displayName()));
        }
        if (request.cohort() != null) {
            user.setCohort(blankToNull(request.cohort()));
        }
        if (request.notes() != null) {
            user.setNotes(blankToNull(request.notes()));
        }
        if (request.label() != null) {
            user.setLabel(blankToNull(request.label()));
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        userRepository.save(user);
        return toVo(user);
    }

    @Transactional
    public DeleteAdminUserResponse delete(Long adminUserId, Long targetUserId, boolean revokeSsh) {
        if (Objects.equals(adminUserId, targetUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot delete your own account");
        }

        User target = requireUser(targetUserId);
        assertNotLastAdmin(target);

        if (revokeSsh) {
            applicationService.revokeAllForUser(targetUserId);
            long pendingRevokes = applicationRepository.countByUserIdAndAuditStatusIn(
                    targetUserId, List.of(AuditStatus.REVOKING));
            if (pendingRevokes > 0) {
                return new DeleteAdminUserResponse(
                        false,
                        (int) pendingRevokes,
                        "SSH/GPU revoke in progress; retry delete after completion");
            }
        }

        applicationRepository.deleteByUserId(targetUserId);
        userRepository.delete(target);
        return new DeleteAdminUserResponse(true, 0, "User deleted");
    }

    @Transactional
    public BulkDisableUsersResponse bulkDisable(Long adminUserId, BulkUserActionRequest request) {
        List<User> targets = resolveTargetUsers(request);
        int disabled = 0;
        int skipped = 0;
        List<BulkUserError> errors = new ArrayList<>();

        for (User user : targets) {
            if (user.getStatus() == UserStatus.INACTIVE) {
                skipped++;
                continue;
            }
            try {
                update(user.getId(), new UpdateAdminUserRequest(null, null, null, null, UserStatus.INACTIVE));
                disabled++;
            } catch (BusinessException ex) {
                errors.add(toBulkError(user, ex.getMessage()));
            }
        }

        return new BulkDisableUsersResponse(disabled, skipped, errors);
    }

    @Transactional
    public BulkEnableUsersResponse bulkEnable(Long adminUserId, BulkUserActionRequest request) {
        List<User> targets = resolveTargetUsers(request);
        int enabled = 0;
        int skipped = 0;
        List<BulkUserError> errors = new ArrayList<>();

        for (User user : targets) {
            if (user.getStatus() == UserStatus.ACTIVE) {
                skipped++;
                continue;
            }
            try {
                update(user.getId(), new UpdateAdminUserRequest(null, null, null, null, UserStatus.ACTIVE));
                enabled++;
            } catch (BusinessException ex) {
                errors.add(toBulkError(user, ex.getMessage()));
            }
        }

        return new BulkEnableUsersResponse(enabled, skipped, errors);
    }

    @Transactional
    public BulkDeleteUsersResponse bulkDelete(Long adminUserId, BulkDeleteUsersRequest request) {
        List<User> targets = resolveTargetUsers(
                new BulkUserActionRequest(request.ids(), request.selectAll(), request.cohort(), request.status()));
        int deleted = 0;
        int pending = 0;
        int skipped = 0;
        List<BulkUserError> errors = new ArrayList<>();

        for (User user : targets) {
            try {
                DeleteAdminUserResponse result = delete(adminUserId, user.getId(), request.revokeSsh());
                if (result.deleted()) {
                    deleted++;
                } else {
                    pending++;
                }
            } catch (BusinessException ex) {
                if (ex.getErrorCode() == ErrorCode.FORBIDDEN
                        && Objects.equals(adminUserId, user.getId())) {
                    skipped++;
                } else {
                    errors.add(toBulkError(user, ex.getMessage()));
                }
            }
        }

        return new BulkDeleteUsersResponse(deleted, pending, skipped, errors);
    }

    private List<User> resolveTargetUsers(BulkUserActionRequest request) {
        boolean selectAll = Boolean.TRUE.equals(request.selectAll());
        List<User> targets;

        if (selectAll) {
            UserStatus userStatus = parseStatusFilter(request.status());
            String cohortFilter = StringUtils.hasText(request.cohort()) ? request.cohort().trim() : null;
            targets = userRepository.findFiltered(
                            userStatus,
                            cohortFilter,
                            PageRequest.of(0, BULK_MAX_SIZE + 1, Sort.by(Sort.Direction.DESC, "updatedAt")))
                    .getContent();
        } else {
            if (request.ids() == null || request.ids().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "User ids are required");
            }
            Set<Long> uniqueIds = new HashSet<>(request.ids());
            targets = userRepository.findAllById(uniqueIds);
            if (targets.size() != uniqueIds.size()) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "One or more users not found");
            }
        }

        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "No users matched");
        }
        if (targets.size() > BULK_MAX_SIZE) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "Bulk action supports at most " + BULK_MAX_SIZE + " users");
        }
        return targets;
    }

    private static BulkUserError toBulkError(User user, String reason) {
        return new BulkUserError(user.getId(), user.getEmail(), reason);
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
    }

    private void assertNotLastAdmin(User target) {
        if (!isAdmin(target)) {
            return;
        }
        long adminCount = userRepository.findAll().stream().filter(this::isAdmin).count();
        if (adminCount <= 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot delete the last admin");
        }
    }

    private boolean isAdmin(User user) {
        return AuthorityUtils.parseRoles(user.getRoles()).stream()
                .anyMatch(role -> "admin".equalsIgnoreCase(role));
    }

    private AdminUserVO toVo(User user) {
        long activeAccessCount = applicationRepository.countByUserIdAndAuditStatusIn(
                user.getId(), ACTIVE_ACCESS_STATUSES);
        return new AdminUserVO(
                user.getId(),
                user.getEmail(),
                user.getLinuxUsername(),
                user.getStatus().name(),
                user.getCohort(),
                user.getDisplayName(),
                user.getStudentId(),
                user.getNotes(),
                user.getLabel(),
                activeAccessCount,
                user.getCreatedAt(),
                user.getUpdatedAt());
    }

    private static UserStatus parseStatusFilter(String status) {
        if (!StringUtils.hasText(status) || "all".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return UserStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Invalid status filter: " + status);
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
