package org.spauter.dingtodo;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.spauter.dingtodo.store.InMemoryTodoRecordStore;
import org.spauter.dingtodo.store.TodoRecordStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 默认 profile 冒烟测试：不连数据库（JPA 自动配置被排除），内存台账生效。
 */
@SpringBootTest
class ApplicationSmokeTest {

    @Autowired
    private TodoRecordStore store;

    @Test
    void contextShouldLoadWithInMemoryStoreByDefault() {
        assertInstanceOf(InMemoryTodoRecordStore.class, store);
    }
}
