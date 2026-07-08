package com.mos.plc.controller;

import com.mos.plc.config.PlcProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.file.Files;
import java.nio.file.Path;

@Controller
public class FrontendController {
    private final Path frontendDir;

    public FrontendController(PlcProperties properties) {
        this.frontendDir = Path.of(properties.getFrontendDir()).toAbsolutePath().normalize();
    }

    @GetMapping("/")
    public ResponseEntity<Resource> index() {
        Path index = frontendDir.resolve("index.html");
        if (!Files.exists(index)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(new FileSystemResource(createMissingFrontendPage()));
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(new FileSystemResource(index));
    }

    @GetMapping("/assets/{filename}")
    public ResponseEntity<Resource> asset(@PathVariable String filename) throws Exception {
        Path asset = frontendDir.resolve("assets").resolve(filename).normalize();
        if (!asset.startsWith(frontendDir) || !Files.exists(asset)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(asset);
        MediaType mediaType = contentType == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok().contentType(mediaType).body(new FileSystemResource(asset));
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build();
    }

    private Path createMissingFrontendPage() {
        try {
            Path temp = Files.createTempFile("missing-front", ".html");
            Files.writeString(temp, """
                    <!doctype html>
                    <html lang="zh-CN">
                    <head><meta charset="utf-8"><title>PLC Receiver</title></head>
                    <body style="font-family: sans-serif; padding: 32px;">
                    <h1>前端还没有构建</h1>
                    <p>请先进入 front 目录运行 npm install 和 npm run build。</p>
                    </body>
                    </html>
                    """);
            return temp;
        } catch (Exception exc) {
            throw new IllegalStateException("Frontend is not built", exc);
        }
    }
}
