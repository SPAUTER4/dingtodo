package org.spauter.dingtodo.store;

import org.spauter.dingtodo.model.ApiCallLog;

/**
 * 钉钉 API 调用留痕存储接口。
 *
 * <p>实现跟随 dingtodo.record-store 切换（memory 默认 / mysql）。留痕写入失败不影响业务调用。
 */
public interface ApiCallLogStore {

    void save(ApiCallLog log);
}
