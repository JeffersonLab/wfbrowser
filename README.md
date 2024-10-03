# Waveform Browser (wfbrowser)

A Java EE 8 web application for displaying timeseries data collected at CEBAF. This application is build using
the [Smoothness](https://github.com/JeffersonLab/smoothness) web template.

![Screenshot](https://github.com/JeffersonLab/wfbrowser/raw/master/screenshot.png?raw=true "Screenshot")

## Overview

The waveform viewer presents timeseries data that is saved in TSV format while also showing a timeline view of when and
where data was collected. Originally written to display C100 RF waveform records collected by the harvester daemon, it
now also supports accelerometer and BPM waveform records.

## Quick Start with Compose

1. Grab project
    ```bash
    git clone https://github.com/JeffersonLab/wfbrowser
    cd rfdashboard
    ```
2. Connect to JLab network either directly or through VPN.  Some services have not yet been containerized.
3. Launch Compose.  (No images available on dockerhub ... yet.)
    ```bash
    docker compose -f build.yml up
    ```

**Note**: Login with demo username "dev1" and password "password" to see the admin interface.

**Note**: Logout functionality does not work unless you update the KEYCLOAK_FRONT_END_SERVER in the deps.yml
and docker-compose.yml to the hostname of your development machine due to an upstream issue.  More comments in those
files.

**Note**: Demonstration data is pre-populated for 2024-03-15 and/or 2024-03-16.  Use these links to see that data:
* [RF data](http://localhost:8080/wfbrowser/graph?begin=2024-03-15+00:00:00&end=2024-03-17+00:00:00&eventId=1&system=rf&seriesSet=GDR%20Trip&location=0L04&location=1L22&location=2L22)
* [BPM data](http://localhost:8080/wfbrowser/graph?begin=2024-03-15+00:00:00&end=2024-03-16+00:00:00&eventId=25&system=bpm&series=E.%20Jitter%20Perc&location=cebaf)
* [Accelerometer data](http://localhost:8080/wfbrowser/graph?begin=2024-03-16+00:00:00&end=2024-03-17+00:00:00&eventId=16&system=acclrm&series=X-Axis&location=1L01&location=1L13&location=1L27)


**See**: [Docker Compose Strategy](https://gist.github.com/slominskir/a7da801e8259f5974c978f9c3091d52c) developed by
Ryan Slominski.


## Install
This application requires a Java 11+ JVM and standard library in addition to a Java EE 8+ application server.  This
application has been developed and tested with Wildfly.

1. Install service [dependencies](https://github.com/JeffersonLab/wfbrowser/blob/master/deps.yml)
2. Download [Wildfly 26.1.3](https://www.wildfly.org/downloads/)
3. [Configure](https://github.com/JeffersonLab/wfbrowser#configure) Wildfly and start it
4. Download [wfbrowser.war](https://github.com/JeffersonLab/rfdashboard/releases) and deploy it to Wildfly
5. Connect to JLab network or VPN (current version uses internal CDN).
5. Navigate your web browser to localhost:8080/wfbrowser

## Configure

### Configtime
Wildfly must be pre-configured before the first deployment of the app.  The
[wildfly bash scripts](https://github.com/JeffersonLab/wildfly#configure) can be used to accomplish this.  See the
[Dockerfile](https://github.com/JeffersonLab/wfbrowser/blob/master/Dockerfile) for an example.

### Runtime
Uses the [Smoothness Environment Variables](https://github.com/JeffersonLab/smoothness#global-runtime) plus the
following application specific:

| Name           | Description                                                                         |
|----------------|-------------------------------------------------------------------------------------|
| WFB_ADMIN_ROLE | Needed to configure keycloak container for development use                          |
| WFB_POST_ROLE  | Needed to configure keycloak container for development use                          |
| WFB_DATA_DIR   | (Optional) Directory with waveform data. Defaults to `/usr/opsdata/waveforms/data`. |


### Database
The Waveform Browser app uses a MariaDB 10.4.6 database with the following
[schema](https://github.com/JeffersonLab/wfbrowser/tree/master/docker/mariadb/) installed.  The application server
hosting the Waveform Browser application must also be configured with a JNDI datasource.  The application expects that
the database contains at least one system with at least one location.  Locations are inferred from capture events so at
least one event must be added to the database.  See this 
[SQL script](https://github.com/JeffersonLab/wfbrowser/tree/master/docker/mariadb/docker-entrypoint-initdb.d/01_import_event_data)
or the bulk_import_* scripts under [scripts](https://github.com/JeffersonLab/wfbrowser/tree/master/scripts/) for
examples on how to do this.

## Build
This project is built with [Java 17](https://adoptium.net/) (compiled to Java 11 bytecode), and uses the
[Gradle 7](https://gradle.org/) build tool to automatically download dependencies and build the project from source:

```bash
git clone https://github.com/JeffersonLab/wfbrowser
cd wfbrowser
gradlew build
```

**Note**: This will run some integration tests that require `docker compose -f deps.yml up` be run for successful
testing.  TODO: Split these out into separate gradle target.

**Note**: If you do not already have Gradle installed, it will be installed automatically by the wrapper script included
in the source

**Note for JLab On-Site Users**: Jefferson Lab has an intercepting
[proxy](https://gist.github.com/slominskir/92c25a033db93a90184a5994e71d0b78)

**See**: [Docker Development Quick Reference](https://gist.github.com/slominskir/a7da801e8259f5974c978f9c3091d52c#development-quick-reference)

## Release
No CI/CD work has been done yet on this project.

1. Bump the version number and release date in build.gradle and commit and push to GitHub (using [Semantic Versioning](https://semver.org/)).
2. Create a new release on the GitHub Releases page corresponding to the same version in the build.gradle.   The release should enumerate changes and link issues.   A war artifact can be attached to the release to facilitate easy installation by users.
3. Build and publish a new Docker image [from the GitHub tag](https://gist.github.com/slominskir/a7da801e8259f5974c978f9c3091d52c#8-build-an-image-based-of-github-tag). GitHub is configured to do this automatically on git push of semver tag (typically part of GitHub release) or the [Publish to DockerHub](https://github.com/JeffersonLab/rfdashboard/actions/workflows/docker-publish.yml) action can be manually triggered after selecting a tag.
4. Bump and commit quick start [image version](https://github.com/JeffersonLab/wfbrowser/blob/master/docker-compose.override.yml)

## Data Model Notes

The data model is based on the initial directory structure used by the harvester daemon. This defines a data capture
event
based on the system, location, time, and classification of the event. An example of this would be a capture event for
the
RF system from zone 2L22 (location) at 2024-05-09T18:34:00.123456. Classification may not be used in all systems as is
the case
for the RF system, but the systems that do could distinguish, for example, between periodic or event-driven captures.
Additionally, capture events could be directories of files (grouped) or individual files.

The waveform browser has a configurable data directory that should contain any capture events that are to be displayed.
The path to events should follow the following formats.

For grouped data:
`<data_root>/<system>/<location>/[classification]/<date>/<time>/{file1,file2,...}`
e.g. RF without a classification system,
`<data_root>/rf/0L04/2024_03_15/122116.5/{R1M1WFSharv.2018_05_02_122116.9.txt,...}`

For ungrouped data:
`<data_root>/<system>/<location>/[classification]/<date>/<filename_with_time>`
e.g., Accelerometer with classifications. For ungrouped systems, the time should be included in the file name as shown
below.
`<data_root>/acclrm/1L01/periodic/2024_03_16/IAM1L01HRV.2024_03_16_003009.2.txt.tar.gz`

Adding new systems requires updates to the server side code and an addition of the system to the related database. As
part of this
the developer must create a scheme for mapping individual column names (R1M1WFTGMES) to a more generic series (GMES),
applying
a labeling scheme to individual waveforms so that the javascript presentation layer knows how to group the series
together.
This portion of the code could probably be improved upon, but it worked well enough for the few systems we are currently
supporting.

Which data is displayed is controlled by the user through 'Series' and 'Series Sets' controls. Admins are able to define
the 'Series'
and 'Series Sets' through use of a hidden 'Admin' tab. How series are constructed and which systems are available is
controlled on the server side code. Admins must have the proper role granted through OIDC that
matches the defined admin role in `build.gradle` (e.g., wfb_admin). Programatic posting of new data is allowed if the
user has a similarly defined role (e.g., wfb_data).

## Old Repo

The old repo has issues and release history. Move to new streamlined repo was made to remove old commits that contained
lots of large unnecssary data files.

https://github.com/JeffersonLab/wfBrowser-old

## Installation Notes

This data requires access to the waveform data on disk and the metadata in the waveforms database. If a standalone
wildfly server is used to host the wfbrowser app, then make sure to the data is accessible on that system (e.g., NFS
automount, rsync mirror, etc.).
