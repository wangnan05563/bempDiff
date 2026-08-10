/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.alibaba.fastjson.JSONObject
 *  com.hundsun.bemp.adapter.client.dto.bs.corpstdbill.EndorseDto
 *  com.hundsun.bemp.adapter.client.dto.bs.corpstdbill.QryEndorseDto
 *  com.hundsun.bemp.adapter.client.service.bs.POBS010001Service
 *  com.hundsun.bemp.fw.common.pojo.BasePageRequest
 *  com.hundsun.bemp.fw.common.pojo.PageInfo
 *  com.hundsun.bemp.fw.common.pojo.ResultData
 *  com.hundsun.jrescloud.rpc.annotation.CloudComponent
 *  org.apache.commons.lang3.StringUtils
 */
package com.hundsun.bemp.adapter.stdsp4.converter.client.impl;

import com.alibaba.fastjson.JSONObject;
import com.hundsun.bemp.adapter.client.dto.bs.corpstdbill.EndorseDto;
import com.hundsun.bemp.adapter.client.dto.bs.corpstdbill.QryEndorseDto;
import com.hundsun.bemp.adapter.client.service.bs.POBS010001Service;
import com.hundsun.bemp.adapter.stdsp4.wsclient.AbstractServiceInvoker;
import com.hundsun.bemp.adapter.stdsp4.wsclient.dto.Request;
import com.hundsun.bemp.adapter.stdsp4.wsclient.dto.ResponseDTO;
import com.hundsun.bemp.adapter.stdsp4.wsclient.ebank._EBANK30600533.MsgbillsDto;
import com.hundsun.bemp.adapter.stdsp4.wsclient.ebank._EBANK30600533.RequestEBANK30600533BusiDTO;
import com.hundsun.bemp.adapter.stdsp4.wsclient.ebank._EBANK30600533.ResponseEBANK30600533BusiDTO;
import com.hundsun.bemp.fw.common.pojo.BasePageRequest;
import com.hundsun.bemp.fw.common.pojo.PageInfo;
import com.hundsun.bemp.fw.common.pojo.ResultData;
import com.hundsun.jrescloud.rpc.annotation.CloudComponent;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

