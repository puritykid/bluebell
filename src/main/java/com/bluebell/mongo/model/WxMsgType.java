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
@UpsertEntity(strategy = UpsertStrategy.INSERT_ONLY)
public class WxMsgType {
    @Id
    private String id;
    @UpsertKey
    @Indexed(unique = true)
    private String type;
    @UpsertField(mode = FieldUpsertMode.INSERT_ONLY)
    private LocalDateTime createTime;
}
