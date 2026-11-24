package org.spauter.dingtodo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spauter.dingtodo.client.DingTalkNotifyClient;
import org.spauter.dingtodo.client.DingTalkTodoClient;
import org.spauter.dingtodo.client.DingTalkUserClient;
import org.spauter.dingtodo.client.JsonHttp;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.config.DingTalkProperties;
import org.spauter.dingtodo.config.DingTodoProperties;
import org.spauter.dingtodo.dto.CreateTaskResult;
import org.spauter.dingtodo.dto.NotifySendRequest;
import org.spauter.dingtodo.dto.TodoSendRequest;
import org.spauter.dingtodo.dto.TodoSendResult;
import org.spauter.dingtodo.dto.WithdrawResult;
import org.spauter.dingtodo.enums.NotifyType;
import org.spauter.dingtodo.enums.TodoRecordStatus;
import org.spauter.dingtodo.model.TodoRecord;
import org.spauter.dingtodo.store.InMemoryTodoRecordStore;

@ExtendWith(MockitoExtension.class)
class DingTodoServiceTest {

    @Mock
    private DingTalkUserClient userClient;
    @Mock
    private DingTalkTodoClient todoClient;
    @Mock
    private DingTalkNotifyClient notifyClient;
    @Mock
    private JsonHttp jsonHttp;

    private InMemoryTodoRecordStore store;
    private DingTodoService service;
    private DingTodoProperties props;

    @BeforeEach
    void setUp() {
        DingTalkProperties dingProps = new DingTalkProperties("k", "s", "100",
                "https://oapi.dingtalk.com", "https://api.dingtalk.com", 7000);
        props = new DingTodoProperties(true, "memory", 3600);
        store = new InMemoryTodoRecordStore();
        lenient().when(jsonHttp.newObject()).thenAnswer(inv -> JsonNodeFactory.instance.objectNode());
        service = new DingTodoService(dingProps, props, userClient, todoClient, notifyClient, store, jsonHttp);
    }

    private TodoSendRequest sendRequest(Boolean sendNotify) {
        return new TodoSendRequest("BIZ-1", "标题", "描述",
                "https://app.example/detail", "https://pc.example/detail",
                List.of("u1"), null, sendNotify, null, null);
    }

    @Test
    void sendTodoShouldBuildTaskBodyAndRecord() {
        when(userClient.getUnionId("u1")).thenReturn("union-1");
        when(todoClient.createTask(eq("union-1"), any())).thenReturn(new CreateTaskResult("t-1", "req-1"));

        List<TodoSendResult> results = service.sendTodo(sendRequest(null));

        assertEquals(1, results.size());
        assertEquals("t-1", results.get(0).taskId());
        assertNull(results.get(0).msgTaskId());

        ArgumentCaptor<ObjectNode> captor = ArgumentCaptor.forClass(ObjectNode.class);
        verify(todoClient).createTask(eq("union-1"), captor.capture());
        JsonNode body = new ObjectMapper().valueToTree(captor.getValue());
        assertEquals("标题", body.path("subject").asText());
        assertEquals("union-1", body.path("executorIds").get(0).asText());
        assertEquals("https://app.example/detail", body.path("detailUrl").path("appUrl").asText());
        assertEquals("1", body.path("remindNotifyConfigs").path("dingNotify").asText());

        assertEquals(1, store.findPushed("BIZ-1").size());
        verify(notifyClient, never()).sendActionCard(anyList(), any(), any(), any(), any());
    }

    @Test
    void sendTodoWithNotifyShouldSendActionCardAndRecord() {
        when(userClient.getUnionId("u1")).thenReturn("union-1");
        when(todoClient.createTask(eq("union-1"), any())).thenReturn(new CreateTaskResult("t-1", "req-1"));
        when(notifyClient.sendActionCard(eq(List.of("u1")), eq("标题"), eq("描述"),
                eq("查看详情"), eq("https://app.example/detail"))).thenReturn(9L);

        List<TodoSendResult> results = service.sendTodo(sendRequest(true));

        assertEquals(9L, results.get(0).msgTaskId());
        var notifies = store.findLatestNotify("BIZ-1");
        assertEquals(9L, notifies.orElseThrow().getMsgTaskId());
    }

    @Test
    void withdrawByBizIdShouldWithdrawAllPushedAndRecallNotify() {
        TodoRecord r1 = newRecord("u1", "t-1", TodoRecordStatus.PUSHED);
        TodoRecord r2 = newRecord("u2", "t-2", TodoRecordStatus.PUSHED);
        // 补一条通知记录（用于验证撤回时同步 recall 通知）
        TodoRecord notify = new TodoRecord();
        notify.setBizId("BIZ-1");
        notify.setMsgTaskId(9L);
        notify.setStatus(TodoRecordStatus.NOTIFY);
        store.save(notify);

        when(userClient.getUnionId("u1")).thenReturn("union-1");
        when(userClient.getUnionId("u2")).thenReturn("union-2");

        WithdrawResult result = service.withdrawByBizId("BIZ-1");

        assertEquals(2, result.total());
        assertEquals(2, result.success());
        verify(todoClient).deleteTask("union-1", "t-1");
        verify(todoClient).deleteTask("union-2", "t-2");
        verify(notifyClient).recallNotify(9L);
        assertEquals(TodoRecordStatus.WITHDRAWN, r1.getStatus());
        assertEquals(TodoRecordStatus.WITHDRAWN, r2.getStatus());
    }

    @Test
    void withdrawByBizIdShouldFailWhenRecordDisabled() {
        props = new DingTodoProperties(false, "memory", 3600);
        DingTalkProperties dingProps = new DingTalkProperties("k", "s", "100",
                "https://oapi.dingtalk.com", "https://api.dingtalk.com", 7000);
        service = new DingTodoService(dingProps, props, userClient, todoClient, notifyClient, store, jsonHttp);

        assertThrows(ApiException.class, () -> service.withdrawByBizId("BIZ-1"));
    }

    @Test
    void sendNotifyShouldValidateActionCardFields() {
        NotifySendRequest invalid = new NotifySendRequest(NotifyType.ACTION_CARD, List.of("u1"),
                null, null, null, null, null, null);

        ApiException e = assertThrows(ApiException.class, () -> service.sendNotify(invalid));
        assertEquals("ACTION_CARD 通知必须传 title 和 markdown", e.getMessage());
    }

    private TodoRecord newRecord(String userId, String taskId, TodoRecordStatus status) {
        TodoRecord record = new TodoRecord();
        record.setBizId("BIZ-1");
        record.setUserId(userId);
        record.setTaskId(taskId);
        record.setStatus(status);
        return store.save(record);
    }
}
