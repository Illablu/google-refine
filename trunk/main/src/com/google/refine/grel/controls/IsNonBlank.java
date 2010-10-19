package com.google.refine.grel.controls;

import com.google.refine.expr.ExpressionUtils;

public class IsNonBlank extends IsTest {
    @Override
    protected String getDescription() {
        return "Returns whether o is not null and not an empty string";
    }

    @Override
    protected boolean test(Object o) {
        return ExpressionUtils.isNonBlankData(o);
    }
}