package com.mau.trading.action

import com.mau.trading.PortfolioManager
import com.sleet.api.model.OptionChain

/**
 * An implementation of an [Action] that executes each [PortfolioManager]'s re-balancing logic
 *
 * @author mautomic
 */
class RebalanceAction(val portfolioManagers: List<PortfolioManager>, val chain: OptionChain) : Action {
    override fun process() {
        for (manager in portfolioManagers)
            manager.rebalancePortfolio(chain)
    }
}