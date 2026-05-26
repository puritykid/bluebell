package com.bluebell.mongo.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("wx_msg_type")
public class WxMsgType {
    @Id
    private Long id;
    @Indexed(unique = true)
    private String type;
    private Long createTime;
}
