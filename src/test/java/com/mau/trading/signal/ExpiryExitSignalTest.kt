package com.mau.trading.signal

import com.mau.trading.Position
import com.sleet.api.model.Option
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito

/**
 * Test class for an [ExpiryExitSignal]
 *
 * @author mautomic
 */
class ExpiryExitSignalTest {
    @Test
    fun testExpiryExitSignal() {
        val expiryExitSignal = ExpiryExitSignal()
        val position = Mockito.mock(Position::class.java)
        val option = Mockito.mock(Option::class.java)

        Mockito.`when`(position.isOption).thenReturn(false)
        expiryExitSignal.option = option
        expiryExitSignal.position = position
        Assert.assertFalse("Assert false if position is an equity", expiryExitSignal.trigger())

        Mockito.`when`(position.isOption).thenReturn(true)
        Mockito.`when`(position.symbol).thenReturn("symbol")
        Mockito.`when`(option.symbol).thenReturn("symbol")
        Mockito.`when`(option.daysToExpiration).thenReturn(5)
        expiryExitSignal.option = option
        expiryExitSignal.position = position
        Assert.assertFalse("Assert false if option is not expiring within 1 day", expiryExitSignal.trigger())

        Mockito.`when`(position.isOption).thenReturn(true)
        Mockito.`when`(position.symbol).thenReturn("symbol")
        Mockito.`when`(option.symbol).thenReturn("symbol")
        Mockito.`when`(option.daysToExpiration).thenReturn(1)
        expiryExitSignal.option = option
        expiryExitSignal.position = position
        Assert.assertTrue("Assert true if option is expiring within 1 day", expiryExitSignal.trigger())
    }
}