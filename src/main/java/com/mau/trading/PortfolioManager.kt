package com.mau.trading

import com.mongodb.client.MongoCollection
import com.sleet.api.model.OptionChain
import com.mau.trading.strategy.Strategy
import org.apache.logging.log4j.LogManager
import org.bson.Document

/**
 * A portfolio manager is an object used to assess portfolio risk, enter/exit trades,
 * and manage active positions for a single strategy. Every portfolio manager is tied to
 * a singular [Strategy] that runs the core trading logic with provided [Signal]s
 *
 * @author mautomic
 * @author 350rosen
 */
class PortfolioManager(private val strategy: Strategy, val positionCollection: MongoCollection<Document>) {

    /**
     * Execute core strategy for entering and exiting positions
     */
    fun runStrategy(chain: OptionChain?) {
        strategy.run(chain)
    }

    companion object {
        private val LOG = LogManager.getLogger(
            PortfolioManager::class.java
        )
    }
}