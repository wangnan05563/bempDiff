package com.internal;

import java.math.BigDecimal;
import java.util.List;

/** 票据开具服务（v1：基准版本） */
public class InvoiceService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999.99");

    public String issue(String buyer, BigDecimal amount, List<String> items) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("金额必须大于0");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("金额超限");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("INV|").append(buyer).append("|").append(amount.toPlainString());
        for (String it : items) {
            sb.append("|").append(it);
        }
        return sb.toString();
    }

    public boolean validate(String invoiceNo) {
        return invoiceNo != null && invoiceNo.startsWith("INV-");
    }
}
