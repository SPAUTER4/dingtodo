package org.spauter.dingtodo.dto;

import java.util.List;

/** 组织待办分页查询结果 */
public record TodoPage(List<TodoTaskInfo> tasks, String nextToken, boolean hasMore) {
}
