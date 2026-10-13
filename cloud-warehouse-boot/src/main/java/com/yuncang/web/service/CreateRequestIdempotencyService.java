package com.yuncang.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuncang.common.BusinessException;
import com.yuncang.common.ErrorCode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Optional protocol extension for create endpoints. The unique row and order share one transaction. */
@Service
public class CreateRequestIdempotencyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public CreateRequestIdempotencyService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public <T> T create(String key, long userId, String operation, Object request,
            Supplier<T> creator, ToLongFunction<T> id, Function<Long, T> finder) {
        if (key == null) return creator.get();
        if (key.isBlank() || key.length() > 128 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key 格式不合法");
        }
        String hash = fingerprint(request);
        boolean inserted;
        try {
            jdbc.update("INSERT INTO create_request_idempotency "
                    + "(user_id, operation, idem_key, payload_sha256) VALUES (?, ?, ?, ?)",
                    userId, operation, key, hash);
            inserted = true;
        } catch (DuplicateKeyException duplicate) {
            inserted = false;
        }
        if (inserted) {
            T result = creator.get();
            jdbc.update("UPDATE create_request_idempotency SET biz_id=? "
                    + "WHERE user_id=? AND operation=? AND idem_key=?",
                    id.applyAsLong(result), userId, operation, key);
            return result;
        }
        // A locking read sees the committed winning request even under InnoDB REPEATABLE READ.
        return jdbc.query("SELECT payload_sha256, biz_id FROM create_request_idempotency "
                        + "WHERE user_id=? AND operation=? AND idem_key=? FOR UPDATE",
                rs -> {
                    if (!rs.next()) throw new IllegalStateException("幂等记录不存在");
                    if (!hash.equals(rs.getString(1))) {
                        throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
                    }
                    long bizId = rs.getLong(2);
                    if (rs.wasNull()) throw new IllegalStateException("幂等记录缺少业务单据");
                    return finder.apply(bizId);
                }, userId, operation, key);
    }

    private String fingerprint(Object request) {
        try {
            byte[] bytes = json.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法计算请求摘要", e);
        }
    }
}
