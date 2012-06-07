--------------------------------------------------------------------------------
-- log: poor mans logging
-- in
-- level int : error level
-- errornum int : error number
-- osmid bigint : offending OSM object number
-- msg varchar : error message
-- out
-- 0 : OK
-- 1 : NOK
--------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION log(level int, errornum int, osmid bigint, msg varchar) RETURNS bigint AS $$
DECLARE
BEGIN

    BEGIN

        insert into log values (now(), level, errornum, osmid, msg );
        RETURN 0;     

    EXCEPTION WHEN OTHERS THEN
       RETURN 1;
    END;
END;
$$ LANGUAGE plpgsql;

--------------------------------------------------------------------------------
-- initlog: create table for log function (poor mans logging)
--------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION initlog() RETURNS bigint AS $$
DECLARE
BEGIN

     EXECUTE 'DROP TABLE IF EXISTS log;';
     EXECUTE 'create table log(logtime timestamp, level int, errornum int, osmid bigint, msg text);';
     RETURN 0;

END;
$$ LANGUAGE plpgsql;

--------------------------------------------------------------------------------
-- getpolygonfromrelation_recur : build recursivly a geometry from a given relation id
-- in
-- rel_id big int : osm relation id
-- out
-- geometry : not necessary of type MULTIPOLYGON since 
--------------------------------------------------------------------------------


CREATE OR REPLACE FUNCTION getpolygonfromrelation_recur(rel_id bigint) RETURNS geometry AS $$
DECLARE
        result geometry;
        temp geometry;
        i record;
        rel_size bigint;
BEGIN
    BEGIN

        perform log(3, 0, rel_id, 'getpolygonfromrelation_recur: start');

-- erst alle subrelationen rekursiv abgrasen
        FOR i IN (select * from relation_members where relation_id=rel_id and member_type='R' and member_role != 'subarea' ORDER BY sequence_id) LOOP

            SELECT st_multi((ST_collect(getpolygonfromrelation_recur(i.member_id), temp))) into temp;

        END LOOP;

        select count(*) into rel_size from relation_members where relation_id=rel_id and member_type='W';

        IF rel_size > 1200 THEN
            perform log(2, 2, rel_id, 'Too many members for '||rel_id||'  Member count: '||rel_size);
            RETURN NULL;
        END IF;

        SELECT st_multi((ST_collect(border_ways))) into result
        FROM (SELECT linestring as border_ways FROM ways where id in
        (select member_id from relation_members where relation_id=rel_id and member_type='W' ORDER BY sequence_id)) as foo;


        perform log(3, 99, rel_id, 'getpolygonfromrelation_recur: found type result: '||COALESCE(GeometryType(result), 'NULL')||'      temp: '||COALESCE(GeometryType(temp), 'NULL')
        ||'     st_multi(ST_BuildArea(ST_Collect(result, temp))): '||COALESCE(GeometryType(st_multi(ST_BuildArea(ST_Collect(result, temp)))), 'NULL'));


        IF temp is null THEN
            return result;
        END IF;
        IF result is null THEN
            return temp;
        END IF;

        RETURN st_multi((ST_Collect(result, temp)));


    EXCEPTION WHEN OTHERS THEN
        perform log(1, 2, rel_id, 'getpolygonfromrelation_recur: EXCEPTION!');
    END;

END;
$$ LANGUAGE plpgsql;

---------------------------------------------------------------------------------
-- getparentfrompoly : identify parent relation
-- in
-- childpoy geometry : child geometry. must be type MULTIPOLYGON
-- lev bigint : admin level where to search for a parent that fits
-- out
-- bigint : parent osm relation id
---------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION getparentfrompoly(lev bigint, childpoly geometry) RETURNS bigint AS $$
DECLARE
    c_parents CURSOR (l bigint) IS select * from tree_world where level=l;
    counter bigint := lev;
BEGIN
    BEGIN
        IF childpoly is null THEN
           perform log(2, 3, 0, 'getparentfrompoly: childpoly null');
           RETURN 0;
        END IF;

        IF (GeometryType(childpoly)='GEOMETRYCOLLECTION') THEN
            perform log(2, 4, 0, 'getparentfrompoly: Type GEOMETRYCOLLECTION');
            RETURN 0;
        END IF;

        WHILE counter > 0 LOOP
            FOR i IN c_parents(counter) LOOP
                IF st_contains(i.poly, childpoly) is true THEN
                    RETURN i.relation_id;
                END IF;
            END LOOP;
        counter := counter-1;
        END LOOP;
    RETURN 0;

    EXCEPTION WHEN OTHERS THEN
        perform log(1, 1, 0, 'getparentfrompoly: EXCEPTION!');
        RETURN 0;
    END;
END;
$$ LANGUAGE plpgsql;

