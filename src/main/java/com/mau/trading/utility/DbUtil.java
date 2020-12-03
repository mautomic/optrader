package com.mau.trading.utility;

import com.mau.trading.PortfolioParams;
import com.mau.trading.Position;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.sleet.api.model.Option;
import org.bson.Document;

import java.util.HashSet;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;

public class DbUtil {

    /**
     * Return all positions in document format in a provided MongoDb collection
     *
     * @param positionsCollection to retrieve positions from
     * @return a set of {@link Document}s
     */
    public static Set<Document> getAllPositionsDocuments(MongoCollection<Document> positionsCollection) {
        Set<Document> documentSet = new HashSet<>();
        final FindIterable<Document> positions = positionsCollection.find();
        MongoCursor<Document> positionIterator = positions.iterator();
        while (positionIterator.hasNext())
            documentSet.add(positionIterator.next());
        return documentSet;
    }

    /**
     * Deserialize and return all positions in a provided MongoDb collection
     *
     * @param positionsCollection to retrieve positions from
     * @return a set of {@link Position}s
     */
    public static Set<Position> getAllPositions(MongoCollection<Document> positionsCollection) {
        final Set<Document> documentSet = getAllPositionsDocuments(positionsCollection);
        final Set<Position> positionSet = new HashSet<>();
        for (Document positionDoc : documentSet)
            positionSet.add(extractPositionFromDocument(positionDoc));
        return positionSet;
    }

    /**
     * Deserialize and return a position from a provided MongoDb collection that matches the symbol
     *
     * @param positionsCollection to retrieve positions from
     * @param symbol that matches the security in the position
     * @return a {@link Position} or null if not found
     */
    public static Position getPosition(MongoCollection<Document> positionsCollection, String symbol) {
        Document positionDoc = positionsCollection.find(eq(DbColumn.ID.getColumnName(), symbol)).first();
        return extractPositionFromDocument(positionDoc);
    }

    /**
     * Create a {@link Position} from a MongoDb document
     *
     * @param positionDocument that contains position fields
     * @return a {@link Position}
     */
    public static Position extractPositionFromDocument(Document positionDocument) {
        if (positionDocument == null)
            return null;
        return new Position(
                positionDocument.getString(DbColumn.ID.getColumnName()),
                positionDocument.getDouble(DbColumn.LastPrice.getNumStr()),
                positionDocument.getDouble(DbColumn.BuyPrice.getNumStr()),
                positionDocument.getInteger(DbColumn.Quantity.getNumStr()),
                positionDocument.getString(DbColumn.DatePulled.getNumStr()),
                positionDocument.getDouble(DbColumn.Delta.getNumStr()),
                positionDocument.getDouble(DbColumn.Gamma.getNumStr()),
                positionDocument.getDouble(DbColumn.Theta.getNumStr()),
                positionDocument.getDouble(DbColumn.Vega.getNumStr()),
                positionDocument.getDouble(DbColumn.Volatility.getNumStr()),
                positionDocument.getDouble(DbColumn.Commission.getNumStr()),
                positionDocument.getDouble(DbColumn.BuyNotional.getNumStr()),
                positionDocument.getDouble(DbColumn.CurrentNotional.getNumStr()),
                positionDocument.getDouble(DbColumn.UnrealizedPnL.getNumStr()),
                positionDocument.getDouble(DbColumn.RealizedPnL.getNumStr()));
    }

    /**
     * Updates a current position in a particular collection with latest data for ticking fields
     *
     * @param positionCollection the collection to update a position within
     * @param currentPosition the position to update
     * @param option with the latest data
     */
    public static void updatePosition(MongoCollection<Document> positionCollection, Document currentPosition, Option option) {
        int quantity = currentPosition.getInteger(DbColumn.Quantity.getNumStr());
        positionCollection.updateOne(
                Filters.eq(DbColumn.ID.getColumnName(), currentPosition.getString(DbColumn.ID.getColumnName())),
                Updates.combine(
                        Updates.set(DbColumn.LastPrice.getNumStr(), option.getLast()),
                        Updates.set(DbColumn.Delta.getNumStr(), option.getDelta() * quantity),
                        Updates.set(DbColumn.Theta.getNumStr(), option.getTheta() * quantity),
                        Updates.set(DbColumn.Gamma.getNumStr(), option.getGamma() * quantity),
                        Updates.set(DbColumn.Vega.getNumStr(), option.getVega() * quantity),
                        Updates.set(DbColumn.Volatility.getNumStr(), option.getVolatility())
                )
        );
    }

