package com.yuncang.web.controller;

import com.yuncang.biz.entity.AppUser;
import com.yuncang.biz.service.UserService;
import com.yuncang.common.ApiResponse;
import com.yuncang.web.Responses;
import com.yuncang.web.dto.LoginDTO;
import com.yuncang.web.security.BaseContext;
import com.yuncang.web.security.JwtService;
import com.yuncang.web.vo.LoginVO;
import com.yuncang.web.vo.UserVO;
import com.yuncang.web.vo.Views;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "认证与用户")
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @Operation(summary = "用户名密码登录")
    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        AppUser user = userService.authenticate(dto.username(), dto.password());
        JwtService.IssuedToken issued = jwtService.issue(user);
        return Responses.ok(new LoginVO(issued.token(), OffsetDateTime.ofInstant(issued.expiresAt(), ZoneOffset.UTC), Views.of(user)));
    }

    @Operation(summary = "获取当前用户", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public ApiResponse<UserVO> me() {
        return Responses.ok(Views.of(BaseContext.getUser()));
    }
}
