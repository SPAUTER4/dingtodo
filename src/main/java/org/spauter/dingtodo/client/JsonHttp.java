package org.spauter.dingtodo.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.spauter.dingtodo.common.ApiException;
import org.springframework.stereotype.Component;

/**
 * 钉钉 HTTP 调用统一封装。
 *
 * <p>旧版 oapi 接口按 errcode 判定成功；新版 v1.0 REST 按 HTTP 状态码判定。
 * 统一使用 OkHttp3 + Jackson，不再依赖旧 SDK 的序列化。
 */
@Component
public class JsonHttp {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String TOKEN_HEADER = "x-acs-dingtalk-access-token";

    private final OkHttpClient ok;
    private final ObjectMapper mapper;

    public JsonHttp(OkHttpClient ok, ObjectMapper mapper) {
        this.ok = ok;
        this.mapper = mapper;
    }

    /** oapi GET（如 gettoken），校验 errcode */
    public JsonNode getOapi(String url) {
        return executeOapi(new Request.Builder().url(url).get().build());
    }

    /** oapi POST JSON（url 需已拼好 access_token），校验 errcode */
    public JsonNode postOapi(String url, Object body) {
        return executeOapi(new Request.Builder().url(url).post(jsonBody(body)).build());
    }

    /** v1.0 REST POST JSON，HTTP 非 2xx 抛 ApiException */
    public JsonNode postApiV1(String url, String token, Object body) {
        Request request = new Request.Builder()
                .url(url)
                .header(TOKEN_HEADER, token)
                .post(jsonBody(body))
                .build();
        return executeV1(request);
    }

    /** v1.0 REST DELETE，HTTP 非 2xx 抛 ApiException */
    public JsonNode deleteApiV1(String url, String token) {
        Request request = new Request.Builder()
                .url(url)
                .header(TOKEN_HEADER, token)
                .delete()
                .build();
        return executeV1(request);
    }

    public com.fasterxml.jackson.databind.node.ObjectNode newObject() {
        return mapper.createObjectNode();
    }

    private JsonNode executeOapi(Request request) {
        JsonNode resp = execute(request);
        int errcode = resp.path("errcode").asInt(-1);
        if (errcode != 0) {
            throw new ApiException(String.valueOf(errcode),
                    resp.path("errmsg").asText("钉钉 oapi 调用失败"));
        }
        return resp;
    }

    private JsonNode executeV1(Request request) {
        return execute(request);
    }

    private JsonNode execute(Request request) {
        try (Response response = ok.newCall(request).execute()) {
            String body = bodyAsString(response.body());
            JsonNode resp = mapper.readTree(body == null || body.isEmpty() ? "{}" : body);
            if (!response.isSuccessful()) {
                // v1.0 错误响应形如 {"code": "xx", "message": "xx"}
                String code = resp.path("code").asText(String.valueOf(response.code()));
                String message = resp.path("message").asText("HTTP " + response.code());
                throw new ApiException(code, message);
            }
            return resp;
        } catch (IOException e) {
            throw new ApiException("网络请求失败: " + e.getMessage(), e);
        }
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
}
