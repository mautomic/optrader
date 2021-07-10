package com.mau.trading;

import com.mau.trading.signal.Signal;
import com.mau.trading.strategy.Strategy;
import com.mongodb.client.MongoCollection;
import com.sleet.api.model.OptionChain;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

/**
 * A portfolio manager is an object used to assess portfolio risk, enter/exit trades,
 * and manage active positions for a single strategy. Every portfolio manager is tied to
 * a singular {@link Strategy} that runs the core trading logic with provided {@link Signal}s
 *
 * @author mautomic
 * @author 350rosen
 */
public class PortfolioManager {

    private static final Logger LOG = LogManager.getLogger(PortfolioManager.class);

    private final Strategy strategy;
    private final MongoCollection<Document> positionsCollection;

    public PortfolioManager(Strategy strategy, MongoCollection<Document> positionCollection) {
        this.strategy = strategy;
        this.positionsCollection = positionCollection;
    }

    /**
     * Execute core strategy for entering and exiting positions
     */
    public void runStrategy(OptionChain chain) {
        strategy.run(chain);
    }

    public MongoCollection<Document> getPositionCollection() {
        return positionsCollection;
    }

}
