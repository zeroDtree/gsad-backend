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
import com.zerodtree.gsad.domain.user.api.DeleteAdminUserResponse;
import com.zerodtree.gsad.domain.user.api.UpdateAdminUserRequest;
import com.zerodtree.gsad.domain.user.model.UserStatus;
import com.zerodtree.gsad.domain.user.persistence.User;
import com.zerodtree.gsad.domain.user.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private AdminUserService adminUserService;

    @Test
    void list_filtersByCohortStatusAndRole() {
        User user = sampleUser(2L, "student@example.com");
        when(userRepository.findFiltered(eq(UserStatus.ACTIVE), eq("2024"), eq("user"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(applicationRepository.countByUserIdAndAuditStatusIn(eq(2L), any()))
                .thenReturn(0L);

        PageResult<AdminUserVO> result = adminUserService.list("2024", "ACTIVE", "user", 1, 20);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst().email()).isEqualTo("student@example.com");
        assertThat(result.getItems().getFirst().status()).isEqualTo("ACTIVE");
        assertThat(result.getItems().getFirst().roles()).isEmpty();
    }

    @Test
    void list_adminUser_includesRoles() {
        User admin = sampleUser(1L, "admin@gsad.local");
        admin.setRoles("admin");
        when(userRepository.findFiltered(isNull(), isNull(), eq("admin"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(admin)));
        when(applicationRepository.countByUserIdAndAuditStatusIn(eq(1L), any()))
                .thenReturn(0L);

        PageResult<AdminUserVO> result = adminUserService.list(null, null, "admin", 1, 20);

        assertThat(result.getItems().getFirst().roles()).containsExactly("admin");
    }

    @Test
    void update_setsInactive() {
        User user = sampleUser(2L, "student@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(applicationRepository.countByUserIdAndAuditStatusIn(eq(2L), any()))
                .thenReturn(0L);

        AdminUserVO vo = adminUserService.update(2L, new UpdateAdminUserRequest(null, null, null, null, UserStatus.INACTIVE));

        assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
        assertThat(vo.status()).isEqualTo("INACTIVE");
    }

    @Test
    void update_disableAdmin_forbidden() {
        User admin = sampleUser(1L, "admin@gsad.local");
        admin.setRoles("admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> adminUserService.update(
                        1L, new UpdateAdminUserRequest(null, null, null, null, UserStatus.INACTIVE)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void delete_withoutRevokeSsh_removesUserAndApplications() {
        User user = sampleUser(2L, "student@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        DeleteAdminUserResponse response = adminUserService.delete(1L, 2L, false);

        assertThat(response.deleted()).isTrue();
        verify(applicationRepository).deleteByUserId(2L);
        verify(userRepository).delete(user);
        verify(applicationService, never()).revokeAllForUser(any());
    }

    @Test
    void delete_withRevokeSsh_andActiveAccess_returnsPending() {
        User user = sampleUser(2L, "student@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(applicationRepository.countByUserIdAndAuditStatusIn(2L, List.of(AuditStatus.REVOKING)))
                .thenReturn(1L);

        DeleteAdminUserResponse response = adminUserService.delete(1L, 2L, true);

        assertThat(response.deleted()).isFalse();
        assertThat(response.pendingRevokes()).isEqualTo(1);
        verify(applicationService).revokeAllForUser(2L);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void delete_withRevokeSsh_andAllTerminal_deletesUser() {
        User user = sampleUser(2L, "student@example.com");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(applicationRepository.countByUserIdAndAuditStatusIn(2L, List.of(AuditStatus.REVOKING)))
                .thenReturn(0L);

        DeleteAdminUserResponse response = adminUserService.delete(1L, 2L, true);

        assertThat(response.deleted()).isTrue();
        verify(applicationRepository).deleteByUserId(2L);
        verify(userRepository).delete(user);
    }

    @Test
    void delete_admin_forbidden() {
        User admin = sampleUser(1L, "admin@gsad.local");
        admin.setRoles("admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> adminUserService.delete(2L, 1L, false))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void bulkDisable_selectAll_disablesActiveAndSkipsInactive() {
        User active = sampleUser(2L, "active@example.com");
        User inactive = sampleUser(3L, "inactive@example.com");
        inactive.setStatus(UserStatus.INACTIVE);
        when(userRepository.findFiltered(eq(UserStatus.ACTIVE), eq("2024"), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(active, inactive)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(active));
        when(userRepository.save(active)).thenReturn(active);
        when(applicationRepository.countByUserIdAndAuditStatusIn(eq(2L), any())).thenReturn(0L);

        BulkDisableUsersResponse response = adminUserService.bulkDisable(
                1L, new BulkUserActionRequest(null, true, "2024", "ACTIVE", null));

        assertThat(response.disabled()).isEqualTo(1);
        assertThat(response.skipped()).isEqualTo(1);
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void bulkDisable_skipsAdminWithError() {
        User student = sampleUser(2L, "student@example.com");
        User admin = sampleUser(1L, "admin@gsad.local");
        admin.setRoles("admin");
        when(userRepository.findAllById(any())).thenReturn(List.of(student, admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.save(student)).thenReturn(student);
        when(applicationRepository.countByUserIdAndAuditStatusIn(eq(2L), any())).thenReturn(0L);

        BulkDisableUsersResponse response = adminUserService.bulkDisable(
                1L, new BulkUserActionRequest(List.of(2L, 1L), false, null, null, null));

        assertThat(response.disabled()).isEqualTo(1);
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().getFirst().email()).isEqualTo("admin@gsad.local");
    }

    @Test
    void bulkDisable_withExplicitIds() {
        User user = sampleUser(2L, "student@example.com");
        when(userRepository.findAllById(any())).thenReturn(List.of(user));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(applicationRepository.countByUserIdAndAuditStatusIn(eq(2L), any())).thenReturn(0L);

        BulkDisableUsersResponse response = adminUserService.bulkDisable(
                1L, new BulkUserActionRequest(List.of(2L), false, null, null, null));

        assertThat(response.disabled()).isEqualTo(1);
    }

    @Test
    void bulkEnable_selectAll_enablesInactiveAndSkipsActive() {
        User active = sampleUser(2L, "active@example.com");
        User inactive = sampleUser(3L, "inactive@example.com");
        inactive.setStatus(UserStatus.INACTIVE);
        when(userRepository.findFiltered(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(active, inactive)));
        when(userRepository.findById(3L)).thenReturn(Optional.of(inactive));
        when(userRepository.save(inactive)).thenReturn(inactive);
        when(applicationRepository.countByUserIdAndAuditStatusIn(eq(3L), any())).thenReturn(0L);

        BulkEnableUsersResponse response = adminUserService.bulkEnable(
                1L, new BulkUserActionRequest(null, true, null, "all", null));

        assertThat(response.enabled()).isEqualTo(1);
        assertThat(response.skipped()).isEqualTo(1);
        assertThat(response.errors()).isEmpty();
        assertThat(inactive.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void bulkEnable_withExplicitIds() {
        User user = sampleUser(2L, "student@example.com");
        user.setStatus(UserStatus.INACTIVE);
        when(userRepository.findAllById(any())).thenReturn(List.of(user));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(applicationRepository.countByUserIdAndAuditStatusIn(eq(2L), any())).thenReturn(0L);

        BulkEnableUsersResponse response = adminUserService.bulkEnable(
                1L, new BulkUserActionRequest(List.of(2L), false, null, null, null));

        assertThat(response.enabled()).isEqualTo(1);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void bulkDelete_mixedResults() {
        User student = sampleUser(2L, "student@example.com");
        User admin = sampleUser(1L, "admin@gsad.local");
        admin.setRoles("admin");
        when(userRepository.findAllById(any())).thenReturn(List.of(student, admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        BulkDeleteUsersResponse response = adminUserService.bulkDelete(
                1L, new BulkDeleteUsersRequest(List.of(2L, 1L), false, null, null, false, null));

        assertThat(response.deleted()).isEqualTo(1);
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().getFirst().email()).isEqualTo("admin@gsad.local");
        verify(applicationRepository).deleteByUserId(2L);
        verify(userRepository).delete(student);
    }

    @Test
    void bulkDelete_pendingRevoke() {
        User student = sampleUser(2L, "student@example.com");
        when(userRepository.findAllById(any())).thenReturn(List.of(student));
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(applicationRepository.countByUserIdAndAuditStatusIn(2L, List.of(AuditStatus.REVOKING)))
                .thenReturn(1L);

        BulkDeleteUsersResponse response = adminUserService.bulkDelete(
                1L, new BulkDeleteUsersRequest(List.of(2L), false, null, null, true, null));

        assertThat(response.deleted()).isZero();
        assertThat(response.pending()).isEqualTo(1);
    }

    @Test
    void bulkAction_over500_throwsBadRequest() {
        List<User> tooMany = LongStream.rangeClosed(1, 501)
                .mapToObj(id -> sampleUser(id, "user" + id + "@example.com"))
                .toList();
        when(userRepository.findFiltered(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(tooMany));

        assertThatThrownBy(() -> adminUserService.bulkDisable(
                        1L, new BulkUserActionRequest(null, true, null, "all", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ARGUMENT);
    }

    private static User sampleUser(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setLinuxUsername("user" + id);
        user.setPassword("hash");
        user.setRoles("");
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
