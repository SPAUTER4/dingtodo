package org.spauter.dingtodo.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.spauter.dingtodo.common.ApiException;
import org.spauter.dingtodo.model.ApiCallLog;
import org.spauter.dingtodo.store.ApiCallLogStore;
import org.springframework.stereotype.Component;

/**
 * 钉钉 HTTP 调用统一封装。
 *
 * <p>旧版 oapi 接口按 errcode 判定成功；新版 v1.0 REST 按 HTTP 状态码判定。
 * 统一使用 OkHttp3 + Jackson，不再依赖旧 SDK 的序列化。
 *
 * <p>所有调用自动留痕到 ApiCallLogStore（url 中 access_token 脱敏、超长截断、
 * 留痕失败不影响业务调用）。
 */
@Component
public class JsonHttp {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String TOKEN_HEADER = "x-acs-dingtalk-access-token";
    private static final Pattern TOKEN_QUERY_PARAM = Pattern.compile("([?&]access_token=)[^&]+");
    private static final int MAX_BODY_LOG_LENGTH = 2000;
    private static final int MAX_ERR_MSG_LENGTH = 250;

    private final OkHttpClient ok;
    private final ObjectMapper mapper;
    private final ApiCallLogStore logStore;

    public JsonHttp(OkHttpClient ok, ObjectMapper mapper, ApiCallLogStore logStore) {
        this.ok = ok;
        this.mapper = mapper;
        this.logStore = logStore;
    }

    /** oapi GET（如 gettoken），校验 errcode */
    public JsonNode getOapi(String url, String operation) {
        return executeOapi(new Request.Builder().url(url).get().build(), operation);
    }

    /** oapi POST JSON（url 需已拼好 access_token），校验 errcode */
    public JsonNode postOapi(String url, Object body, String operation) {
        return executeOapi(new Request.Builder().url(url).post(jsonBody(body)).build(), operation);
    }

    /** v1.0 REST POST JSON，HTTP 非 2xx 抛 ApiException */
    public JsonNode postApiV1(String url, String token, Object body, String operation) {
        Request request = new Request.Builder()
                .url(url)
                .header(TOKEN_HEADER, token)
                .post(jsonBody(body))
                .build();
        return executeV1(request, operation);
    }

    /** v1.0 REST DELETE，HTTP 非 2xx 抛 ApiException */
    public JsonNode deleteApiV1(String url, String token, String operation) {
        Request request = new Request.Builder()
                .url(url)
                .header(TOKEN_HEADER, token)
                .delete()
                .build();
        return executeV1(request, operation);
    }

    public ObjectNode newObject() {
        return mapper.createObjectNode();
    }

    private JsonNode executeOapi(Request request, String operation) {
        return execute(request, operation, resp -> resp.path("errcode").asInt(-1) == 0);
    }

    private JsonNode executeV1(Request request, String operation) {
        // v1.0 的业务成功即 HTTP 2xx，无需额外判定
        return execute(request, operation, resp -> true);
    }

    /** 执行调用并按 businessOk 判定业务成功，留痕 success 反映业务结果 */
    private JsonNode execute(Request request, String operation, java.util.function.Predicate<JsonNode> businessOk) {
        long start = System.currentTimeMillis();
        String maskedUrl = maskToken(request.url().toString());
        String method = request.method();
        String params = truncate(requestBodyString(request));
        String result = null;
        boolean success = false;
        String errMsg = null;
        try (Response response = ok.newCall(request).execute()) {
            String body = bodyAsString(response.body());
            JsonNode resp = mapper.readTree(body == null || body.isEmpty() ? "{}" : body);
            result = truncate(body);
            if (response.isSuccessful() && businessOk.test(resp)) {
                success = true;
                return resp;
            }
            if (!response.isSuccessful()) {
                // v1.0 错误响应形如 {"code": "xx", "message": "xx"}
                String code = resp.path("code").asText(String.valueOf(response.code()));
                String message = resp.path("message").asText("HTTP " + response.code());
                errMsg = truncate(code + ": " + message, MAX_ERR_MSG_LENGTH);
                throw new ApiException(code, message);
            }
            // oapi 业务失败：HTTP 200 但 errcode != 0
            int errcode = resp.path("errcode").asInt(-1);
            String errmsg = resp.path("errmsg").asText("钉钉 oapi 调用失败");
            errMsg = truncate(errcode + ": " + errmsg, MAX_ERR_MSG_LENGTH);
            throw new ApiException(String.valueOf(errcode), errmsg);
        } catch (IOException e) {
            errMsg = truncate("网络请求失败: " + e.getMessage(), MAX_ERR_MSG_LENGTH);
            throw new ApiException(errMsg, e);
        } finally {
            saveLog(operation, maskedUrl, method, params, result, success, errMsg,
                    (int) (System.currentTimeMillis() - start));
        }
    }

    /** 留痕写入失败只记 warn，不影响业务调用 */
    private void saveLog(String operation, String url, String method, String params,
                         String result, boolean success, String errMsg, int elapsedMs) {
        try {
            ApiCallLog log = new ApiCallLog();
            log.setCreateTime(java.time.Instant.now());
            log.setOperation(operation);
            log.setDingUrl(truncate(url, 500));
            log.setMethod(method);
            log.setParams(params);
            log.setResult(result);
            log.setSuccess(success);
            log.setErrMsg(errMsg);
            log.setElapsedMs(elapsedMs);
            log.setOpenId(extractUserId(params));
            logStore.save(log);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(JsonHttp.class)
                    .warn("API 调用留痕失败（不影响业务）: {}", e.getMessage());
        }
    }

    /** 从请求体 JSON 提取 userid / userid_list 作为排查用的用户标识 */
    private String extractUserId(String params) {
        if (params == null) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(params);
            for (String field : new String[]{"userid", "userid_list", "userIds", "executorIds"}) {
                if (node.hasNonNull(field)) {
                    return truncate(node.path(field).asText(), 60);
                }
            }
        } catch (IOException ignored) {
            // 非法 JSON 忽略
        }
        return null;
    }

    /** URL 中的 access_token 查询参数脱敏 */
    private String maskToken(String url) {
        return TOKEN_QUERY_PARAM.matcher(url).replaceAll("$1***");
    }

    private String truncate(String value) {
        return truncate(value, MAX_BODY_LOG_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...(truncated)";
    }

    private String bodyAsString(ResponseBody body) throws IOException {
        return body == null ? null : body.string();
    }

    private RequestBody jsonBody(Object body) {
        try {
            return RequestBody.create(mapper.writeValueAsString(body), JSON);
        } catch (IOException e) {
            throw new ApiException("请求序列化失败: " + e.getMessage(), e);
        }
    }

    private String requestBodyString(Request request) {
        RequestBody body = request.body();
        if (body == null) {
            return null;
        }
        try (Buffer buffer = new Buffer()) {
            body.writeTo(buffer);
            return buffer.readUtf8();
        } catch (IOException e) {
            return null;
        }
    }
}
