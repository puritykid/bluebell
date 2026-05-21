package com.bluebell.mongo.model;

import com.bluebell.mongo.upsert.UpsertEntity;
import com.bluebell.mongo.upsert.UpsertKey;
import com.bluebell.mongo.upsert.UpsertStrategy;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 按 wxId+chatType+talker：不存在插入（雪花 _id），存在则全量更新。
 */
@Data
@Document("wx_msg_latest")
@CompoundIndex(name = "uk_session", def = "{'wxId':1,'chatType':1,'talker':1}", unique = true)
@UpsertEntity(strategy = UpsertStrategy.FULL_BY_KEY, generateIdOnInsert = true)
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
