CREATE TABLE IF NOT EXISTS raw_logs
(
    id        SERIAL PRIMARY KEY,
    message   TEXT NOT NULL,
    create_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS parsed_logs
(
    id                  SERIAL PRIMARY KEY,
    timestamp           TIMESTAMP,
    eventID             INT NOT NULL,
    rack                INT,
    midplane            INT,
    uid                 INT,
    jid                 INT,
    type                INT,
    node_type           INT,
    control_IO          INT,
    node_no             INT,
    channel_C           INT,
    channel_E           INT,
    channel_S           INT,
    channel_D           INT,
    channel_A           INT,
    component_KERNEL    INT,
    component_LINKCARD  INT,
    component_APP       INT,
    component_MMCS      INT,
    component_HARDWARE  INT,
    component_DISCOVERY INT,
    component_CMCS      INT,
    component_BGLMASTER INT,
    component_MONITOR   INT,
    component_SERV_NET  INT,
    level_INFO          INT,
    level_FATAL         INT,
    level_WARNING       INT,
    level_SEVERE        INT,
    level_ERROR         INT,
    level_FAILURE       INT,
    create_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE IF NOT EXISTS event_logs
(
    id         SERIAL PRIMARY KEY,
    timestamp  TEXT,
    date       DATE,
    node       TEXT,
    time       TIMESTAMP,
    nodeRepeat TEXT,
    type       TEXT,
    component  TEXT,
    level      TEXT,
    content    TEXT,
    label      TEXT
);

CREATE TABLE IF NOT EXISTS test_raw_logs
(
    id        SERIAL PRIMARY KEY,
    message   TEXT    NOT NULL,
    label     BOOLEAN NOT NULL,
    create_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);