---------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION myinsertupdatetree(lev bigint, rel_id bigint, par_id bigint, polygon geometry, grenzname varchar) RETURNS bigint AS $$
DECLARE
    temp bigint;
BEGIN
    BEGIN
        select count(*) into temp from tree_world where relation_id=rel_id;
        IF temp=0 THEN
            perform log(3, 5, rel_id, 'myinsertupdatetree: insert');
            insert into tree_world (level, relation_id, parent_id, poly, name) values (lev, rel_id, par_id, polygon, grenzname);
            RETURN 0;
        END IF;

        perform log(3, 6, rel_id, 'myinsertupdatetree: update');


        IF polygon is null THEN
            RETURN 0;
        END IF;

        IF grenzname is null THEN
            grenzname = '';
        END IF;

        update tree_world set level=lev, parent_id=par_id, poly=polygon, name=grenzname where relation_id=rel_id;
    
        RETURN 0;

    EXCEPTION WHEN OTHERS THEN
        perform log(1, 3, rel_id, 'EXCEPTION! myinsertupdatetree: (level, rel_id, par_id, polygon, grenzname) : '||lev||', '||rel_id||', '||par_id||', '||GeometryType(polygon)||', '||grenzname);
        RETURN 1;
    END;
END;
$$ LANGUAGE plpgsql;

---------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION updatepolys() RETURNS bigint AS $$
DECLARE
--    c_upoly CURSOR FOR select relation_id as id, name, level from tree_world where relation_id=378734;
    c_upoly CURSOR FOR select id, tags->'name' as name, (tags->'admin_level')::int as level from "public".relations where  tags @> '"boundary"=>"administrative"'
            AND tags @> '"admin_level"=>"3"' AND not tags @> '"type"=>"multilinestring"'; --1152564; --
    polygon geometry;
    parent bigint;
    grenzname varchar;
    insup bigint;
BEGIN
    BEGIN
        FOR i IN c_upoly LOOP
            polygon = getpolygonfromrelation_recur(i.id);
            IF (GeometryType(polygon)!='MULTIPOLYGON') THEN
                perform log(3, 30, i.id, 'Transforming to Area '||GeometryType(polygon));
                polygon=ST_multi(ST_BuildArea(polygon));
                perform log(3, 30, i.id, 'Transformed Area to '||GeometryType(polygon));
            END IF;

            IF (GeometryType(polygon)='GEOMETRYCOLLECTION') THEN
                perform log(1, 1, i.id, 'Type GEOMETRYCOLLECTION found: Throwing away');
                polygon=null;
            END IF;

            select getparentfrompoly(i.level-1, polygon) into parent;
            insup = myinsertupdatetree(i.level, i.id, parent, polygon, i.name);

        END LOOP;

     RETURN 0;

    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'EXCEPTION on updatepolys';
        RETURN 0;
    END;
END;
$$ LANGUAGE plpgsql;

---------------------------------------------------------------------------------
---------------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION updatepoly(rel_id bigint) RETURNS bigint AS $$
DECLARE
    polygon geometry;
    lev bigint;
    parent bigint;
    grenzname varchar;
    insup bigint;
BEGIN

    select tags->'name', tags->'admin_level' into grenzname, lev from relations where id=rel_id;
    polygon = getpolygonfromrelation_recur(rel_id);
    IF (GeometryType(polygon)!='MULTIPOLYGON') THEN
        polygon=ST_multi(ST_BuildArea(polygon));
    END IF;

    IF (GeometryType(polygon)='GEOMETRYCOLLECTION') THEN
        perform log(1, 1, rel_id, 'Type GEOMETRYCOLLECTION found: Throwing away');
        polygon=null;
    END IF;

    select getparentfrompoly(lev-1, polygon) into parent;
    insup = myinsertupdatetree(lev, rel_id, parent, polygon, grenzname);

    RETURN 0;

END;
$$ LANGUAGE plpgsql;
---------------------------------------------------------------------------------
-- children_of : return all child relation ids of a tree
-- in
-- root_id bigint : root relation id
-- out
-- all but no geometry (does not work recursivly)
---------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION children_of(root_id bigint) 
        RETURNS TABLE (name text, relation_id bigint, parent_id bigint, level bigint)
        AS $$
WITH RECURSIVE path(name, relation_id, parent_id, level ) AS (
          SELECT name, relation_id, parent_id, level FROM tree_world WHERE relation_id = $1
          UNION
          SELECT
            tree_world.name, 
            tree_world.relation_id, 
            tree_world.parent_id,
            tree_world.level
          FROM tree_world, path as parentpath
          WHERE tree_world.parent_id = parentpath.relation_id)
        SELECT * FROM path;
$$ LANGUAGE 'sql';

---------------------------------------------------------------------------------

select initlog();


 