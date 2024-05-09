# Waveform Viewer/Browser (wfbrowser)
CEBAF Waveform viewer and browser web-app.

## Old Repo
The old repo has issues and release history.  Move to new streamlined repo was made to remove old commits that contained lots of large unnecssary data files.

https://github.com/JeffersonLab/wfBrowser-old

## Installation Notes
This data requires access to the waveform data on disk and the metadata in the waveforms database.  If a standalone wildfly server is used to host the wfbrowser app, then make sure to the data is accessible on that system (e.g., NFS automount, rsync mirror, etc.).
