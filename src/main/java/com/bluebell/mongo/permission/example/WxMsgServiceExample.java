package com.bluebell.mongo.permission.example;

import com.bluebell.mongo.model.WxMsgDTO;
import com.bluebell.mongo.permission.DataPermission;
import com.bluebell.mongo.permission.DataPermissionWxMsgBatchWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WxMsgServiceExample {

    private final DataPermissionWxMsgBatchWriter batchWriter;

    @DataPermission
    public void saveBatch(List<WxMsgDTO> list) {
        batchWriter.writeBatchInTransaction(list);
    }

    @DataPermission(field = "organizationId")
    public void saveBatchCustomField(List<WxMsgDTO> list) {
        batchWriter.writeBatchInTransaction(list);
    }
}
