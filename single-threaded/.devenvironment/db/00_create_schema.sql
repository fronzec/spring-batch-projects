-- SQL Script to create schema for dev env

-- Just a test table
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

-- auto-generated definition
create table dispatched_group
(
    id               bigint auto_increment
        primary key,
    uuid_v4          char(36)                              not null,
    dispatch_status  varchar(10) default 'UNKNOWN'         not null,
    records_included int                                   not null,
    created_at       datetime    default CURRENT_TIMESTAMP not null,
    updated_at       datetime    default CURRENT_TIMESTAMP null,
    constraint dispatched_group_uuid_v4_uindex
        unique (uuid_v4)
);

-- auto-generated definition
create table persons_v2
(
    id                     bigint auto_increment
        primary key,
    first_name             varchar(50)                              not null,
    last_name              varchar(50)                              not null,
    email                  varchar(50)                              not null,
    profession             char(15)                                 not null,
    salary                 decimal(19, 2) default 0.00              not null,
    uuid_v4                char(36)                                 not null,
    created_at             datetime       default CURRENT_TIMESTAMP not null,
    updated_at             datetime       default CURRENT_TIMESTAMP not null,
    fk_dispatched_group_id bigint                                   null,
    constraint persons_v2_uuid_v4_uindex
        unique (uuid_v4),
    constraint persons_v2_dispatched_group_id_fk
        foreign key (fk_dispatched_group_id) references dispatched_group (id)
);

create index persons_v2_profession_index
    on persons_v2 (profession);

-- auto-generated definition
create table persons
(
    id         bigint auto_increment
        primary key,
    first_name varchar(50)                        not null,
    last_name  varchar(50)                        null,
    profession varchar(30)                        not null,
    email      varchar(50)                        not null,
    processed  bit      default b'0'              not null,
    created_at datetime default CURRENT_TIMESTAMP not null,
    updated_at datetime default CURRENT_TIMESTAMP not null
);

create index persons_processed_index
    on persons (processed);
