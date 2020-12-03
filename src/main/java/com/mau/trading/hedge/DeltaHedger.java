package com.mau.trading.hedge;

import com.google.common.util.concurrent.AtomicDouble;
import com.mau.trading.Position;
import com.mau.trading.utility.Constants;
import com.mau.trading.utility.DbUtil;
import com.mau.trading.utility.Util;
import com.mongodb.client.MongoCollection;
import com.sleet.api.model.OptionChain;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements a delta hedge with a ratio skew if required.  If doing a 1-to-1 hedge: skew = 1.
 *
 * @author 350rosen
 */
public class DeltaHedger implements Hedger {
    private static final Logger LOG = LogManager.getLogger(DeltaHedger.class);

    @Override
    public void hedge(MongoCollection<Document> positions, OptionChain chain, List<String> tickers, Set<Position> currentPositions, double skew) {
        Map<String, Double> deltaMap = new HashMap<>();
        Map<String, Integer> underlyingMap = new HashMap<>();
        Map<String, Position> equityPositionMap = new HashMap<>();

        // first we need to pull current positions and add them to the maps
        AtomicInteger quantity = new AtomicInteger();
        AtomicDouble delta = new AtomicDouble();

        // this loop will go through all the tickers within positions and assign quantities and average deltas
        tickers.forEach(ticker -> {
            // resetting quantity and delta to 0 for next ticker
            quantity.set(0);
            delta.set(0.0);
            currentPositions.forEach(position -> {
                String[] symbolParts = position.getSymbol().split(Constants.UNDERSCORE);
                if (symbolParts[0].equals(ticker) && !symbolParts[1].equals(Constants.EQUITY)) {
                    quantity.addAndGet(position.getQty());
                    delta.addAndGet(position.getDelta());
                } else if (symbolParts[0].equals(ticker)) {
                    underlyingMap.put(ticker, position.getQty());
                    equityPositionMap.put(ticker, position);
                }
            });
            deltaMap.put(ticker, (delta.get() / quantity.doubleValue()));
        });

        // this loop runs through the delta map to determine if we need to add a hedge
        deltaMap.forEach((ticker, deltaAvg) -> {
            if (!deltaAvg.isNaN()) {
                // hedge ratio is calculated to whole lots here
                int deltaRatio = (int)(Util.roundVal(deltaMap.get(ticker)) * (-100 * skew));
                // if we have the underlying hedge already, we need to potentially update the hedge
                if (underlyingMap.containsKey(ticker)) {
                    int underlyingQuantity = underlyingMap.get(ticker);
                    // if our current hedge is equal to our expected hedge ratio then we consider ourselves hedged
                    if (underlyingQuantity != deltaRatio) {
                        int tradeAmount = deltaRatio - underlyingQuantity;
                        Position position = equityPositionMap.get(ticker);
                        double averagePrice = Util.createAveragePrice(position.getQty(), position.getLastPrice(), tradeAmount, chain.getUnderlyingPrice());
                        DbUtil.updateHedge(positions, ticker + Constants.UNDERSCORE + Constants.EQUITY, deltaRatio, averagePrice, Constants.OPEN);
                        LOG.info("Traded hedge ratio to update - Ticker: " + ticker + ", Price: " + averagePrice + ", Quantity: " + deltaRatio);
                    }
                    // if we do not have the underlying hedge than we add a new hedge equal to the total ratio
                } else {
                    DbUtil.insertHedge(positions, ticker + Constants.UNDERSCORE + Constants.EQUITY, deltaRatio, chain.getUnderlyingPrice());
                    LOG.info("Traded hedge ratio to update - Ticker: " + ticker + ", Price: " + chain.getUnderlyingPrice() + ", Quantity: " + deltaRatio);
                }
            }
        });
    }
}