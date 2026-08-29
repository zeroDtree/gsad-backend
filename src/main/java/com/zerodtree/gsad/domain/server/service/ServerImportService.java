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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServerImportService {

    private final ServerRepository serverRepository;

    @Transactional
    public ServerImportResponse importCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "CSV file is required");
        }

        List<ServerImportError> errors = new ArrayList<>();
        Map<String, ImportRow> lastWins = new LinkedHashMap<>();

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
                lastWins.put(row.serverId(), row);
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Failed to read CSV file");
        }

        int created = 0;
        int updated = 0;
        for (ImportRow row : lastWins.values()) {
            Server existing = serverRepository.findByServerId(row.serverId()).orElse(null);
            if (existing != null) {
                existing.setAgentPsk(row.agentPsk());
                serverRepository.save(existing);
                updated++;
                continue;
            }

            Server server = new Server();
            server.setServerId(row.serverId());
            server.setAgentPsk(row.agentPsk());
            server.setStatus(ServerStatus.OFFLINE);
            server.setMetricsJson("{}");
            serverRepository.save(server);
            created++;
        }

        return new ServerImportResponse(created, updated, errors);
    }

    private ColumnIndex parseHeader(String headerLine) {
        String[] headers = headerLine.split(",", -1);
        ColumnIndex index = new ColumnIndex();
        for (int i = 0; i < headers.length; i++) {
            String name = headers[i].trim().toLowerCase(Locale.ROOT);
            switch (name) {
                case "server_id" -> index.serverId = i;
                case "agent_psk" -> index.agentPsk = i;
                default -> {
                }
            }
        }
        if (index.serverId < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Missing CSV column: server_id");
        }
        if (index.agentPsk < 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Missing CSV column: agent_psk");
        }
        return index;
    }

    private ImportRow parseRow(
            String[] fields, ColumnIndex columns, int rowNumber, List<ServerImportError> errors) {
        String serverId = ServerConstraints.normalize(readField(fields, columns.serverId));
        String serverIdError = ServerConstraints.serverIdError(serverId);
        if (serverIdError != null) {
            errors.add(new ServerImportError(rowNumber, serverIdError));
            return null;
        }

        String agentPsk = ServerConstraints.normalize(readField(fields, columns.agentPsk));
        String pskError = ServerConstraints.agentPskError(agentPsk);
        if (pskError != null) {
            errors.add(new ServerImportError(rowNumber, pskError));
            return null;
        }

        return new ImportRow(serverId, agentPsk);
    }

    private static String readField(String[] fields, int index) {
        if (index < 0 || index >= fields.length) {
            return "";
        }
        return fields[index].trim();
    }

    private record ImportRow(String serverId, String agentPsk) {}

    private static final class ColumnIndex {
        private int serverId = -1;
        private int agentPsk = -1;
    }
}
