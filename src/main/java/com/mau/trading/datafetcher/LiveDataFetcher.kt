package com.mau.trading.datafetcher

import com.fasterxml.jackson.databind.ObjectMapper
import com.mau.trading.PortfolioManager
import com.mau.trading.action.Action
import com.mau.trading.action.DbInsertAction
import com.mau.trading.action.TradingAction
import com.mau.trading.action.UpdatePositionsAction
import com.mau.trading.config.ScannerCfg
import com.mau.trading.utility.Constants
import com.mau.trading.utility.DbUtil
import com.mau.trading.utility.Util
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.sleet.api.model.OptionChain
import com.sleet.api.service.OptionService
import org.apache.logging.log4j.LogManager
import org.bson.Document
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * An implementation of a [DataFetcher] that retrieves data from the TD REST API. This implementation also
 * records [OptionChain]s into MongoDb using sequence numbers for later historical replay
 *
 * @author mautomic
 */
class LiveDataFetcher(val optionService: OptionService, val queue: BlockingQueue<Action>, val tickers: List<List<String>>,
                      val portfolioManagers: List<PortfolioManager>, val db: MongoDatabase, val config: ScannerCfg,
                      val sequenceNum: AtomicInteger) : DataFetcher, Runnable {

    companion object {
        private val LOG = LogManager.getLogger(LiveDataFetcher::class.java)
        private const val TIMEOUT_MILLIS = 20_000L
        private val mapper = ObjectMapper()
        private val currentDate = Util.getDate(0, false)
    }

    /**
     * Method that contains the core logic to fetch [OptionChain]s using an [OptionService].
     * Tickers are batched into groups of the proposed batch size and GET requests are sent asynchronously
     * to TD, instead of sending one at a time and waiting for the response. [CompletableFuture]s
     * are returned and we join on the list of futures to assure that we have received all the responses.
     * Once they are received, the option chains are added to the [Action] processing queue for
     * later analysis and inserting into Mongo.
     */
    override fun run() {
        val currentDayRecordingCollection : MongoCollection<Document> = db.getCollection(Constants.DATA + Constants.UNDERSCORE + currentDate)

        // If this is the first entry of the day, then we get back -1
        var currentSequenceNum = DbUtil.getSequenceNum(currentDayRecordingCollection)
        if (currentSequenceNum == -1) {
            DbUtil.addSequenceNum(currentDayRecordingCollection, sequenceNum.get());
            currentSequenceNum = 1
        }

        // If the application is bounced mid-day, then the sequence number will be re-initialized to 1
        // and so it will be have to be updated the whatever sequence number we last stored in the db
        if (sequenceNum.get() == 1 && currentSequenceNum != 1) {
            sequenceNum.set(currentSequenceNum)
            LOG.info("Sequence num starting at " + sequenceNum.get() + " for recording option chains")
        }

        for (batch in tickers) {
            val requestTime = System.currentTimeMillis()
            val requestFutures: ArrayList<CompletableFuture<OptionChain>> = ArrayList();

            for (ticker in batch) {
                try {
                    requestFutures.add(optionService.getCloseExpirationOptionChainAsync(
                            ticker, Util.getMaxExpirationDate(config.daysToExpirationMax), config.strikeCount, false))
                } catch (e: Exception) {
                    LOG.error("Error getting option chain", e)
                }
            }
            try {
                CompletableFuture.allOf(*requestFutures.toTypedArray<CompletableFuture<*>>())[TIMEOUT_MILLIS, TimeUnit.SECONDS]
                LOG.info("Request time for batch " + batch + " took " + (System.currentTimeMillis() - requestTime) + " ms")

                for (future in requestFutures) {
                    val chain = future.get()
                    if (chain != null) {
                        queue.offer(UpdatePositionsAction(portfolioManagers, chain))
                        queue.offer(TradingAction(portfolioManagers, chain))
                        queue.offer(DbInsertAction(chain, currentDayRecordingCollection, mapper, sequenceNum.get()))
                    }
                }
            } catch (e: Exception) {
                LOG.error("Error retrieving OptionChains in this batch", e)
            }
        }

        // Update the sequence number to the value that should be inserted on the next run
        DbUtil.updateSequenceNum(currentDayRecordingCollection, sequenceNum.addAndGet(1))
        LOG.info("Sequence num is now " + sequenceNum.get() + " for recording option chains")
    }
}