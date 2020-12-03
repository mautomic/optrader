package com.mau.trading.datafetcher

/**
 * Represents a generic data fetcher that can be used to retrieve data from
 * an external source
 *
 * @author mautomic
 */
interface DataFetcher : Runnable {
    override fun run()
}