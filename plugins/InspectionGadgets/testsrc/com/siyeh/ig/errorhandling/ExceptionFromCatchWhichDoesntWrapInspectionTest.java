package com.siyeh.ig.errorhandling;

import com.IGInspectionTestCase;

public class ExceptionFromCatchWhichDoesntWrapInspectionTest extends
        IGInspectionTestCase {

    public void test() throws Exception {
        doTest("com/siyeh/igtest/errorhandling/exception_from_catch",
                new ExceptionFromCatchWhichDoesntWrapInspection());
    }
}
