package com.yuncang.biz.mapper;

import com.yuncang.biz.entity.Sku;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SkuMapper {
    Sku findById(@Param("id") Long id);
    Sku findForUpdate(@Param("id") Long id);
    List<Sku> list(@Param("code") String code, @Param("name") String name,
                   @Param("categoryId") Long categoryId, @Param("enabled") Boolean enabled);
    long countEnabledByCategory(@Param("categoryId") Long categoryId);
    int insert(Sku sku);
    int update(Sku sku);
    int setEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);
}
