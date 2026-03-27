package pl.piomin.signalmind.signal.sse;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE endpoint that streams real-time signal events to the React dashboard (SM-30).
 *
 * <p>Clients must be authenticated (JWT bearer token via {@code Authorization} header).
 * The path {@code /api/stream/signals} is intentionally <em>not</em> in
 * {@code SecurityConfig.PUBLIC_PATHS} so it falls through to
 * {@code anyRequest().authenticated()}.
 *
 * <p>On reconnect, the browser automatically sends the {@code Last-Event-ID} header
 * with the last event ID it received.  The service uses this to replay any missed
 * events before streaming live ones.
 */
@RestController
@RequestMapping("/api/stream")
public class SignalSseController {

    private final SignalSseService sseService;

    public SignalSseController(SignalSseService sseService) {
        this.sseService = sseService;
    }

    /**
     * Opens a persistent SSE connection for the caller.
     *
     * @param lastEventId optional; sent automatically by the browser after reconnect
     * @return a live {@link SseEmitter} that pushes signal events indefinitely
     */
    @GetMapping(value = "/signals", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSignals(
            @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId
    ) {
        return sseService.register(lastEventId);
    }
}
