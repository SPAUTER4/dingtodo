package org.spauter.dingtodo.store.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.spauter.dingtodo.store.mysql.MysqlApiCallLogStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spauter.dingtodo.enums.TodoRecordStatus;
import org.spauter.dingtodo.model.TodoRecord;
import org.spauter.dingtodo.store.TodoRecordStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * MySQL 台账链路集成测试：激活 mysql profile，用 H2（MySQL 模式）代替真实数据库。
 */
@SpringBootTest(properties = "dingtodo.api-log-retention-days=1")
@ActiveProfiles("mysql")
class MysqlTodoRecordStoreTest {

    @Autowired
    private TodoRecordStore store;
    @Autowired
    private org.spauter.dingtodo.store.ApiCallLogStore apiCallLogStore;
    @Autowired
    private TodoRecordRepository repository;
    @Autowired
    private org.spauter.dingtodo.store.mysql.ApiCallLogRepository apiCallLogRepository;
    @Autowired
    private org.spauter.dingtodo.service.ApiLogCleanupTask cleanupTask;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:dingtodo;MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @BeforeEach
    void clean() {
        repository.deleteAll();
        apiCallLogRepository.deleteAll();
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
    void mysqlStoreShouldBeWired() {
        assertInstanceOf(MysqlTodoRecordStore.class, store);
    }

    @Test
    void saveAndQueryShouldPersist() {
        record("B1", "u1", "t1", TodoRecordStatus.PUSHED);
        record("B1", "u2", "t2", TodoRecordStatus.WITHDRAWN);
        record("B1", null, null, TodoRecordStatus.NOTIFY);
        record("B2", "u4", "t3", TodoRecordStatus.PUSHED);

        List<TodoRecord> pushed = store.findPushed("B1");
        assertEquals(1, pushed.size());
        assertEquals("t1", pushed.get(0).getTaskId());
        assertEquals("B1", store.findByTaskId("t1").orElseThrow().getBizId());
        assertTrue(store.findLatestNotify("B1").isPresent());
        assertTrue(store.findLatestNotify("B2").isEmpty());
    }

    @Test
    void markWithdrawnShouldPersistStatusChange() {
        TodoRecord saved = record("B1", "u1", "t1", TodoRecordStatus.PUSHED);

        store.markWithdrawn(saved);

        TodoRecord reloaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals(TodoRecordStatus.WITHDRAWN, reloaded.getStatus());
        assertTrue(store.findPushed("B1").isEmpty());
    }

    @Test
    void apiCallLogShouldPersistViaMysqlStore() {
        assertInstanceOf(MysqlApiCallLogStore.class, apiCallLogStore);

        org.spauter.dingtodo.model.ApiCallLog log = new org.spauter.dingtodo.model.ApiCallLog();
        log.setOperation("创建待办");
        log.setDingUrl("https://api.dingtalk.com/v1.0/todo/users/u/tasks");
        log.setMethod("POST");
        log.setSuccess(true);
        log.setOpenId("u1");
        apiCallLogStore.save(log);

        org.spauter.dingtodo.model.ApiCallLog saved =
                apiCallLogRepository.findAll().iterator().next();
        assertEquals("创建待办", saved.getOperation());
        assertEquals("u1", saved.getOpenId());
        assertTrue(saved.isSuccess());
        assertNotNull(saved.getCreateTime());
    }

    @Test
    void cleanupShouldDeleteOnlyLogsOlderThanRetention() {
        org.spauter.dingtodo.model.ApiCallLog old = new org.spauter.dingtodo.model.ApiCallLog();
        old.setOperation("旧留痕");
        old.setSuccess(true);
        apiCallLogStore.save(old);
        // 手工改旧：两条入库时间一样，把其中一条的 create_time 拨到保留期之外
        old.setCreateTime(java.time.Instant.now().minus(java.time.Duration.ofDays(2)));
        apiCallLogRepository.save(old);

        org.spauter.dingtodo.model.ApiCallLog fresh = new org.spauter.dingtodo.model.ApiCallLog();
        fresh.setOperation("新留痕");
        fresh.setSuccess(true);
        apiCallLogStore.save(fresh);

        // 保留 1 天（测试类 properties 已开启）：走容器 bean 使 @Transactional 生效
        cleanupTask.cleanup();

        assertEquals(1, apiCallLogRepository.count());
        assertEquals("新留痕", apiCallLogRepository.findAll().iterator().next().getOperation());
    }
}
