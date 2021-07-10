package com.mau.trading.utility

import com.mau.trading.utility.Util.getDate
import com.mongodb.client.MongoCollection
import java.util.HashSet
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mau.trading.PortfolioParams
import com.mau.trading.Position
import com.sleet.api.model.Option
import org.bson.Document

object DbUtil {
    /**
     * Return all positions in document format in a provided MongoDb collection
     *
     * @param positionsCollection to retrieve positions from
     * @return a set of [Document]s
     */
    fun getAllPositionsDocuments(positionsCollection: MongoCollection<Document>): Set<Document> {
        val documentSet: MutableSet<Document> = HashSet()
        val positions = positionsCollection.find()
        val positionIterator = positions.iterator()
        while (positionIterator.hasNext())
            documentSet.add(positionIterator.next())
        return documentSet
    }

    /**
     * Deserialize and return all positions in a provided MongoDb collection
     *
     * @param positionsCollection to retrieve positions from
     * @return a set of [Position]s
     */
    fun getAllPositions(positionsCollection: MongoCollection<Document>): Set<Position> {
        val documentSet = getAllPositionsDocuments(positionsCollection)
        val positionSet: MutableSet<Position> = HashSet()
        for (positionDoc in documentSet) {
            val pos = extractPositionFromDocument(positionDoc)
            if (pos != null)
                positionSet.add(pos)
        }
        return positionSet
    }

    /**
     * Deserialize and return a position from a provided MongoDb collection that matches the symbol
     *
     * @param positionsCollection to retrieve positions from
     * @param symbol that matches the security in the position
     * @return a [Position] or null if not found
     */
    fun getPosition(positionsCollection: MongoCollection<Document>, symbol: String): Position? {
        val positionDoc = positionsCollection.find(Filters.eq(DbColumn.ID.columnName, symbol)).first()
        return extractPositionFromDocument(positionDoc)
    }

    /**
     * Create a [Position] from a MongoDb document
     *
     * @param positionDocument that contains position fields
     * @return a [Position]
     */
    fun extractPositionFromDocument(positionDocument: Document?): Position? {
        return if (positionDocument == null)
            null
        else Position(
            positionDocument.getString(DbColumn.ID.columnName),
            positionDocument.getDouble(DbColumn.LastPrice.numStr),
            positionDocument.getDouble(DbColumn.BuyPrice.numStr),
            positionDocument.getInteger(DbColumn.Quantity.numStr),
            positionDocument.getString(DbColumn.DatePulled.numStr),
            positionDocument.getDouble(DbColumn.Delta.numStr),
            positionDocument.getDouble(DbColumn.Gamma.numStr),
            positionDocument.getDouble(DbColumn.Theta.numStr),
            positionDocument.getDouble(DbColumn.Vega.numStr),
            positionDocument.getDouble(DbColumn.Volatility.numStr),
            positionDocument.getDouble(DbColumn.Commission.numStr),
            positionDocument.getDouble(DbColumn.BuyNotional.numStr),
            positionDocument.getDouble(DbColumn.CurrentNotional.numStr),
            positionDocument.getDouble(DbColumn.UnrealizedPnL.numStr),
            positionDocument.getDouble(DbColumn.RealizedPnL.numStr)
        )
    }

    /**
     * Updates a current position in a particular collection with latest data for ticking fields
     *
     * @param positionCollection the collection to update a position within
     * @param currentPosition the position to update
     * @param option with the latest data
     */
    fun updatePosition(positionCollection: MongoCollection<Document>, currentPosition: Document, option: Option) {
        val quantity = currentPosition.getInteger(DbColumn.Quantity.numStr)
        positionCollection.updateOne(
            Filters.eq(DbColumn.ID.columnName, currentPosition.getString(DbColumn.ID.columnName)),
            Updates.combine(
                Updates.set(DbColumn.LastPrice.numStr, option.last),
                Updates.set(DbColumn.Delta.numStr, option.delta * quantity),
                Updates.set(DbColumn.Theta.numStr, option.theta * quantity),
                Updates.set(DbColumn.Gamma.numStr, option.gamma * quantity),
                Updates.set(DbColumn.Vega.numStr, option.vega * quantity),
                Updates.set(DbColumn.Volatility.numStr, option.volatility)
            )
        )
    }

