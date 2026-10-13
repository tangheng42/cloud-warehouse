package com.yuncang.biz.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActiveOrderMapper {
    @Select("""
        SELECT (SELECT COUNT(*) FROM inbound_order WHERE warehouse_id = #{id} AND status = 'PENDING')
             + (SELECT COUNT(*) FROM outbound_order WHERE warehouse_id = #{id} AND status IN ('CREATED','RESERVED'))
             + (SELECT COUNT(*) FROM transfer_order WHERE (source_warehouse_id = #{id} OR target_warehouse_id = #{id})
                    AND status IN ('CREATED','RESERVED','IN_TRANSIT'))
             + (SELECT COUNT(*) FROM stocktake_order WHERE warehouse_id = #{id} AND status = 'DRAFT')
        """)
    long countForWarehouse(@Param("id") Long id);

    @Select("""
        SELECT (SELECT COUNT(*) FROM inbound_order_item i JOIN inbound_order o ON o.id = i.order_id
                    WHERE i.sku_id = #{id} AND o.status = 'PENDING')
             + (SELECT COUNT(*) FROM outbound_order_item i JOIN outbound_order o ON o.id = i.order_id
                    WHERE i.sku_id = #{id} AND o.status IN ('CREATED','RESERVED'))
             + (SELECT COUNT(*) FROM transfer_order_item i JOIN transfer_order o ON o.id = i.order_id
                    WHERE i.sku_id = #{id} AND o.status IN ('CREATED','RESERVED','IN_TRANSIT'))
             + (SELECT COUNT(*) FROM stocktake_order_item i JOIN stocktake_order o ON o.id = i.order_id
                    WHERE i.sku_id = #{id} AND o.status = 'DRAFT')
        """)
    long countForSku(@Param("id") Long id);
}
