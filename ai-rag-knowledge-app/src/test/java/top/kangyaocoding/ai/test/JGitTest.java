package top.kangyaocoding.ai.test;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.PathResource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.print.Doc;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileVisitResult;
import java.util.List;

/**
 * 描述:
 *
 * @author K·Herbert herbert501@qq.com
 * @since 2025-06-24 16:21
 */
@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
public class JGitTest {

    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private PgVectorStore pgVectorStore;

    @Test
    public void test() throws IOException, GitAPIException {
        String repoUrl = "https://gitee.com/Herbert0501/ecommerce-user-behavior-analysis-java.git";
        String username = "";
        String password = "";

        String localPath = "./cloned-repo";
        log.info("开始克隆仓库： {} 到本地仓库： {}", repoUrl, localPath);

        FileUtils.deleteDirectory(new File(localPath));

        Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                .setDirectory(new File(localPath))
                .call();
        git.close();
        log.info("仓库克隆完成");
    }

    @Test
    public void test_file() {
        try {
            Files.walkFileTree(Paths.get("./cloned-repo"), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    log.info("Found file: {}", file);
                    PathResource resource = new PathResource(file);
                    TikaDocumentReader reader = new TikaDocumentReader(resource);
                    log.info("文件内容： {}", reader.read());
                    List<Document> documents = reader.get();
                    List<Document> documentSplitterList = tokenTextSplitter.apply(documents);
                    documents.forEach(document -> document.getMetadata().put("knowledge", "ecommerce-user-behavior-analysis-java"));
                    documentSplitterList.forEach(document -> {
                        document.getMetadata().put("knowledge", "ecommerce-user-behavior-analysis-java");
                    });
                    pgVectorStore.accept(documentSplitterList);
                    log.info("上传成功：{}", documentSplitterList);
                    return FileVisitResult.TERMINATE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
