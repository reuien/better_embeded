package com.mos.plc.service;

import com.mos.plc.config.PlcProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".bmp");

    private final Path uploadRoot;

    public FileStorageService(PlcProperties properties) {
        this.uploadRoot = Path.of(properties.getUploadRoot()).toAbsolutePath().normalize();
    }

    public StoredImage store(MultipartFile image, String result, LocalDate date) throws IOException {
        return store(image, result, date, null);
    }

    public StoredImage store(MultipartFile image, String result, LocalDate date, String purpose) throws IOException {
        String fieldName = purpose == null || purpose.isBlank() ? "image" : purpose + "_image";
        validateImage(image, fieldName);
        String original = StringUtils.cleanPath(image.getOriginalFilename() == null ? "image.jpg" : image.getOriginalFilename());
        String extension = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(fieldName + " must use jpg, jpeg, png, or bmp extension");
        }
        String prefix = purpose == null || purpose.isBlank() ? "" : safeSegment(purpose) + "_";
        String filename = date + "_" + prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + extension;
        Path folder = uploadRoot.resolve(ResultNormalizer.folderForResult(result)).resolve(date.toString());
        if (purpose != null && !purpose.isBlank()) {
            folder = folder.resolve(safeSegment(purpose));
        }
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

    private static void validateImage(MultipartFile image, String fieldName) throws IOException {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String contentType = image.getContentType();
        if (contentType != null
                && !contentType.isBlank()
                && !contentType.equalsIgnoreCase("application/octet-stream")
                && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException(fieldName + " content type must be an image");
        }
        try (InputStream input = image.getInputStream()) {
            BufferedImage decoded = ImageIO.read(input);
            if (decoded == null || decoded.getWidth() <= 0 || decoded.getHeight() <= 0) {
                throw new IllegalArgumentException(fieldName + " must be a readable image file");
            }
        }
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return ".jpg";
        }
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String safeSegment(String value) {
        return value.trim().replaceAll("[^A-Za-z0-9_-]", "_");
    }

    public record StoredImage(String filename, Path path) {
    }
}
