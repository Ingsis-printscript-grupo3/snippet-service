alter table snippet add column formatted_key varchar(255);

create table lint_violation (
    id            uuid primary key,
    snippet_id    uuid          not null references snippet (id) on delete cascade,
    rule          varchar(100)  not null,
    message       varchar(1000) not null,
    line          int           not null,
    column_number int           not null
);

create index lint_violation_snippet_idx on lint_violation (snippet_id);

create table rule_config (
    user_id    varchar(255) not null,
    language   varchar(50)  not null,
    type       varchar(20)  not null,
    name       varchar(120) not null,
    enabled    boolean      not null,
    rule_value varchar(255),
    primary key (user_id, language, type, name)
);

create table bulk_job (
    id         uuid primary key,
    user_id    varchar(255),
    type       varchar(20)              not null,
    language   varchar(50)              not null,
    total      int                      not null,
    done       int                      not null,
    failed     int                      not null,
    status     varchar(20)              not null,
    created_at timestamp with time zone not null
);

create table bulk_job_item (
    job_id     uuid        not null references bulk_job (id) on delete cascade,
    snippet_id uuid        not null,
    status     varchar(20) not null,
    primary key (job_id, snippet_id)
);

create table snippet_test (
    id               uuid primary key,
    snippet_id       uuid         not null references snippet (id) on delete cascade,
    name             varchar(255) not null,
    inputs           text         not null,
    expected_outputs text         not null,
    status           varchar(20)  not null,
    last_output      text,
    last_run_at      timestamp with time zone
);

create index snippet_test_snippet_idx on snippet_test (snippet_id);
