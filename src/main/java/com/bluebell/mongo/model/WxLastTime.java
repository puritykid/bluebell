package com.bluebell.mongo.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("wx_last_time")
public class WxLastTime {
    @Id
    private Long id;
    @Indexed(unique = true)
    private String wxId;
    @Indexed
    private String organizationId;
    private Long lastMsgTime;
}
