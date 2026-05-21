package com.bluebell.mongo.upsert.example;

import com.bluebell.mongo.model.WxMsgDTO;
import com.bluebell.mongo.upsert.ReflectiveMongoBulkHelper;
import com.bluebell.mongo.upsert.ReflectiveWxMsgBatchWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 纯批量写入示例：不要加 @DataPermission，不要注入 DataPermission* 类。
 */
@Service
@RequiredArgsConstructor
public class WxMsgPureServiceExample {

    private final ReflectiveWxMsgBatchWriter wxMsgBatchWriter;
    private final ReflectiveMongoBulkHelper reflectiveMongoBulkHelper;

    /** 微信四表同事务 */
    public void saveBatch(List<WxMsgDTO> list) {
        wxMsgBatchWriter.writeBatchInTransaction(list);
    }

    /** 单表通用写法 */
    public void saveMainOnly(List<com.bluebell.mongo.model.WxMsgMain> mainList) {
        reflectiveMongoBulkHelper.bulkUpsertByEntity(
                com.bluebell.mongo.model.WxMsgMain.class, mainList, false);
    }
}