    /**
     * This method enters a new position to a given portfolio database.
     *
     * @param positions: the collection to add to - aka the fund/portfolio name.
     * @param option: the option to pull all data for addition.
     */
    fun enterPosition(positions: MongoCollection<Document>, option: Option, commission: Double, quantity: Int) {
        val document = Document()
        document[DbColumn.ID.columnName] = option.symbol // id must be represented as itself and not a number
        document[DbColumn.BuyPrice.numStr] = option.last
        document[DbColumn.LastPrice.numStr] = option.last
        document[DbColumn.Quantity.numStr] = quantity
        document[DbColumn.BuyNotional.numStr] = option.last * quantity * 100
        document[DbColumn.CurrentNotional.numStr] = option.last * quantity * 100
        document[DbColumn.Delta.numStr] = option.delta * quantity
        document[DbColumn.Gamma.numStr] = option.gamma * quantity
        document[DbColumn.Theta.numStr] = option.theta * quantity
        document[DbColumn.Vega.numStr] = option.vega * quantity
        document[DbColumn.Volatility.numStr] = option.volatility
        document[DbColumn.DatePulled.numStr] = getDate(0, false)
        document[DbColumn.ClosePrice.numStr] = 0.0
        document[DbColumn.OpenCloseIndicator.numStr] = Constants.OPEN
        document[DbColumn.UnrealizedPnL.numStr] = 0.0
        document[DbColumn.RealizedPnL.numStr] = 0.0
        document[DbColumn.Commission.numStr] = commission * quantity
        positions.insertOne(document)
    }

    /**
     * Increases the size on a position
     *
     * @param positions: the collection to add to - aka the fund/portfolio name.
     * @param option: the option we are increasing a position in.
     * @param currentPosition: the position to increase.
     * @param averagePrice: the new average price of the position based on the latest entry
     * @param quantity: quantity to increase to.
     */
    fun enterPosition(
        positions: MongoCollection<Document>,
        option: Option,
        currentPosition: Document,
        averagePrice: Double,
        quantity: Int
    ) {
        val totalCommissions = PortfolioParams.CommissionPerContract.num * quantity
        positions.updateOne(
            Filters.eq(DbColumn.ID.columnName, currentPosition.getString(DbColumn.ID.columnName)),
            Updates.combine(
                Updates.set(DbColumn.LastPrice.numStr, averagePrice),
                Updates.set(DbColumn.Quantity.numStr, quantity),
                Updates.set(DbColumn.CurrentNotional.numStr, averagePrice * quantity * 100),
                Updates.set(DbColumn.Commission.numStr, totalCommissions),
                Updates.set(DbColumn.Delta.numStr, option.delta * quantity),
                Updates.set(DbColumn.Theta.numStr, option.theta * quantity),
                Updates.set(DbColumn.Gamma.numStr, option.delta * quantity),
                Updates.set(DbColumn.Vega.numStr, option.theta * quantity)
            )
        )
    }

