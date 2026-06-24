package com.zerodtree.gsad.domain.server.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.server.api.ServerImportError;
import com.zerodtree.gsad.domain.server.api.ServerImportResponse;
import com.zerodtree.gsad.domain.server.model.ServerStatus;
import com.zerodtree.gsad.domain.server.persistence.Server;
import com.zerodtree.gsad.domain.server.persistence.ServerRepository;
import lombok.RequiredArgsConstructor;
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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ServerImportService {

    private static final Pattern SERVER_ID_PATTERN =
            Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,254}$");

    private final ServerRepository serverRepository;

    @Transactional
    public ServerImportResponse importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "CSV file is required");
        }

        List<ServerImportError> errors = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        Set<String> seenServerIds = new HashSet<>();

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

                if (!seenServerIds.add(row.serverId())) {
                    errors.add(new ServerImportError(rowNumber, "duplicate server_id in CSV"));
                    continue;
                }

                if (serverRepository.existsByServerId(row.serverId())) {
                    skipped++;
                    continue;
                }

                Server server = new Server();
                server.setServerId(row.serverId());
                server.setSshHost(blankToNull(row.sshHost()));
                server.setResourceLevel(blankToNull(row.resourceLevel()));
                server.setStatus(ServerStatus.OFFLINE);
                server.setMetricsJson("{}");
                serverRepository.save(server);
                created++;
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Failed to read CSV file");
        }

        return new ServerImportResponse(created, skipped, errors);
    }

    private ColumnIndex parseHeader(String headerLine) {
        String[] headers = headerLine.split(",", -1);
        ColumnIndex index = new ColumnIndex();
        for (int i = 0; i < headers.length; i++) {
            String name = headers[i].trim().toLowerCase(Locale.ROOT);
            switch (name) {
                case "server_id" -> index.serverId = i;
                case "ssh_host" -> index.sshHost = i;
                case "resource_level" -> index.resourceLevel = i;
                default -> {
                }
            }
        }
        if (index.serverId < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Missing CSV column: server_id");
        }
        return index;
    }

    private ImportRow parseRow(
            String[] fields, ColumnIndex columns, int rowNumber, List<ServerImportError> errors) {
        String serverId = readField(fields, columns.serverId).trim();
        if (!StringUtils.hasText(serverId)) {
            errors.add(new ServerImportError(rowNumber, "server_id is required"));
            return null;
        }
        if (serverId.length() > 255) {
            errors.add(new ServerImportError(rowNumber, "server_id must be at most 255 characters"));
            return null;
        }
        if (!SERVER_ID_PATTERN.matcher(serverId).matches()) {
            errors.add(new ServerImportError(
                    rowNumber,
                    "server_id must start with alphanumeric and contain only letters, digits, ., _, -"));
            return null;
        }
        return new ImportRow(
                serverId,
                readField(fields, columns.sshHost),
                readField(fields, columns.resourceLevel));
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

    private record ImportRow(String serverId, String sshHost, String resourceLevel) {}

    private static final class ColumnIndex {
        private int serverId = -1;
        private int sshHost = -1;
        private int resourceLevel = -1;
    }
}
