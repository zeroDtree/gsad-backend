package com.zerodtree.gsad.domain.user.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.application.service.LinuxUsernameResolver;
import com.zerodtree.gsad.domain.user.api.UserImportError;
import com.zerodtree.gsad.domain.user.api.UserImportResponse;
import com.zerodtree.gsad.domain.user.model.UserStatus;
import com.zerodtree.gsad.domain.user.persistence.User;
import com.zerodtree.gsad.domain.user.persistence.UserRepository;
import com.zerodtree.gsad.security.AuthorityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserImportService {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final List<String> REQUIRED_HEADERS =
            List.of("email", "linux_username", "initial_password");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LinuxUsernameResolver linuxUsernameResolver;

    @Transactional
    public UserImportResponse importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "CSV file is required");
        }

        List<UserImportError> errors = new ArrayList<>();
        int created = 0;
        int skipped = 0;

        Set<String> seenEmails = new HashSet<>();
        Set<String> seenUsernames = new HashSet<>();
        Set<String> seenStudentIds = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (!StringUtils.hasText(headerLine)) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "CSV file is empty");
            }

            ColumnIndex columns = parseHeader(headerLine);
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split(",", -1);
                ImportRow row = parseRow(fields, columns, rowNumber, errors);
                if (row == null) {
                    continue;
                }

                if (!seenEmails.add(row.email())) {
                    errors.add(new UserImportError(rowNumber, "duplicate email in CSV"));
                    continue;
                }
                if (!seenUsernames.add(row.linuxUsername())) {
                    errors.add(new UserImportError(rowNumber, "duplicate linux_username in CSV"));
                    continue;
                }
                if (StringUtils.hasText(row.studentId()) && !seenStudentIds.add(row.studentId())) {
                    errors.add(new UserImportError(rowNumber, "duplicate student_id in CSV"));
                    continue;
                }

                if (userRepository.existsByEmail(row.email())) {
                    skipped++;
                    continue;
                }
                if (userRepository.existsByLinuxUsername(row.linuxUsername())) {
                    errors.add(new UserImportError(rowNumber, "linux_username already exists"));
                    continue;
                }
                if (StringUtils.hasText(row.studentId()) && userRepository.existsByStudentId(row.studentId())) {
                    errors.add(new UserImportError(rowNumber, "student_id already exists"));
                    continue;
                }

                User user = new User();
                user.setEmail(row.email());
                user.setLinuxUsername(row.linuxUsername());
                user.setPassword(passwordEncoder.encode(row.initialPassword()));
                user.setStatus(UserStatus.ACTIVE);
                user.setDisplayName(blankToNull(row.displayName()));
                user.setStudentId(blankToNull(row.studentId()));
                user.setCohort(blankToNull(row.cohort()));
                user.setRoles(StringUtils.hasText(row.roles()) ? row.roles().trim() : "");
                userRepository.save(user);

                created++;
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Failed to read CSV file");
        }

        return new UserImportResponse(created, skipped, errors);
    }

    private ColumnIndex parseHeader(String headerLine) {
        String[] headers = headerLine.split(",", -1);
        ColumnIndex index = new ColumnIndex();
        for (int i = 0; i < headers.length; i++) {
            String name = headers[i].trim().toLowerCase(Locale.ROOT);
            switch (name) {
                case "email" -> index.email = i;
                case "linux_username" -> index.linuxUsername = i;
                case "display_name" -> index.displayName = i;
                case "student_id" -> index.studentId = i;
                case "cohort" -> index.cohort = i;
                case "initial_password" -> index.initialPassword = i;
                case "roles" -> index.roles = i;
                default -> {
                }
            }
        }
        for (String required : REQUIRED_HEADERS) {
            if (!index.has(required)) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Missing CSV column: " + required);
            }
        }
        return index;
    }

    private ImportRow parseRow(String[] fields, ColumnIndex columns, int rowNumber, List<UserImportError> errors) {
        String email = readField(fields, columns.email).trim().toLowerCase(Locale.ROOT);
        String linuxUsername = readField(fields, columns.linuxUsername).trim();
        String initialPassword = readField(fields, columns.initialPassword);
        if (!StringUtils.hasText(email)) {
            errors.add(new UserImportError(rowNumber, "email is required"));
            return null;
        }
        if (!StringUtils.hasText(linuxUsername)) {
            errors.add(new UserImportError(rowNumber, "linux_username is required"));
            return null;
        }
        if (!StringUtils.hasText(initialPassword)) {
            errors.add(new UserImportError(rowNumber, "initial_password is required"));
            return null;
        }
        if (initialPassword.length() < MIN_PASSWORD_LENGTH) {
            errors.add(new UserImportError(rowNumber, "initial_password must be at least 8 characters"));
            return null;
        }
        try {
            linuxUsernameResolver.validateAndReturn(linuxUsername);
        } catch (BusinessException ex) {
            errors.add(new UserImportError(rowNumber, ex.getMessage()));
            return null;
        }
        String rolesField = readField(fields, columns.roles);
        if (StringUtils.hasText(rolesField)
                && AuthorityUtils.parseRoles(rolesField).stream()
                        .anyMatch(role -> "admin".equalsIgnoreCase(role))) {
            errors.add(new UserImportError(rowNumber, "admin role cannot be assigned via import"));
            return null;
        }
        return new ImportRow(
                email,
                linuxUsername,
                readField(fields, columns.displayName),
                readField(fields, columns.studentId),
                readField(fields, columns.cohort),
                initialPassword,
                rolesField);
    }

    private static String readField(String[] fields, int index) {
        if (index < 0 || index >= fields.length) {
            return "";
        }
        return fields[index].trim();
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record ImportRow(
            String email,
            String linuxUsername,
            String displayName,
            String studentId,
            String cohort,
            String initialPassword,
            String roles) {}

    private static final class ColumnIndex {
        private int email = -1;
        private int linuxUsername = -1;
        private int displayName = -1;
        private int studentId = -1;
        private int cohort = -1;
        private int initialPassword = -1;
        private int roles = -1;

        private boolean has(String name) {
            return switch (name) {
                case "email" -> email >= 0;
                case "linux_username" -> linuxUsername >= 0;
                case "initial_password" -> initialPassword >= 0;
                default -> false;
            };
        }
    }
}
