package com.mau.trading.strategy

import com.fasterxml.jackson.databind.ObjectMapper
import com.mongodb.client.MongoCollection
import kotlin.Throws
import java.io.IOException
import com.sleet.api.model.OptionChain
import com.mau.trading.utility.DbUtil
import com.mau.trading.utility.Util
import com.mongodb.MongoClient
import com.sleet.api.model.Equity
import org.bson.Document
import org.junit.Assert
import org.junit.Test

/**
 * Test class for a [BaseStrategy]
 *
 * If a build is cancelled and the mongo instance on port 27019 isn't shutdown, then tests will not
 * run properly. In this scenario, find the temporary mongodb pid and kill it manually. There will
 * be clear messages about the mongo instance starting up and shutting down in the build console.
 *
 * @author mautomic
 */
class BaseStrategyTest {

    companion object {
        private const val TEST_HOST = "localhost"
        private const val TEST_PORT = 27019
        private const val TEST_DB = "testMongoDb"
        private val mapper = ObjectMapper()
    }

    private fun setupMongoCollection(): MongoCollection<Document> {
        // Setup MongoDb
        val mongoClient = MongoClient(TEST_HOST, TEST_PORT)
        val db = mongoClient.getDatabase(TEST_DB)

        // Setup Mongo collection for this test
        db.createCollection("baseStrategyCollection")
        return db.getCollection("baseStrategyCollection")
    }

    @Test
    @Throws(IOException::class)
    fun testBaseStrategy() {

        // Setup option chain to use for testing
        val classLoader = this.javaClass.classLoader
        val inputStream = classLoader.getResourceAsStream("spy-option-chain.json")
        val optionChain = mapper.readValue(inputStream, OptionChain::class.java)
        val baseStrategyCollection = setupMongoCollection()

        // Create an implementation of BaseStrategy to test entry and exit
        val strategy = DoNothingStrategy(baseStrategyCollection)

        // Collect options and ensure the list size is greater than one
        val allOptions = Util.collectOptions(optionChain)
        Assert.assertTrue(allOptions.size >= 1)

        // Enter a new position for the first time
        val option = allOptions[0]
        strategy.enter(option, 5)
        var position = DbUtil.getPosition(baseStrategyCollection, option.symbol)

        // Assert position object we retrieved from the Db matches data from the Option object
        Assert.assertNotNull(position)
        Assert.assertEquals(option.last, position.buyPrice, 0.0)
        Assert.assertEquals(5, position.qty.toLong())
        Assert.assertEquals(option.delta * position.qty, position.delta, 0.01)


        // Increase size on the same position
        strategy.enter(option, 2)
        position = DbUtil.getPosition(baseStrategyCollection, option.symbol)

        // Assert position object we retrieved from the Db matches the increased quantity and metrics from upsizing
        // We should have 7 shares now
        Assert.assertNotNull(position)
        Assert.assertEquals(option.last, position.buyPrice, 0.0)
        Assert.assertEquals(7, position.qty.toLong())
        Assert.assertEquals(option.delta * position.qty, position.delta, 0.01)


        // Exit the position partially
        strategy.exit(position, option, 1)
        position = DbUtil.getPosition(baseStrategyCollection, option.symbol)

        // Assert position object we retrieved from the Db matches the increased quantity and metrics from downsizing
        // We should have 6 shares now
        Assert.assertNotNull(position)
        Assert.assertEquals(option.last, position.buyPrice, 0.0)
        Assert.assertEquals(6, position.qty.toLong())
        Assert.assertEquals(option.delta * position.qty, position.delta, 0.01)


        // Exit rest of the position
        strategy.exit(position, option, 6)
        position = DbUtil.getPosition(baseStrategyCollection, option.symbol)

        // Assert position object we retrieved from the Db matches the increased quantity and metrics from downsizing
        // We should have 0 shares now
        Assert.assertNotNull(position)
        Assert.assertEquals(option.last, position.buyPrice, 0.0)
        Assert.assertEquals(0, position.qty.toLong())
        Assert.assertEquals(option.delta * position.qty, position.delta, 0.01)
    }
}

/**
 * An implementation of [BaseStrategy] purely for testing entry and exit
 */
internal class DoNothingStrategy(positions: MongoCollection<Document>?) : BaseStrategy(positions!!) {
    override fun run(chain: OptionChain?) {}
    override fun enter(equity: Equity?, enterQuantity: Int) {}
}