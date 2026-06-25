package com.plp.program.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * Local filesystem storage for digital invoice PDFs/images (dev / when MinIO is off).
 */
@Component
public class LocalDigitalInvoiceStorage {

    private final Path root;

    public LocalDigitalInvoiceStorage(
            @Value("${plp.storage.local.root:./uploads/digital-invoices}") String rootPath) {
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create digital invoice storage root: " + this.root, e);
        }
    }

    public void put(String key, byte[] data) throws Exception {
        Path p = resolveSafe(key);
        Files.createDirectories(p.getParent());
        Files.write(p, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public Optional<byte[]> get(String key) {
        try {
            Path p = resolveSafe(key);
            if (!Files.isRegularFile(p)) {
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(p));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Path resolveSafe(String key) {
        if (key == null || key.isBlank() || key.contains("..")) {
            throw new IllegalArgumentException("Invalid object key");
        }
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new SecurityException("Key escapes storage root");
        }
        return p;
    }
}
