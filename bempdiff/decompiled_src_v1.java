/*
 * Decompiled with CFR 0.152.
 */
package com.internal;

import java.math.BigDecimal;
import java.util.List;

public class InvoiceService {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999.99");

    public String issue(String string, BigDecimal bigDecimal, List<String> list) {
        if (bigDecimal == null || bigDecimal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("金额必须大于0");
        }
        if (bigDecimal.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("金额超限");
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("INV|").append(string).append("|").append(bigDecimal.toPlainString());
        for (String string2 : list) {
            stringBuilder.append("|").append(string2);
        }
        return stringBuilder.toString();
    }

    public boolean validate(String string) {
        return string != null && string.startsWith("INV-");
    }
}
