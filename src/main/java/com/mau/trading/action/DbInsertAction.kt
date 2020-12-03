package com.mau.trading.action

import com.fasterxml.jackson.databind.ObjectMapper
import com.mau.trading.utility.DbUtil
import com.mongodb.client.MongoCollection
import com.sleet.api.model.OptionChain
import org.bson.Document

/**
 * An implementation of an [Action] that inserts the provided [OptionChain]
 * into the appropriate MongoDB collection
 *
 * @author mautomic
 */
class DbInsertAction(private val optionChain: OptionChain, private val recordingCollection: MongoCollection<Document>,
                     private val mapper: ObjectMapper, private var sequenceNum: Int) : Action {
    override fun process() {
        val ticker = optionChain.symbol
        val jsonChain = mapper.writeValueAsString(optionChain)
        DbUtil.enterOptionChainDb(recordingCollection, ticker, jsonChain, sequenceNum)
    }
}