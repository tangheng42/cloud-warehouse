package com.yuncang.biz.mapper;

import com.yuncang.biz.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    AppUser findByUsername(@Param("username") String username);
    AppUser findById(@Param("id") Long id);
}
