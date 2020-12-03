package com.mau.trading.config

import com.sksamuel.hoplite.ConfigLoader
import java.nio.file.Path

data class MainCfg(val credentials: Credentials,
                   val email: EmailCfg,
                   val scanner: ScannerCfg,
                   val tickers: List<String>,
                   val database: DatabaseCfg)

data class Credentials(val apiKey: String,
                       val awsUser: String,
                       val awsPwd: String)

data class DatabaseCfg(val host: String = "localhost",
                       val remoteHost: String = "",
                       val port: Int = 27017,
                       val db: String)

data class ScannerCfg(val enableReplay: Boolean = false,
                      val replayDate: String,
                      val batchSize: Int,
                      val strikeCount: String,
                      val daysToExpirationMax: Int,
                      val timeout: Int,
                      val scanFrequency: Int)

data class EmailCfg(val sender: String,
                    val recipients: List<String>,
                    val eodReportTime: String)

/**
 * A config loader to read and store the provided properties from file via a [ConfigLoader]
 *
 * @author mautomic
 */
class Config constructor(file: String) {

    private val cfg : MainCfg = ConfigLoader().loadConfigOrThrow(Path.of(file))

    fun getDatabaseCfg(): DatabaseCfg {
        return cfg.database
    }

    fun getScannerCfg(): ScannerCfg {
        return cfg.scanner
    }

    fun getTickers(): List<String> {
        return cfg.tickers
    }

    fun getEmailCfg(): EmailCfg {
        return cfg.email;
    }

    fun getCredentials(): Credentials {
        return cfg.credentials;
    }
}