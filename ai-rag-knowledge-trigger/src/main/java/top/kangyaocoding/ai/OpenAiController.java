package top.kangyaocoding.ai;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 描述: OpenAi 模型 Controller
 *
 * @author K·Herbert herbert501@qq.com
 * @since 2025-07-07 11:45
 */

@Slf4j
@RestController
@CrossOrigin(origins = "${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/openai/")
public class OpenAiController implements IAiService {

    @Resource
    private OpenAiChatModel openAiChatModel;

    @Resource
    PgVectorStore pgVectorStore;

    @Override
    @RequestMapping(value = "generate", method = RequestMethod.GET)
    public ChatResponse generate(@RequestParam("model") String model, @RequestParam("message") String message) {
        log.info("调用 OpenAi 普通对话接口");
        log.info("接收到参数model: {}, message: {}", model, message);
        return openAiChatModel.call(new Prompt(message,
                OpenAiChatOptions.builder()
                        .model(model)
                        .build()));
    }

    @Override
    @RequestMapping(value = "generate_stream", method = RequestMethod.GET)
    public Flux<ChatResponse> generateStream(@RequestParam("model") String model, @RequestParam("message") String message) {
        log.info("调用 OpenAi 流式对话接口");
        log.info("接收到参数model: {}, message: {}", model, message);
        return openAiChatModel.stream(new Prompt(message,
                OpenAiChatOptions.builder()
                        .model(model)
                        .build()));
    }

    @Override
    @RequestMapping(value = "generate_stream_rag", method = RequestMethod.POST)
    public Flux<ChatResponse> generateStreamRag(@RequestParam("model") String model, @RequestParam("ragTag") String ragTag, @RequestParam("message") String message) {
        log.info("调用 OpenAi RAG 的流式对话接口");
        log.info("接收到参数model: {}, ragTag: {}, message: {}", model, ragTag, message);

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
        // 参数校验
        if (StringUtils.isBlank(model) || StringUtils.isBlank(ragTag) || StringUtils.isBlank(message)) {
            return Flux.error(new IllegalArgumentException("模型名称、知识库标签和消息内容不能为空"));
        }

        SearchRequest searchRequest = SearchRequest.builder()
                .query(message)
                .topK(5)
                .filterExpression("knowledge == '" + ragTag + "'")
                .build();

        List<Document> documents = pgVectorStore.similaritySearch(searchRequest);

        if (documents == null || documents.isEmpty()) {
            log.warn("未找到 ragTag： {} 的文档", ragTag);
            Document document = new Document("知识库中未找到相关内容，请直接返回无法回答问题的内容");
            documents = List.of(document);
        }

        String documentsContent = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT)
                .createMessage(Map.of("documents", documentsContent));

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        log.info("向 OpenAI 发送消息，消息大小为: {}", messages.size());
        return openAiChatModel.stream(new Prompt(messages,
                        OpenAiChatOptions.builder()
                                .model(model)
                                .build()))
                .onErrorResume(e -> {
                    log.error("模型响应生成失败", e);
                    return Flux.error(new RuntimeException("生成回答时出错，请稍后重试"));
                });
    }
}
