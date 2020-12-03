package com.mau.trading;

import com.mau.trading.action.Action;
import com.mau.trading.config.Config;
import com.mau.trading.config.ScannerCfg;
import com.mau.trading.datafetcher.DataFetcher;
import com.mau.trading.datafetcher.LiveDataFetcher;
import com.mau.trading.datafetcher.ReplayDataFetcher;
import com.mau.trading.utility.Constants;
import com.mau.trading.utility.Util;
import com.mongodb.client.MongoDatabase;
import com.sleet.api.model.OptionChain;
import com.sleet.api.service.OptionService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A scanner kicks off a periodically repeating task of retrieving {@link OptionChain}s
 * for the list of defined tickers from the TD API via a {@link OptionService}. It then
 * passes them along to an action processing queue for running any trading logic or any
 * other {@link Action}s which occur concurrently on a separate thread
 *
 * @author mautomic
 */
public class OptionTrader {

    private static final Logger LOG = LogManager.getLogger(OptionTrader.class);
    private final String currentDate = Util.getDate(0, true);

    private final List<PortfolioManager> portfolioManagers;
    private final OptionService optionService;
    private final Alerter alerter;
    private final MongoDatabase localDb;
    private final MongoDatabase remoteDb;
    private final List<List<String>> batchedTickers;
    private final List<String> tickers;
    private final String eodOperationsTime;
    private final String maxExpirationDate;
    private final ScannerCfg scannerCfg;

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final BlockingQueue<Action> processingQueue = new LinkedBlockingQueue<>();

    public OptionTrader(List<PortfolioManager> portfolioManagers,
                        OptionService optionService,
                        Alerter alerter,
                        MongoDatabase localDb,
                        MongoDatabase remoteDb,
                        Config config) {

        this.portfolioManagers = portfolioManagers;
        this.optionService = optionService;
        this.alerter = alerter;
        this.eodOperationsTime = config.getEmailCfg().getEodReportTime();
        this.localDb = localDb;
        this.remoteDb = remoteDb;
        this.scannerCfg = config.getScannerCfg();
        this.maxExpirationDate = Util.getMaxExpirationDate(scannerCfg.getDaysToExpirationMax());
        this.tickers = config.getTickers();
        this.batchedTickers = Util.batch(tickers, scannerCfg.getBatchSize());
    }

    /**
     * Starts the option trader by doing three things:
     * <p>
     * 1. Kick off a separate thread for processing {@link Action}s in a {@link BlockingQueue}
     * <p>
     * 2. Schedule a type of {@link DataFetcher} runnable. If its a {@link LiveDataFetcher}, it repeats
     * every X seconds to retrieve {@link OptionChain}s from the API and places then in the processing
     * queue for trading and recording. If it's a {@link ReplayDataFetcher} then it reads all option chains
     * from a local/remote mongodb instance in sequence and creates the same actions as the live fetcher.
     * <p>
     * 3. Create a timer task to run end-of-trading-day operations
     * <p>
     * This follows the producer-consumer design pattern, which helps increase throughput
     * significantly, especially if {@link Action}s take a long time to complete. They can
     * now occur concurrently while the threads spawned by an {@link ExecutorService} continue
     * to query the data source for new option chains. By default, the {@link LiveDataFetcher}
     * is used unless replay is explicitly turned on
     */
    public void start() {
        new Thread(() -> {
            while(true) {
                try {
                    final Action action = processingQueue.take();
                    action.process();
                } catch (InterruptedException e) {
                    LOG.error("Interrupted taking an action from the queue", e);
                } catch (Exception e) {
                    LOG.error("Error processing action", e);
                }
            }
        }, "ActionProcessingThread").start();

        DataFetcher dataFetcher;
        // ReplayDataFetcher
        if (scannerCfg.getEnableReplay()) {
            if (remoteDb == null) {
                LOG.error("Remote db must be connected to run replay...exiting");
                System.exit(1);
            }
            dataFetcher = new ReplayDataFetcher(scannerCfg.getReplayDate(), processingQueue, tickers, portfolioManagers, remoteDb);
            executorService.schedule(dataFetcher, 0, TimeUnit.SECONDS);
            // Need to sleep this thread, otherwise there is a race condition of checking the next while statement
            // without the processing queue getting any actions added
            sleep(3000);

            // Log status of the number of actions left in queue every seconds
            while(!processingQueue.isEmpty()) {
                LOG.info("Queue draining, still " + processingQueue.size() + " actions to go");
                sleep(1000);
            }
            LOG.info("Replay complete...exiting program");
            System.exit(1);
        }
        // LiveDataFetcher
        else {
            dataFetcher = new LiveDataFetcher(optionService, processingQueue, batchedTickers, portfolioManagers, localDb, scannerCfg, new AtomicInteger(1));
            executorService.scheduleWithFixedDelay(dataFetcher, 0, scannerCfg.getScanFrequency(), TimeUnit.SECONDS);
            scheduleEodOperations();
        }
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch(Exception e) {
            LOG.error("Thread sleep interrupted", e);
        }
    }

    /**
     * EOD operations to run at 4:15 EST before program exits
     */
    private void scheduleEodOperations() {
        try {
            DateFormat dateFormatter = new SimpleDateFormat(Constants.DATE_FORMAT_DASHES + Constants.SPACE + Constants.TIME_FORMAT_LESS);
            Date date = dateFormatter.parse(currentDate + Constants.SPACE + eodOperationsTime);
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    sendEodReport();
                }
            }, date);
        } catch (Exception e) {
            LOG.error("Error parsing date", e);
        }
    }

    /**
     * Send a customized EOD report via the {@link Alerter}
     */
    private void sendEodReport() {
        // don't send report if we didn't start up with an alerter
        if (alerter == null)
            return;

        final String subject = "Subject";
        final String body = "Body";

        try {
            alerter.sendEmail(subject, body);
            LOG.info("EOD report sent");
        } catch(Exception e) {
            LOG.error("Issue sending EOD report", e);
        }
    }
}