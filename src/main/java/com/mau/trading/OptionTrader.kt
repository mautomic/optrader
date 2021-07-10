package com.mau.trading

import com.sleet.api.service.OptionService
import com.mongodb.client.MongoDatabase
import com.mau.trading.config.ScannerCfg
import java.util.concurrent.Executors
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.lang.InterruptedException
import com.mau.trading.action.Action
import com.mau.trading.config.Config
import com.mau.trading.datafetcher.DataFetcher
import com.mau.trading.datafetcher.ReplayDataFetcher
import java.util.concurrent.TimeUnit
import com.mau.trading.datafetcher.LiveDataFetcher
import com.mau.trading.utility.Constants
import com.mau.trading.utility.Util
import org.apache.logging.log4j.LogManager
import java.lang.Exception
import java.util.concurrent.atomic.AtomicInteger
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * A scanner kicks off a periodically repeating task of retrieving [OptionChain]s
 * for the list of defined tickers from the TD API via a [OptionService]. It then
 * passes them along to an action processing queue for running any trading logic or any
 * other [Action]s which occur concurrently on a separate thread
 *
 * @author mautomic
 */
class OptionTrader(
    private val portfolioManagers: List<PortfolioManager>,
    private val optionService: OptionService,
    private val alerter: Alerter?,
    private val localDb: MongoDatabase,
    private val remoteDb: MongoDatabase?,
    config: Config
) {
    private val currentDate = Util.getDate(0, true)
    private val batchedTickers: List<List<String>>
    private val tickers: List<String>
    private val eodOperationsTime: String = config.getEmailCfg().eodReportTime
    private val maxExpirationDate: String
    private val scannerCfg: ScannerCfg = config.getScannerCfg()
    private val executorService = Executors.newSingleThreadScheduledExecutor()
    private val processingQueue: BlockingQueue<Action> = LinkedBlockingQueue()

    init {
        maxExpirationDate = Util.getMaxExpirationDate(scannerCfg.daysToExpirationMax)
        tickers = config.getTickers()
        batchedTickers = Util.batch(tickers, scannerCfg.batchSize)
    }

    companion object {
        private val LOG = LogManager.getLogger(OptionTrader::class.java)
    }

    /**
     * Starts the option trader by doing three things:
     *
     * 1. Kick off a separate thread for processing [Action]s in a [BlockingQueue]
     *
     * 2. Schedule a type of [DataFetcher] runnable. If its a [LiveDataFetcher], it repeats
     * every X seconds to retrieve [OptionChain]s from the API and places then in the processing
     * queue for trading and recording. If it's a [ReplayDataFetcher] then it reads all option chains
     * from a local/remote mongodb instance in sequence and creates the same actions as the live fetcher.
     *
     * 3. Create a timer task to run end-of-trading-day operations
     *
     * This follows the producer-consumer design pattern, which helps increase throughput
     * significantly, especially if [Action]s take a long time to complete. They can
     * now occur concurrently while the threads spawned by an [ExecutorService] continue
     * to query the data source for new option chains. By default, the [LiveDataFetcher]
     * is used unless replay is explicitly turned on
     */
    fun start() {
        Thread({
            while (true) {
                try {
                    val action = processingQueue.take()
                    action.process()
                } catch (e: InterruptedException) {
                    LOG.error("Interrupted taking an action from the queue", e)
                } catch (e: Exception) {
                    LOG.error("Error processing action", e)
                }
            }
        }, "ActionProcessingThread").start()

        val dataFetcher: DataFetcher
        if (scannerCfg.enableReplay) {
            if (remoteDb == null) {
                LOG.error("Remote db must be connected to run replay...exiting")
                System.exit(1)
            }
            dataFetcher = ReplayDataFetcher(scannerCfg.replayDate, processingQueue, tickers, portfolioManagers, remoteDb!!)
            executorService.schedule(dataFetcher, 0, TimeUnit.SECONDS)
            // Need to sleep this thread, otherwise there is a race condition of checking the next while statement
            // without the processing queue getting any actions added
            sleep(3000)

            // Log status of the number of actions left in queue every seconds
            while (!processingQueue.isEmpty()) {
                LOG.info("Queue draining, still " + processingQueue.size + " actions to go")
                sleep(1000)
            }
            LOG.info("Replay complete...exiting program")
            System.exit(1)

        } else {
            dataFetcher = LiveDataFetcher(
                optionService,
                processingQueue,
                batchedTickers,
                portfolioManagers,
                localDb,
                scannerCfg,
                AtomicInteger(1)
            )
            executorService.scheduleWithFixedDelay(dataFetcher, 0, scannerCfg.scanFrequency.toLong(), TimeUnit.SECONDS)
            scheduleEodOperations()
        }
    }

    private fun sleep(millis: Int) {
        try {
            Thread.sleep(millis.toLong())
        } catch (e: Exception) {
            LOG.error("Thread sleep interrupted", e)
        }
    }

    /**
     * EOD operations to run at 4:15 EST before program exits
     */
    private fun scheduleEodOperations() {
        try {
            val dateFormatter: DateFormat =
                SimpleDateFormat(Constants.DATE_FORMAT_DASHES + Constants.SPACE + Constants.TIME_FORMAT_LESS)
            val date = dateFormatter.parse(currentDate + Constants.SPACE + eodOperationsTime)
            val timer = Timer()
            timer.schedule(object : TimerTask() {
                override fun run() {
                    sendEodReport()
                }
            }, date)
        } catch (e: Exception) {
            LOG.error("Error parsing date", e)
        }
    }

    /**
     * Send a customized EOD report via the [Alerter]
     */
    private fun sendEodReport() {
        // don't send report if we didn't start up with an alerter
        if (alerter == null) return
        val subject = "Subject"
        val body = "Body"
        try {
            alerter.sendEmail(subject, body)
            LOG.info("EOD report sent")
        } catch (e: Exception) {
            LOG.error("Issue sending EOD report", e)
        }
    }
}