---
--- v4.2.2
---
CREATE TABLE IF NOT EXISTS `connect_connection_attribute` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'Id for connection attribute',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Record insertion time',
  	`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Record modification time',
    `name` varchar(1024) NOT NULL COMMENT 'Name for an attribute',
		`connection_id` bigint(20) NOT NULL COMMENT 'Related connection id, reference connect_connection(id)',
    `content` mediumtext DEFAULT NULL COMMENT 'Content for key',
    `connect_unique_hash` varbinary(16) GENERATED ALWAYS AS (
      UNHEX(MD5(CONCAT_WS('#', `connection_id`, `name`)))
      ) STORED COMMENT 'hash for composite unique constraint',
    CONSTRAINT `pk_connect_connection_attribute` PRIMARY KEY (`id`),
    UNIQUE KEY `uk_connect_connection_attribute` (`connect_unique_hash`)
);