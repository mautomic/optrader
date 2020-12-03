package com.mau.trading

/**
 * An enum for the portfolio risk parameters
 *
 * @author 350rosen
 */
enum class PortfolioParams(var num: Double) {
    // Risk parameters
    MaxVolatility(0.5000),
    MinVolatility(0.2000),

    // Portfolio parameters
    BaseLiquidity(10000.00),
    RiskFreeRate(0.005),
    CommissionPerContract(0.65);
}