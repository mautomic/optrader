package com.mau.trading.datafetcher

import com.fasterxml.jackson.databind.ObjectMapper
import com.mau.trading.PortfolioManager
import com.mau.trading.action.Action
import com.mau.trading.action.TradingAction
import com.mau.trading.action.UpdatePositionsAction
import com.mongodb.client.MongoDatabase
import com.mau.trading.utility.Constants
import com.mau.trading.utility.DbUtil
import com.mongodb.client.MongoCollection
import com.sleet.api.model.OptionChain
import org.apache.logging.log4j.LogManager
import org.bson.Document
import java.lang.Exception
import java.util.concurrent.BlockingQueue

/**
 * An implementation of a [DataFetcher] that retrieves historical data from MongoDb
 *
 * @author mautomic
 */
class ReplayDataFetcher(val replayDate: String, val queue: BlockingQueue<Action>, val tickers: List<String>,
                        val portfolioManagers: List<PortfolioManager>, val db: MongoDatabase) : DataFetcher {

    companion object {
        private val LOG = LogManager.getLogger(ReplayDataFetcher::class.java)
        private val mapper = ObjectMapper()
    }

    override fun run() {
        val replayDataCollection : MongoCollection<Document>
        try {
            replayDataCollection = db.getCollection(Constants.DATA + Constants.UNDERSCORE + replayDate)
        } catch (e: Exception) {
            LOG.error("Data for replay date $replayDate does not exist", e)
            return
        }

        val sequenceNumber = DbUtil.getSequenceNum(replayDataCollection)
        if (sequenceNumber == -1) {
            LOG.error("Sequence number document does not exist in collection for date $replayDate")
            return
        }

        // Loop through from 1 to sequenceNumber to get option chains in order for each ticker
        for (currentNum in 1 until sequenceNumber) {
            for (ticker in tickers) {
                val jsonChain = DbUtil.getOptionChainDb(replayDataCollection, ticker, currentNum)
                if (jsonChain == null) {
                    LOG.warn("Skipping sequence num $currentNum for $ticker as json is null")
                    continue;
                }
                val chain = mapper.readValue(jsonChain, OptionChain::class.java)

                // Run actions in same order as LiveDataFetcher
                queue.offer(UpdatePositionsAction(portfolioManagers, chain))
                queue.offer(TradingAction(portfolioManagers, chain))
            }
        }
        LOG.info("Finished adding all chains to queue");
    }
}