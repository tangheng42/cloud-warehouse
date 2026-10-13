package com.yuncang.biz.mapper;

import com.yuncang.biz.entity.Warehouse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WarehouseMapper {
    Warehouse findById(@Param("id") Long id);
    Warehouse findForUpdate(@Param("id") Long id);
    List<Warehouse> list(@Param("code") String code, @Param("name") String name, @Param("enabled") Boolean enabled);
    int insert(Warehouse warehouse);
    int update(Warehouse warehouse);
    int setEnabled(@Param("id") Long id, @Param("enabled") boolean enabled);
}
