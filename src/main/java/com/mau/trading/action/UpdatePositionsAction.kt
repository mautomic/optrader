package com.mau.trading.action

import com.mau.trading.PortfolioManager
import com.mau.trading.utility.DbColumn
import com.mau.trading.utility.DbUtil
import com.mau.trading.utility.Util
import com.sleet.api.model.OptionChain

/**
 * An implementation of an [Action] that updates the positions in each [PortfolioManager]
 * with the latest data pulled from the API.
 *
 * This will most likely be deprecated in the future for a true message broker between producers and consumers,
 * as its only purpose is to send newly retrieved data to the portfolio managers.
 *
 * @author mautomic
 */
class UpdatePositionsAction(private val portfolioManagers: List<PortfolioManager>, private val optionChain: OptionChain) : Action {

    override fun process() {
        val optionMap = Util.flattenOptionChain(optionChain)
        for (portfolioManager in portfolioManagers) {
            // Get all positions from the db for a portfolio manager
            val positionsCollection = portfolioManager.positionCollection
            val currentPositions = DbUtil.getAllPositionsDocuments(positionsCollection)
            // Update the data in the db for each position
            for (position in currentPositions) {
                val optionForPosition = optionMap[position.getString(DbColumn.ID.columnName)]
                // The option can be null if the position we are checking is for a different ticker
                if (optionForPosition != null)
                    DbUtil.updatePosition(positionsCollection, position, optionForPosition)
            }
        }
    }
}