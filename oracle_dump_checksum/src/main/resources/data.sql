-- Demo seed data for client / client_file_location.
-- Guarded with WHERE NOT EXISTS so it is safe to re-run on every app
-- restart (spring.sql.init.mode=always) without violating the unique
-- constraint on client.code or duplicating locations.

INSERT INTO client (code, name, active)
SELECT 'ACME', 'Acme Corporation', TRUE
WHERE NOT EXISTS (SELECT 1 FROM client WHERE code = 'ACME');

INSERT INTO client (code, name, active)
SELECT 'GLOBEX', 'Globex Inc', TRUE
WHERE NOT EXISTS (SELECT 1 FROM client WHERE code = 'GLOBEX');

INSERT INTO client_file_location (client_id, base_path, file_pattern, active)
SELECT c.id, '/Users/chandragauro/temp/oracle-dumps/acme', '*.dmp', TRUE
FROM client c
WHERE c.code = 'ACME'
  AND NOT EXISTS (
      SELECT 1 FROM client_file_location l
      WHERE l.client_id = c.id AND l.base_path = '/Users/chandragauro/temp/oracle-dumps/acme'
  );

INSERT INTO client_file_location (client_id, base_path, file_pattern, active)
SELECT c.id, '/Users/chandragauro/temp/oracle-dumps/globex', '*.dmp', TRUE
FROM client c
WHERE c.code = 'GLOBEX'
  AND NOT EXISTS (
      SELECT 1 FROM client_file_location l
      WHERE l.client_id = c.id AND l.base_path = '/Users/chandragauro/temp/oracle-dumps/globex'
  );
