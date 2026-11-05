package com.hbeu.campusguide.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbeu.campusguide.ai.AiGateway;
import com.hbeu.campusguide.algorithm.LocalEmbedding;
import com.hbeu.campusguide.config.AppProperties;
import com.hbeu.campusguide.domain.FaqItem;
import com.hbeu.campusguide.domain.KnowledgeChunk;
import com.hbeu.campusguide.domain.KnowledgeDocument;
import com.hbeu.campusguide.dto.SourceRef;
import com.hbeu.campusguide.mapper.FaqItemMapper;
import com.hbeu.campusguide.mapper.KnowledgeChunkMapper;
import com.hbeu.campusguide.mapper.KnowledgeDocumentMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * FAQ and private RAG pipeline. Retrieval is always evaluated before an LLM is
 * allowed to compose an answer.
 */
@Service
public class RagService {
    public static final String FALLBACK_ANSWER =
            "暂时无法回答这个问题。该问题未在已审核的校园知识库中找到可核验信息，"
                    + "请访问湖北工程学院新技术学院官网（https://www.hbeutc.cn/）或联系学校相关部门确认。";

    private final FaqItemMapper faqMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final LocalEmbedding embedding;
    private final AppProperties properties;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    public RagService(
            FaqItemMapper faqMapper,
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper,
            LocalEmbedding embedding,
            AppProperties properties,
            AiGateway aiGateway,
            ObjectMapper objectMapper
    ) {
        this.faqMapper = faqMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.embedding = embedding;
        this.properties = properties;
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
    }

    public RagAnswer answer(String question) {
        Optional<FaqItem> faq = bestFaq(question);
        if (faq.isPresent()) {
            FaqItem item = faq.get();
            return new RagAnswer(
                    item.getAnswer(),
                    "FAQ",
                    List.of(new SourceRef(
                            item.getSourceTitle() == null ? "已审核常见问题" : item.getSourceTitle(),
                            item.getSourceUrl(),
                            item.getAnswer(),
                            null
                    ))
            );
        }

        List<RetrievedChunk> chunks = retrieve(question, properties.rag().topK());
        if (chunks.isEmpty() || chunks.get(0).score() < properties.rag().minScore()) {
            return new RagAnswer(FALLBACK_ANSWER, "NO_EVIDENCE", List.of());
        }

        String answer = aiGateway.composeRagAnswer(question, chunks)
                .orElseGet(() -> extractiveAnswer(chunks));
        List<SourceRef> sources = chunks.stream()
                .limit(3)
                .map(chunk -> new SourceRef(
                        chunk.sourceTitle(),
                        chunk.sourceUrl(),
                        abbreviate(chunk.content(), 160),
                        chunk.effectiveYear()
                ))
                .distinct()
                .toList();
        return new RagAnswer(answer, aiGateway.configured() ? "RAG_LLM" : "RAG_EXTRACTIVE", sources);
    }

