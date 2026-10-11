package com.pfm.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Helpers for consistent monetary JSON output: two decimals, and a bare {@code 0} for zero. */
public final class Money {
    private Money() { }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static BigDecimal fmt(BigDecimal v) {
        BigDecimal n = nz(v);
        return n.signum() == 0 ? BigDecimal.ZERO : n.setScale(2, RoundingMode.HALF_UP);
    }
}
