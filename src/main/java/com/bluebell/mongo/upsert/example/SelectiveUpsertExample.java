package com.bluebell.mongo.upsert.example;

import com.bluebell.mongo.upsert.UpsertEntity;
import com.bluebell.mongo.upsert.UpsertKey;
import com.bluebell.mongo.upsert.UpsertOnUpdate;
import com.bluebell.mongo.upsert.UpsertStrategy;
import lombok.Data;
import org.springframework.data.annotation.Id;

/**
 * 示例：不存在新增（雪花 id），存在只更新指定字段。
 */
@Data
@UpsertEntity(strategy = UpsertStrategy.UPSERT_SELECTIVE, generateIdOnInsert = true)
public class SelectiveUpsertExample {

    @Id
    private Long id;

    @UpsertKey
    private String orderNo;

    /** 仅首次插入写入 */
    private String createBy;
    private Long createTime;

    /** 存在时也会更新 */
    @UpsertOnUpdate
    private String status;
    @UpsertOnUpdate
    private Long updateTime;
}
