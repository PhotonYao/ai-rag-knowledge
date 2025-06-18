package top.kangyaocoding.ai;

import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * 描述:
 *
 * @author K·Herbert herbert501@qq.com
 * @since 2025-06-17 15:11
 */
public interface IAiService {

    ChatResponse generate(String model, String message);

    Flux<ChatResponse> generateStream(String model, String message);
}
