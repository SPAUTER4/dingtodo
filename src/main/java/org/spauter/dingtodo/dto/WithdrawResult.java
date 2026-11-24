package org.spauter.dingtodo.dto;

import java.util.List;

/** 按业务键批量撤回结果 */
public record WithdrawResult(int total, int success, List<String> failedUserIds) {
}
