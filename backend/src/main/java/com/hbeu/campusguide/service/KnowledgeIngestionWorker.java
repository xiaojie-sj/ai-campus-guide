package com.hbeu.campusguide.service;

import com.hbeu.campusguide.algorithm.TextChunker;
import com.hbeu.campusguide.config.AppProperties;
import com.hbeu.campusguide.domain.KnowledgeDocument;
import com.hbeu.campusguide.mapper.KnowledgeDocumentMapper;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Runs file parsing and indexing outside the HTTP request thread.
 */
@Service
public class KnowledgeIngestionWorker {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionWorker.class);
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeIndexWriter indexWriter;
    private final TextChunker chunker;
    private final AppProperties properties;

    public KnowledgeIngestionWorker(
            KnowledgeDocumentMapper documentMapper,
            KnowledgeIndexWriter indexWriter,
            TextChunker chunker,
            AppProperties properties
    ) {
        this.documentMapper = documentMapper;
        this.indexWriter = indexWriter;
        this.chunker = chunker;
        this.properties = properties;
    }

    @Async("ingestionExecutor")
    public void processAsync(long documentId) {
        process(documentId);
    }

    public void process(long documentId) {
        KnowledgeDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            return;
        }
        document.setParseStatus("PROCESSING");
        document.setFailureReason(null);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        try (InputStream input = Files.newInputStream(Path.of(document.getStoragePath()))) {
            String text = new Tika().parseToString(input);
            List<String> chunks = chunker.chunk(
                    text,
                    properties.rag().chunkSize(),
                    properties.rag().overlap()
            );
            if (chunks.isEmpty()) {
                throw new IllegalStateException("文档没有可提取文本，扫描件请先进行 OCR");
            }
            String version = "idx-" + UUID.randomUUID().toString().substring(0, 12);
            indexWriter.replace(document, chunks, version);
            log.info("Knowledge document {} indexed with {} chunks", documentId, chunks.size());
        } catch (Exception exception) {
            log.warn("Knowledge document {} ingestion failed", documentId, exception);
            document.setParseStatus("FAILED");
            document.setFailureReason(trim(exception.getMessage(), 500));
            document.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(document);
        }
    }

    private String trim(String value, int max) {
        if (value == null) {
            return "未知解析错误";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
