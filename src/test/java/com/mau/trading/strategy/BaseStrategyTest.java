package com.mau.trading.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mau.trading.Position;
import com.mau.trading.utility.DbUtil;
import com.mau.trading.utility.Util;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.sleet.api.model.Equity;
import com.sleet.api.model.Option;
import com.sleet.api.model.OptionChain;
import org.bson.Document;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Test class for a {@link BaseStrategy}
 * <p>
 * If a build is cancelled and the mongo instance on port 27019 isn't shutdown, then tests will not
 * run properly. In this scenario, find the temporary mongodb pid and kill it manually. There will
 * be clear messages about the mongo instance starting up and shutting down in the build console.
 *
 * @author mautomic
 */
public class BaseStrategyTest {

    private static final String TEST_HOST = "localhost";
    private static final int TEST_PORT = 27019;
    private static final String TEST_DB = "testMongoDb";
    private static final ObjectMapper mapper = new ObjectMapper();

    private MongoCollection<Document> setupMongoCollection() {

        // Setup MongoDb
        MongoClient mongoClient = new MongoClient(TEST_HOST, TEST_PORT);
        MongoDatabase db = mongoClient.getDatabase(TEST_DB);

        // Setup Mongo collection for this test
        db.createCollection("baseStrategyCollection");
        return db.getCollection("baseStrategyCollection");
    }

    @Test
    public void testBaseStrategy() throws IOException {

        // Setup option chain to use for testing
        ClassLoader classLoader = this.getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream("spy-option-chain.json");
        OptionChain optionChain = mapper.readValue(inputStream, OptionChain.class);

        MongoCollection<Document> baseStrategyCollection = setupMongoCollection();

        // Create an implementation of BaseStrategy to test entry and exit
        DoNothingStrategy strategy = new DoNothingStrategy(baseStrategyCollection);

        // Collect options and ensure the list size is greater than one
        List<Option> allOptions = Util.collectOptions(optionChain);
        Assert.assertTrue(allOptions.size() >= 1);

        // Enter a new position for the first time
        Option option = allOptions.get(0);
        strategy.enter(option, 5);
        Position position = DbUtil.getPosition(baseStrategyCollection, option.getSymbol());

        // Assert position object we retrieved from the Db matches data from the Option object
        Assert.assertNotNull(position);
        Assert.assertEquals(option.getLast(), position.getBuyPrice(), 0.0);
        Assert.assertEquals(5, position.getQty());
        Assert.assertEquals(option.getDelta() * position.getQty(), position.getDelta() , 0.01);


        // Increase size on the same position
        strategy.enter(option, 2);
        position = DbUtil.getPosition(baseStrategyCollection, option.getSymbol());

        // Assert position object we retrieved from the Db matches the increased quantity and metrics from upsizing
        // We should have 7 shares now
        Assert.assertNotNull(position);
        Assert.assertEquals(option.getLast(), position.getBuyPrice(), 0.0);
        Assert.assertEquals(7, position.getQty());
        Assert.assertEquals(option.getDelta() * position.getQty(), position.getDelta() , 0.01);


        // Exit the position partially
        strategy.exit(position, option, 1);
        position = DbUtil.getPosition(baseStrategyCollection, option.getSymbol());

        // Assert position object we retrieved from the Db matches the increased quantity and metrics from downsizing
        // We should have 6 shares now
        Assert.assertNotNull(position);
        Assert.assertEquals(option.getLast(), position.getBuyPrice(), 0.0);
        Assert.assertEquals(6, position.getQty());
        Assert.assertEquals(option.getDelta() * position.getQty(), position.getDelta() , 0.01);


        // Exit rest of the position
        strategy.exit(position, option, 6);
        position = DbUtil.getPosition(baseStrategyCollection, option.getSymbol());

        // Assert position object we retrieved from the Db matches the increased quantity and metrics from downsizing
        // We should have 0 shares now
        Assert.assertNotNull(position);
        Assert.assertEquals(option.getLast(), position.getBuyPrice(), 0.0);
        Assert.assertEquals(0, position.getQty());
        Assert.assertEquals(option.getDelta() * position.getQty(), position.getDelta() , 0.01);
    }
}

/**
 * An implementation of {@link BaseStrategy} purely for testing entry and exit
 */
class DoNothingStrategy extends BaseStrategy {
    public DoNothingStrategy(MongoCollection<Document> positions) {
        super(positions);
    }

    @Override
    public void run(OptionChain chain) {}

    @Override
    public void enter(Equity equity, int enterQuantity) {}

    @Override
    public void hedge(OptionChain chain) {}
}
