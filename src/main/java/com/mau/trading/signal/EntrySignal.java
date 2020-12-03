package com.mau.trading.signal;

import com.sleet.api.model.Option;
import com.sleet.api.model.OptionChain;

/**
 * An implementation of a {@link Signal} that should be used for triggering a position entry.
 * This type of signal requires at minimum a option and the chain it has come from.
 * <p>
 * Subclasses of EntrySignal can create and contain more parameters if required for their trigger logic
 *
 * @author mautomic
 */
public class EntrySignal implements Signal {

    protected Option option;
    protected OptionChain chain;

    // The default implementation of an EntrySignal will trigger
    @Override
    public boolean trigger() {
        return true;
    }

    public void setOption(Option option) {
        this.option = option;
    }

    public void setChain(OptionChain chain) {
        this.chain = chain;
    }
}
