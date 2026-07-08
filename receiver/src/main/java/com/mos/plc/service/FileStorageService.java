package com.mos.plc.service;

import com.mos.plc.config.PlcProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path uploadRoot;

    public FileStorageService(PlcProperties properties) {
        this.uploadRoot = Path.of(properties.getUploadRoot()).toAbsolutePath().normalize();
    }

    public StoredImage store(MultipartFile image, String result, LocalDate date) throws IOException {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("image is required");
        }
        String original = StringUtils.cleanPath(image.getOriginalFilename() == null ? "image.jpg" : image.getOriginalFilename());
        String extension = extensionOf(original);
        String filename = date + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + extension;
        Path folder = uploadRoot.resolve(ResultNormalizer.folderForResult(result)).resolve(date.toString());
        Files.createDirectories(folder);
        Path target = folder.resolve(filename).normalize();
        image.transferTo(target);
        return new StoredImage(filename, target);
    }

    public Path resolveExisting(String filename) {
        try {
            if (!Files.exists(uploadRoot)) {
                return null;
            }
            try (var stream = Files.walk(uploadRoot)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equals(filename))
                        .findFirst()
                        .orElse(null);
            }
        } catch (IOException ignored) {
            return null;
        }
    }

    public void deleteIfExists(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(imagePath));
        } catch (IOException ignored) {
        }
    }

    public Path getUploadRoot() {
        return uploadRoot;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return ".jpg";
        }
        return filename.substring(dot).toLowerCase();
    }

    public record StoredImage(String filename, Path path) {
    }
}
