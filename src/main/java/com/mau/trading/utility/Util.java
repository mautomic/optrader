package com.mau.trading.utility;

import com.sleet.api.model.Contract;
import com.sleet.api.model.Option;
import com.sleet.api.model.OptionChain;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for general methods that can be used across the application
 *
 * @author mautomic
 */
public class Util {

    /**
     * Finds a prior date for use of timeseries, can find current date by passing dayCount as 0
     *
     * @return current date
     */
    public static String getDate(int dayCount, boolean dash) {
        DateTimeFormatter dtf = dash ? DateTimeFormatter.ofPattern(Constants.DATE_FORMAT_DASHES)
                : DateTimeFormatter.ofPattern(Constants.DATE_FORMAT);
        LocalDateTime now = LocalDateTime.now().minusDays(dayCount);
        return dtf.format(now);
    }

    /**
     * Finds a prior date and formats it to use the month abbreviation
     *
     * @return current date
     */
    public static String getDatePretty(int dayCount) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(Constants.DATE_FORMAT_PRETTY);
        LocalDateTime now = LocalDateTime.now().minusDays(dayCount);
        return dtf.format(now);
    }

    /**
     * Get the formatted date of the furthest option chain we can possibly retrieve
     *
     * @return the date as a String
     */
    public static String getMaxExpirationDate(int daysToExpiration) {
        final SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATE_FORMAT_DASHES);
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, daysToExpiration);
        return sdf.format(cal.getTime());
    }

    /**
     * Rounds the value to two decimal places
     */
    public static double roundVal(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Break down a list of tickers into batches, defined by the batch size
     *
     * @param tickers to batch
     * @return a list of lists of tickers
     */
    public static List<List<String>> batch(List<String> tickers, int batchSize) {
        final List<List<String>> batches = new ArrayList<>();
        if (tickers.size() < batchSize) {
            batches.add(tickers);
            return batches;
        }
        for (int i=0; i<tickers.size(); i+=batchSize)
            batches.add(tickers.subList(i, Math.min(i+batchSize, tickers.size())));
        return batches;
    }

    /**
     * Takes each individual ticker as an input and if contains invalid characters, normalizes to the simple string.
     * Open question, do we want to maintain a ticker type?  Wondering if this will be useful later.
     *
     * @return a ticker absent of details not consumable by MongoDB
     */
    public static String tickerNormalization(final String ticker) {
        // Check first if ticker is an index by looking for the $ prefix and .X suffix
        if (ticker.startsWith("$") && ticker.endsWith(".X")) {
            String newTicker = ticker.substring(0, ticker.length() - 1);
            return newTicker.replaceAll("[^a-zA-Z ]", StringUtils.EMPTY);
        }
        return ticker;
    }

    /**
     * Collect all {@link Option}s from the option chain map
     *
     * @param chain {@link OptionChain} containing call and put maps
     * @return list of {@link Option}s
     */
    public static List<Option> collectOptions(final OptionChain chain) {

        final List<Option> optionList = new ArrayList<>();
        chain.getCallExpDateMap().forEach((date, strikes) -> strikes.forEach((strike, options) -> optionList.addAll(options)));
        chain.getPutExpDateMap().forEach((date, strikes) -> strikes.forEach((strike, options) -> optionList.addAll(options)));
        return optionList;
    }

    /**
     * Flatten the map structure of {@link OptionChain}s into a standard map
     *
     * @param chain the {@link OptionChain}
     * @return map of symbols and their associated {@link Option}s
     */
    public static Map<String, Option> flattenOptionChain(final OptionChain chain) {

        final Map<String, Option> optionMap = new HashMap<>();
        chain.getCallExpDateMap().forEach((date, strikes) ->
                strikes.forEach((strike, options) -> {
                    Option option = options.get(0);
                    optionMap.put(option.getSymbol(), option);
        }));
        chain.getPutExpDateMap().forEach((date, strikes) ->
                strikes.forEach((strike, options) -> {
                    Option option = options.get(0);
                    optionMap.put(option.getSymbol(), option);
        }));
        return optionMap;
    }

    /**
     * Get the ticker from the option symbol string
     *
     * @param optionSymbol to extract the ticker from
     * @return ticker
     */
    public static String getTickerFromOptionSymbol(final String optionSymbol) {
        return optionSymbol.split(Constants.UNDERSCORE)[0];
    }

    /**
     * Parse out the contract type (call/put), strike, and expiration date from the option symbol string
     *
     * @param optionSymbol to extract data from
     * @return an array of the data
     */
    public static String[] parseDataFromOptionSymbol(final String optionSymbol) {
        final String[] items = optionSymbol.split(Constants.UNDERSCORE);
        String strike;
        String date;
        Contract contractType;
        if (items[1].contains("C")) {
            contractType = Contract.CALL;
            date = items[1].split("C")[0];
            strike = items[1].split("C")[1];
        } else {
            contractType = Contract.PUT;
            date = items[1].split("P")[0];
            strike = items[1].split("P")[1];
        }

        String updatedDate = "20" + date.substring(4, 6) + "-" + date.substring(0, 2) + "-" + date.substring(2, 4);
        return new String[] { strike, updatedDate, contractType.name() };
    }

    /**
     * Get the tranche for an option based on how many days to expiration are left
     *
     * @return a string indicating SHORT, LONG, or MONTHLY
     */
    public static String getTrancheForOption(Option option) {
        if (option.getDaysToExpiration() <= Constants.SHORT_THRESHOLD_DAYS)
            return Constants.SHORT;
        else if (option.getDaysToExpiration() > Constants.SHORT_THRESHOLD_DAYS && option.getDaysToExpiration() < Constants.LONG_THRESHOLD_DAYS)
            return Constants.LONG;
        else
            return Constants.MONTHLY;
    }

    /**
     * Finds the mean of the given option chain
     *
     * @return mean of the map
     */
    public static double findMean(Map<String, Option> map) {
        final AtomicLong totalVolume = new AtomicLong(0);
        map.forEach((strike, option) -> totalVolume.addAndGet(option.getTotalVolume()));
        return roundVal((double)totalVolume.get() / map.size());
    }

    /**
     * Finds the standard deviation of the given option chain
     *
     * @return standard deviation of the filteredMap
     */
    public static double findStandardDeviation(Map<String, Option> map, double mean) {
        final AtomicLong variance = new AtomicLong();
        map.forEach((strike, option) -> {
            double difference = mean - option.getTotalVolume();
            variance.addAndGet((long)difference * (long)difference);
        });

        final double avgVariance = (double)variance.get() / map.size();
        return roundVal(Math.sqrt(avgVariance));
    }

    /**
     * Finds the average price of inputs
     *
     * @return average price
     */
    public static double createAveragePrice(int originalQuantity, double originalPrice, int newQuantity, double newPrice) {
        return ((Math.abs(originalQuantity) * originalPrice) + (newQuantity * newPrice)) / (Math.abs(originalQuantity) + newQuantity);
    }
}