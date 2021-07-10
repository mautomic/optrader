package com.mau.trading

import com.mau.trading.utility.Util.getDate
import kotlin.jvm.JvmStatic
import com.mongodb.client.MongoDatabase
import com.sleet.api.service.OptionService
import com.mau.trading.signal.EntrySignal
import com.mau.trading.signal.ExitSignal
import com.mau.trading.signal.ExpiryExitSignal
import com.mau.trading.strategy.UnusualOptionsStrategy
import com.mau.trading.config.DatabaseCfg
import com.mongodb.MongoCommandException
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.mau.trading.config.Config
import com.mau.trading.strategy.Strategy
import com.mau.trading.utility.Constants
import com.mongodb.MongoClient
import org.apache.commons.lang3.StringUtils
import org.apache.logging.log4j.LogManager
import java.lang.Exception
import java.util.ArrayList

/**
 * Main class to kick off optrader
 *
 * @author mautomic
 */
object Main {
    private val LOG = LogManager.getLogger(Main::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val config = Config(args[0])
        val alerter = setupAlerter(config)
        val localDb = setupMongoDbInstance(config.getDatabaseCfg())
        val remoteDb = setupRemoteDbInstance(config.getDatabaseCfg())
        val portfolioManagers = setupPortfolioManagers(localDb, config.getTickers(), config.getScannerCfg().enableReplay)
        val optionService = OptionService(config.getCredentials().apiKey)
        val optionTrader = OptionTrader(portfolioManagers, optionService, alerter, localDb, remoteDb, config)
        optionTrader.start()
    }

    /**
     * Set up all [PortfolioManager]s we want to run
     *
     * @param db to use to create unique position collections
     * @return a list of portfolio managers
     */
    private fun setupPortfolioManagers(db: MongoDatabase, tickers: List<String>, isReplayEnabled: Boolean): List<PortfolioManager> {
        val portfolioManagers: MutableList<PortfolioManager> = ArrayList()
        portfolioManagers.add(setupUnusualOptionsPortfolioManager(db, tickers, isReplayEnabled))
        // New portfolio managers should be added here, created with a new method
        return portfolioManagers
    }

    /**
     * Setup a [PortfolioManager] with a [Strategy] for unusual options volume
     *
     * @param db to use to create unique position collections
     * @return a portfolio manager
     */
    private fun setupUnusualOptionsPortfolioManager(db: MongoDatabase, tickers: List<String>, isReplayEnabled: Boolean): PortfolioManager {

        // Create any entry signals
        val unusualOptionsEntrySignals: List<EntrySignal> = ArrayList()

        // Create any exit signals
        val unusualOptionsExitSignals: MutableList<ExitSignal> = ArrayList()
        unusualOptionsExitSignals.add(ExpiryExitSignal())

        // Create the unique collection for tracking positions via MongoDb in this portfolio
        var unusualOptionsCollectionName = "unusual_options_positions"
        // If we are replaying off the remote mongo server, than we don't want to overwrite the current
        // collection for the strategy
        if (isReplayEnabled)
            unusualOptionsCollectionName += Constants.UNDERSCORE + Constants.REPLAY
        createCollection(db, unusualOptionsCollectionName)

        // These same objects must be passed to both the strategy and the portfolio manager so they can be synchronized
        // when new positions are entered/exited
        val positionsCollection = db.getCollection(unusualOptionsCollectionName)

        // Create the strategy with the appropriate MongoDb collections, and entry/exit signals
        val unusualOptionsStrategy: Strategy = UnusualOptionsStrategy(
            positionsCollection,
            unusualOptionsEntrySignals,
            unusualOptionsExitSignals,
            tickers
        )

        // Finally, return a portfolio manager created with this strategy
        return PortfolioManager(unusualOptionsStrategy, positionsCollection)
    }

    /**
     * Connect to a mongo database and create relevant collections
     *
     * @return a [MongoDatabase] object
     */
    private fun setupMongoDbInstance(cfg: DatabaseCfg): MongoDatabase {
        val mongoClient = MongoClient(cfg.host, cfg.port)
        val db = mongoClient.getDatabase(cfg.db)
        val todayDate = Constants.DATA + Constants.UNDERSCORE + getDate(0, false)
        createCollection(db, todayDate)
        return db
    }

    /**
     * Connect to a remote mongo database. This will only be utilized to read data for re-playability
     *
     * @return a [MongoDatabase] object or null if not configured
     */
    private fun setupRemoteDbInstance(cfg: DatabaseCfg): MongoDatabase? {
        if (StringUtils.isEmpty(cfg.remoteHost))
            return null
        val mongoClient = MongoClient(cfg.remoteHost, cfg.port)
        return mongoClient.getDatabase(cfg.db)
    }

    /**
     * Create a new collection in this db, or log if collection already exists
     *
     * @param db to create collection in
     * @param name of the new collections
     */
    private fun createCollection(db: MongoDatabase, name: String) {
        try {
            db.createCollection(name)
            LOG.info("Created collection for $name")
        } catch (e: MongoCommandException) {
            LOG.info("Collection already created for $name")
        }
    }

    /**
     * Setup an alerter that will be responsible for intra-day and EOD communications
     *
     * @param config containing AWS credentials and email details
     * @return a [Alerter] that is responsible for communications
     */
    private fun setupAlerter(config: Config): Alerter? {
        var alerter: Alerter? = null
        try {
            val awsCredentials: AWSCredentials =
                BasicAWSCredentials(config.getCredentials().awsUser, config.getCredentials().awsPwd)
            alerter = Alerter(awsCredentials, config.getEmailCfg().sender, config.getEmailCfg().recipients)
        } catch (e: Exception) {
            LOG.error("Alerter not setup correctly...will not be communicating via email", e)
        }
        return alerter
    }
}