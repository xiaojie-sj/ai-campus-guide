package com.hbeu.campusguide.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbeu.campusguide.algorithm.LocalEmbedding;
import com.hbeu.campusguide.domain.KnowledgeChunk;
import com.hbeu.campusguide.domain.KnowledgeDocument;
import com.hbeu.campusguide.mapper.KnowledgeChunkMapper;
import com.hbeu.campusguide.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Atomically replaces a document's chunks only after parsing and embedding have
 * completed. A failed rebuild therefore leaves the previous index intact.
 */
@Service
public class KnowledgeIndexWriter {
    private final KnowledgeChunkMapper chunkMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final LocalEmbedding embedding;
    private final ObjectMapper objectMapper;

    public KnowledgeIndexWriter(
            KnowledgeChunkMapper chunkMapper,
            KnowledgeDocumentMapper documentMapper,
            LocalEmbedding embedding,
            ObjectMapper objectMapper
    ) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
        this.embedding = embedding;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void replace(KnowledgeDocument document, List<String> chunks, String indexVersion) throws Exception {
        chunkMapper.delete(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getDocumentId, document.getId()));
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(i);
            chunk.setTitle(document.getTitle());
            chunk.setHeading(guessHeading(chunks.get(i)));
            chunk.setContent(chunks.get(i));
            chunk.setEmbedding(objectMapper.writeValueAsString(embedding.embed(chunks.get(i))));
            chunk.setEmbeddingModel(LocalEmbedding.MODEL);
            chunk.setMetadataJson("{\"sourceTitle\":\""
                    + safe(document.getSourceTitle()) + "\",\"effectiveYear\":\""
                    + safe(document.getEffectiveYear()) + "\"}");
            chunk.setStatus("ACTIVE");
            chunkMapper.insert(chunk);
        }
        document.setChunkCount(chunks.size());
        document.setIndexVersion(indexVersion);
        document.setParseStatus("READY");
        document.setFailureReason(null);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    private String guessHeading(String text) {
        String firstLine = text.lines().findFirst().orElse("");
        return firstLine.length() <= 60 ? firstLine : firstLine.substring(0, 60);
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
