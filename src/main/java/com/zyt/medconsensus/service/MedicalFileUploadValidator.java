package com.zyt.medconsensus.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Validates upload content and parser resource bounds before handing files to workers. */
@Component
public class MedicalFileUploadValidator {

    public static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
    private static final int MAX_PDF_PAGES = 200;
    private static final long MAX_IMAGE_PIXELS = 40_000_000L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "jpg", "jpeg", "png");

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            reject("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            reject("上传文件过大，请上传 50MB 以内的 PDF、DOCX、JPG 或 PNG 文件");
        }
        String filename = file.getOriginalFilename();
        String extension = extension(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            reject("不支持的文件格式，请上传 PDF、DOCX 或 JPG/PNG 图片");
        }
        try {
            byte[] bytes = readBounded(file.getInputStream());
            if ("pdf".equals(extension)) {
                requireHeader(bytes, "%PDF-");
                try (PDDocument document = Loader.loadPDF(bytes)) {
                    if (document.getNumberOfPages() > MAX_PDF_PAGES) {
                        reject("PDF 页数过多，请上传不超过 200 页的文件");
                    }
                }
            } else if ("docx".equals(extension)) {
                requireZipDocument(bytes);
            } else {
                validateImage(bytes);
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件内容无法解析，请检查文件格式");
        }
    }

    private byte[] readBounded(InputStream input) throws IOException {
        byte[] bytes = input.readAllBytes();
        if (bytes.length > MAX_FILE_BYTES) {
            reject("上传文件过大，请上传 50MB 以内的文件");
        }
        return bytes;
    }

    private void requireZipDocument(byte[] bytes) throws IOException {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            reject("文件内容与 DOCX 扩展名不匹配");
        }
        boolean documentXml = false;
        long uncompressed = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("..")) {
                    reject("DOCX 包含不安全的文件路径");
                }
                if ("word/document.xml".equals(name)) {
                    documentXml = true;
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    uncompressed += read;
                    if (uncompressed > 200L * 1024 * 1024) {
                        reject("DOCX 解压内容过大");
                    }
                }
            }
        }
        if (!documentXml) {
            reject("文件内容与 DOCX 扩展名不匹配");
        }
    }

    private void validateImage(byte[] bytes) throws IOException {
        if (bytes.length < 4 || !(isJpeg(bytes) || isPng(bytes))) {
            reject("文件内容与图片扩展名不匹配");
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null || (long) image.getWidth() * image.getHeight() > MAX_IMAGE_PIXELS) {
            reject("图片尺寸过大，请上传不超过 4000 万像素的图片");
        }
    }

    private boolean isJpeg(byte[] b) { return (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff; }
    private boolean isPng(byte[] b) { return (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'; }
    private void requireHeader(byte[] bytes, String header) {
        if (bytes.length < header.length() || !new String(bytes, 0, header.length(), java.nio.charset.StandardCharsets.US_ASCII).equals(header)) {
            reject("文件内容与 PDF 扩展名不匹配");
        }
    }
    private String extension(String filename) {
        if (filename == null) return "";
        String clean = filename.replace('\\', '/');
        int dot = clean.lastIndexOf('.');
        return dot >= 0 ? clean.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
    private void reject(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
