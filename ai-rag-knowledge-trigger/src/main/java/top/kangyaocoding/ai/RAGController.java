package top.kangyaocoding.ai;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.kangyaocoding.ai.response.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * 描述: RAG 服务 Controller
 *
 * @author K·Herbert herbert501@qq.com
 * @since 2025-06-18 15:19
 */
@Slf4j
@RestController()
@CrossOrigin(origins = "${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/rag/")
public class RAGController {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private PgVectorStore pgVectorStore;

    @RequestMapping(value = "query_rag_tag_list", method = RequestMethod.GET)
    public Response<List<String>> queryRagTagList() {
        return Response.<List<String>>builder()
                .code("200")
                .info("success")
                .data(new ArrayList<>(redissonClient.getList("ragTagList")))
                .build();
    }

    @RequestMapping(value = "upload_file", method = RequestMethod.POST)
    public Response<String> uploadFile(@RequestParam String ragTag, @RequestParam List<MultipartFile> files) {
        log.info("开始上传知识库 {}", ragTag);
        for (MultipartFile file : files) {
            log.info("开始上传文件 {}", file.getOriginalFilename());
            TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
            List<Document> documents = reader.get();
            List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

            // 添加知识库标签
            documents.forEach(document -> document.getMetadata().put("knowledge", ragTag));
            documentSplitterList.forEach(document -> document.getMetadata().put("knowledge", ragTag));

            pgVectorStore.accept(documentSplitterList);

            // 添加知识库记录
            RList<String> ragTagList = redissonClient.getList("ragTagList");
            if (!ragTagList.contains(ragTag)) {
                ragTagList.add(ragTag);
            }
        }
        log.info("上传知识库成功：{}", ragTag);
        return Response.<String>builder()
                .code("200")
                .info("success")
                .data("上传成功")
                .build();
    }
}
