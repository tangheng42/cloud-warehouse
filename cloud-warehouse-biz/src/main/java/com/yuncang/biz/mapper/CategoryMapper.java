package com.yuncang.biz.mapper;

import com.yuncang.biz.entity.SkuCategory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CategoryMapper {
    SkuCategory findById(@Param("id") Long id);
    SkuCategory findForUpdate(@Param("id") Long id);
    List<SkuCategory> list(@Param("name") String name, @Param("enabled") Boolean enabled);
    int insert(SkuCategory category);
    int update(SkuCategory category);
    int setEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);
}
