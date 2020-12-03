package com.mau.trading.strategy;

import com.mau.trading.PortfolioManager;
import com.mau.trading.Position;
import com.sleet.api.model.Equity;
import com.sleet.api.model.Option;
import com.sleet.api.model.OptionChain;

/**
 * A generic strategy that can be passed into a {@link PortfolioManager} to execute core trading logic.
 * A strategy must ALWAYS have a run method, but enter and exit can be overridden and customized
 * depending on the implementation of the strategy. Methods marked as default DO NOT have to be
 * implemented, and will do nothing if called in this scenario
 *
 * @author mautomic
 */
public interface Strategy {
     void run(OptionChain chain);
     default void enter(Option option, int enterQuantity) {}
     default void enter(Equity equity, int enterQuantity) {}
     default void exit(Position position, Option option, int exitQuantity) {}
     default void hedge(OptionChain chain) {}
}
