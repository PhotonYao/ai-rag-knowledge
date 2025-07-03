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

    /**
     * 测试文件上传功能
     * <p>
     * 1. 读取测试文件内容
     * 2. 对文档内容进行分割处理
     * 3. 为文档添加元数据
     * 4. 将处理后的文档存储到向量数据库
     */
    @Test
    public void upload() {
        // 使用Tika文档阅读器读取测试文件
        TikaDocumentReader tikaDocumentReader = new TikaDocumentReader("static/test.txt");
        List<Document> documents = tikaDocumentReader.get();

        // 对文档内容进行分割处理
        List<Document> documentsSplitterList = tokenTextSplitter.apply(documents);

        // 为每个分割后的文档添加知识库名称元数据
        documentsSplitterList.forEach(document -> {
            document.getMetadata().put("knowledge", "王大瓜知识库");
        });

        // 将处理后的文档存入向量数据库
        pgVectorStore.accept(documentsSplitterList);

        log.info("上传成功");
    }


    /**
     * 测试聊天功能，使用RAG(检索增强生成)模型回答问题。
     * 1. 根据用户问题检索相关文档
     * 2. 将检索结果和系统提示组合成消息
     * 3. 调用Ollama聊天模型生成回答
     */
    @Test
    public void chat() {
        // 测试用问题
        String message = "王大瓜的个人信息有哪些？";

        // 系统提示模板，要求模型基于检索文档回答但表现得像已知信息
        String SYSTEM_PROMPT = """
                请严格依据提供的 DOCUMENTS 内容进行回答，确保答案与文档信息完全一致。
                                如果在文档中未找到相关信息，请直接回复“未找到相关信息”。
                                回答必须使用中文，并保持自然流畅的口语化表达。
                                文档内容如下：
                                    {documents}
                """;

        // 构建搜索请求，查询知识库中相关文档
        SearchRequest request = SearchRequest.builder()
                .query(message)
                .topK(5)
                .filterExpression("knowledge == '王大瓜知识库'")
                .build();

        // 执行相似度搜索获取相关文档
        List<Document> documents = pgVectorStore.similaritySearch(request);
        if (documents == null) {
            log.info("没有找到匹配的文档");
            return;
        }

        // 将检索到的文档内容合并为一个字符串
        String documentsCollectors = documents.stream()
                .map(Document::getText)
                .collect(java.util.stream.Collectors.joining());

        // 创建系统提示消息，插入检索到的文档内容
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT)
                .createMessage(Map.of("documents", documentsCollectors));

        // 构建消息列表：用户问题+系统提示
        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        // 调用Ollama聊天模型生成回答
        ChatResponse chatResponse = ollamaChatModel.call(
                new Prompt(messages,
                        OllamaOptions.builder()
                                .model("qwen3:1.7b")
                                .build()));
        log.info("测试结果：{}", JSON.toJSONString(chatResponse));
    }

}
