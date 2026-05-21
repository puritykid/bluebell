package com.bluebell.mongo.model;

import com.bluebell.mongo.upsert.UpsertEntity;
import com.bluebell.mongo.upsert.UpsertField;
import com.bluebell.mongo.upsert.UpsertKey;
import com.bluebell.mongo.upsert.UpsertStrategy;
import com.bluebell.mongo.upsert.FieldUpsertMode;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document("wx_msg_type")
@UpsertEntity(strategy = UpsertStrategy.INSERT_ONLY, generateIdOnInsert = true)
public class WxMsgType {
    @Id
    private Long id;
    /** 字典键：Integer / String 均可，与入库 type 类型一致 */
    @UpsertKey
    @Indexed(unique = true)
    private Object type;
    @UpsertField(mode = FieldUpsertMode.INSERT_ONLY)
    private LocalDateTime createTime;
}
