package org.spauter.dingtodo.store.mysql;

import java.time.Instant;
import org.spauter.dingtodo.model.ApiCallLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiCallLogRepository extends JpaRepository<ApiCallLog, Long> {

    /** 删除指定时间之前的留痕（保留期清理用） */
    long deleteByCreateTimeBefore(Instant time);
}
