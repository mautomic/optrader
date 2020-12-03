package com.mau.trading.datafetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mau.trading.PortfolioManager;
import com.mau.trading.TestConstants;
import com.mau.trading.action.Action;
import com.mau.trading.config.ScannerCfg;
import com.mau.trading.utility.Constants;
import com.mau.trading.utility.DbColumn;
import com.mau.trading.utility.DbUtil;
import com.mau.trading.utility.Util;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.sleet.api.model.OptionChain;
import com.sleet.api.service.OptionService;
import org.bson.Document;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test class for an {@link LiveDataFetcher}
 *
 * @author mautomic
 */
public class LiveDataFetcherTest {

    private MongoDatabase setupMongoDb() {
        MongoClient mongoClient = new MongoClient(TestConstants.TEST_HOST, TestConstants.TEST_PORT);
        return mongoClient.getDatabase(TestConstants.TEST_DB);
    }

    private MongoDatabase setupMongoDb2() {
        MongoClient mongoClient = new MongoClient(TestConstants.TEST_HOST, TestConstants.TEST_PORT);
        return mongoClient.getDatabase(TestConstants.TEST_DB + "2");
    }

    @Test
    public void testLiveDataFetcherFresh() throws Exception {

        // Setup objects needed for LiveDataFetcher
        OptionService service = Mockito.mock(OptionService.class);
        BlockingQueue<Action> queue = new LinkedBlockingQueue<>();
        List<List<String>> tickerBatches = Collections.singletonList(Collections.singletonList("SPY"));
        List<PortfolioManager> portfolioManagers = Collections.emptyList();
        MongoDatabase db = setupMongoDb();
        ScannerCfg scannerCfg = new ScannerCfg(false, "20201130", 10, "20", 100, 10, 10);
        AtomicInteger sequenceNum = new AtomicInteger(1);

        LiveDataFetcher liveDataFetcher = new LiveDataFetcher(service, queue, tickerBatches, portfolioManagers, db, scannerCfg, sequenceNum);
        Assert.assertEquals("Assert sequence number starts at 1", 1, liveDataFetcher.getSequenceNum().get());

        // Setup option chain to use for testing
        ObjectMapper mapper = new ObjectMapper();
        ClassLoader classLoader = this.getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream("spy-option-chain.json");
        OptionChain optionChain = mapper.readValue(inputStream, OptionChain.class);
        String jsonOptionChain = mapper.writeValueAsString(optionChain);

        // This is the future that will return with the option chain created above
        CompletableFuture<OptionChain> future = new CompletableFuture<>();
        future.complete(optionChain);

        // Instead of making a live call to TD Ameritrade, return the option chain from above
        Mockito.when(service.getCloseExpirationOptionChainAsync(
                "SPY",
                Util.getMaxExpirationDate(scannerCfg.getDaysToExpirationMax()),
                scannerCfg.getStrikeCount(),
                false))
                .thenReturn(future);

        // Start running the LiveDataFetcher we created on another thread
        new Thread(liveDataFetcher).start();

        // Stop this current thread until the LiveDataFetcher completes and updates its sequence number
        while(liveDataFetcher.getSequenceNum().get() < 2) {
            Thread.sleep(500);
        }

        Assert.assertEquals("There should be 3 items in the queue", 3, liveDataFetcher.getQueue().size());

        // Get the collection from the DB that contains historical chains and the sequence number
        String currentDate = Util.getDate(0, false);
        MongoCollection<Document> collection = db.getCollection(Constants.DATA + Constants.UNDERSCORE + currentDate);

        // Get the current sequence number from mongo
        int currentSequenceNum = DbUtil.getSequenceNum(collection);
        Assert.assertEquals("MongoDb collection sequence number is 2", 2, currentSequenceNum);

        // Get the latest recorded option chain (should be null at this point)
        Document doc = collection.find(Filters.eq("SPY" + Constants.UNDERSCORE + (currentSequenceNum-1))).first();
        Assert.assertNull("Json Chain is null because DbInsertAction hasn't processed yet", doc);

        // Process the three actions in the queue
        while(!liveDataFetcher.getQueue().isEmpty()) {
            liveDataFetcher.getQueue().take().process();
        }

        // Get the latest recorded option chain (should now be inserted into the db)
        doc = collection.find(Filters.eq("SPY" + Constants.UNDERSCORE + (currentSequenceNum-1))).first();
        Assert.assertNotNull("DbInsertAction should have been processed at this point", doc);
        Assert.assertEquals(jsonOptionChain, doc.getString(DbColumn.JsonChain.getNumStr()));
    }

    @Test
    public void testLiveDataFetcherFromSequenceNum() throws Exception {

        // Setup objects needed for LiveDataFetcher
        OptionService service = Mockito.mock(OptionService.class);
        BlockingQueue<Action> queue = new LinkedBlockingQueue<>();
        List<List<String>> tickerBatches = Collections.singletonList(Collections.singletonList("SPY"));
        List<PortfolioManager> portfolioManagers = Collections.emptyList();
        MongoDatabase db = setupMongoDb2();
        ScannerCfg scannerCfg = new ScannerCfg(false,"20201130", 10, "20", 100, 10, 10);
        AtomicInteger sequenceNum = new AtomicInteger(1);

        LiveDataFetcher liveDataFetcher = new LiveDataFetcher(service, queue, tickerBatches, portfolioManagers, db, scannerCfg, sequenceNum);
        Assert.assertEquals("Assert sequence number starts at 1", 1, liveDataFetcher.getSequenceNum().get());

        // Setup option chain to use for testing
        ObjectMapper mapper = new ObjectMapper();
        ClassLoader classLoader = this.getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream("spy-option-chain.json");
        OptionChain optionChain = mapper.readValue(inputStream, OptionChain.class);

        // This is the future that will return with the option chain created above
        CompletableFuture<OptionChain> future = new CompletableFuture<>();
        future.complete(optionChain);

        // Instead of making a live call to TD Ameritrade, return the option chain from above
        Mockito.when(service.getCloseExpirationOptionChainAsync(
                "SPY",
                Util.getMaxExpirationDate(scannerCfg.getDaysToExpirationMax()),
                scannerCfg.getStrikeCount(),
                false))
                .thenReturn(future);

        // Get the collection from the DB that contains historical chains and the sequence number
        String currentDate = Util.getDate(0, false);
        MongoCollection<Document> collection = db.getCollection(Constants.DATA + Constants.UNDERSCORE + currentDate);

        // Preset the sequence number for today to 27 - meaning the program was already running before
        DbUtil.addSequenceNum(collection, 27);

        // Start running the LiveDataFetcher we created on another thread
        new Thread(liveDataFetcher).start();

        // Stop this current thread until the LiveDataFetcher completes and updates its sequence number
        while(liveDataFetcher.getSequenceNum().get() < 28) {
            Thread.sleep(500);
        }

        // Get the current sequence number from mongo
        int currentSequenceNum = DbUtil.getSequenceNum(collection);
        Assert.assertEquals("MongoDb collection sequence number is 28", 28, currentSequenceNum);
    }
}
