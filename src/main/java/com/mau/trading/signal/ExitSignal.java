package com.mau.trading.signal;

import com.mau.trading.Position;
import com.sleet.api.model.Option;

/**
 * An implementation of a {@link Signal} that should be used for triggering a position exit.
 * This type of signal requires at minimum a option and the chain it has come from.
 * <p>
 * Subclasses of ExitSignal can create and contain more parameters if required for their trigger logic
 *
 * @author mautomic
 */
public class ExitSignal implements Signal {

    protected Position position;
    protected Option option;

    // The default implementation of an ExitSignal will trigger
    @Override
    public boolean trigger() {
        return true;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public void setOption(Option option) {
        this.option = option;
    }
}
