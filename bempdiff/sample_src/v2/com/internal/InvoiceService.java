package com.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 票据开具服务（v2：新增税率、有效期校验，调整金额上限） */
public class InvoiceService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.99");
    private static final BigDecimal TAX_RATE = new BigDecimal("0.13");

    public String issue(String buyer, BigDecimal amount, List<String> items, BigDecimal taxRate) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("金额必须大于0");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("金额超限");
        }
        BigDecimal rate = taxRate == null ? TAX_RATE : taxRate;
        BigDecimal tax = amount.multiply(rate).setScale(2, BigDecimal.ROUND_HALF_UP);
        StringBuilder sb = new StringBuilder();
        sb.append("INV|").append(buyer).append("|").append(amount.toPlainString());
        sb.append("|TAX|").append(tax.toPlainString());
        for (String it : items) {
            sb.append("|").append(it);
        }
        return sb.toString();
    }

    public boolean validate(String invoiceNo, LocalDate validDate) {
        if (invoiceNo == null || !invoiceNo.startsWith("INV-")) {
            return false;
        }
        return validDate == null || !validDate.isBefore(LocalDate.now());
    }
}
