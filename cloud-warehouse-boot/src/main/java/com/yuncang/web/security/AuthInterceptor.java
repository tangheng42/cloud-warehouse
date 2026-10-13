package com.yuncang.web.security;

import com.yuncang.biz.entity.AppUser;
import com.yuncang.biz.service.UserService;
import com.yuncang.common.BusinessException;
import com.yuncang.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final JwtService jwtService;
    private final UserService userService;

    public AuthInterceptor(JwtService jwtService, UserService userService) {
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "请先登录");
        }
        JwtService.Identity identity = jwtService.parse(header.substring(7));
        AppUser user = userService.activeById(identity.userId());
        if (!user.getRole().equals(identity.role())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "用户角色已变化，请重新登录");
        }
        if (handler instanceof HandlerMethod method &&
                (method.hasMethodAnnotation(RequireAdmin.class) ||
                 method.getBeanType().isAnnotationPresent(RequireAdmin.class)) &&
                !"ADMIN".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权执行该操作");
        }
        if (handler instanceof HandlerMethod method) {
            RequireRoles roles = method.getMethodAnnotation(RequireRoles.class);
            if (roles == null) roles = method.getBeanType().getAnnotation(RequireRoles.class);
            if (roles != null && Arrays.stream(roles.value()).noneMatch(user.getRole()::equals)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "无权执行该操作");
            }
        }
        BaseContext.setUser(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        BaseContext.clear();
    }
}
