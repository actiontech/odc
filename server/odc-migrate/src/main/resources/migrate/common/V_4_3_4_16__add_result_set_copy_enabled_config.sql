INSERT INTO config_system_configuration(`key`, `value`, `description`)
VALUES('odc.data.result-set.copy.enabled', 'false', '是否允许 SQL 工作台结果集直接复制及输出为 INSERT/CSV，默认关闭')
ON DUPLICATE KEY UPDATE `id`=`id`;
