package com.bluebell.mongo.model;

import lombok.Data;

@Data
public class WxMsgDTO {
    private String organizationId;
    private String uniqueId;
    private String wxId;
    private String chatType;
    private String talker;
    private String type;
    private Object content;
    /** epoch 毫秒，与 MongoDB 中 long 一致 */
    private Long msgTime;
}
