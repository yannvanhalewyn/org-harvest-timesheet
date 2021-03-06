# Org Harvest Timesheet

A command line tool built in Clojure to parse and sync a time sheet kept in [Org mode](https://orgmode.org/). Made for personal use but public in case anyone wonders how this would be achieved.


## The time sheet

This tool aims to parse a time sheet written in Org mode, extract time entries and client/projects from it, calculate the duration and timestamps for them and sync those entries to the time tracker (Harvest).

A time sheet might look like this (how I do it, subject to my own personal preferences):

![time sheet](/.github/timesheet.png)

The file is separated into weeks, then weekdays, then tasks. Each task uses Org tags to specify the project. Use the *%* character to represent a wildcard when searching for a client/project.

## Usage

Install the [clojure tools.deps](https://clojure.org/guides/getting_started) and have a recent version of emacs (> 24) on your PATH.

In order to test this tool immediately, use:

``` shell
export HARVEST_ACCESS_TOKEN=xxx
export HARVEST_ACCOUNT_ID=xxx
clj -m hs.core sync <path/to/timesheet> --default-project clientname-projectname
```

To use this actively, build an uberjar and create a wrapper script on your PATH

``` shell
clj -A:uberjar
cp target/harvest_sync-0.0.1-standalone.jar ~/bin/harvest_sync.jar

# file: ~/bin/harvest
#!/bin/sh

java -jar ~/bin/harvest_sync.jar \
  --harvest-access-token <token> \
  --harvest-account-id <account-id> \
  --default-project my-default-project \
  "$@"
```

These are the supported options:

``` shell
Usage: harvest sync FILENAME <options>

  -p, --default-project PROJECT              Default project
  -w, --week WEEK                       all  The week. One of 'all', 'last' or a weekstring like '20 May'
  -t, --harvest-access-token TOKEN           The Harvest access token, defaults to HARVEST_ACCESS_TOKEN env
  -a, --harvest-account-id ACCOUNT_ID        The Harvest access token, defaults to HARVEST_ACCOUNT_ID env
  -h, --help                                 Show this message
```

## Methodology

- Transform the Org file to JSON
- Parse the JSON into time entries
- Find matching projects for every entry according to the tags - projects are cached in `~/.harvest_sync/cache`
- Fetch existing entries from Harvest in same time range as the entries found in the timesheet
- Diff the local entries with the remote entries
- Display the diff (to add, to delete) and only when user confirms apply it

---

![terminal output example](/.github/terminal_output.png)

## License

None, just steal it.
