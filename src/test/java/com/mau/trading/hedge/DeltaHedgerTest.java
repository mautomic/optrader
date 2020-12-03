package com.mau.trading.hedge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mau.trading.PortfolioParams;
import com.mau.trading.Position;
import com.mau.trading.utility.Constants;
import com.mau.trading.utility.DbUtil;
import com.mau.trading.utility.Util;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.sleet.api.model.Option;
import com.sleet.api.model.OptionChain;
import org.bson.Document;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Test class for a {@link DeltaHedger}
 *
 * @author mautomic
 */
public class DeltaHedgerTest {

    private static final String TEST_HOST = "localhost";
    private static final int TEST_PORT = 27019;
    private static final String TEST_DB = "testMongoDb";
    private static final ObjectMapper mapper = new ObjectMapper();

    private MongoCollection<Document> setupMongoCollection() {

        // Setup MongoDb
        MongoClient mongoClient = new MongoClient(TEST_HOST, TEST_PORT);
        MongoDatabase db = mongoClient.getDatabase(TEST_DB);

        // Setup Mongo collection for this test
        db.createCollection("deltaHedgerCollection");
        return db.getCollection("deltaHedgerCollection");
    }

    @Test
    public void testDeltaHedge() throws IOException {

        MongoCollection<Document> deltaHedgerCollection = setupMongoCollection();

        // Setup option chain to use for testing
        ClassLoader classLoader = this.getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream("spy-option-chain.json");
        OptionChain optionChain = mapper.readValue(inputStream, OptionChain.class);

        List<Option> allOptions = Util.collectOptions(optionChain);
        // Grab an arbitrary option to enter into the collection first before hedging against it
        Option option = allOptions.get(1);

        // Enter 1 contract into our position collection
        DbUtil.enterPosition(deltaHedgerCollection, option, PortfolioParams.CommissionPerContract.getNum(), 1);

        // Grab the current positions in this collection
        Set<Position> currentPositions = DbUtil.getAllPositions(deltaHedgerCollection);

        // Loop over positions and do an assert directly here because there is only one position
        for(Position position : currentPositions) {
            // Assert position object we retrieved from the Db matches data from the Option object
            Assert.assertNotNull(position);
            Assert.assertEquals(1, position.getQty());
            Assert.assertEquals(option.getDelta(), position.getDelta(), 0.01);
        }

        // Create our delta hedger
        DeltaHedger deltaHedger = new DeltaHedger();

        // Hedge against our current positions
        deltaHedger.hedge(deltaHedgerCollection, optionChain, Collections.singletonList(optionChain.getSymbol()), currentPositions,1);

        Position equityHedgePosition = DbUtil.getPosition(deltaHedgerCollection,
                optionChain.getSymbol() + Constants.UNDERSCORE + Constants.EQUITY);

        // Assert equity hedge position retrieved from the Db matches expectations of the hedge
        Assert.assertNotNull(equityHedgePosition);
        Assert.assertEquals(option.getDelta() * -100, equityHedgePosition.getQty(), 0.5);
    }
}