    public List<RetrievedChunk> retrieve(String question, int topK) {
        Set<Long> effectiveDocumentIds = effectiveDocuments();
        if (effectiveDocumentIds.isEmpty()) {
            return List.of();
        }
        double[] query = embedding.embed(question);
        List<KnowledgeChunk> chunks = chunkMapper.selectList(Wrappers.<KnowledgeChunk>lambdaQuery()
                .eq(KnowledgeChunk::getStatus, "ACTIVE")
                .in(KnowledgeChunk::getDocumentId, effectiveDocumentIds));
        Map<Long, KnowledgeDocument> documents = new HashMap<>();
        documentMapper.selectBatchIds(effectiveDocumentIds)
                .forEach(document -> documents.put(document.getId(), document));

        List<RetrievedChunk> result = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            KnowledgeDocument document = documents.get(chunk.getDocumentId());
            if (document == null) {
                continue;
            }
            double score = embedding.cosine(query, parseEmbedding(chunk.getEmbedding()));
            result.add(new RetrievedChunk(
                    chunk.getId(),
                    chunk.getDocumentId(),
                    document.getTitle(),
                    document.getSourceTitle(),
                    document.getSourceUrl(),
                    document.getEffectiveYear(),
                    chunk.getHeading(),
                    chunk.getPageRef(),
                    chunk.getContent(),
                    score
            ));
        }
        return result.stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(Math.max(1, topK))
                .toList();
    }

    private Optional<FaqItem> bestFaq(String question) {
        LocalDate today = LocalDate.now();
        List<FaqItem> items = faqMapper.selectList(Wrappers.<FaqItem>lambdaQuery()
                .eq(FaqItem::getStatus, "PUBLISHED")
                .orderByDesc(FaqItem::getPriority));
        FaqItem best = null;
        double bestScore = 0.0;
        for (FaqItem item : items) {
            if (item.getEffectiveFrom() != null && item.getEffectiveFrom().isAfter(today)) {
                continue;
            }
            if (item.getEffectiveTo() != null && item.getEffectiveTo().isBefore(today)) {
                continue;
            }
            double score = similarity(question, item.getQuestion());
            if (item.getAliases() != null) {
                for (String alias : item.getAliases().split("[,，、]")) {
                    score = Math.max(score, similarity(question, alias));
                }
            }
            score += Math.min(0.08, (item.getPriority() == null ? 0 : item.getPriority()) * 0.005);
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }
        return bestScore >= 0.66 ? Optional.ofNullable(best) : Optional.empty();
    }

    private double similarity(String left, String right) {
        Set<String> a = bigrams(left);
        Set<String> b = bigrams(right);
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private Set<String> bigrams(String value) {
        String normalized = value == null ? "" : value.replaceAll("[\\s\\p{Punct}，。！？；：、]", "");
        Set<String> grams = new HashSet<>();
        for (int i = 0; i < normalized.length(); i++) {
            grams.add(String.valueOf(normalized.charAt(i)));
            if (i + 1 < normalized.length()) {
                grams.add(normalized.substring(i, i + 2));
            }
        }
        return grams;
    }

    private Set<Long> effectiveDocuments() {
        LocalDate today = LocalDate.now();
        return documentMapper.selectList(Wrappers.<KnowledgeDocument>lambdaQuery()
                        .eq(KnowledgeDocument::getStatus, "PUBLISHED")
                        .eq(KnowledgeDocument::getParseStatus, "READY"))
                .stream()
                .filter(document -> document.getEffectiveFrom() == null
                        || !document.getEffectiveFrom().isAfter(today))
                .filter(document -> document.getEffectiveTo() == null
                        || !document.getEffectiveTo().isBefore(today))
                .map(KnowledgeDocument::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    private double[] parseEmbedding(String json) {
        try {
            List<Double> values = objectMapper.readValue(json, new TypeReference<>() {
            });
            return values.stream().mapToDouble(Double::doubleValue).toArray();
        } catch (Exception exception) {
            return new double[LocalEmbedding.DIMENSIONS];
        }
    }

    private String extractiveAnswer(List<RetrievedChunk> chunks) {
        StringBuilder answer = new StringBuilder("根据已审核的校园资料：");
        int remaining = 520;
        for (int i = 0; i < chunks.size() && remaining > 0; i++) {
            RetrievedChunk chunk = chunks.get(i);
            String piece = abbreviate(chunk.content().replaceAll("\\s+", " ").trim(), remaining);
            answer.append("\n").append(i + 1).append(". ").append(piece).append(" [S").append(i + 1).append("]");
            remaining -= piece.length();
            if (chunk.score() < properties.rag().minScore() + 0.08) {
                break;
            }
        }
        return answer.toString();
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String normalized = text.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, Math.max(1, max - 1)) + "…";
    }

    public record RetrievedChunk(
            Long chunkId,
            Long documentId,
            String title,
            String sourceTitle,
            String sourceUrl,
            String effectiveYear,
            String heading,
            String pageRef,
            String content,
            double score
    ) {
    }

    public record RagAnswer(String answer, String status, List<SourceRef> sources) {
    }
}
