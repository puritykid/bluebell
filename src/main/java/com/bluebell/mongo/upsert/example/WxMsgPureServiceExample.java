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

    /** 批量新增：uniqueId 已存在则忽略 */
    public void insertMainOnly(List<com.bluebell.mongo.model.WxMsgMain> mainList) {
        reflectiveMongoBulkHelper.bulkInsertByEntity(
                com.bluebell.mongo.model.WxMsgMain.class, mainList, false);
    }

    /** 批量修改：仅更新已存在文档 */
    public void updateLatestOnly(List<com.bluebell.mongo.model.WxMsgLatest> list) {
        reflectiveMongoBulkHelper.bulkUpdateByEntity(
                com.bluebell.mongo.model.WxMsgLatest.class, list, false);
    }

    /** 批量 upsert：不存在插入、存在修改 */
    public void upsertLatest(List<com.bluebell.mongo.model.WxMsgLatest> list) {
        reflectiveMongoBulkHelper.bulkUpsertByEntity(
                com.bluebell.mongo.model.WxMsgLatest.class, list, false);
    }
}
