package com.mau.trading.signal

import com.sleet.api.model.Option
import com.sleet.api.model.OptionChain

/**
 * An implementation of a [Signal] that should be used for triggering a position entry.
 * This type of signal requires at minimum a option and the chain it has come from.
 *
 *
 * Subclasses of EntrySignal can create and contain more parameters if required for their trigger logic
 *
 * @author mautomic
 */
class EntrySignal : Signal {
    var option: Option? = null
    var chain: OptionChain? = null

    // The default implementation of an EntrySignal will trigger
    override fun trigger(): Boolean {
        return true
    }
}