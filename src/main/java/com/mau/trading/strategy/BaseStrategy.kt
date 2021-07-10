package com.mau.trading.strategy

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mau.trading.utility.DbColumn
import com.mau.trading.utility.DbUtil
import com.mau.trading.PortfolioParams
import com.mau.trading.Position
import com.mau.trading.utility.Constants
import com.mau.trading.utility.Util
import com.sleet.api.model.Option
import org.apache.logging.log4j.LogManager
import org.bson.Document

/**
 * A basic implementation of a [Strategy] that contains boilerplate logic for basic
 * entries and exits into a mongodb collection. These methods can always be overridden
 * for more complex strategies.
 *
 * @author mautomic
 */
abstract class BaseStrategy(val positions: MongoCollection<Document>) : Strategy {

    companion object {
        private val LOG = LogManager.getLogger(BaseStrategy::class.java)
    }

    /**
     * Checks any pre-trade entry controls and adds a position to the portfolio.
     *
     * @param option to add to portfolio
     * @param enterQuantity to purchase
     */
    override fun enter(option: Option, enterQuantity: Int) {

        // Creates a position for a marked option in the mongodb positions collection. We don't want
        // to append the timestamp to the end of the option symbol, otherwise, we'll add a new position
        // at every scan; we want only one position document per option.
        val currentPosition = positions.find(Filters.eq(DbColumn.ID.columnName, option.symbol)).first()

        // Insert a fresh position with the qty
        if (currentPosition == null) {
            DbUtil.enterPosition(positions, option, PortfolioParams.CommissionPerContract.num * enterQuantity, enterQuantity)
            LOG.info("Entered a new position, " + enterQuantity + " " + option.symbol + " @ " + option.last)
        } else {
            val originalPrice = currentPosition.getDouble(DbColumn.LastPrice.numStr)
            val originalQty = currentPosition.getInteger(DbColumn.Quantity.numStr)
            val totalQty = originalQty + enterQuantity
            val averagePrice = Util.createAveragePrice(originalQty, originalPrice, enterQuantity, option.last)
            DbUtil.enterPosition(positions, option, currentPosition, averagePrice, totalQty)
            LOG.info("Increased an existing position, " + enterQuantity + " " + option.symbol + " @ " + option.last)
            LOG.info("Average price is now $averagePrice with $totalQty lots")
        }
    }

    /**
     * Exits a position in the portfolio by updating to close and realizing PnL.
     *
     * @param position to decrease or eliminate exposure to
     * @param option that contains symbols and their associated [Option] objects
     * @param exitQuantity lots to decrease
     */
    override fun exit(position: Position?, option: Option, exitQuantity: Int) {
        val symbol = position!!.symbol

        // exit the option position
        val currentPosition = positions.find(Filters.eq(DbColumn.ID.columnName, symbol)).first()
        if (currentPosition != null && currentPosition.getString(DbColumn.OpenCloseIndicator.numStr) == Constants.OPEN) {
            val currentQty = currentPosition.getInteger(DbColumn.Quantity.numStr)
            val currentNotional = position.lastPrice * currentQty * 100
            val pastNotional =
                currentPosition.getDouble(DbColumn.ClosePrice.numStr) * currentPosition.getInteger(DbColumn.Quantity.numStr) * 100
            val currentPnL = currentNotional - pastNotional

            // If exit quantity is greater than or equal to current quantity, exit the entire position
            DbUtil.exitPosition(positions, option, currentPosition, currentPnL, exitQuantity)
            if (exitQuantity >= position.qty)
                LOG.info("Exited all of existing position, " + currentQty + " " + symbol + " @ " + option.last)
            else
                LOG.info("Exited portion of existing position, " + exitQuantity + " " + symbol + " @ " + option.last)
        }
    }
}