package com.mau.trading.signal

import com.mau.trading.Position
import com.sleet.api.model.Option

/**
 * An implementation of a [Signal] that should be used for triggering a position exit.
 * This type of signal requires at minimum a option and the chain it has come from.
 *
 *
 * Subclasses of ExitSignal can create and contain more parameters if required for their trigger logic
 *
 * @author mautomic
 */
open class ExitSignal : Signal {
    var position: Position? = null
    var option: Option? = null

    // The default implementation of an ExitSignal will trigger
    override fun trigger(): Boolean {
        return true
    }
}