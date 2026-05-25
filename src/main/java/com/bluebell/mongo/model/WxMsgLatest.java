package com.bluebell.mongo.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("wx_msg_latest")
@CompoundIndex(name = "uk_session", def = "{'wxId':1,'chatType':1,'talker':1}", unique = true)
public class WxMsgLatest {
    @Id
    private Long id;
    private String organizationId;
    private String wxId;
    private String chatType;
    private String talker;
    private String uniqueId;
    private String type;
    private Object content;
    private Long msgTime;
}
