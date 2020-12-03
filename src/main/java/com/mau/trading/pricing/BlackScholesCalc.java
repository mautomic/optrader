package com.mau.trading.pricing;

import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.Random;

/**
 * A pricing estimator using the BlackScholes model. The aim is to produce realistic pricing to determine if entering
 * a position based on the other indicators is rich or cheap vs. the market.
 *
 * This class will only currently look at pricing calls but might aim to work in puts or otherwise at a later date.
 *
 * We use the standard Black Scholes model for price calculation and two variations of the Monte Carlo estimation
 * methods.
 *
 * Calculations and methods are taken from:
 * https://introcs.cs.princeton.edu/java/22library/BlackScholes.java.html
 *
 * @author 350rosen
 */
public class BlackScholesCalc {

    private static final Random random = new Random();
    private static final NormalDistribution normalDist = new NormalDistribution();

    /**
     * This method calculates D1 and D2 of Black Scholes and returns the entire Black Scholes equation estimate.
     *
     * @return the calculated call price
     */
    public static double callPriceBSM(final double spotPrice, final double strikePrice, final double timeToMaturity,
                                      final double riskFreeRate, final double volatility) {
        double D1 = ((Math.log(spotPrice / strikePrice) + ((riskFreeRate + (Math.pow(volatility, 2)/2)) * timeToMaturity))) / (volatility * Math.sqrt(timeToMaturity));
        double D2 = (D1 - (volatility * Math.sqrt(timeToMaturity)));
        return spotPrice * normalDist.cumulativeProbability(D1) - strikePrice * Math.exp(-riskFreeRate*timeToMaturity) * normalDist.cumulativeProbability(D2);
    }

    /**
     * Estimate by Monte Carlo simulation.
     *
     * Risk neutral assumption, mu drift becomes the risk free rate, exponential function defines the stock price at
     * T maturity. Simulation generates a large amount of stock price estimates with the given {price} equation.
     * As option price is expected value of pay-off function, i.e. strikePrice > spotPrice.
     *
     * @return the mean arithmetic average of prices and we need to use the discounting time value of money factor
     */
    public static double monteCarloEstimation(final double spotPrice, final double strikePrice, final double timeToMaturity,
                                              final double riskFreeRate, final double volatility) {
        int n = 10000;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double price = spotPrice * Math.exp((riskFreeRate * timeToMaturity) -
                    ((0.5 * (volatility * volatility)) * timeToMaturity) +
                    (volatility * Math.sqrt(timeToMaturity) * random.nextGaussian()));
            double value = Math.max(price - strikePrice, 0);
            sum += value;
        }
        double mean = sum / n;
        return Math.exp(-1 * riskFreeRate * timeToMaturity) * mean;
    }
}
