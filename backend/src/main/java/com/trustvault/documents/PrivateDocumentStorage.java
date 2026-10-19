package com.trustvault.documents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PrivateDocumentStorage {
    private final Path root;

    public PrivateDocumentStorage(@Value("${trustvault.storage.directory:./var/private-documents}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(byte[] bytes) throws IOException {
        Files.createDirectories(root);
        String key = UUID.randomUUID() + ".pdf";
        Path destination = resolve(key);
        Files.write(destination, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return key;
    }

    public byte[] read(String key) throws IOException {
        return Files.readAllBytes(resolve(key));
    }

    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
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
