package com.mos.plc.controller;

import com.mos.plc.entity.DetectionRecord;
import com.mos.plc.exception.NotFoundException;
import com.mos.plc.repository.DetectionRecordRepository;
import com.mos.plc.service.FileStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
public class ImageController {
    private final DetectionRecordRepository repository;
    private final FileStorageService storageService;

    public ImageController(DetectionRecordRepository repository, FileStorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }

    @GetMapping({"/api/images/{filename}", "/files/{filename}"})
    public ResponseEntity<Resource> image(@PathVariable String filename) throws Exception {
        Path path = repository.findFirstByResultFilenameOrderByIdDesc(filename)
                .map(DetectionRecord::getResultImagePath)
                .map(Path::of)
                .or(() -> repository.findFirstByOriginalFilenameOrderByIdDesc(filename)
                        .map(DetectionRecord::getOriginalImagePath)
                        .map(Path::of))
                .or(() -> repository.findFirstByFilenameOrderByIdDesc(filename)
                        .map(DetectionRecord::getImagePath)
                        .map(Path::of))
                .orElseGet(() -> storageService.resolveExisting(filename));
        return imageResponse(filename, path);
    }

    @GetMapping("/files/original/{filename}")
    public ResponseEntity<Resource> originalImage(@PathVariable String filename) throws Exception {
        Path path = repository.findFirstByOriginalFilenameOrderByIdDesc(filename)
                .map(DetectionRecord::getOriginalImagePath)
                .map(Path::of)
                .orElseGet(() -> storageService.resolveExisting(filename));
        return imageResponse(filename, path);
    }

    @GetMapping("/files/result/{filename}")
    public ResponseEntity<Resource> resultImage(@PathVariable String filename) throws Exception {
        Path path = repository.findFirstByResultFilenameOrderByIdDesc(filename)
                .map(DetectionRecord::getResultImagePath)
                .map(Path::of)
                .or(() -> repository.findFirstByFilenameOrderByIdDesc(filename)
                        .map(DetectionRecord::getImagePath)
                        .map(Path::of))
                .orElseGet(() -> storageService.resolveExisting(filename));
        return imageResponse(filename, path);
    }

    private ResponseEntity<Resource> imageResponse(String filename, Path path) throws Exception {
        if (path == null || !Files.exists(path)) {
            throw new NotFoundException("Image not found: " + filename);
        }
        String contentType = Files.probeContentType(path);
        MediaType mediaType = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok().contentType(mediaType).body(new FileSystemResource(path));
    }
}
