package com.bluebell.mongo.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WxMsgDTO {
    private String organizationId;
    private String uniqueId;
    private String wxId;
    private String chatType;
    private String talker;
    private String type;
    private Object content;
    private LocalDateTime msgTime;
}