@CloudComponent
public class POBS010001ServieImpl
extends AbstractServiceInvoker
implements POBS010001Service {
    public ResultData<EndorseDto> queryWaitSignBillPage(BasePageRequest<QryEndorseDto> signRequest) {
        ResultData resultData = new ResultData();
        PageInfo pageInfo = signRequest.getPageInfo();
        QryEndorseDto requestDto = (QryEndorseDto)signRequest.getRequestDto();
        RequestEBANK30600533BusiDTO eaiRequestDTO = this.buildEaiRequestDTO(pageInfo, requestDto);
        Request req = this.buildReq(eaiRequestDTO);
        this.logger.info("EBANK30600533查询待签收票据请求信息：" + JSONObject.toJSONString((Object)req));
        ResponseDTO responseDTO = this.sendRequest(req);
        this.logger.info("EBANK30600533查询待签收票据响应信息：" + JSONObject.toJSONString((Object)responseDTO));
        ArrayList<EndorseDto> list = new ArrayList<EndorseDto>();
        if (responseDTO.isError()) {
            resultData.setPageInfo(pageInfo);
            this.logger.error("调用BBSPEBANK30600533失败：错误码[" + responseDTO.getErrorCode() + "]，错误信息：" + responseDTO.getErrorMesg());
        } else {
            ResponseEBANK30600533BusiDTO eaiResponseDTO = this.getEaiResponseDTO(responseDTO);
            if (StringUtils.isBlank((CharSequence)eaiResponseDTO.getCode()) || "000000".equals(eaiResponseDTO.getCode())) {
                List<MsgbillsDto> msgbillsDtos = eaiResponseDTO.getMsgbillsDtos();
                if (msgbillsDtos != null && msgbillsDtos.size() > 0) {
                    for (MsgbillsDto eaiBillInfoDTO : msgbillsDtos) {
                        list.add(this.copyEaiBillInfoDTO2EndorseDto(eaiBillInfoDTO));
                    }
                }
                pageInfo.setCount((long)eaiResponseDTO.getTotalRows().intValue());
                pageInfo.setPageCount(eaiResponseDTO.getTotalPages().intValue());
            } else {
                resultData.setPageInfo(pageInfo);
                this.logger.error("调用BBSPEBANK30600533失败：错误码[" + eaiResponseDTO.getCode() + "]，错误信息：" + eaiResponseDTO.getMsg());
            }
        }
        resultData.setList(list);
        resultData.setPageInfo(pageInfo);
        return resultData;
    }

    private RequestEBANK30600533BusiDTO buildEaiRequestDTO(PageInfo pageInfo, QryEndorseDto requestDto) {
        RequestEBANK30600533BusiDTO eaiRequestDTO = new RequestEBANK30600533BusiDTO();
        eaiRequestDTO.setPageSize(pageInfo.getPageSize());
        eaiRequestDTO.setCurrentPage(pageInfo.getPageNo());
        eaiRequestDTO.setApplicantAcctNo(requestDto.getEcdsAcctNo());
        if (StringUtils.isNotBlank((CharSequence)requestDto.getCustAcctNo())) {
            eaiRequestDTO.setAcctNo(requestDto.getCustAcctNo().trim());
        }
        if (StringUtils.isNotBlank((CharSequence)requestDto.getCustName())) {
            eaiRequestDTO.setCustName(requestDto.getCustName().trim());
        }
        if (StringUtils.isNotBlank((CharSequence)requestDto.getBillNo())) {
            eaiRequestDTO.setBillNo(requestDto.getBillNo().trim());
        }
        if (StringUtils.isNotBlank((CharSequence)requestDto.getBillType())) {
            if ("AC01".equals(requestDto.getBillType().trim())) {
                eaiRequestDTO.setBillType("1");
            } else if ("AC02".equals(requestDto.getBillType().trim())) {
                eaiRequestDTO.setBillType("2");
            }
        }
        if (requestDto.getMaxRemitDt() != null) {
            eaiRequestDTO.setMaxAcptDt(requestDto.getMaxRemitDt().toString());
        }
        if (requestDto.getMinRemitDt() != null) {
            eaiRequestDTO.setMinAcptDt(requestDto.getMinRemitDt().toString());
        }
        if (requestDto.getMaxDueDt() != null) {
            eaiRequestDTO.setMaxDueDt(requestDto.getMaxDueDt().toString());
        }
        if (requestDto.getMinDueDt() != null) {
            eaiRequestDTO.setMinDueDt(requestDto.getMinDueDt().toString());
        }
        if (requestDto.getMaxBillMoney() != null) {
            eaiRequestDTO.setMaxBillMoney(requestDto.getMaxBillMoney());
        }
        if (requestDto.getMinBillMoney() != null) {
            eaiRequestDTO.setMinBillMoney(requestDto.getMinBillMoney());
        }
        return eaiRequestDTO;
    }

    private EndorseDto copyEaiBillInfoDTO2EndorseDto(MsgbillsDto eaiBillInfoDTO) {
        EndorseDto endorseDto = null;
        if (eaiBillInfoDTO != null) {
            endorseDto = new EndorseDto();
            endorseDto.setId(eaiBillInfoDTO.getBillId());
            endorseDto.setBillId(eaiBillInfoDTO.getBillId());
            endorseDto.setBillNo(eaiBillInfoDTO.getBillNo());
            if (StringUtils.isNotBlank((CharSequence)eaiBillInfoDTO.getBillType())) {
                endorseDto.setBillType("1".equals(eaiBillInfoDTO.getBillType()) ? "AC01" : "AC02");
            }
            endorseDto.setBillClass("ME02");
            if (eaiBillInfoDTO.getAcptDt() != null) {
                endorseDto.setRemitDt(new Integer(eaiBillInfoDTO.getAcptDt()));
            }
            if (eaiBillInfoDTO.getAcptDt() != null) {
                endorseDto.setAcptDt(new Integer(eaiBillInfoDTO.getAcptDt()));
            }
            if (eaiBillInfoDTO.getDueDt() != null) {
                endorseDto.setDueDt(new Integer(eaiBillInfoDTO.getDueDt()));
            }
            endorseDto.setBillMoney(eaiBillInfoDTO.getBillMoney());
            endorseDto.setDrwrName(eaiBillInfoDTO.getRemitter());
            endorseDto.setDrwrAcctNo(eaiBillInfoDTO.getRemitterAcctNo());
            endorseDto.setDrwrBankNo(eaiBillInfoDTO.getRemitterBankNo());
            endorseDto.setDrwrBankName(eaiBillInfoDTO.getRemitterBankName());
            endorseDto.setDrwrOrgCode(eaiBillInfoDTO.getRemitterOrgCode());
            endorseDto.setPyeeName(eaiBillInfoDTO.getPayee());
            endorseDto.setPyeeAcctNo(eaiBillInfoDTO.getPayeeAcctNo());
            endorseDto.setPyeeBankName(eaiBillInfoDTO.getPayeeBankName());
            endorseDto.setPyeeBankNo(eaiBillInfoDTO.getPayeeBankNo());
            endorseDto.setAcptName(eaiBillInfoDTO.getAcceptor());
            endorseDto.setAcptAcctNo(eaiBillInfoDTO.getAcceptorAcctNo());
            endorseDto.setAcptOrgCode(eaiBillInfoDTO.getAcceptorOrgNo());
            endorseDto.setAcptBankName(eaiBillInfoDTO.getAcceptorBankName());
            endorseDto.setAcptBankNo(eaiBillInfoDTO.getAcceptorBankNo());
            endorseDto.setAcptGuarntrName(eaiBillInfoDTO.getAcceptorAssu());
            endorseDto.setAcptGuarntrOrgCode(eaiBillInfoDTO.getAcceptorAssuOrgno());
            endorseDto.setAcptGuarntrAcctNo(eaiBillInfoDTO.getAcceptorAssuAcct());
            endorseDto.setAcptGuarntrBankNo(eaiBillInfoDTO.getAcceptorAssuAcctBankNo());
            endorseDto.setSignFlag(eaiBillInfoDTO.getForbidFlag());
            endorseDto.setCustAcctNo(eaiBillInfoDTO.getAcctNo());
            endorseDto.setCustName(eaiBillInfoDTO.getName());
            endorseDto.setCustBankNo(eaiBillInfoDTO.getBankNo());
            endorseDto.setCustBankName(eaiBillInfoDTO.getBankName());
            endorseDto.setCustOrgCode(eaiBillInfoDTO.getOrgNo());
            endorseDto.setEndrsmtApplDt(new Integer(eaiBillInfoDTO.getApplyDt()));
        }
        return endorseDto;
    }

    public ResponseDTO sendRequest(Request req) {
        ResponseDTO responseDTO = this.invoke(req);
        return responseDTO;
    }

    private Request buildReq(RequestEBANK30600533BusiDTO eaiRequestDTO) {
        Request req = new Request();
        req.setServiceId("EBANK30600533");
        req.setServiceParam("eaiRequestDto", eaiRequestDTO);
        req.setServiceReturnName("eaiResponseDto");
        req.setServiceReturnClass(ResponseEBANK30600533BusiDTO.class);
        return req;
    }

    private ResponseEBANK30600533BusiDTO getEaiResponseDTO(ResponseDTO responseDTO) {
        ResponseEBANK30600533BusiDTO eaiResponseDTO = (ResponseEBANK30600533BusiDTO)responseDTO.getData();
        return eaiResponseDTO;
    }
}
