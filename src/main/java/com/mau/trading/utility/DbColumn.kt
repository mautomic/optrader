package com.mau.trading.utility

/**
 * An enum for the column definitions that will be inserted into the database
 *
 * @author 350rosen
 */
enum class DbColumn(val columnName: String, val numStr: String) {

    // Main columns
    ID("_id", "0"),
    LastPrice("lastPrice", "1"),
    TotalVolume("totalVolume", "2"),
    Volatility("volatility", "3"),
    Delta("delta", "4"),
    Gamma("gamma", "5"),
    Theta("theta", "6"),
    Vega("vega", "7"),
    OpenInterest("openInterest", "8"),
    StrikePrice("strikePrice", "9"),
    DaysExpiry("daysExpiration", "10"),
    DatePulled("datePulled", "11"),
    Weekly("weekly", "12"),
    Quantity("qty", "13"),
    JsonChain("jsonChain", "23"),

    // Portfolio Manager columns
    BuyNotional("buyNotional", "14"),
    CurrentNotional("currentNotional", "15"),
    BuyPrice("buyPrice", "16"),
    ClosePrice("closePrice", "17"),
    OpenCloseIndicator("openClose", "18"),
    Commission("commission", "19"),
    UnrealizedPnL("unrealizedPnL", "21"),
    RealizedPnL("realizedPnL", "22"),
    SequenceNum("sequenceNum", "24"),

    // Cross-method columns
    ValueCalculation("valueCalculation", "20");
}