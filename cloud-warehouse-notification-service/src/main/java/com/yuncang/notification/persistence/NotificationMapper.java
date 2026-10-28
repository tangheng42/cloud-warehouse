package com.yuncang.notification.persistence;

import com.yuncang.common.notification.NotificationView;
import java.util.List;
import org.apache.ibatis.annotations.*;
import org.springframework.context.annotation.Profile;

@Mapper
@Profile("notification-mysql")
public interface NotificationMapper {
    @Select("SELECT COUNT(*) FROM notification")
    long count();

    @Select("""
        SELECT id,event_id,warehouse_id,sku_id,alert_id,type,title,content,status,created_at
        FROM notification ORDER BY created_at DESC,id DESC LIMIT #{size} OFFSET #{offset}
        """)
    List<NotificationView> page(@Param("size") int size, @Param("offset") long offset);

    @Insert("""
        INSERT INTO notification(event_id,warehouse_id,sku_id,alert_id,type,title,content)
        VALUES(#{eventId},#{warehouseId},#{skuId},#{alertId},#{type},#{title},#{content})
        """)
    int insert(@Param("eventId") String eventId, @Param("warehouseId") long warehouseId,
            @Param("skuId") long skuId, @Param("alertId") long alertId,
            @Param("type") String type, @Param("title") String title, @Param("content") String content);
}
