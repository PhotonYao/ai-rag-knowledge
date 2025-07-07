package top.kangyaocoding.ai;

import com.drew.lang.annotations.NotNull;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.kangyaocoding.ai.response.Response;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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
public class RAGController implements IRAGService {

    @Resource
    private OllamaChatModel ollamaChatModel;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private PgVectorStore pgVectorStore;

    @Override
    @RequestMapping(value = "query_rag_tag_list", method = RequestMethod.GET)
    public Response<List<String>> queryRagTagList() {
        return Response.<List<String>>builder()
                .code("200")
                .info("success")
                .data(new ArrayList<>(redissonClient.getList("ragTagList")))
                .build();
    }

    @Override
    @RequestMapping(value = "upload_file", method = RequestMethod.POST)
    public Response<String> uploadFile(@RequestParam("ragTag") String ragTag, @RequestParam("files") List<MultipartFile> files) {
        log.info("开始上传知识库 {}", ragTag);
        for (MultipartFile file : files) {
            log.info("开始上传文件 {}", file.getOriginalFilename());
            TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
            List<Document> documents = reader.get();
            List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

            // 添加知识库标签
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

    @Override
    @RequestMapping(value = "analyze_git_repository", method = RequestMethod.POST)
    public Response<String> analyzeGitRepository(@RequestParam("ragTag") String ragTag,
                                                 @RequestParam("gitUrl") String gitUrl,
                                                 @RequestParam("username") String username,
                                                 @RequestParam("password") String password) throws IOException {
        log.info("开始上传知识库 {}", ragTag);
        String localPath = "./git-cloned-repo";
        String repoProjectName = gitUrl.substring(gitUrl.lastIndexOf("/") + 1).replace(".git", "");
        log.info("开始克隆仓库: {} 到本地目录: {}", repoProjectName, new File(localPath).getAbsoluteFile());

        FileUtils.deleteDirectory(new File(localPath));
        try (Git git = Git.cloneRepository()
                .setURI(gitUrl)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                .call()) {
            log.info("仓库克隆成功: {}", repoProjectName);
        } catch (GitAPIException e) {
            log.error("仓库克隆失败: {}", repoProjectName, e);
        }

        Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                log.info("开始处理拉取的文件，文件名: {}", file.getFileName());
                try {
                    TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
                    List<Document> documents = reader.get();
                    List<Document> documentSplitterList = tokenTextSplitter.apply(documents);
                    documentSplitterList.forEach(document -> document.getMetadata().put("knowledge", ragTag));
                    pgVectorStore.accept(documentSplitterList);
                    log.info("处理文件: {} 成功", file.getFileName());
                } catch (Exception e) {
                    log.error("处理文件: {} 失败", file.getFileName(), e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.error("处理文件: {} 失败", file.getFileName(), exc);
                return FileVisitResult.CONTINUE;
            }
        });
        // 删除临时本地仓库
        FileUtils.deleteDirectory(new File(localPath));
        // 添加知识库记录
        RList<String> ragTagList = redissonClient.getList("ragTagList");
        if (!ragTagList.contains(ragTag)) {
            ragTagList.add(repoProjectName);
        }
        log.info("上传知识库成功：{}", ragTag);
        return Response.<String>builder()
                .code("200")
                .info("success")
                .data("上传成功")
                .build();
    }

}
