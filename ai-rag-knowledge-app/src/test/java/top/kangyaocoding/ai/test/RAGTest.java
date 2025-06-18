package top.kangyaocoding.ai.test;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.ai.reader.tika.TikaDocumentReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 描述: RAG测试
 *
 * @author K·Herbert herbert501@qq.com
 * @since 2025-06-18 09:58
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RAGTest {
    @Resource
    private OllamaChatModel ollamaChatModel;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private PgVectorStore pgVectorStore;

    @Test
    public void upload() {
        TikaDocumentReader tikaDocumentReader = new TikaDocumentReader("static/test.txt");
        List<Document> documents = tikaDocumentReader.get();
        List<Document> documentsSplitterList = tokenTextSplitter.apply(documents);

        documents.forEach(document -> {
            document.getMetadata().put("knowledge", "知识库名称");
        });
        documentsSplitterList.forEach(document -> {
            document.getMetadata().put("knowledge", "知识库名称");
        });

        pgVectorStore.accept(documentsSplitterList);

        log.info("上传成功");
    }

    @Test
    public void chat() {
        String message = "王大瓜，哪年出生";

        String SYSTEM_PROMPT = """
                使用 DOCUMENTS 部分的信息提供准确的答案，但要表现得好像您天生就知道这些信息一样。
                                如果不确定，只需说明您不知道。
                                您需要注意的另一件事是您的回复必须是中文！
                                DOCUMENTS:
                                    {documents}
                """;
        SearchRequest request = SearchRequest.builder()
                .query(message)
                .topK(5)
                .filterExpression("knowledge == '知识库名称'")
                .build();
        List<Document> documents = pgVectorStore.similaritySearch(request);
        if (documents == null) {
            log.info("没有找到匹配的文档");
            return;
        }
        String documentsCollectors = documents.stream()
                .map(Document::getText)
                .collect(java.util.stream.Collectors.joining());

        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT)
                .createMessage(Map.of("documents", documentsCollectors));

        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        ChatResponse chatResponse = ollamaChatModel.call(
                new Prompt(messages,
                        OllamaOptions.builder()
                                .model("qwen3:1.7b")
                                .build()));
        log.info("测试结果：{}", JSON.toJSONString(chatResponse));
    }
}
