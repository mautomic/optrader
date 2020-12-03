package com.mau.trading.signal;

/**
 * A simple {@link ExitSignal} that applies only to option positions. Exit a position if the
 * option will expire today or tomorrow.
 *
 * @author 350rosen
 */
public class ExpiryExitSignal extends ExitSignal {

    @Override
    public boolean trigger() {
        if (option != null && position != null && position.isOption() && position.getSymbol().equals(option.getSymbol()))
            return option.getDaysToExpiration() <= 1;
        return false;
    }
}
