package com.mau.trading;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.mau.trading.hedge.DeltaHedger;
import com.mau.trading.hedge.Hedger;
import com.mau.trading.signal.BSMPriceSignal;
import com.mau.trading.signal.EntrySignal;
import com.mau.trading.signal.ExitSignal;
import com.mau.trading.signal.ExpiryExitSignal;
import com.mau.trading.strategy.Strategy;
import com.mau.trading.strategy.UnusualOptionsStrategy;
import com.mau.trading.config.Config;
import com.mau.trading.config.DatabaseCfg;
import com.mau.trading.utility.Constants;
import com.mau.trading.utility.Util;
import com.mongodb.MongoClient;
import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.sleet.api.service.OptionService;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Main class to kick off optrader
 *
 * @author mautomic
 */
public class Main {

    private static final Logger LOG = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        final Config config = new Config(args[0]);

        final Alerter alerter = setupAlerter(config);
        final MongoDatabase localDb = setupMongoDbInstance(config.getDatabaseCfg());
        final MongoDatabase remoteDb = setupRemoteDbInstance(config.getDatabaseCfg());
        final List<PortfolioManager> portfolioManagers = setupPortfolioManagers(localDb, config.getTickers(), config.getScannerCfg().getEnableReplay());

        final OptionService optionService = new OptionService(config.getCredentials().getApiKey());
        final OptionTrader optionTrader = new OptionTrader(portfolioManagers, optionService, alerter, localDb, remoteDb, config);
        optionTrader.start();
    }

    /**
     * Set up all {@link PortfolioManager}s we want to run
     *
     * @param db to use to create unique position collections
     * @return a list of portfolio managers
     */
    private static List<PortfolioManager> setupPortfolioManagers(MongoDatabase db, List<String> tickers, boolean isReplayEnabled) {

        final List<PortfolioManager> portfolioManagers = new ArrayList<>();
        portfolioManagers.add(setupUnusualOptionsPortfolioManager(db, tickers, isReplayEnabled));
        // New portfolio managers should be added here, created with a new method
        return portfolioManagers;
    }

    /**
     * Setup a {@link PortfolioManager} with a {@link Strategy} for unusual options volume
     *
     * @param db to use to create unique position collections
     * @return a portfolio manager
     */
    private static PortfolioManager setupUnusualOptionsPortfolioManager(MongoDatabase db, List<String> tickers, boolean isReplayEnabled) {

        // Create any entry signals
        List<EntrySignal> unusualOptionsEntrySignals = new ArrayList<>();
        unusualOptionsEntrySignals.add(new BSMPriceSignal());

        // Create any exit signals
        List<ExitSignal> unusualOptionsExitSignals = new ArrayList<>();
        unusualOptionsExitSignals.add(new ExpiryExitSignal());

        // Create any hedges
        List<Hedger> unusualOptionsHedgers = new ArrayList<>();
        unusualOptionsHedgers.add(new DeltaHedger());

        // Create the unique collection for tracking positions via MongoDb in this portfolio
        String unusualOptionsCollectionName = "unusual_options_positions";
        // If we are replaying off the remote mongo server, than we don't want to overwrite the current
        // collection for the strategy
        if (isReplayEnabled)
            unusualOptionsCollectionName += Constants.UNDERSCORE + Constants.REPLAY;

        createCollection(db, unusualOptionsCollectionName);

        // These same objects must be passed to both the strategy and the portfolio manager so they can be synchronized
        // when new positions are entered/exited
        final MongoCollection<Document> positionsCollection = db.getCollection(unusualOptionsCollectionName);

        // Create the strategy with the appropriate MongoDb collections, and entry/exit signals
        Strategy unusualOptionsStrategy = new UnusualOptionsStrategy(
                positionsCollection,
                unusualOptionsEntrySignals,
                unusualOptionsExitSignals,
                tickers,
                unusualOptionsHedgers);

        // Finally, return a portfolio manager created with this strategy
        return new PortfolioManager(unusualOptionsStrategy, positionsCollection);
    }

    /**
     * Connect to a mongo database and create relevant collections
     *
     * @return a {@link MongoDatabase} object
     */
    private static MongoDatabase setupMongoDbInstance(final DatabaseCfg cfg) {
        final MongoClient mongoClient = new MongoClient(cfg.getHost(), cfg.getPort());
        final MongoDatabase db = mongoClient.getDatabase(cfg.getDb());
        String todayDate =  Constants.DATA + Constants.UNDERSCORE + Util.getDate(0, false);
        createCollection(db, todayDate);
        return db;
    }

    /**
     * Connect to a remote mongo database. This will only be utilized to read data for re-playability
     *
     * @return a {@link MongoDatabase} object or null if not configured
     */
    private static MongoDatabase setupRemoteDbInstance(final DatabaseCfg cfg) {
        if (StringUtils.isEmpty(cfg.getRemoteHost()))
            return null;
        final MongoClient mongoClient = new MongoClient(cfg.getRemoteHost(), cfg.getPort());
        return mongoClient.getDatabase(cfg.getDb());
    }

    /**
     * Create a new collection in this db, or log if collection already exists
     *
     * @param db to create collection in
     * @param name of the new collections
     */
    private static void createCollection(final MongoDatabase db, final String name) {
        try {
            db.createCollection(name);
            LOG.info("Created collection for " + name);
        } catch(MongoCommandException e) {
            LOG.info("Collection already created for " + name);
        }
    }

    /**
     * Setup an alerter that will be responsible for intra-day and EOD communications
     *
     * @param config containing AWS credentials and email details
     * @return a {@link Alerter} that is responsible for communications
     */
    private static Alerter setupAlerter(Config config) {
        Alerter alerter = null;
        try {
            AWSCredentials awsCredentials = new BasicAWSCredentials(config.getCredentials().getAwsUser(), config.getCredentials().getAwsPwd());
            alerter = new Alerter(awsCredentials, config.getEmailCfg().getSender(), config.getEmailCfg().getRecipients());
        } catch(Exception e) {
            LOG.error("Alerter not setup correctly...will not be communicating via email", e);
        }
        return alerter;
    }
}