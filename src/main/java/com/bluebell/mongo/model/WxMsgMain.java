package com.bluebell.mongo.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("wx_msg_main")
public class WxMsgMain {
    @Id
    private Long id;
    @Indexed(unique = true)
    private String uniqueId;
    @Indexed
    private String organizationId;
    private String wxId;
    private String chatType;
    private String talker;
    private String type;
    private Object content;
    private Long msgTime;
}
