package top.kangyaocoding.ai;

import org.springframework.web.multipart.MultipartFile;
import top.kangyaocoding.ai.response.Response;

import java.util.List;

/**
 * 描述:  rag 服务接口
 *
 * @author K·Herbert herbert501@qq.com
 * @since 2025-06-18 15:12
 */
public interface IRAGService {

    Response<List<String>> queryRagTagList();

    Response<String> uploadFile(String ragTag, List<MultipartFile> files);
}
