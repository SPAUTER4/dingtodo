package org.spauter.dingtodo.store;

import java.util.List;
import java.util.Optional;
import org.spauter.dingtodo.model.TodoRecord;

/**
 * 待办/通知台账存储接口。
 *
 * <p>默认提供内存实现（重启丢失）；接入方可提供自己的 bean（如 MySQL 实现）替换。
 */
public interface TodoRecordStore {

    TodoRecord save(TodoRecord record);

    Optional<TodoRecord> findByTaskId(String taskId);

    /** 指定业务键下所有已推送（PUSHED）的待办记录 */
    List<TodoRecord> findPushed(String bizId);

    /** 指定业务键下最近一条通知（NOTIFY）记录 */
    Optional<TodoRecord> findLatestNotify(String bizId);

    void markWithdrawn(TodoRecord record);
}
