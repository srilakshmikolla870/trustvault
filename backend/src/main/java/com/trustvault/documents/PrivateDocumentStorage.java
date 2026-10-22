package com.trustvault.documents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PrivateDocumentStorage {
    private final JdbcTemplate jdbc;
    private final Path root;
    private final boolean databaseBacked;

    public PrivateDocumentStorage(JdbcTemplate jdbc,
            @Value("${trustvault.storage.mode:filesystem}") String mode,
            @Value("${trustvault.storage.directory:./var/private-documents}") String directory) {
        this.jdbc = jdbc;
        this.root = Path.of(directory).toAbsolutePath().normalize();
        if (!mode.equals("filesystem") && !mode.equals("database")) {
            throw new IllegalArgumentException("TRUSTVAULT_STORAGE_MODE must be filesystem or database");
        }
        this.databaseBacked = mode.equals("database");
    }

    public String store(byte[] bytes) throws IOException {
        String key = UUID.randomUUID() + ".pdf";
        if (!databaseBacked) {
            Files.createDirectories(root);
            Files.write(resolve(key), bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return key;
        }
        try {
            jdbc.update("INSERT INTO document_contents (storage_key, content) VALUES (?, ?)", key, bytes);
            return key;
        } catch (DataAccessException exception) {
            throw new IOException("Could not store private document content", exception);
        }
    }

    public byte[] read(String key) throws IOException {
        if (!databaseBacked) return Files.readAllBytes(resolve(key));
        try {
            return jdbc.queryForObject("SELECT content FROM document_contents WHERE storage_key = ?", byte[].class, key);
        } catch (EmptyResultDataAccessException exception) {
            throw new IOException("Private document content was not found", exception);
        } catch (DataAccessException exception) {
            throw new IOException("Could not read private document content", exception);
        }
    }

    public void delete(String key) throws IOException {
        if (!databaseBacked) {
            Files.deleteIfExists(resolve(key));
            return;
        }
        try {
            jdbc.update("DELETE FROM document_contents WHERE storage_key = ?", key);
        } catch (DataAccessException exception) {
            throw new IOException("Could not delete private document content", exception);
        }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-fA-F-]{36}\\.pdf")) {
            throw new IllegalArgumentException("Invalid private storage key");
        }
        Path resolved = root.resolve(key).normalize();
        if (!resolved.getParent().equals(root)) throw new IllegalArgumentException("Invalid private storage key");
        return resolved;
    }
}
