-- solo la metadata: el codigo del snippet vive en el bucket, con el id como key
create table snippet (
    id          uuid primary key,
    name        varchar(255)             not null,
    description varchar(1000)            not null,
    language    varchar(50)              not null,
    version     varchar(20)              not null,
    -- el userId de quien lo creo. Cuando llegue Auth0 va a ser su sub
    owner_id    varchar(255)             not null,
    created_at  timestamp with time zone not null,
    updated_at  timestamp with time zone not null
);

create index snippet_owner_idx on snippet (owner_id);
