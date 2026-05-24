package com.bluebell.mongo.upsert;

import com.bluebell.mongo.bulk.MergeUtils;
import com.bluebell.mongo.model.WxMsgDTO;
import com.bluebell.mongo.support.EpochTimeUtils;
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
        List<WxMsgDTO> writable = filterWritable(batch);
        reflectiveBulk.getMongoBulkHelper().executeInTransaction(
                () -> writeMain(writable),
                () -> writeLatest(writable),
                () -> writeWxLastTime(writable),
                () -> writeMsgType(writable)
        );
    }

    /** 未来时间不写入；msgTime 为 null 仍允许 upsert */
    public static List<WxMsgDTO> filterWritable(List<WxMsgDTO> batch) {
        if (batch == null || batch.isEmpty()) {
            return List.of();
        }
        return batch.stream().filter(d -> EpochTimeUtils.isWritable(d.getMsgTime())).toList();
    }

    public BulkWriteResult writeMain(List<WxMsgDTO> batch) {
        return reflectiveBulk.bulkUpsertByEntity(
                WxMsgMain.class, WxMsgEntityMapper.toMainList(filterWritable(batch)), inTransaction());
    }

    public BulkWriteResult writeLatest(List<WxMsgDTO> batch) {
        return reflectiveBulk.bulkUpsertMergedByEntity(
                WxMsgLatest.class,
                WxMsgEntityMapper.toLatestList(filterWritable(batch)),
                m -> m.getWxId() + "|" + m.getChatType() + "|" + m.getTalker(),
                MergeUtils.keepNewer(WxMsgLatest::getMsgTime),
                inTransaction()
        );
    }

    public BulkWriteResult writeWxLastTime(List<WxMsgDTO> batch) {
        return reflectiveBulk.bulkUpsertByEntity(
                com.bluebell.mongo.model.WxLastTime.class,
                WxMsgEntityMapper.toLastTimeList(filterWritable(batch)),
                inTransaction()
        );
    }

    public BulkWriteResult writeMsgType(List<WxMsgDTO> batch) {
        return reflectiveBulk.bulkUpsertByEntity(
                WxMsgType.class, WxMsgEntityMapper.toTypeList(filterWritable(batch)), inTransaction());
    }

    private boolean inTransaction() {
        return reflectiveBulk.getMongoBulkHelper().getTransactionTemplate() != null;
    }
}
