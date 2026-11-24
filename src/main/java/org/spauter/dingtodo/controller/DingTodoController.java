package org.spauter.dingtodo.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.spauter.dingtodo.common.Result;
import org.spauter.dingtodo.dto.NotifySendRequest;
import org.spauter.dingtodo.dto.NotifyWithdrawRequest;
import org.spauter.dingtodo.dto.TodoSendRequest;
import org.spauter.dingtodo.dto.TodoSendResult;
import org.spauter.dingtodo.dto.TodoTaskInfo;
import org.spauter.dingtodo.dto.TodoWithdrawByBizRequest;
import org.spauter.dingtodo.dto.TodoWithdrawByTaskRequest;
import org.spauter.dingtodo.dto.WithdrawResult;
import org.spauter.dingtodo.service.DingTodoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class DingTodoController {

    private final DingTodoService service;

    public DingTodoController(DingTodoService service) {
        this.service = service;
    }

    /** 发待办（可选同时发工作通知） */
    @PostMapping("/todo/send")
    public Result<List<TodoSendResult>> sendTodo(@Valid @RequestBody TodoSendRequest request) {
        return Result.ok(service.sendTodo(request));
    }

    /** 按业务键撤回待办（撤回该 bizId 下所有已推送记录及配套通知） */
    @PostMapping("/todo/withdraw/by-biz")
    public Result<WithdrawResult> withdrawByBiz(@Valid @RequestBody TodoWithdrawByBizRequest request) {
        return Result.ok(service.withdrawByBizId(request.bizId()));
    }

    /** 按 taskId 直接撤回待办 */
    @PostMapping("/todo/withdraw/by-task")
    public Result<Void> withdrawByTask(@Valid @RequestBody TodoWithdrawByTaskRequest request) {
        service.withdrawByTaskId(request.userId(), request.taskId());
        return Result.ok();
    }

    /** 查询用户组织内全部待办 */
    @GetMapping("/todo/users/{userId}")
    public Result<List<TodoTaskInfo>> listTodos(@PathVariable String userId,
                                                @RequestParam(defaultValue = "false") boolean fetchAll) {
        return Result.ok(service.listOrgTodos(userId, fetchAll));
    }

    /** 发工作通知（action_card / text） */
    @PostMapping("/notify/send")
    public Result<Long> sendNotify(@Valid @RequestBody NotifySendRequest request) {
        return Result.ok(service.sendNotify(request));
    }

    /** 撤回工作通知 */
    @PostMapping("/notify/withdraw")
    public Result<Void> withdrawNotify(@Valid @RequestBody NotifyWithdrawRequest request) {
        service.withdrawNotify(request.msgTaskId());
        return Result.ok();
    }

    /** 钉钉免登：code 换 userId */
    @GetMapping("/auth/userid")
    public Result<String> userIdByCode(@RequestParam String code) {
        return Result.ok(service.getUserIdByCode(code));
    }

    /** 根据手机号查询钉钉 userId（员工离职后返回 60121 未找到） */
    @GetMapping("/auth/userid-by-mobile")
    public Result<String> userIdByMobile(@RequestParam String mobile) {
        return Result.ok(service.getUserIdByMobile(mobile));
    }
}
