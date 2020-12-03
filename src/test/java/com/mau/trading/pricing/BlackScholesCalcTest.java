package com.mau.trading.pricing;

import com.mau.trading.utility.Util;
import org.junit.Assert;
import org.junit.Test;

public class BlackScholesCalcTest {

    @Test
    public void testBlackScholes() {
        double price = BlackScholesCalc.callPriceBSM(3510.45, 3500, 0.0219178082191781, 0.005, 0.25296);
        Assert.assertEquals(57.96, Util.roundVal(price), 0.0);
    }
}
