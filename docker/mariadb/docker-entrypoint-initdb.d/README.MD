# Database Notes

## "Real" Database
This database is waveforms.  It is what gets used by the application when running.  This
can/should hold some real data to try out features on, etc.  The `01_import_rf_data.sql` file
loads this data, for example.

## "Test" Database
This database only gets used by a suite of integration/unit tests.  The database needs only
exist as the tests add and remove events from it.

I need to look more closely at this.  Perhaps I should add a "test" system  to the production database
at the start of tests and remove it when done.