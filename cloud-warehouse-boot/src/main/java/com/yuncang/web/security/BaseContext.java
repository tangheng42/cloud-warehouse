package com.yuncang.web.security;

import com.yuncang.biz.entity.AppUser;

public final class BaseContext {
    private static final ThreadLocal<AppUser> USER = new ThreadLocal<>();
    private BaseContext() {}

    public static void setUser(AppUser user) { USER.set(user); }
    public static AppUser getUser() { return USER.get(); }
    public static void clear() { USER.remove(); }
}
