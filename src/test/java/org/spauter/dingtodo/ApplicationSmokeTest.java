package org.spauter.dingtodo;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.spauter.dingtodo.config.DingTalkProperties;
import org.spauter.dingtodo.service.DingTodoService;
import org.spauter.dingtodo.store.InMemoryTodoRecordStore;
import org.spauter.dingtodo.store.TodoRecordStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.StringUtils;

/**
 * 默认 profile 冒烟测试：不连数据库（JPA 自动配置被排除），内存台账生效。
 *
 * <p>激活 local profile（application-local.yml，gitignored）后，联调用例自动启用；
 * 其他环境无凭证时联调用例自动跳过。
 */
@SpringBootTest
@ActiveProfiles("local")
class ApplicationSmokeTest {

    @Autowired
    private TodoRecordStore store;

    @Autowired
    private DingTalkProperties dingProps;

    @Resource
    DingTodoService service;

    @Test
    void contextShouldLoadWithInMemoryStoreByDefault() {
        assertInstanceOf(InMemoryTodoRecordStore.class, store);
    }

    /** 联调用例：需要 application-local.yml 提供真实凭证，否则跳过 */
    @Test
    void getIdByMobile() {
        assumeTrue(StringUtils.hasText(dingProps.appKey()), "未配置钉钉凭证（application-local.yml），跳过联调测试");
        System.out.println(service.getUserIdByMobile("18574730762"));
    }
}
