package com.bluebell.mongo.bulk.example;

import com.bluebell.mongo.bulk.MongoBulkHelper;
import com.bluebell.mongo.bulk.WxMsgBatchWriter;
import com.bluebell.mongo.model.WxMsgDTO;
import com.bluebell.mongo.model.WxMsgMain;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 纯批量写入示例。
 */
@Service
@RequiredArgsConstructor
public class WxMsgPureServiceExample {

    private final WxMsgBatchWriter wxMsgBatchWriter;
    private final MongoBulkHelper mongoBulkHelper;

    public void saveWxBatch(List<WxMsgDTO> list) {
        wxMsgBatchWriter.writeBatchInTransaction(list);
    }

    public void insertOrders(List<WxMsgMain> list) {
        mongoBulkHelper.batchInsertOnly(
                WxMsgMain.class,
                list,
                m -> Query.query(Criteria.where("uniqueId").is(m.getUniqueId())),
                m -> new Update()
                        .setOnInsert("uniqueId", m.getUniqueId())
                        .setOnInsert("wxId", m.getWxId())
                        .setOnInsert("msgTime", m.getMsgTime()),
                false);
    }

    public void updateOrders(List<WxMsgMain> list) {
        mongoBulkHelper.batchUpdate(
                WxMsgMain.class,
                list,
                m -> Query.query(Criteria.where("uniqueId").is(m.getUniqueId())),
                m -> new Update().set("content", m.getContent()).set("msgTime", m.getMsgTime()),
                false);
    }

    public void upsertOrders(List<WxMsgMain> list) {
        mongoBulkHelper.batchUpsert(
                WxMsgMain.class,
                list,
                m -> Query.query(Criteria.where("uniqueId").is(m.getUniqueId())),
                m -> new Update()
                        .set("content", m.getContent())
                        .set("msgTime", m.getMsgTime())
                        .setOnInsert("uniqueId", m.getUniqueId())
                        .setOnInsert("wxId", m.getWxId()),
                false);
    }
}
