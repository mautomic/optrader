package com.mau.trading.strategy

import com.mau.trading.Position
import com.sleet.api.model.OptionChain
import com.sleet.api.model.Equity
import com.sleet.api.model.Option

/**
 * A generic strategy that can be passed into a [PortfolioManager] to execute core trading logic.
 * A strategy must ALWAYS have a run method, but enter and exit can be overridden and customized
 * depending on the implementation of the strategy. Methods marked as default DO NOT have to be
 * implemented, and will do nothing if called in this scenario
 *
 * @author mautomic
 */
interface Strategy {
    fun run(chain: OptionChain?)
    fun enter(option: Option, enterQuantity: Int) {}
    fun enter(equity: Equity, enterQuantity: Int) {}
    fun exit(position: Position?, option: Option, exitQuantity: Int) {}
}