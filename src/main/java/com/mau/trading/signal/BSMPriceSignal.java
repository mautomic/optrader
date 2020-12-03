package com.mau.trading.signal;

import com.mau.trading.PortfolioParams;
import com.mau.trading.pricing.BlackScholesCalc;

/**
 * A price {@link EntrySignal} to run the BlackScholes non-div monteCarlo model to determine if price of option
 * is attractive. We are passing a predetermined param to manage risk controls, currently it just checks if
 * the option has greater vol than our min requirement.
 *
 * @author 350rosen
 */
public class BSMPriceSignal extends EntrySignal {

    public BSMPriceSignal() {
        super();
    }

    @Override
    public boolean trigger() {
        double expiry = ((double)option.getDaysToExpiration() / 365);
        double volatility = (option.getVolatility() / 100);
        double riskFreeRate = PortfolioParams.RiskFreeRate.getNum();

        if (option.getDaysToExpiration() > 1 && option.getLast() < BlackScholesCalc.monteCarloEstimation(
                chain.getUnderlyingPrice(), option.getStrikePrice(), expiry, riskFreeRate, volatility)) {
            return option.getVolatility() > PortfolioParams.MinVolatility.getNum();
        }
        return false;
    }
}