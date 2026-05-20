package com.bluebell.mongo.model;

import com.bluebell.mongo.upsert.UpsertEntity;
import com.bluebell.mongo.upsert.UpsertKey;
import com.bluebell.mongo.upsert.UpsertStrategy;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document("wx_msg_main")
@UpsertEntity(strategy = UpsertStrategy.INSERT_ONLY, generateIdOnInsert = true)
public class WxMsgMain {
    @Id
    private Long id;
    @UpsertKey
    @Indexed(unique = true)
    private String uniqueId;
    @Indexed
    private String organizationId;
    private String wxId;
    private String chatType;
    private String talker;
    private String type;
    private Object content;
    private LocalDateTime msgTime;
}