    /**
     * This method exits a position from a given portfolio database.
     *
     * @param positions: the collection to remove from - aka the fund/portfolio name.
     * @param option: the option to pull all data for addition.
     * @param currentPosition: the position to remove.
     * @param currentPnL: the calculated PnL on the position.
     * @param quantity: the quantity to decrease to
     */
    fun exitPosition(
        positions: MongoCollection<Document>,
        option: Option,
        currentPosition: Document,
        currentPnL: Double,
        quantity: Int
    ) {
        // check if the quantity we are closing out is equal to the position size
        // ** if we are closing greater than the current position we will actually be closing old position and entering new one **
        if (quantity == currentPosition.getInteger(DbColumn.Quantity.numStr)) {
            // if yes we simple update to close the position
            positions.updateOne(
                Filters.eq(DbColumn.ID.columnName, currentPosition.getString(DbColumn.ID.columnName)),
                Updates.combine(
                    Updates.set(DbColumn.OpenCloseIndicator.numStr, Constants.CLOSE),
                    Updates.set(DbColumn.Quantity.numStr, 0),
                    Updates.set(DbColumn.ClosePrice.numStr, option.last),
                    Updates.set(DbColumn.Delta.numStr, 0.0),
                    Updates.set(DbColumn.Theta.numStr, 0.0),
                    Updates.set(DbColumn.Gamma.numStr, 0.0),
                    Updates.set(DbColumn.Vega.numStr, 0.0),
                    Updates.set(DbColumn.CurrentNotional.numStr, 0.0),
                    Updates.set(
                        DbColumn.Commission.numStr,
                        currentPosition.getInteger(DbColumn.Quantity.numStr)
                            .toDouble() * PortfolioParams.CommissionPerContract.num * 2
                    ),
                    Updates.set(DbColumn.RealizedPnL.numStr, currentPnL)
                )
            )
        } else if (quantity < currentPosition.getInteger(DbColumn.Quantity.numStr)) {
            // if not, we update to the new quantity, and insert a new position for the closed lots
            val quantityDiff = currentPosition.getInteger(DbColumn.Quantity.numStr) - quantity
            val realizedPnL = option.last * quantity - currentPosition.getDouble(DbColumn.BuyPrice.numStr) * quantity
            val realizedCommission =
                quantity * PortfolioParams.CommissionPerContract.num * 2 + quantityDiff * PortfolioParams.CommissionPerContract.num
            positions.updateOne(
                Filters.eq(DbColumn.ID.columnName, currentPosition.getString(DbColumn.ID.columnName)),
                Updates.combine(
                    Updates.set(DbColumn.Quantity.numStr, quantityDiff),
                    Updates.set(DbColumn.Delta.numStr, option.delta * quantityDiff),
                    Updates.set(DbColumn.Theta.numStr, option.theta * quantityDiff),
                    Updates.set(DbColumn.Gamma.numStr, option.gamma * quantityDiff),
                    Updates.set(DbColumn.Vega.numStr, option.vega * quantityDiff),
                    Updates.set(
                        DbColumn.BuyNotional.numStr,
                        currentPosition.getDouble(DbColumn.BuyPrice.numStr) * quantityDiff
                    ),
                    Updates.set(DbColumn.Commission.numStr, realizedCommission),
                    Updates.set(DbColumn.RealizedPnL.numStr, realizedPnL)
                )
            )
        }
    }

    /**
     * This method inserts the option data into the Db.
     *
     * @param collection: the collection to add to.
     * @param jsonChain: the deserialized to Json option chain.
     * @param count: an iterative count.
     */
    fun enterOptionChainDb(collection: MongoCollection<Document>, ticker: String, jsonChain: String?, count: Int) {
        val document = Document()
        val id = ticker + Constants.UNDERSCORE + count
        document[DbColumn.ID.columnName] = id
        document[DbColumn.JsonChain.numStr] = jsonChain
        collection.insertOne(document)
    }

    /**
     * Gets a historical option data from the Db.
     *
     * @param collection: the collection to retrieve from
     * @param ticker: the ticker to retrieve the chain for
     * @param count: the chain to get associated with a this sequence number
     * @sreturn option chain in json format
     */
    fun getOptionChainDb(collection: MongoCollection<Document>, ticker: String, count: Int): String? {
        val id = ticker + Constants.UNDERSCORE + count
        val document = collection.find(Filters.eq(DbColumn.ID.columnName, id)).first() ?: return null
        return document.getString(DbColumn.JsonChain.numStr)
    }

    /**
     * Get the latest sequence number for the day for recording option chains
     *
     * @param collection the collection containing today's data
     * @return the current sequence number to use for recording new option chains
     */
    fun getSequenceNum(collection: MongoCollection<Document>): Int {
        val document = collection.find(Filters.eq(DbColumn.ID.columnName, DbColumn.SequenceNum.columnName)).first()
            ?: return -1
        return document.getInteger(DbColumn.SequenceNum.numStr)
    }

    /**
     * Add the sequence number document to this collection
     *
     * @param collection the collection containing today's data
     * @param count for the sequence number
     */
    fun addSequenceNum(collection: MongoCollection<Document>, count: Int) {
        val document = Document()
        val id = DbColumn.SequenceNum.columnName
        document[DbColumn.ID.columnName] = id
        document[DbColumn.SequenceNum.numStr] = count
        collection.insertOne(document)
    }

    /**
     * Update the sequence number in this collection for recording option chains
     *
     * @param collection the collection containing today's data
     * @param count to update sequence number to
     */
    fun updateSequenceNum(collection: MongoCollection<Document>, count: Int) {
        collection.updateOne(
            Filters.eq(DbColumn.ID.columnName, DbColumn.SequenceNum.columnName),
            Updates.set(DbColumn.SequenceNum.numStr, count)
        )
    }
}