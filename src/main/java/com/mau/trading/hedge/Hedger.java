package com.mau.trading.hedge;

import com.mau.trading.Position;
import com.mongodb.client.MongoCollection;
import com.sleet.api.model.OptionChain;
import org.bson.Document;

import java.util.List;
import java.util.Set;

/**
 * A generic hedger interface
 *
 * @author 350rosen
 */
public interface Hedger {
    void hedge(MongoCollection<Document> positions, OptionChain chain, List<String> tickers, Set<Position> currentPositions, double skew);
}
