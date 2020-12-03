package com.mau.trading

import com.mau.trading.utility.Constants

/**
 * Represents a position stored by the [PortfolioManager]. This is a more abstract object
 * that can hold different securities like a [Option] or [Equity] and only contains
 * the most critical details at entry time.
 *
 * @author mautomic
 */
open class Position(symbol: String, lastPrice: Double, buyPrice: Double, qty: Int, entryDate: String,
                    delta: Double, gamma: Double, theta: Double, vega: Double, volatility: Double, commission: Double,
                    buyNotional: Double, currentNotional: Double, unrealizedPnl: Double, realizedPnl: Double) {
    open val isOption: Boolean
    open val symbol: String
    var lastPrice: Double
    var buyPrice: Double
    var qty: Int
    open val entryDate: String
    var delta: Double
    var gamma: Double
    var theta: Double
    var vega: Double
    var volatility: Double
    var commission: Double
    var buyNotional: Double
    var currentNotional: Double
    var unrealizedPnl: Double
    var realizedPnl: Double
    open val ticker: String
        get() = if (symbol.contains(Constants.UNDERSCORE)) symbol.split(Constants.UNDERSCORE).toTypedArray()[0] else symbol

    init {
        isOption = symbol.contains(Constants.UNDERSCORE)
        this.symbol = symbol
        this.lastPrice = lastPrice
        this.buyPrice = buyPrice
        this.qty = qty
        this.entryDate = entryDate
        this.delta = delta
        this.gamma = gamma
        this.theta = theta
        this.vega = vega
        this.volatility = volatility
        this.commission = commission
        this.buyNotional = buyNotional
        this.currentNotional = currentNotional
        this.unrealizedPnl = unrealizedPnl
        this.realizedPnl = realizedPnl
    }
}