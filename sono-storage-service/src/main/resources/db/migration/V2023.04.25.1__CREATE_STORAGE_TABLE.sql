CREATE TABLE IF NOT EXISTS `STORAGE`
(
    `ID`              VARCHAR(44) NOT NULL,
    `USER_ID`         VARCHAR(41) NOT NULL,
    `PATH`            TEXT        NOT NULL,
    `VERSION`         LONG        NOT NULL,
    `CREATED_AT`      DATETIME    NOT NULL,
    `LAST_UPDATED_AT` DATETIME,

    CONSTRAINT `STORAGE_PK` PRIMARY KEY (`ID`),
    CONSTRAINT `SINGLE_STORAGE_PER_USER` UNIQUE (`USER_ID`)
);
