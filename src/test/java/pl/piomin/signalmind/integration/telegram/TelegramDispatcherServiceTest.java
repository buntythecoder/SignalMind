package pl.piomin.signalmind.integration.telegram;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TelegramDispatcherService}.
 *
 * <p>Redis, ObjectMapper, and TelegramProperties are fully mocked;
 * no Spring context is loaded.
 *
 * <p>Lenient strictness is used because {@code props.getBotToken()} is stubbed
 * globally in setUp but is only exercised by tests that invoke {@code sendToTelegram}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramDispatcherServiceTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private TelegramProperties props;

    @Mock
    private ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    @Mock
    private ListOperations<String, String> listOps;

    private TelegramDispatcherService dispatcher;

    @BeforeEach
    void setUp() {
        when(redis.opsForList()).thenReturn(listOps);
        when(props.getBotToken()).thenReturn("test-token");
        dispatcher = new TelegramDispatcherService(redis, props, objectMapper);
    }

    // ── enqueue ────────────────────────────────────────────────────────────────

    @Test
    void enqueue_serialisesAndRightPushesToQueueKey() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"chatId\":\"123\",\"text\":\"hello\",\"attempts\":0,\"retryAfterMs\":0}");

        dispatcher.enqueue("123", "hello");

        verify(listOps).rightPush(eq(TelegramDispatcherService.QUEUE_KEY), anyString());
    }

    @Test
    void enqueue_doesNotThrowWhenSerializationFails() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});

        // Must not propagate the exception
        dispatcher.enqueue("123", "hello");

        verify(listOps, never()).rightPush(anyString(), anyString());
    }

    // ── processQueue — null pop ────────────────────────────────────────────────

    @Test
    void processQueue_noOp_whenQueueIsEmpty() {
        when(listOps.leftPop(TelegramDispatcherService.QUEUE_KEY)).thenReturn(null);

        dispatcher.processQueue();

        // Nothing else should happen
        verify(listOps, only()).leftPop(TelegramDispatcherService.QUEUE_KEY);
    }

    // ── processQueue — happy path ──────────────────────────────────────────────

    @Test
    void processQueue_sendsMessage_andIncrementsRateCounter() throws Exception {
        TelegramDispatcherService.QueuedMessage msg =
                new TelegramDispatcherService.QueuedMessage("42", "hi", 0, 0L, null);
        String json = "{\"chatId\":\"42\",\"text\":\"hi\",\"attempts\":0,\"retryAfterMs\":0}";

        when(listOps.leftPop(TelegramDispatcherService.QUEUE_KEY)).thenReturn(json);
        when(objectMapper.readValue(json, TelegramDispatcherService.QueuedMessage.class)).thenReturn(msg);

        // Spy the dispatcher so we can stub sendToTelegram without making real HTTP calls
        TelegramDispatcherService spy = spy(dispatcher);
        doReturn(true).when(spy).sendToTelegram("42", "hi", null);

        spy.processQueue();

        verify(spy).sendToTelegram("42", "hi", null);
        // Rate counter is an internal field — we verify no re-push happened (success path)
        verify(listOps, never()).rightPush(anyString(), anyString());
        verify(listOps, never()).leftPush(anyString(), anyString());
    }

    // ── processQueue — retry-after in future ──────────────────────────────────

    @Test
    void processQueue_pushesBack_whenRetryAfterMsIsInFuture() throws Exception {
        long futureMs = System.currentTimeMillis() + 60_000L;
        TelegramDispatcherService.QueuedMessage msg =
                new TelegramDispatcherService.QueuedMessage("42", "hi", 1, futureMs, null);
        String json = "{...}";

        when(listOps.leftPop(TelegramDispatcherService.QUEUE_KEY)).thenReturn(json);
        when(objectMapper.readValue(json, TelegramDispatcherService.QueuedMessage.class)).thenReturn(msg);

        TelegramDispatcherService spy = spy(dispatcher);
        spy.processQueue();

        // Message must be put back; sendToTelegram must NOT be called
        verify(listOps).rightPush(eq(TelegramDispatcherService.QUEUE_KEY), eq(json));
        verify(spy, never()).sendToTelegram(anyString(), anyString(), any());
    }

    // ── processQueue — send fails, attempts < MAX_ATTEMPTS-1 ──────────────────

    @Test
    void processQueue_lpushRetry_whenSendFailsAndAttemptsBelow_MAX() throws Exception {
        TelegramDispatcherService.QueuedMessage msg =
                new TelegramDispatcherService.QueuedMessage("42", "hi", 0, 0L, null);
        String json = "{\"chatId\":\"42\",\"text\":\"hi\",\"attempts\":0,\"retryAfterMs\":0}";

        when(listOps.leftPop(TelegramDispatcherService.QUEUE_KEY)).thenReturn(json);
        when(objectMapper.readValue(json, TelegramDispatcherService.QueuedMessage.class)).thenReturn(msg);

        // Serialize the retry message
        String retryJson = "{\"chatId\":\"42\",\"text\":\"hi\",\"attempts\":1,\"retryAfterMs\":9999}";
        when(objectMapper.writeValueAsString(any())).thenReturn(retryJson);

        TelegramDispatcherService spy = spy(dispatcher);
        doReturn(false).when(spy).sendToTelegram("42", "hi", null);

        spy.processQueue();

        // Retry must be pushed to the FRONT of the queue (leftPush)
        verify(listOps).leftPush(eq(TelegramDispatcherService.QUEUE_KEY), eq(retryJson));
        // Must NOT go to DLQ
        verify(listOps, never()).rightPush(eq(TelegramDispatcherService.DLQ_KEY), anyString());
    }

    // ── processQueue — send fails, attempts == MAX_ATTEMPTS-1 ─────────────────

    @Test
    void processQueue_rpushToDlq_whenSendFailsAndAttemptsExhausted() throws Exception {
        int exhausted = TelegramDispatcherService.MAX_ATTEMPTS - 1;
        TelegramDispatcherService.QueuedMessage msg =
                new TelegramDispatcherService.QueuedMessage("42", "hi", exhausted, 0L, null);
        String json = "{\"chatId\":\"42\",\"text\":\"hi\",\"attempts\":2,\"retryAfterMs\":0}";

        when(listOps.leftPop(TelegramDispatcherService.QUEUE_KEY)).thenReturn(json);
        when(objectMapper.readValue(json, TelegramDispatcherService.QueuedMessage.class)).thenReturn(msg);

        TelegramDispatcherService spy = spy(dispatcher);
        doReturn(false).when(spy).sendToTelegram("42", "hi", null);

        spy.processQueue();

        // Must go to DLQ
        verify(listOps).rightPush(eq(TelegramDispatcherService.DLQ_KEY), eq(json));
        // Must NOT retry
        verify(listOps, never()).leftPush(anyString(), anyString());
    }

    // ── retryDlq ──────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void retryDlq_drainsDlqAndReEnqueuesWithAttemptsReset() throws Exception {
        String dlqJson   = "{\"chatId\":\"42\",\"text\":\"hi\",\"attempts\":2,\"retryAfterMs\":0}";
        String freshJson = "{\"chatId\":\"42\",\"text\":\"hi\",\"attempts\":0,\"retryAfterMs\":0}";

        TelegramDispatcherService.QueuedMessage dead =
                new TelegramDispatcherService.QueuedMessage("42", "hi", 2, 0L, null);

        // Return item once, then null to stop the loop
        when(listOps.leftPop(TelegramDispatcherService.DLQ_KEY))
                .thenReturn(dlqJson)
                .thenReturn(null);
        when(objectMapper.readValue(dlqJson, TelegramDispatcherService.QueuedMessage.class)).thenReturn(dead);
        when(objectMapper.writeValueAsString(any())).thenReturn(freshJson);

        dispatcher.retryDlq();

        // Verify re-enqueue to main queue with attempts=0
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(listOps).rightPush(eq(TelegramDispatcherService.QUEUE_KEY), captor.capture());
        assertThat(captor.getValue()).isEqualTo(freshJson);
    }
}
