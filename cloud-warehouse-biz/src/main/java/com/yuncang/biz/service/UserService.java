package com.yuncang.biz.service;

import com.yuncang.biz.entity.AppUser;
import com.yuncang.biz.mapper.UserMapper;
import com.yuncang.common.BusinessException;
import com.yuncang.common.ErrorCode;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 验证账号凭据及启用状态，为登录和请求鉴权提供当前用户。
 */
@Service
public class UserService {
    private final UserMapper mapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(UserMapper mapper) { this.mapper = mapper; }

    /**
     * 验证密码并拒绝停用账号；用户名不存在与密码错误使用相同错误提示。
     * @return 已通过验证的用户实体，包含密码哈希，仅供内部处理，不应直接序列化给客户端
     */
    public AppUser authenticate(String username, String password) {
        AppUser user = mapper.findByUsername(username);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "用户名或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(ErrorCode.USER_DISABLED, "用户已停用");
        }
        return user;
    }

    public AppUser activeById(Long id) {
        AppUser user = mapper.findById(id);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "登录已失效");
        }
        return user;
    }
}