    /**
     * This method enters a new position to a given portfolio database.
     *
     * @param positions: the collection to add to - aka the fund/portfolio name.
     * @param option: the option to pull all data for addition.
     */
    public static void enterPosition(MongoCollection<Document> positions, Option option, double commission, int quantity) {

        final Document document = new Document();
        document.put(DbColumn.ID.getColumnName(), option.getSymbol()); // id must be represented as itself and not a number
        document.put(DbColumn.BuyPrice.getNumStr(), option.getLast());
        document.put(DbColumn.LastPrice.getNumStr(), option.getLast());
        document.put(DbColumn.Quantity.getNumStr(), quantity);
        document.put(DbColumn.BuyNotional.getNumStr(), option.getLast() * quantity * 100);
        document.put(DbColumn.CurrentNotional.getNumStr(), option.getLast() * quantity * 100);
        document.put(DbColumn.Delta.getNumStr(), option.getDelta() * quantity);
        document.put(DbColumn.Gamma.getNumStr(), option.getGamma() * quantity);
        document.put(DbColumn.Theta.getNumStr(), option.getTheta() * quantity);
        document.put(DbColumn.Vega.getNumStr(), option.getVega() * quantity);
        document.put(DbColumn.Volatility.getNumStr(), option.getVolatility());
        document.put(DbColumn.DatePulled.getNumStr(), Util.getDate(0, false));
        document.put(DbColumn.ClosePrice.getNumStr(), 0.0);
        document.put(DbColumn.OpenCloseIndicator.getNumStr(), Constants.OPEN);
        document.put(DbColumn.UnrealizedPnL.getNumStr(), 0.0);
        document.put(DbColumn.RealizedPnL.getNumStr(), 0.0);
        document.put(DbColumn.Commission.getNumStr(), commission * quantity);
        positions.insertOne(document);
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
    public static void enterPosition(MongoCollection<Document> positions, Option option, Document currentPosition, double averagePrice, int quantity) {

        double totalCommissions = PortfolioParams.CommissionPerContract.getNum() * quantity;

        positions.updateOne(
                Filters.eq(DbColumn.ID.getColumnName(), currentPosition.getString(DbColumn.ID.getColumnName())),
                Updates.combine(
                        Updates.set(DbColumn.LastPrice.getNumStr(), averagePrice),
                        Updates.set(DbColumn.Quantity.getNumStr(), quantity),
                        Updates.set(DbColumn.CurrentNotional.getNumStr(), averagePrice * quantity * 100),
                        Updates.set(DbColumn.Commission.getNumStr(), totalCommissions),
                        Updates.set(DbColumn.Delta.getNumStr(), option.getDelta() * quantity),
                        Updates.set(DbColumn.Theta.getNumStr(), option.getTheta() * quantity),
                        Updates.set(DbColumn.Gamma.getNumStr(), option.getDelta() * quantity),
                        Updates.set(DbColumn.Vega.getNumStr(), option.getTheta() * quantity)
                )
        );
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
    public static void exitPosition(MongoCollection<Document> positions, Option option, Document currentPosition, double currentPnL, int quantity) {
        // check if the quantity we are closing out is equal to the position size
        // ** if we are closing greater than the current position we will actually be closing old position and entering new one **
        if (quantity == currentPosition.getInteger(DbColumn.Quantity.getNumStr())) {
            // if yes we simple update to close the position
            positions.updateOne(
                    Filters.eq(DbColumn.ID.getColumnName(), currentPosition.getString(DbColumn.ID.getColumnName())),
                    Updates.combine(
                            Updates.set(DbColumn.OpenCloseIndicator.getNumStr(), Constants.CLOSE),
                            Updates.set(DbColumn.Quantity.getNumStr(), 0),
                            Updates.set(DbColumn.ClosePrice.getNumStr(), option.getLast()),
                            Updates.set(DbColumn.Delta.getNumStr(), 0.0),
                            Updates.set(DbColumn.Theta.getNumStr(), 0.0),
                            Updates.set(DbColumn.Gamma.getNumStr(), 0.0),
                            Updates.set(DbColumn.Vega.getNumStr(), 0.0),
                            Updates.set(DbColumn.CurrentNotional.getNumStr(), 0.0),
                            Updates.set(DbColumn.Commission.getNumStr(), (double)currentPosition.getInteger(DbColumn.Quantity.getNumStr()) * PortfolioParams.CommissionPerContract.getNum() * 2),
                            Updates.set(DbColumn.RealizedPnL.getNumStr(), currentPnL)
                    )
            );
        } else if (quantity < currentPosition.getInteger(DbColumn.Quantity.getNumStr())) {
            // if not, we update to the new quantity, and insert a new position for the closed lots
            int quantityDiff = currentPosition.getInteger(DbColumn.Quantity.getNumStr()) - quantity;
            double realizedPnL = (option.getLast() * quantity) - (currentPosition.getDouble(DbColumn.BuyPrice.getNumStr()) * quantity);
            double realizedCommission = (quantity * PortfolioParams.CommissionPerContract.getNum() * 2) + (quantityDiff * PortfolioParams.CommissionPerContract.getNum());

            positions.updateOne(
                    Filters.eq(DbColumn.ID.getColumnName(), currentPosition.getString(DbColumn.ID.getColumnName())),
                    Updates.combine(
                            Updates.set(DbColumn.Quantity.getNumStr(), quantityDiff),
                            Updates.set(DbColumn.Delta.getNumStr(), option.getDelta() * quantityDiff),
                            Updates.set(DbColumn.Theta.getNumStr(), option.getTheta() * quantityDiff),
                            Updates.set(DbColumn.Gamma.getNumStr(), option.getGamma() * quantityDiff),
                            Updates.set(DbColumn.Vega.getNumStr(), option.getVega() * quantityDiff),
                            Updates.set(DbColumn.BuyNotional.getNumStr(), currentPosition.getDouble(DbColumn.BuyPrice.getNumStr()) * quantityDiff),
                            Updates.set(DbColumn.Commission.getNumStr(), realizedCommission),
                            Updates.set(DbColumn.RealizedPnL.getNumStr(), realizedPnL)
                    )
            );
        }
    }

    /**
     * This method updates the quantity and price of a Db item.
     *
     * @param collection: the collection ID is held in.
     * @param updateId: the ID that is receiving the update.
     * @param quantity: the quantity to update to.
     * @param price: the price to update to.
     */
    public static void updateHedge(MongoCollection<Document> collection, String updateId, int quantity, double price, String openCloseIndicator) {
        Position currentPosition = DbUtil.getPosition(collection, updateId);
        // if quantity equals the current position, we don't need to change anything except update the price/notional
        if (quantity == currentPosition.getQty()) {
            collection.updateOne(
                    Filters.eq(DbColumn.ID.getColumnName(), updateId),
                    Updates.combine(
                            Updates.set(DbColumn.LastPrice.getNumStr(), price),
                            Updates.set(DbColumn.BuyNotional.getNumStr(), currentPosition.getBuyPrice() * quantity),
                            Updates.set(DbColumn.OpenCloseIndicator.getNumStr(), openCloseIndicator)
                    )
            );

            // if quantity is greater than current position, we are reducing lots and closing out a partial position
        } else if (quantity > currentPosition.getQty()) {
            int quantityDiff = currentPosition.getQty() - quantity;
            double realizedPnL = (currentPosition.getCurrentNotional()) - (currentPosition.getBuyPrice() * quantityDiff);

            collection.updateOne(
                    Filters.eq(DbColumn.ID.getColumnName(), updateId),
                    Updates.combine(
                            Updates.set(DbColumn.Quantity.getNumStr(), quantity),
                            Updates.set(DbColumn.BuyNotional.getNumStr(), currentPosition.getBuyPrice() * quantity),
                            Updates.set(DbColumn.RealizedPnL.getNumStr(), realizedPnL)
                    )
            );
            // if quantity is less than current position we are just adding to the position
        } else if (quantity < currentPosition.getQty()) {
            int quantityDiff = currentPosition.getQty() - quantity;
            double averagePrice = Util.createAveragePrice(currentPosition.getQty(), currentPosition.getBuyPrice(), quantityDiff, price);
            collection.updateOne(
                    Filters.eq(DbColumn.ID.getColumnName(), updateId),
                    Updates.combine(
                            Updates.set(DbColumn.LastPrice.getNumStr(), price),
                            Updates.set(DbColumn.BuyPrice.getNumStr(), averagePrice),
                            Updates.set(DbColumn.Quantity.getNumStr(), quantity),
                            Updates.set(DbColumn.BuyNotional.getNumStr(), averagePrice * quantity)
                    )
            );
        }

    }

    /**
     * This method inserts a hedge for a given ticker.
     *
     * @param collection: the collection ID is held in.
     * @param builtId: the ID that is receiving the new hedge.
     * @param quantity: the quantity to update to.
     * @param price: the quantity to update to.
     */
    public static void insertHedge(MongoCollection<Document> collection, String builtId, int quantity, double price) {
        final Document insertDoc = new Document();
        String date = Util.getDate(0, false);
        insertDoc.put(DbColumn.ID.getColumnName(), builtId);
        insertDoc.put(DbColumn.BuyPrice.getNumStr(), price);
        insertDoc.put(DbColumn.LastPrice.getNumStr(), price);
        insertDoc.put(DbColumn.Quantity.getNumStr(), quantity);
        insertDoc.put(DbColumn.BuyNotional.getNumStr(), price * quantity);
        insertDoc.put(DbColumn.CurrentNotional.getNumStr(), 0.0);
        insertDoc.put(DbColumn.Delta.getNumStr(), 0.0);
        insertDoc.put(DbColumn.Gamma.getNumStr(), 0.0);
        insertDoc.put(DbColumn.Theta.getNumStr(), 0.0);
        insertDoc.put(DbColumn.Vega.getNumStr(), 0.0);
        insertDoc.put(DbColumn.Volatility.getNumStr(), 0.0);
        insertDoc.put(DbColumn.DatePulled.getNumStr(), date);
        insertDoc.put(DbColumn.ClosePrice.getNumStr(), 0.0);
        insertDoc.put(DbColumn.OpenCloseIndicator.getNumStr(), Constants.OPEN);
        insertDoc.put(DbColumn.UnrealizedPnL.getNumStr(), 0.0);
        insertDoc.put(DbColumn.RealizedPnL.getNumStr(), 0.0);
        insertDoc.put(DbColumn.Commission.getNumStr(), 0.0);
        collection.insertOne(insertDoc);
    }

    /**
     * This method closes down the associated hedge position.
     *
     * @param collection: the collection ID is held in.
     * @param updateId: the ID that is receiving the closure.
     * @param price: the price to update to.
     * @param underlyingPnL: the realized PnL.
     */
    public static void closeHedge(MongoCollection<Document> collection, String updateId, double price, double underlyingPnL) {
        collection.updateOne(
                Filters.eq(DbColumn.ID.getColumnName(), updateId),
                Updates.combine(
                        Updates.set(DbColumn.Quantity.getNumStr(), 0),
                        Updates.set(DbColumn.LastPrice.getNumStr(), price),
                        Updates.set(DbColumn.RealizedPnL.getNumStr(), underlyingPnL),
                        Updates.set(DbColumn.OpenCloseIndicator.getNumStr(), Constants.CLOSE)
                )
        );
    }

    /**
     * This method inserts the option data into the Db.
     *
     * @param collection: the collection to add to.
     * @param jsonChain: the deserialized to Json option chain.
     * @param count: an iterative count.
     */
    public static void enterOptionChainDb(MongoCollection<Document> collection, String ticker, String jsonChain, int count) {
        final Document document = new Document();
        final String id = ticker + Constants.UNDERSCORE + count;
        document.put(DbColumn.ID.getColumnName(), id);
        document.put(DbColumn.JsonChain.getNumStr(), jsonChain);
        collection.insertOne(document);
    }

    /**
     * Gets a historical option data from the Db.
     *
     * @param collection: the collection to retrieve from
     * @param ticker: the ticker to retrieve the chain for
     * @param count: the chain to get associated with a this sequence number
     * @sreturn option chain in json format
     */
    public static String getOptionChainDb(MongoCollection<Document> collection, String ticker, int count) {

        final String id = ticker + Constants.UNDERSCORE + count;
        Document document = collection.find(eq(DbColumn.ID.getColumnName(), id)).first();
        if (document == null)
            return null;
        return document.getString(DbColumn.JsonChain.getNumStr());
    }

    /**
     * Get the latest sequence number for the day for recording option chains
     *
     * @param collection the collection containing today's data
     * @return the current sequence number to use for recording new option chains
     */
    public static int getSequenceNum(MongoCollection<Document> collection) {
        Document document = collection.find(eq(DbColumn.ID.getColumnName(), DbColumn.SequenceNum.getColumnName())).first();
        if (document == null)
            return -1;
        return document.getInteger(DbColumn.SequenceNum.getNumStr());
    }

    /**
     * Add the sequence number document to this collection
     *
     * @param collection the collection containing today's data
     * @param count for the sequence number
     */
    public static void addSequenceNum(MongoCollection<Document> collection, int count) {
        final Document document = new Document();
        final String id = DbColumn.SequenceNum.getColumnName();
        document.put(DbColumn.ID.getColumnName(), id);
        document.put(DbColumn.SequenceNum.getNumStr(), count);
        collection.insertOne(document);
    }

    /**
     * Update the sequence number in this collection for recording option chains
     *
     * @param collection the collection containing today's data
     * @param count to update sequence number to
     */
    public static void updateSequenceNum(MongoCollection<Document> collection, int count) {
        collection.updateOne(
                Filters.eq(DbColumn.ID.getColumnName(), DbColumn.SequenceNum.getColumnName()),
                Updates.set(DbColumn.SequenceNum.getNumStr(), count)
        );
    }
}