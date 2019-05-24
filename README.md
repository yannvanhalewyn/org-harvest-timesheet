# Org Harvest Timesheet

A command line tool built in Clojure to parse and sync a time sheet kept in [Org mode](https://orgmode.org/). Made for personal use but public in case anyone wonders how this would be achieved.


## The time sheet

This tool aims to parse a time sheet written in Org mode, extract time entries and client/projects from it, calculate the duration and timestamps for them and sync those entries to the time tracker (Harvest).

A time sheet might look like this (how I do it, subject to my own personal preferences):

![time sheet](/resources/screenshot.png)

The file is separated into weeks, then into weekdays, then tasks. Each task uses Org tags to specify the project. Every tag will be concatenated with a regex wildcard (.*) in order to search through harvest client and projects to find the correct project.

## Usage

In order to use this tool, run it with:

``` shell
clj -m hs.core sync <path/to/timesheet>
```

This will:

- Transform the Org file to JSON
- Parse the JSON into time entries
- Find matching projects for every entry according to the tags
- Fetch existing entries from harvest in same time range as the existing sheet
- If any are found in that time range will ask to delete them (or will exit if locked entries are in that range)
- If we made it this far, push those entries to Harvest.

## License

None, just steal it.
