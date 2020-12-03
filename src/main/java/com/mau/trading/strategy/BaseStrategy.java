package com.mau.trading.strategy;

import com.mau.trading.PortfolioParams;
import com.mau.trading.Position;
import com.mau.trading.utility.Constants;
import com.mau.trading.utility.DbColumn;
import com.mau.trading.utility.DbUtil;
import com.mau.trading.utility.Util;
import com.mongodb.client.MongoCollection;
import com.sleet.api.model.Option;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;

import static com.mongodb.client.model.Filters.eq;

/**
 * A basic implementation of a {@link Strategy} that contains boilerplate logic for basic
 * entries and exits into a mongodb collection. These methods can always be overridden
 * for more complex strategies.
 *
 * @author mautomic
 */
public abstract class BaseStrategy implements Strategy {

    private static final Logger LOG = LogManager.getLogger(BaseStrategy.class);

    protected final MongoCollection<Document> positions;

    public BaseStrategy(MongoCollection<Document> positions) {
        this.positions = positions;
    }

    /**
     * Checks any pre-trade entry controls and adds a position to the portfolio.
     *
     * @param option to add to portfolio
     * @param enterQuantity to purchase
     */
    @Override
    public void enter(Option option, int enterQuantity) {

        // Creates a position for a marked option in the mongodb positions collection. We don't want
        // to append the timestamp to the end of the option symbol, otherwise, we'll add a new position
        // at every scan; we want only one position document per option.
        final Document currentPosition = positions.find(eq(DbColumn.ID.getColumnName(), option.getSymbol())).first();

        // Insert a fresh position with the qty
        if (currentPosition == null) {
            DbUtil.enterPosition(positions, option, PortfolioParams.CommissionPerContract.getNum() * enterQuantity, enterQuantity);
            LOG.info("Entered a new position, " + enterQuantity + " " + option.getSymbol() + " @ " + option.getLast());
        }
        // Update original position
        else {
            double originalPrice = currentPosition.getDouble(DbColumn.LastPrice.getNumStr());
            int originalQty = currentPosition.getInteger(DbColumn.Quantity.getNumStr());
            int totalQty = originalQty + enterQuantity;
            double averagePrice = Util.createAveragePrice(originalQty, originalPrice, enterQuantity, option.getLast());
            DbUtil.enterPosition(positions, option, currentPosition, averagePrice, totalQty);
            LOG.info("Increased an existing position, " + enterQuantity + " " + option.getSymbol() + " @ " + option.getLast());
            LOG.info("Average price is now " + averagePrice + " with " + totalQty + " lots");
        }
    }

    /**
     * Exits a position in the portfolio by updating to close and realizing PnL.
     *
     * @param position to decrease or eliminate exposure to
     * @param option that contains symbols and their associated {@link Option} objects
     * @param exitQuantity lots to decrease
     */
    @Override
    public void exit(Position position, Option option, int exitQuantity) {

        final String symbol = position.getSymbol();

        // exit the option position
        final Document currentPosition = positions.find(eq(DbColumn.ID.getColumnName(), symbol)).first();
        if (currentPosition != null && currentPosition.getString(DbColumn.OpenCloseIndicator.getNumStr()).equals(Constants.OPEN)) {

            int currentQty = currentPosition.getInteger(DbColumn.Quantity.getNumStr());
            double currentNotional = (position.getLastPrice() * currentQty) * 100;
            double pastNotional = (currentPosition.getDouble(DbColumn.ClosePrice.getNumStr()) * currentPosition.getInteger(DbColumn.Quantity.getNumStr())) * 100;
            double currentPnL = currentNotional - pastNotional;

            // If exit quantity is greater than or equal to current quantity, exit the entire position
            DbUtil.exitPosition(positions, option, currentPosition, currentPnL, exitQuantity);
            if (exitQuantity >= position.getQty())
                LOG.info("Exited all of existing position, " + currentQty + " " + symbol + " @ " + option.getLast());
            else
                LOG.info("Exited portion of existing position, " + exitQuantity + " " + symbol + " @ " + option.getLast());
        }
    }
}
