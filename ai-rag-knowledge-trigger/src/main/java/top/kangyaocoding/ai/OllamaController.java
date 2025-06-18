package top.kangyaocoding.ai;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 描述: Ollama 模型对外接口
 *
 * @author K·Herbert herbert501@qq.com
 * @since 2025-06-17 15:39
 */
@RestController()
@CrossOrigin(origins = "${app.config.cross-origin}")
@RequestMapping("/api/${app.config.api-version}/ollama/")
public class OllamaController {
    @Resource
    private OllamaChatModel ollamaChatModel;

    /**
     * curl http://localhost:8089/api/v1/ollama/generate?model=qwen3:1.7b&message=1+1
     */
    @RequestMapping(value = "generate", method = RequestMethod.GET)
    public ChatResponse generate(@RequestParam String model, @RequestParam String message) {
        return ollamaChatModel.call(new Prompt(
                message,
                OllamaOptions.builder()
                        .model(model)
                        .build()
        ));
    }

    /**
     * curl http://localhost:8089/api/v1/ollama/generate_stream?model=qwen3:1.7b&message=1+1
     */
    @RequestMapping(value = "generate_stream", method = RequestMethod.GET)
    public Flux<ChatResponse> generateStream(@RequestParam String model, @RequestParam String message) {
        return ollamaChatModel.stream(new Prompt(
                message,
                OllamaOptions.builder()
                        .model(model)
                        .build()
        ));
    }
}
