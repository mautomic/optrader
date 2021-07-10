package com.mau.trading.strategy

import com.mau.trading.Position
import com.mongodb.client.MongoCollection
import com.mau.trading.signal.EntrySignal
import com.mau.trading.signal.ExitSignal
import com.sleet.api.model.OptionChain
import java.util.HashSet
import java.util.HashMap
import com.mau.trading.utility.DbUtil
import com.mau.trading.utility.Util
import com.sleet.api.model.Option
import org.apache.logging.log4j.LogManager
import org.bson.Document
import java.util.function.Consumer

/**
 * An implementation of an [Strategy] that analyzes the provided [OptionChain]
 * for any unusually high volumes and enters a position if the threshold is breached
 *
 * @author mautomic
 * @author 350rosen
 */
class UnusualOptionsStrategy(
    positions: MongoCollection<Document>,
    private val entrySignals: List<EntrySignal>,
    private val exitSignals: List<ExitSignal>,
) : BaseStrategy(positions) {

    private var latestOptionChain: OptionChain? = null
    private val currentPositions: MutableSet<Position> = HashSet()
    private val latestOptionMap: MutableMap<String, Option> = HashMap()

    companion object {
        private val LOG = LogManager.getLogger(
            UnusualOptionsStrategy::class.java
        )
        private const val STD_DEVIATIONS = 4
    }

    /**
     * Retrieve both call and put maps from the [OptionChain] and pass along a
     * map of strikes for a given date to be marked for high volume
     */
    override fun run(chain: OptionChain?) {
        latestOptionChain = chain
        latestOptionMap.clear()
        latestOptionMap.putAll(Util.flattenOptionChain(chain))

        // Mark high volume call options and enter a position when marked
        val callMap = latestOptionChain!!.callExpDateMap
        callMap.forEach { (date: String?, strikes: Map<String, List<Option>>) -> markHighVolumeOptions(strikes) }

        // Get the collection of positions currently in the position db and check if they need to be exited
        currentPositions.clear()
        currentPositions.addAll(DbUtil.getAllPositions(positions))
        currentPositions.forEach(Consumer { position: Position -> checkExitSignals(position, 1) })
        currentPositions.clear()
        currentPositions.addAll(DbUtil.getAllPositions(positions))
    }

    /**
     * Assesses entry signals for the portfolio with the proposed option. Only enter the position if
     * all signals return true
     */
    fun checkEntrySignals(option: Option?, quantity: Int) {
        for (signal in entrySignals) {
            signal.chain = latestOptionChain
            signal.option = option
            if (!signal.trigger()) return
        }
        enter(option, quantity)
    }

    /**
     * Assesses entry signals for the portfolio with the proposed option. Only exit the position if
     * all signals return true
     */
    fun checkExitSignals(position: Position, quantity: Int) {
        for (signal in exitSignals) {
            signal.position = position
            signal.option = latestOptionMap[position.symbol]
            if (!signal.trigger()) return
        }
        val optionInPosition = latestOptionMap[position.symbol]
        if (optionInPosition == null) {
            LOG.warn("Option " + position.symbol + " is not found in latest option map, aborting exit attempt")
            return
        }
        exit(position, optionInPosition, quantity)
    }

    /**
     * This method filters out options with no volume, calculates the threshold, and
     * adds options breaching that threshold to the set referenced by the [OptionTrader],
     * to be globally visible.
     *
     * An option's volume is considered unusual if it is greater than the mean + multiple
     * standard deviations of all strikes for that particular date. This is more granular
     * than calculating standard deviations on the entire chain for a single ticker.
     *
     * @param strikeMap map containing strikes a keys and their associated [Option]s
     */
    private fun markHighVolumeOptions(strikeMap: Map<String, List<Option>>) {
        val filteredMap: MutableMap<String, Option> = HashMap()
        strikeMap.forEach { (strike: String, options: List<Option>) ->
            val option = options[0]
            if (option.totalVolume > 10 && option.ask > 0.10 && option.bid > 0.10) {
                filteredMap[strike] = option
            }
        }
        val mean = Util.findMean(filteredMap)
        val stdDeviation = Util.findStandardDeviation(filteredMap, mean)
        filteredMap.forEach { (strike: String?, option: Option) ->
            val threshold = mean + STD_DEVIATIONS * stdDeviation
            if (option.totalVolume > 10 && option.totalVolume > threshold.toInt()) {
                // For now, only attempt to enter one contract of an option
                checkEntrySignals(option, 1)
            }
        }
    }
}