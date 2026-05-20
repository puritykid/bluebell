package com.bluebell.mongo.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document("wx_msg_type")
public class WxMsgType {
    @Id
    private String id;
    @Indexed(unique = true)
    private String type;
    /** 全局字典可不设组织；若按组织隔离字典则启用 */
    private String organizationId;
    private LocalDateTime createTime;
}
