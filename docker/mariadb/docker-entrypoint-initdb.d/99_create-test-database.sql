/* No DROP USER IF EXISTS in this version! */

USE waveforms;

INSERT INTO system_type (system_id, system_name) VALUES (99, 'test');
INSERT INTO series (series_id, system_id, pattern, series_name, description) VALUES(990, 99, 't%', 'stest-all', 'Should match all of the test waveforms');
INSERT INTO series (series_id, system_id, pattern, series_name, description) VALUES(991, 99, 't1%1', 'stest1', 'Should match test1');
INSERT INTO series (series_id, system_id, pattern, series_name, description) VALUES(992, 99, 't2%1', 'stest2', 'Should match test1');
