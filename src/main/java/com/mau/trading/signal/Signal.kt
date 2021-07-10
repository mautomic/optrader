package com.mau.trading.signal

/**
 * A generic signal that can be triggered
 *
 * @author 350rosen
 */
interface Signal {
    fun trigger(): Boolean
}