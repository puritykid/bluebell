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
@Document("wx_last_time")
@UpsertEntity(strategy = UpsertStrategy.UPSERT_IF_NEWER, timeField = "lastMsgTime")
public class WxLastTime {
    @Id
    private String id;
    @UpsertKey
    @Indexed(unique = true)
    private String wxId;
    @Indexed
    private String organizationId;
    private LocalDateTime lastMsgTime;
}
