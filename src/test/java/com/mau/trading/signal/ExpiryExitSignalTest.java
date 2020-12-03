package com.mau.trading.signal;

import com.mau.trading.Position;
import com.sleet.api.model.Option;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Test class for an {@link ExpiryExitSignal}
 *
 * @author mautomic
 */
public class ExpiryExitSignalTest {

    @Test
    public void testExpiryExitSignal() {

        ExpiryExitSignal expiryExitSignal = new ExpiryExitSignal();

        Position position = Mockito.mock(Position.class);
        Option option = Mockito.mock(Option.class);

        Mockito.when(position.isOption()).thenReturn(false);
        expiryExitSignal.setOption(option);
        expiryExitSignal.setPosition(position);

        Assert.assertFalse("Assert false if position is an equity", expiryExitSignal.trigger());

        Mockito.when(position.isOption()).thenReturn(true);
        Mockito.when(position.getSymbol()).thenReturn("symbol");
        Mockito.when(option.getSymbol()).thenReturn("symbol");
        Mockito.when(option.getDaysToExpiration()).thenReturn(5);

        expiryExitSignal.setOption(option);
        expiryExitSignal.setPosition(position);

        Assert.assertFalse("Assert false if option is not expiring within 1 day", expiryExitSignal.trigger());

        Mockito.when(position.isOption()).thenReturn(true);
        Mockito.when(position.getSymbol()).thenReturn("symbol");
        Mockito.when(option.getSymbol()).thenReturn("symbol");
        Mockito.when(option.getDaysToExpiration()).thenReturn(1);

        expiryExitSignal.setOption(option);
        expiryExitSignal.setPosition(position);

        Assert.assertTrue("Assert true if option is expiring within 1 day", expiryExitSignal.trigger());
    }
}
