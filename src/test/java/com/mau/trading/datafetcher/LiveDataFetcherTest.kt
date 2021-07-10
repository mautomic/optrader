package com.mau.trading.datafetcher

import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.client.MongoDatabase
import com.mau.trading.TestConstants
import kotlin.Throws
import com.sleet.api.service.OptionService
import org.mockito.Mockito
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import com.mau.trading.PortfolioManager
import com.mau.trading.action.Action
import com.mau.trading.config.ScannerCfg
import java.util.concurrent.atomic.AtomicInteger
import com.mau.trading.utility.Constants
import com.sleet.api.model.OptionChain
import java.util.concurrent.CompletableFuture
import com.mau.trading.utility.DbUtil
import com.mongodb.client.model.Filters
import com.mau.trading.utility.DbColumn
import com.mau.trading.utility.Util
import com.mongodb.MongoClient
import org.junit.Assert
import org.junit.Test
import java.lang.Exception

/**
 * Test class for an [LiveDataFetcher]
 *
 * @author mautomic
 */
class LiveDataFetcherTest {
    private fun setupMongoDb(): MongoDatabase {
        val mongoClient = MongoClient(TestConstants.TEST_HOST, TestConstants.TEST_PORT)
        return mongoClient.getDatabase(TestConstants.TEST_DB)
    }

    private fun setupMongoDb2(): MongoDatabase {
        val mongoClient = MongoClient(TestConstants.TEST_HOST, TestConstants.TEST_PORT)
        return mongoClient.getDatabase(TestConstants.TEST_DB + "2")
    }

    @Test
    @Throws(Exception::class)
    fun testLiveDataFetcherFresh() {

        // Setup objects needed for LiveDataFetcher
        val service = Mockito.mock(OptionService::class.java)
        val queue: BlockingQueue<Action> = LinkedBlockingQueue()
        val tickerBatches = listOf(listOf("SPY"))
        val portfolioManagers: List<PortfolioManager> = emptyList()
        val db = setupMongoDb()
        val scannerCfg = ScannerCfg(false, "20201130", 10, "20", 100, 10, 10)
        val sequenceNum = AtomicInteger(1)
        val liveDataFetcher = LiveDataFetcher(service, queue, tickerBatches, portfolioManagers, db, scannerCfg, sequenceNum)
        Assert.assertEquals("Assert sequence number starts at 1", 1, liveDataFetcher.sequenceNum.get().toLong())

        // Setup option chain to use for testing
        val mapper = ObjectMapper()
        val classLoader = this.javaClass.classLoader
        val inputStream = classLoader.getResourceAsStream("spy-option-chain.json")
        val optionChain = mapper.readValue(inputStream, OptionChain::class.java)
        val jsonOptionChain = mapper.writeValueAsString(optionChain)

        // This is the future that will return with the option chain created above
        val future = CompletableFuture<OptionChain>()
        future.complete(optionChain)

        // Instead of making a live call to TD Ameritrade, return the option chain from above
        Mockito.`when`(
            service.getCloseExpirationOptionChainAsync(
                "SPY",
                Util.getMaxExpirationDate(scannerCfg.daysToExpirationMax),
                scannerCfg.strikeCount,
                false
            )
        ).thenReturn(future)

        // Start running the LiveDataFetcher we created on another thread
        Thread(liveDataFetcher).start()

        // Stop this current thread until the LiveDataFetcher completes and updates its sequence number
        while (liveDataFetcher.sequenceNum.get() < 2) {
            Thread.sleep(500)
        }
        Assert.assertEquals("There should be 3 items in the queue", 3, liveDataFetcher.queue.size.toLong())

        // Get the collection from the DB that contains historical chains and the sequence number
        val currentDate = Util.getDate(0, false)
        val collection = db.getCollection(Constants.DATA + Constants.UNDERSCORE + currentDate)

        // Get the current sequence number from mongo
        val currentSequenceNum = DbUtil.getSequenceNum(collection)
        Assert.assertEquals("MongoDb collection sequence number is 2", 2, currentSequenceNum.toLong())

        // Get the latest recorded option chain (should be null at this point)
        var doc = collection.find(Filters.eq("SPY" + Constants.UNDERSCORE + (currentSequenceNum - 1))).first()
        Assert.assertNull("Json Chain is null because DbInsertAction hasn't processed yet", doc)

        // Process the three actions in the queue
        while (!liveDataFetcher.queue.isEmpty()) {
            liveDataFetcher.queue.take().process()
        }

        // Get the latest recorded option chain (should now be inserted into the db)
        doc = collection.find(Filters.eq("SPY" + Constants.UNDERSCORE + (currentSequenceNum - 1))).first()
        Assert.assertNotNull("DbInsertAction should have been processed at this point", doc)
        Assert.assertEquals(jsonOptionChain, doc?.getString(DbColumn.JsonChain.numStr))
    }

    @Test
    @Throws(Exception::class)
    fun testLiveDataFetcherFromSequenceNum() {

        // Setup objects needed for LiveDataFetcher
        val service = Mockito.mock(OptionService::class.java)
        val queue: BlockingQueue<Action> = LinkedBlockingQueue()
        val tickerBatches = listOf(listOf("SPY"))
        val portfolioManagers: List<PortfolioManager> = emptyList()
        val db = setupMongoDb2()
        val scannerCfg = ScannerCfg(false, "20201130", 10, "20", 100, 10, 10)
        val sequenceNum = AtomicInteger(1)
        val liveDataFetcher = LiveDataFetcher(service, queue, tickerBatches, portfolioManagers, db, scannerCfg, sequenceNum)
        Assert.assertEquals("Assert sequence number starts at 1", 1, liveDataFetcher.sequenceNum.get().toLong())

        // Setup option chain to use for testing
        val mapper = ObjectMapper()
        val classLoader = this.javaClass.classLoader
        val inputStream = classLoader.getResourceAsStream("spy-option-chain.json")
        val optionChain = mapper.readValue(inputStream, OptionChain::class.java)

        // This is the future that will return with the option chain created above
        val future = CompletableFuture<OptionChain>()
        future.complete(optionChain)

        // Instead of making a live call to TD Ameritrade, return the option chain from above
        Mockito.`when`(
            service.getCloseExpirationOptionChainAsync(
                "SPY",
                Util.getMaxExpirationDate(scannerCfg.daysToExpirationMax),
                scannerCfg.strikeCount,
                false
            )
        ).thenReturn(future)

        // Get the collection from the DB that contains historical chains and the sequence number
        val currentDate = Util.getDate(0, false)
        val collection = db.getCollection(Constants.DATA + Constants.UNDERSCORE + currentDate)

        // Preset the sequence number for today to 27 - meaning the program was already running before
        DbUtil.addSequenceNum(collection, 27)

        // Start running the LiveDataFetcher we created on another thread
        Thread(liveDataFetcher).start()

        // Stop this current thread until the LiveDataFetcher completes and updates its sequence number
        while (liveDataFetcher.sequenceNum.get() < 28) {
            Thread.sleep(500)
        }

        // Get the current sequence number from mongo
        val currentSequenceNum = DbUtil.getSequenceNum(collection)
        Assert.assertEquals("MongoDb collection sequence number is 28", 28, currentSequenceNum.toLong())
    }
}