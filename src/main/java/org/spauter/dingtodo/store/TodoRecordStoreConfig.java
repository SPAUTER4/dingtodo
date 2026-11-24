package org.spauter.dingtodo.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 台账存储默认实现：接入方声明自己的 TodoRecordStore bean 即可替换。
 */
@Configuration
public class TodoRecordStoreConfig {

    @Bean
    @ConditionalOnMissingBean(TodoRecordStore.class)
    public TodoRecordStore todoRecordStore() {
        return new InMemoryTodoRecordStore();
    }
}
