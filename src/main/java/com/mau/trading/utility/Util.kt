package com.mau.trading.utility

import com.sleet.api.model.Contract
import com.sleet.api.model.Option
import java.text.SimpleDateFormat
import java.util.Calendar
import com.sleet.api.model.OptionChain
import org.apache.commons.lang3.StringUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * Utility class for general methods that can be used across the application
 *
 * @author mautomic
 */
object Util {
    /**
     * Finds a prior date for use of timeseries, can find current date by passing dayCount as 0
     *
     * @return current date
     */
    @JvmStatic
    fun getDate(dayCount: Int, dash: Boolean): String {
        val dtf =
            if (dash)
                DateTimeFormatter.ofPattern(Constants.DATE_FORMAT_DASHES)
            else
                DateTimeFormatter.ofPattern(Constants.DATE_FORMAT)
        val now = LocalDateTime.now().minusDays(dayCount.toLong())
        return dtf.format(now)
    }

    /**
     * Finds a prior date and formats it to use the month abbreviation
     *
     * @return current date
     */
    fun getDatePretty(dayCount: Int): String {
        val dtf = DateTimeFormatter.ofPattern(Constants.DATE_FORMAT_PRETTY)
        val now = LocalDateTime.now().minusDays(dayCount.toLong())
        return dtf.format(now)
    }

    /**
     * Get the formatted date of the furthest option chain we can possibly retrieve
     *
     * @return the date as a String
     */
    fun getMaxExpirationDate(daysToExpiration: Int): String {
        val sdf = SimpleDateFormat(Constants.DATE_FORMAT_DASHES)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, daysToExpiration)
        return sdf.format(cal.time)
    }

    /**
     * Rounds the value to two decimal places
     */
    fun roundVal(value: Double): Double {
        return Math.round(value * 100.0) / 100.0
    }

    /**
     * Break down a list of tickers into batches, defined by the batch size
     *
     * @param tickers to batch
     * @return a list of lists of tickers
     */
    fun batch(tickers: List<String>, batchSize: Int): List<List<String>> {
        val batches: MutableList<List<String>> = ArrayList()
        if (tickers.size < batchSize) {
            batches.add(tickers)
            return batches
        }
        var i = 0
        while (i < tickers.size) {
            batches.add(tickers.subList(i, Math.min(i + batchSize, tickers.size)))
            i += batchSize
        }
        return batches
    }

    /**
     * Takes each individual ticker as an input and if contains invalid characters, normalizes to the simple string.
     * Open question, do we want to maintain a ticker type?  Wondering if this will be useful later.
     *
     * @return a ticker absent of details not consumable by MongoDB
     */
    fun tickerNormalization(ticker: String): String {
        // Check first if ticker is an index by looking for the $ prefix and .X suffix
        if (ticker.startsWith("$") && ticker.endsWith(".X")) {
            val newTicker = ticker.substring(0, ticker.length - 1)
            return newTicker.replace("[^a-zA-Z ]".toRegex(), StringUtils.EMPTY)
        }
        return ticker
    }

    /**
     * Collect all [Option]s from the option chain map
     *
     * @param chain [OptionChain] containing call and put maps
     * @return list of [Option]s
     */
    fun collectOptions(chain: OptionChain): List<Option> {
        val optionList: MutableList<Option> = ArrayList()
        chain.callExpDateMap.forEach { (_: String?, strikes: Map<String?, List<Option>?>) ->
            strikes.forEach { (_: String?, options: List<Option>?) ->
                optionList.addAll(options!!)
            }
        }
        chain.putExpDateMap.forEach { (_: String?, strikes: Map<String?, List<Option>?>) ->
            strikes.forEach { (_: String?, options: List<Option>?) ->
                optionList.addAll(options!!)
            }
        }
        return optionList
    }

    /**
     * Flatten the map structure of [OptionChain]s into a standard map
     *
     * @param chain the [OptionChain]
     * @return map of symbols and their associated [Option]s
     */
    fun flattenOptionChain(chain: OptionChain): Map<String, Option> {
        val optionMap: MutableMap<String, Option> = HashMap()
        chain.callExpDateMap.forEach { (_: String?, strikes: Map<String?, List<Option>>) ->
            strikes.forEach { (_: String?, options: List<Option>) ->
                val option = options[0]
                optionMap[option.symbol] = option
            }
        }
        chain.putExpDateMap.forEach { (_: String?, strikes: Map<String?, List<Option>>) ->
            strikes.forEach { (_: String?, options: List<Option>) ->
                val option = options[0]
                optionMap[option.symbol] = option
            }
        }
        return optionMap
    }

    /**
     * Get the ticker from the option symbol string
     *
     * @param optionSymbol to extract the ticker from
     * @return ticker
     */
    fun getTickerFromOptionSymbol(optionSymbol: String): String {
        return optionSymbol.split(Constants.UNDERSCORE).toTypedArray()[0]
    }

    /**
     * Parse out the contract type (call/put), strike, and expiration date from the option symbol string
     *
     * @param optionSymbol to extract data from
     * @return an array of the data
     */
    fun parseDataFromOptionSymbol(optionSymbol: String): Array<String> {
        val items = optionSymbol.split(Constants.UNDERSCORE).toTypedArray()
        val strike: String
        val date: String
        val contractType: Contract
        if (items[1].contains("C")) {
            contractType = Contract.CALL
            date = items[1].split("C").toTypedArray()[0]
            strike = items[1].split("C").toTypedArray()[1]
        } else {
            contractType = Contract.PUT
            date = items[1].split("P").toTypedArray()[0]
            strike = items[1].split("P").toTypedArray()[1]
        }
        val updatedDate = "20" + date.substring(4, 6) + "-" + date.substring(0, 2) + "-" + date.substring(2, 4)
        return arrayOf(strike, updatedDate, contractType.name)
    }

    /**
     * Get the tranche for an option based on how many days to expiration are left
     *
     * @return a string indicating SHORT, LONG, or MONTHLY
     */
    fun getTrancheForOption(option: Option): String {
        return if (option.daysToExpiration <= Constants.SHORT_THRESHOLD_DAYS)
            Constants.SHORT
        else if (option.daysToExpiration > Constants.SHORT_THRESHOLD_DAYS && option.daysToExpiration < Constants.LONG_THRESHOLD_DAYS)
            Constants.LONG
        else
            Constants.MONTHLY
    }

    /**
     * Finds the mean of the given option chain
     *
     * @return mean of the map
     */
    fun findMean(map: Map<String, Option>): Double {
        val totalVolume = AtomicLong(0)
        map.forEach { (_: String, option: Option) -> totalVolume.addAndGet(option.totalVolume.toLong()) }
        return roundVal(totalVolume.get().toDouble() / map.size)
    }

    /**
     * Finds the standard deviation of the given option chain
     *
     * @return standard deviation of the filteredMap
     */
    fun findStandardDeviation(map: Map<String, Option>, mean: Double): Double {
        val variance = AtomicLong()
        map.forEach { (_: String, option: Option) ->
            val difference = mean - option.totalVolume
            variance.addAndGet(difference.toLong() * difference.toLong())
        }
        val avgVariance = variance.get().toDouble() / map.size
        return roundVal(sqrt(avgVariance))
    }

    /**
     * Finds the average price of inputs
     *
     * @return average price
     */
    @JvmStatic
    fun createAveragePrice(originalQuantity: Int, originalPrice: Double, newQuantity: Int, newPrice: Double): Double {
        return (Math.abs(originalQuantity) * originalPrice + newQuantity * newPrice) / (Math.abs(originalQuantity) + newQuantity)
    }
}