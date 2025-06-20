package top.kangyaocoding.ai;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 描述: Ollama 模型 Controller
 *
 * @author K·Herbert herbert501@qq.com
 * @since 2025-06-17 15:39
 */
@Slf4j
@RestController()
@CrossOrigin(origins = "${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/ollama/")
public class OllamaController {
    @Resource
    private OllamaChatModel ollamaChatModel;
    @Resource
    private PgVectorStore pgVectorStore;

    @RequestMapping(value = "generate", method = RequestMethod.GET)
    public ChatResponse generate(@RequestParam String model, @RequestParam String message) {
        return ollamaChatModel.call(new Prompt(
                message,
                OllamaOptions.builder()
                        .model(model)
                        .build()
        ));
    }

    @RequestMapping(value = "generate_stream", method = RequestMethod.GET)
    public Flux<ChatResponse> generateStream(@RequestParam String model, @RequestParam String message) {
        return ollamaChatModel.stream(new Prompt(
                message,
                OllamaOptions.builder()
                        .model(model)
                        .build()
        ));
    }

    @RequestMapping(value = "generate_stream_rag", method = RequestMethod.GET)
    public Flux<ChatResponse> generateStreamRag(@RequestParam String model, @RequestParam String ragTag, @RequestParam String message) {
        String SYSTEM_PROMPT = """
                基于以下给出的 DOCUMENTS 信息，遵守规范约束，专业、简要回答用户的问题。
                规范约束:
                1. 如果已知信息包含图片、链接、表格、代码块等特殊markdown标签格式的信息，确保在答案中包含原文这些标签，不要丢弃或修改。
                   如: 图片格式：![image.png](xxx), 链接格式:[xxx](xxx), 表格格式:|xxx|xxx|xxx|, 代码格式:```xxx```
                2. 如果无法从提供的内容中获取答案，请回答: "知识库中提供的内容不足以回答此问题"，禁止编造答案。
                3. 回答时最好按照1.2.3.点进行总结，并以markdown格式显示。
                4. 请使用与用户相同的语言进行回答。
                DOCUMENTS:
                {documents}""";
        try {
            // 参数校验
            if (StringUtils.isBlank(model) || StringUtils.isBlank(ragTag) || StringUtils.isBlank(message)) {
                return Flux.error(new IllegalArgumentException("模型名称、知识库标签和消息内容不能为空"));
            }

            log.info("开始处理RAG请求 - 模型: {}, 标签: {}, 消息: {}", model, ragTag, message);

            // 1. 构建搜索请求并获取文档
            SearchRequest request = SearchRequest.builder()
                    .query(message)
                    .topK(5)
                    .filterExpression("knowledge == '" + ragTag + "'")
                    .build();

            List<Document> documents;
            try {
                documents = pgVectorStore.similaritySearch(request);
            } catch (Exception e) {
                log.error("知识库搜索失败", e);
                return Flux.error(new RuntimeException("知识库搜索失败，请稍后重试"));
            }

            // 2. 检查知识库是否为空
            if (documents == null || documents.isEmpty()) {
                Document emptyDocument = new Document("知识库中未找到相关内容，请直接返回无法回答问题的内容");
                documents = List.of(emptyDocument);
            }

            // 3. 合并文档内容
            String documentsContent;
            try {
                documentsContent = documents.stream()
                        .map(Document::getText)
                        .collect(Collectors.joining());
            } catch (Exception e) {
                log.error("文档内容合并失败", e);
                return Flux.error(new RuntimeException("文档处理失败，请稍后重试"));
            }

            // 4. 构建消息列表
            List<Message> messages;
            try {
                messages = Arrays.asList(
                        new UserMessage(message),
                        new SystemPromptTemplate(SYSTEM_PROMPT)
                                .createMessage(Map.of("documents", documentsContent))
                );
            } catch (Exception e) {
                log.error("消息构建失败", e);
                return Flux.error(new RuntimeException("消息构建失败，请稍后重试"));
            }
            log.info("消息列表: {}", JSON.toJSONString(messages));
            // 5. 调用模型并返回响应
            try {
                return ollamaChatModel.stream(new Prompt(messages,
                                OllamaOptions.builder()
                                        .model(model)
                                        .build())
                        )
                        .onErrorResume(e -> {
                            log.error("模型响应生成失败", e);
                            return Flux.error(new RuntimeException("生成回答时出错，请稍后重试"));
                        });
            } catch (Exception e) {
                log.error("模型调用失败", e);
                return Flux.error(new RuntimeException("模型服务不可用，请稍后重试"));
            }

        } catch (Exception e) {
            log.error("处理RAG请求时发生未预期错误", e);
            return Flux.error(new RuntimeException("系统繁忙，请稍后重试"));
        }
    }
}
