/* No DROP USER IF EXISTS in this version! */

USE waveforms;

INSERT INTO system_type (system_id, system_name) VALUES (99, 'test');
INSERT INTO series (system_id, pattern, series_name, description) VALUES(99, 't%', 'Test Series - All', 'Should match all of the test waveforms');
INSERT INTO series (system_id, pattern, series_name, description) VALUES(99, 't1%1', 'Test Series - test1', 'Should match test1');
