package com.hbeu.campusguide.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hbeu.campusguide.algorithm.TextChunker;
import com.hbeu.campusguide.common.BusinessException;
import com.hbeu.campusguide.common.ErrorCode;
import com.hbeu.campusguide.config.AdminContext;
import com.hbeu.campusguide.config.AppProperties;
import com.hbeu.campusguide.domain.KnowledgeDocument;
import com.hbeu.campusguide.dto.KnowledgeDocumentView;
import com.hbeu.campusguide.mapper.KnowledgeDocumentMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Admin-facing document lifecycle and demo knowledge bootstrap.
 */
@Service
public class KnowledgeIngestionService {
    private static final Set<String> ALLOWED = Set.of("pdf", "doc", "docx", "txt");
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeIngestionWorker worker;
    private final KnowledgeIndexWriter indexWriter;
    private final TextChunker chunker;
    private final AppProperties properties;
    private final AuditService auditService;

    public KnowledgeIngestionService(
            KnowledgeDocumentMapper documentMapper,
            KnowledgeIngestionWorker worker,
            KnowledgeIndexWriter indexWriter,
            TextChunker chunker,
            AppProperties properties,
            AuditService auditService
    ) {
        this.documentMapper = documentMapper;
        this.worker = worker;
        this.indexWriter = indexWriter;
        this.chunker = chunker;
        this.properties = properties;
        this.auditService = auditService;
    }

    public List<KnowledgeDocumentView> list() {
        return documentMapper.selectList(Wrappers.<KnowledgeDocument>lambdaQuery()
                        .orderByDesc(KnowledgeDocument::getUpdatedAt))
                .stream().map(this::view).toList();
    }

    public KnowledgeDocumentView upload(
            MultipartFile file,
            String title,
            String sourceTitle,
            String sourceUrl,
            String effectiveYear,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String category
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要上传的文档");
        }
        if (file.getSize() > properties.storage().maxFileBytes()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文档超过允许的大小上限");
        }
        String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String extension = extension(original);
        if (!ALLOWED.contains(extension)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 PDF、DOC、DOCX 和 TXT 文档");
        }
        try {
            Path directory = Path.of(properties.storage().uploadDir()).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            String storedName = UUID.randomUUID() + "." + extension;
            Path target = directory.resolve(storedName).normalize();
            if (!target.startsWith(directory)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名不安全");
            }
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }

            KnowledgeDocument document = new KnowledgeDocument();
            document.setTitle(title == null || title.isBlank() ? original : title.trim());
            document.setOriginalFilename(original);
            document.setStoragePath(target.toString());
            document.setSourceTitle(sourceTitle);
            document.setSourceUrl(sourceUrl);
            document.setEffectiveYear(effectiveYear);
            document.setEffectiveFrom(effectiveFrom);
            document.setEffectiveTo(effectiveTo);
            document.setCategory(category == null || category.isBlank() ? "校园资料" : category);
            document.setStatus("DRAFT");
            document.setParseStatus("QUEUED");
            document.setChunkCount(0);
            document.setReviewer(AdminContext.current().displayName());
            document.setChecksum(sha256(file.getBytes()));
            document.setCreatedAt(LocalDateTime.now());
            document.setUpdatedAt(LocalDateTime.now());
            documentMapper.insert(document);
            worker.processAsync(document.getId());
            auditService.record(AdminContext.current().userId(), "KNOWLEDGE_UPLOAD", "KNOWLEDGE_DOCUMENT",
                    String.valueOf(document.getId()), original);
            return view(document);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文档保存失败");
        }
    }

    public KnowledgeDocumentView publish(long id, boolean publish) {
        KnowledgeDocument document = require(id);
        if (publish && (!"READY".equals(document.getParseStatus()) || document.getChunkCount() == 0)) {
            throw new BusinessException(ErrorCode.CONFLICT, "文档尚未成功索引，不能发布");
        }
        document.setStatus(publish ? "PUBLISHED" : "DRAFT");
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        auditService.record(AdminContext.current().userId(), publish ? "KNOWLEDGE_PUBLISH" : "KNOWLEDGE_UNPUBLISH",
                "KNOWLEDGE_DOCUMENT", String.valueOf(id), document.getTitle());
        return view(document);
    }

    public KnowledgeDocumentView reindex(long id) {
        KnowledgeDocument document = require(id);
        document.setParseStatus("QUEUED");
        document.setFailureReason(null);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        worker.processAsync(id);
        auditService.record(AdminContext.current().userId(), "KNOWLEDGE_REINDEX",
                "KNOWLEDGE_DOCUMENT", String.valueOf(id), document.getTitle());
        return view(document);
    }

    public void delete(long id) {
        KnowledgeDocument document = require(id);
        document.setStatus("DELETED");
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        auditService.record(AdminContext.current().userId(), "KNOWLEDGE_ARCHIVE",
                "KNOWLEDGE_DOCUMENT", String.valueOf(id), document.getTitle());
    }

    /**
     * Seeds the bundled demo facts once. The text file contains source labels
     * and an explicit warning not to treat historical reporting data as current.
     */
    public void seedDemoKnowledge() throws Exception {
        long count = documentMapper.selectCount(Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(KnowledgeDocument::getChecksum, "demo-public-facts-v1"));
        if (count > 0) {
            return;
        }
        ClassPathResource resource = new ClassPathResource("demo-knowledge/hbeutc-public-facts-v1.txt");
        String text;
        try (InputStream input = resource.getInputStream()) {
            text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        KnowledgeDocument document = new KnowledgeDocument();
        document.setTitle("湖北工程学院新技术学院公开信息核验摘要");
        document.setOriginalFilename("hbeutc-public-facts-v1.txt");
        document.setStoragePath("classpath:demo-knowledge/hbeutc-public-facts-v1.txt");
        document.setSourceTitle("学校官网及招生信息网公开页面");
        document.setSourceUrl("https://www.hbeutc.cn/");
        document.setEffectiveYear("2026");
        document.setCategory("学校公开信息");
        document.setStatus("PUBLISHED");
        document.setParseStatus("READY");
        document.setReviewer("系统初始化");
        document.setChecksum("demo-public-facts-v1");
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(document);
        List<String> chunks = chunker.chunk(text, properties.rag().chunkSize(), properties.rag().overlap());
        indexWriter.replace(document, chunks, "idx-demo-v1");
        documentMapper.updateById(document);
    }

    private KnowledgeDocument require(long id) {
        KnowledgeDocument document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识文档不存在");
        }
        return document;
    }

    private KnowledgeDocumentView view(KnowledgeDocument document) {
        return new KnowledgeDocumentView(
                document.getId(), document.getTitle(), document.getOriginalFilename(),
                document.getSourceTitle(), document.getSourceUrl(), document.getEffectiveYear(),
                document.getEffectiveFrom(), document.getEffectiveTo(), document.getCategory(),
                document.getStatus(), document.getParseStatus(),
                document.getChunkCount() == null ? 0 : document.getChunkCount(),
                document.getIndexVersion(), document.getReviewer(), document.getFailureReason(),
                document.getUpdatedAt()
        );
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
