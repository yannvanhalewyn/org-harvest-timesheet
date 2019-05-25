# Org Harvest Timesheet

A command line tool built in Clojure to parse and sync a time sheet kept in [Org mode](https://orgmode.org/). Made for personal use but public in case anyone wonders how this would be achieved.


## The time sheet

This tool aims to parse a time sheet written in Org mode, extract time entries and client/projects from it, calculate the duration and timestamps for them and sync those entries to the time tracker (Harvest).

A time sheet might look like this (how I do it, subject to my own personal preferences):

![time sheet](/.github/timesheet.png)

The file is separated into weeks, then into weekdays, then tasks. Each task uses Org tags to specify the project. Every tag will be concatenated with a regex wildcard (.*) in order to search through harvest client and projects to find the correct project.

## Usage

Install the [clojure tools.deps](https://clojure.org/guides/getting_started) and have a recent version of emacs (> 24) on your PATH.

In order to use this tool, run it with:

``` shell
export HARVEST_ACCESS_TOKEN=xxx
export HARVEST_ACCOUNT_ID=xxx
clj -m hs.core sync <path/to/timesheet> --default-project clientname-projectname
```

Or compile the uberjar and add a script to your path

``` shell
clj -A:uberjar
# created target/harvest_sync-0.0.1-standalone.jar
```

And create a wrapper script on your PATH for easy access

``` shell
#!/bin/sh

java -jar <path/to/built/uberjar> sync <path/to/your/timesheet>
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
