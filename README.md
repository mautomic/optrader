# optrader

![master](https://github.com/mautomic/optrader/workflows/master/badge.svg)  

Optrader is a full-scale options trading framework capable of running any customized strategy. It utilizes the TD Ameritrade API 
to continuously query for option chains for a number of user-defined tickers and option expiration dates, and feeds the data to various 
'portfolio managers' that each can run their own strategy and assess entries and exits via signals.

Because the live mode records option chains to a MongoDB instance with an associated sequence number for that day, optrader also supports 
a 'replay' mode, where if enabled via config, can pull saved option chains (in the order they would have actually arrived from the API) from MongoDb to 
rerun any strategies locally in less than a minute.

Optrader uses the library [sleet](https://github.com/mautomic/sleet) to easily and very efficiently retrieve options data from the TD API. It also 
segregates retrieval of data and the actual processing of data, by utilizing the producer-consumer pattern. Actions are placed in a BlockingQueue 
to be processing concurrently while retrieving the next set of option chains.

**NOTE:** This is a generic cut of a private repository that has live working strategies implemented. Figured I can share some of the infrastructure 
I've used to get to this point. :)

## Design

The framework is designed to be very 'plug-and-play' due to the abstractness of the core objects. You can create variations
of each type of these objects if desired.  

Here's some high level documentation of the structure of the framework - 

* PortfolioManager:
    * An object to maintain a single portfolio (which is really a MongoDb collection of positions)
    * A PortfolioManager has the responsibility to re-balance or adjust positions due to constraints of account details/risk metrics
    * A PortfolioManager has ONE strategy tied to it
    
* Strategy:
    * An object that contains a list of entry and exit signals, and evaluates them to start an entry or exit of a position
    * Each strategy is 'run' from its associated PortfolioManager
    * Core trading logic is essentially in these classes
    
* Signal:
    * Single unit of code that will return true or false depending on if a criteria is met
    * There are EntrySignals and ExitSignals, that each take in different arguments
    
* Hedger:
    * Role of a hedger is to decide on how to take an extra position to protect the portfolio against downside
    * This is typically called upon through a strategy or a portfolio manager

* Action:
    * An action can be implemented to have logic run at every scan period
    * Actions are placed in a processing queue to be executed concurrently in sequence that they are entered
    * These are created and called upon in a DataFetcher

* DataFetcher:
    * A runnable that can retrieve different data from sources
    * A LiveDataFetcher will retrieve live data from the TD API
    * A ReplayDataFetcher will retrieve saved option chains from a MongoDb instance
    
The build will spin up a mongo instance on port 27019 and run tests using it, before shutting it down at the end of the build.
    

## Usage
In order to run this program, you must first have an API key. This requires both an account with the
[TDAmeritrade](https://www.tdameritrade.com/home.page), and an account setup on the [developer page](https://developer.tdameritrade.com). 
Once you have the accounts setup, you can create an API key.

You will also need to set up a [MongoDb](https://docs.mongodb.com/manual/installation/) instance, and start it up locally.

The main class takes in a single program argument which is a yaml config file. The main run configuration can be edited to take in `./src/main/resources/config.yml`.

An example of what the config.yml would look like- you can create it off of this template and put it in the src/main/resources directory:
```yaml
credentials:
  apiKey: key
  awsUser: username
  awsPwd: password

email:
  sender: recipient1@email.com
  recipients:
    - recipient1@email.com
    - recipient2@email.com
  eodReportTime: 16:15:00

database:
  host: localhost
  remoteHost: localhost
  port: 27017
  db: dbName

scanner:
  enableReplay: false
  replayDate: 20201130
  batchSize: 10
  strikeCount: 20
  daysToExpirationMax: 100
  timeout: 20
  scanFrequency: 300

tickers:
  - SPY
  - QQQ
  - AAPL
```

The parameters of the scanner can easily be adjusted to run at a different frequency, retrieve a larger/smaller number of strikes, 
further out strikes, or a different set of tickers in the config. 

To run 'replay', just set enableReplay to 'true', and by default the mongo instance running on localhost will be used to find the historical data.
Note that for this to work properly, you should have already run the live fetcher locally through a full day to capture the data.
Say you have this application deployed on a external server (AWS, GCP, whatever it may be), you will have to update 'localhost'
to the actual server running mongo (and don't forget to authorize or have your IP whitelisted - but these controls are up to you). 

```yaml
database:
  host: localhost
  remoteHost: ec2-XX-XX-XX-XXX.us-east-2.compute.amazonaws.com
  port: 27017
  db: dbName

scanner:
  enableReplay: true
  replayDate: 20201130
```

If you would like to disable the AWS SES alerting, just remove awsUser and awsPwd from the config, and the program will realize a 
proper AWS SES service is not configured, and therefore will not send any emails. 