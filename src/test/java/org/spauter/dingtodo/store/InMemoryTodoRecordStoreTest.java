package org.spauter.dingtodo.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spauter.dingtodo.enums.TodoRecordStatus;
import org.spauter.dingtodo.model.TodoRecord;

class InMemoryTodoRecordStoreTest {

    private InMemoryTodoRecordStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryTodoRecordStore();
    }

    private TodoRecord record(String bizId, String userId, String taskId, TodoRecordStatus status) {
        TodoRecord record = new TodoRecord();
        record.setBizId(bizId);
        record.setUserId(userId);
        record.setTaskId(taskId);
        record.setStatus(status);
        return store.save(record);
    }

    @Test
    void findPushedShouldOnlyReturnPushedRecordsOfBizId() {
        record("B1", "u1", "t1", TodoRecordStatus.PUSHED);
        record("B1", "u2", "t2", TodoRecordStatus.WITHDRAWN);
        record("B1", "u3", null, TodoRecordStatus.NOTIFY);
        record("B2", "u4", "t3", TodoRecordStatus.PUSHED);

        var pushed = store.findPushed("B1");

        assertEquals(1, pushed.size());
        assertEquals("t1", pushed.get(0).getTaskId());
    }

    @Test
    void findByTaskIdShouldReturnLatestMatch() {
        record("B1", "u1", "t1", TodoRecordStatus.PUSHED);
        record("B1", "u1", "t1", TodoRecordStatus.PUSHED);

        assertEquals("t1", store.findByTaskId("t1").orElseThrow().getTaskId());
        assertTrue(store.findByTaskId("missing").isEmpty());
    }

    @Test
    void markWithdrawnShouldChangeStatus() {
        TodoRecord record = record("B1", "u1", "t1", TodoRecordStatus.PUSHED);

        store.markWithdrawn(record);

        assertEquals(TodoRecordStatus.WITHDRAWN, record.getStatus());
        assertTrue(store.findPushed("B1").isEmpty());
    }
}
