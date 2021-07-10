package com.mau.trading.signal

/**
 * A simple [ExitSignal] that applies only to option positions. Exit a position if the
 * option will expire today or tomorrow.
 *
 * @author 350rosen
 */
class ExpiryExitSignal : ExitSignal() {
    override fun trigger(): Boolean {
        return if (option != null && position != null && position!!.isOption && position!!.symbol == option!!.symbol)
            option!!.daysToExpiration <= 1
        else false
    }
}