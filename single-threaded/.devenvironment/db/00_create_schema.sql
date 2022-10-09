-- SQL to create schema for dev env
CREATE TABLE if not exists test (
    id bigint primary key auto_increment,
    char_data char(5),
    vchar_data varchar(5),
    sint smallint,
    ddata date,
    dtdata datetime,
    tsdata timestamp,
    bdata bool,
    b2data boolean
    );