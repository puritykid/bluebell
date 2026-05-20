package com.bluebell.mongo.permission.example;

import com.bluebell.mongo.model.WxMsgDTO;
import com.bluebell.mongo.permission.BizOrgId;
import com.bluebell.mongo.permission.DataPermission;
import com.bluebell.mongo.permission.DataPermissionWxMsgBatchWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WxMsgServiceExample {

    private final DataPermissionWxMsgBatchWriter batchWriter;

    /** 仅用户数据权限 */
    @DataPermission
    public void saveBatch(List<WxMsgDTO> list) {
        batchWriter.writeBatchInTransaction(list);
    }

    /** 用户权限 ∩（业务机构 + 子机构） */
    @DataPermission
    public void saveBatch(@BizOrgId String organizationId, List<WxMsgDTO> list) {
        batchWriter.writeBatchInTransaction(list);
    }

    /** 等价：按参数名解析业务机构（需 -parameters） */
    @DataPermission(bizOrgParam = "organizationId")
    public void saveBatchByParamName(String organizationId, List<WxMsgDTO> list) {
        batchWriter.writeBatchInTransaction(list);
    }
}
