package com.yuncang.web.vo;

import java.time.OffsetDateTime;

public record LoginVO(String token, OffsetDateTime expiresAt, UserVO user) {}
