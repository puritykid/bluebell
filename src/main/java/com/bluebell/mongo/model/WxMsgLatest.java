package com.bluebell.mongo.model;

import com.bluebell.mongo.upsert.UpsertEntity;
import com.bluebell.mongo.upsert.UpsertKey;
import com.bluebell.mongo.upsert.UpsertStrategy;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document("wx_msg_latest")
@CompoundIndex(name = "uk_session", def = "{'wxId':1,'chatType':1,'talker':1}", unique = true)
@UpsertEntity(
        strategy = UpsertStrategy.UPSERT_IF_NEWER,
        timeField = "msgTime",
        generateIdOnInsert = true
)
public class WxMsgLatest {
    @Id
    private Long id;
    private String organizationId;
    @UpsertKey
    private String wxId;
    @UpsertKey
    private String chatType;
    @UpsertKey
    private String talker;
    private String uniqueId;
    private String type;
    private Object content;
    private LocalDateTime msgTime;
}
