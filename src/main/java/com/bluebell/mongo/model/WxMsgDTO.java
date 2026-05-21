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
    /** 消息类型：业务可能是 Integer 或 String */
    private Object type;
    private Object content;
    private LocalDateTime msgTime;
}
