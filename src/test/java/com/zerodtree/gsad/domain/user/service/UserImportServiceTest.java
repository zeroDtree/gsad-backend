package com.zerodtree.gsad.domain.user.service;

import com.zerodtree.gsad.domain.application.service.LinuxUsernameResolver;
import com.zerodtree.gsad.domain.user.api.UserImportResponse;
import com.zerodtree.gsad.domain.user.model.UserStatus;
import com.zerodtree.gsad.domain.user.persistence.User;
import com.zerodtree.gsad.domain.user.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserImportServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPasswordService userPasswordService;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private LinuxUsernameResolver linuxUsernameResolver;

    @InjectMocks
    private UserImportService userImportService;

    @Test
    void importCsv_createsUserWithProvidedPassword() {
        String csv = """
                email,linux_username,display_name,student_id,cohort,initial_password,roles
                alice@example.com,alice,Alice,2024001,2024,TempPass2024!,
                """;
        MockMultipartFile file = new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes());

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByLinuxUsername("alice")).thenReturn(false);
        when(userRepository.existsByStudentId("2024001")).thenReturn(false);
        when(linuxUsernameResolver.validateAndReturn("alice")).thenReturn("alice");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserImportResponse response = userImportService.importCsv(file);

        assertThat(response.created()).isEqualTo(1);
        assertThat(response.updated()).isZero();
        assertThat(response.errors()).isEmpty();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getLinuxUsername()).isEqualTo("alice");
        assertThat(saved.getDisplayName()).isEqualTo("Alice");
        assertThat(saved.getStudentId()).isEqualTo("2024001");
        assertThat(saved.getCohort()).isEqualTo("2024");
        verify(userPasswordService).applyPassword(saved, "TempPass2024!");
    }

    @Test
    void importCsv_rejectsMissingInitialPassword() {
        String csv = """
                email,linux_username,display_name,student_id,cohort,initial_password,roles
                bob@example.com,bob,,,,,
                """;
        MockMultipartFile file = new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes());

        UserImportResponse response = userImportService.importCsv(file);

        assertThat(response.created()).isZero();
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().getFirst().reason()).isEqualTo("initial_password is required");
    }

    @Test
    void importCsv_rejectsShortInitialPassword() {
        String csv = """
                email,linux_username,display_name,student_id,cohort,initial_password,roles
                bob@example.com,bob,,,,short,
                """;
        MockMultipartFile file = new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes());

        UserImportResponse response = userImportService.importCsv(file);

        assertThat(response.created()).isZero();
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().getFirst().reason()).isEqualTo("initial_password must be at least 8 characters");
    }

    @Test
    void importCsv_upsertsExistingEmailAndOverwritesPassword() {
        String csv = """
                email,linux_username,display_name,student_id,cohort,initial_password
                existing@example.com,newname,Existing User,2024002,2025,ValidPass1!
                """;
        MockMultipartFile file = new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes());

        User existing = new User();
        existing.setId(2L);
        existing.setEmail("existing@example.com");
        existing.setLinuxUsername("oldname");
        existing.setPassword("hash");
        existing.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));
        when(linuxUsernameResolver.validateAndReturn("newname")).thenReturn("newname");
        when(userRepository.save(existing)).thenReturn(existing);

        UserImportResponse response = userImportService.importCsv(file);

        assertThat(response.created()).isZero();
        assertThat(response.updated()).isEqualTo(1);
        assertThat(response.errors()).isEmpty();
        verify(userProfileService).applyImportProfile(
                existing, "newname", "Existing User", "2024002", "2025");
        verify(userPasswordService).applyPassword(existing, "ValidPass1!");
    }

    @Test
    void importCsv_rejectsExistingAdminEmail() {
        String csv = """
                email,linux_username,initial_password
                admin@example.com,adminuser,ValidPass1!
                """;
        MockMultipartFile file = new MockMultipartFile("file", "users.csv", "text/csv", csv.getBytes());

        User admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@example.com");
        admin.setLinuxUsername("adminuser");
        admin.setRoles("admin");

        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(linuxUsernameResolver.validateAndReturn("adminuser")).thenReturn("adminuser");

        UserImportResponse response = userImportService.importCsv(file);

        assertThat(response.updated()).isZero();
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().getFirst().reason()).isEqualTo("admin account cannot be modified via import");
        verify(userRepository, never()).save(any());
    }
}
