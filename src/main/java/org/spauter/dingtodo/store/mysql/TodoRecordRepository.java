package org.spauter.dingtodo.store.mysql;

import java.util.List;
import java.util.Optional;
import org.spauter.dingtodo.enums.TodoRecordStatus;
import org.spauter.dingtodo.model.TodoRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoRecordRepository extends JpaRepository<TodoRecord, Long> {

    /** 按 taskId 取最近一条记录（同一待办可能多次推送） */
    Optional<TodoRecord> findTopByTaskIdOrderByIdDesc(String taskId);

    /** 指定业务键下所有指定状态的记录 */
    List<TodoRecord> findByBizIdAndStatus(String bizId, TodoRecordStatus status);

    /** 指定业务键下最近一条指定状态的记录 */
    Optional<TodoRecord> findTopByBizIdAndStatusOrderByCreateTimeDesc(String bizId, TodoRecordStatus status);
}
