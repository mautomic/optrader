package com.mau.trading.strategy;

import com.mau.trading.hedge.Hedger;
import com.mau.trading.OptionTrader;
import com.mau.trading.Position;
import com.mau.trading.utility.DbUtil;
import com.mau.trading.utility.Util;
import com.mau.trading.signal.EntrySignal;
import com.mau.trading.signal.ExitSignal;
import com.sleet.api.model.Option;
import com.sleet.api.model.OptionChain;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An implementation of an {@link Strategy} that analyzes the provided {@link OptionChain}
 * for any unusually high volumes and enters a position if the threshold is breached
 *
 * @author mautomic
 * @author 350rosen
 */
public class UnusualOptionsStrategy extends BaseStrategy {

    private static final Logger LOG = LogManager.getLogger(UnusualOptionsStrategy.class);
    private static final int STD_DEVIATIONS = 4;

    private OptionChain latestOptionChain;
    private final Set<Position> currentPositions = new HashSet<>();
    private final Map<String, Option> latestOptionMap = new HashMap<>();
    private final List<EntrySignal> entrySignals;
    private final List<ExitSignal> exitSignals;
    private final List<String> tickers;
    private final List<Hedger> hedgers;


    public UnusualOptionsStrategy(MongoCollection<Document> positions, List<EntrySignal> entrySignals,
                                  List<ExitSignal> exitSignals, List<String> tickers, List<Hedger> hedgers) {
        super(positions);
        this.entrySignals = entrySignals;
        this.exitSignals = exitSignals;
        this.tickers = tickers;
        this.hedgers = hedgers;
    }

    /**
     * Retrieve both call and put maps from the {@link OptionChain} and pass along a
     * map of strikes for a given date to be marked for high volume
     */
    @Override
    public void run(OptionChain chain) {
        latestOptionChain = chain;
        latestOptionMap.clear();
        latestOptionMap.putAll(Util.flattenOptionChain(chain));

        // Mark high volume call options and enter a position when marked
        final Map<String, Map<String, List<Option>>> callMap = latestOptionChain.getCallExpDateMap();
        callMap.forEach((date, strikes) -> markHighVolumeOptions(strikes));

        // Get the collection of positions currently in the position db and check if they need to be exited
        currentPositions.clear();
        currentPositions.addAll(DbUtil.getAllPositions(positions));
        currentPositions.forEach(position -> checkExitSignals(position, 1));

        currentPositions.clear();
        currentPositions.addAll(DbUtil.getAllPositions(positions));
        // Run hedging logic with current chain after latest entries/exits
        hedge(chain);
    }


    @Override
    public void hedge(OptionChain chain) {
        for(Hedger hedger : hedgers) {
            hedger.hedge(positions, chain, tickers, currentPositions, 1);
        }
    }

    /**
     * Assesses entry signals for the portfolio with the proposed option. Only enter the position if
     * all signals return true
     */
    public void checkEntrySignals(final Option option, int quantity) {
        for(EntrySignal signal : entrySignals) {
            signal.setChain(latestOptionChain);
            signal.setOption(option);
            if (!signal.trigger())
                return;
        }
        enter(option, quantity);
    }

    /**
     * Assesses entry signals for the portfolio with the proposed option. Only exit the position if
     * all signals return true
     */
    public void checkExitSignals(final Position position, int quantity) {
        for(ExitSignal signal : exitSignals) {
            signal.setPosition(position);
            signal.setOption(latestOptionMap.get(position.getSymbol()));
            if (!signal.trigger())
                return;
        }
        final Option optionInPosition = latestOptionMap.get(position.getSymbol());
        if (optionInPosition == null) {
            LOG.warn("Option " + position.getSymbol() + " is not found in latest option map, aborting exit attempt");
            return;
        }
        exit(position, optionInPosition, quantity);
    }

    /**
     * This method filters out options with no volume, calculates the threshold, and
     * adds options breaching that threshold to the set referenced by the {@link OptionTrader},
     * to be globally visible.
     * <p>
     * An option's volume is considered unusual if it is greater than the mean + multiple
     * standard deviations of all strikes for that particular date. This is more granular
     * than calculating standard deviations on the entire chain for a single ticker.
     *
     * @param strikeMap map containing strikes a keys and their associated {@link Option}s
     */
    private void markHighVolumeOptions(final Map<String, List<Option>> strikeMap) {
        final Map<String, Option> filteredMap = new HashMap<>();
        strikeMap.forEach((strike, options) -> {
            final Option option = options.get(0);
            if (option.getTotalVolume() > 10 && option.getAsk() > 0.10 && option.getBid() > 0.10) {
                filteredMap.put(strike, option);
            }
        });

        double mean = Util.findMean(filteredMap);
        double stdDeviation = Util.findStandardDeviation(filteredMap, mean);

        filteredMap.forEach((strike, option) -> {
            double threshold = mean + (STD_DEVIATIONS * stdDeviation);
            if (option.getTotalVolume() > 10 && option.getTotalVolume() > (int)threshold) {
                // For now, only attempt to enter one contract of an option
                checkEntrySignals(option, 1);
            }
        });
    }
}