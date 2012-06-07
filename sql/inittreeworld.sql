DROP TABLE IF EXISTS tree_world;
CREATE TABLE tree_world (id serial PRIMARY KEY, level bigint NOT NULL, relation_id bigint NOT NULL, parent_id bigint NOT NULL, name varchar);
create index idx_relation_id on tree_world (relation_id);
create index idx_parent_id on tree_world (parent_id);
SELECT AddGeometryColumn('tree_world', 'poly', 4326, 'MULTIPOLYGON', 2);


DROP TABLE IF EXISTS log;
create table log(logtime timestamp, level int, errornum int, osmid bigint, msg text);
