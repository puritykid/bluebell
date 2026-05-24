package com.bluebell.mongo.model;

import com.bluebell.mongo.upsert.UpsertEntity;
import com.bluebell.mongo.upsert.UpsertKey;
import com.bluebell.mongo.upsert.UpsertOnUpdate;
import com.bluebell.mongo.upsert.UpsertStrategy;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 不存在：插入（雪花 _id）；
 * 存在：仅更新 @UpsertOnUpdate 字段；msgTime 更小则不更新。
 */
@Data
@Document("wx_msg_latest")
@CompoundIndex(name = "uk_session", def = "{'wxId':1,'chatType':1,'talker':1}", unique = true)
@UpsertEntity(
        strategy = UpsertStrategy.UPSERT_SELECTIVE,
        timeField = "msgTime",
        rejectFutureTime = true,
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

    @UpsertOnUpdate
    private String uniqueId;
    @UpsertOnUpdate
    private String type;
    @UpsertOnUpdate
    private Object content;
    @UpsertOnUpdate
    private Long msgTime;
}
