package com.bluebell.mongo.upsert;

import com.bluebell.mongo.bulk.MergeUtils;
import com.bluebell.mongo.model.WxMsgDTO;
import com.bluebell.mongo.model.WxMsgLatest;
import com.bluebell.mongo.model.WxMsgMain;
import com.bluebell.mongo.model.WxMsgType;
import com.mongodb.bulk.BulkWriteResult;

import java.util.List;

/**
 * 微信四表批量写：实体注解 + 反射（纯 bulk，不含数据权限）。
 */
public class ReflectiveWxMsgBatchWriter {

    private final ReflectiveMongoBulkHelper reflectiveBulk;

    public ReflectiveWxMsgBatchWriter(ReflectiveMongoBulkHelper reflectiveBulk) {
        this.reflectiveBulk = reflectiveBulk;
    }

    public void writeBatchInTransaction(List<WxMsgDTO> batch) {
        reflectiveBulk.getMongoBulkHelper().executeInTransaction(
                () -> writeMain(batch),
                () -> writeLatest(batch),
                () -> writeWxLastTime(batch),
                () -> writeMsgType(batch)
        );
    }

    public BulkWriteResult writeMain(List<WxMsgDTO> batch) {
        return reflectiveBulk.bulkUpsertByEntity(
                WxMsgMain.class, WxMsgEntityMapper.toMainList(batch), inTransaction());
    }

    public BulkWriteResult writeLatest(List<WxMsgDTO> batch) {
        return reflectiveBulk.bulkUpsertMergedByEntity(
                WxMsgLatest.class,
                WxMsgEntityMapper.toLatestList(batch),
                m -> m.getWxId() + "|" + m.getChatType() + "|" + m.getTalker(),
                MergeUtils.keepNewer(WxMsgLatest::getMsgTime),
                inTransaction()
        );
    }

    public BulkWriteResult writeWxLastTime(List<WxMsgDTO> batch) {
        return reflectiveBulk.bulkUpsertByEntity(
                com.bluebell.mongo.model.WxLastTime.class,
                WxMsgEntityMapper.toLastTimeList(batch),
                inTransaction()
        );
    }

    public BulkWriteResult writeMsgType(List<WxMsgDTO> batch) {
        return reflectiveBulk.bulkUpsertByEntity(
                WxMsgType.class, WxMsgEntityMapper.toTypeList(batch), inTransaction());
    }

    private boolean inTransaction() {
        return reflectiveBulk.getMongoBulkHelper().getTransactionTemplate() != null;
    }
}
