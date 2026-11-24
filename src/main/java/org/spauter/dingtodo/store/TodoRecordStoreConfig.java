package org.spauter.dingtodo.store;

import org.spauter.dingtodo.store.mysql.MysqlTodoRecordStore;
import org.spauter.dingtodo.store.mysql.TodoRecordRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 台账存储装配：
 *
 * <ul>
 *   <li>dingtodo.record-store=mysql：MySQL 持久化实现（需 mysql profile 提供 DataSource）</li>
 *   <li>默认：内存实现（零配置启动，重启丢失）</li>
 * </ul>
 *
 * <p>接入方也可以声明自己的 TodoRecordStore bean 覆盖默认装配。
 */
@Configuration
public class TodoRecordStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "dingtodo.record-store", havingValue = "mysql")
    public TodoRecordStore mysqlTodoRecordStore(TodoRecordRepository repository) {
        return new MysqlTodoRecordStore(repository);
    }

    @Bean
    @ConditionalOnMissingBean(TodoRecordStore.class)
    public TodoRecordStore todoRecordStore() {
        return new InMemoryTodoRecordStore();
    }
}
