DROP TRIGGER IF EXISTS `test_trigger_bi`;
DROP TRIGGER IF EXISTS `test_trigger_au`;
DROP TABLE IF EXISTS `test_trigger_table`;
CREATE TABLE `test_trigger_table` (
  `id` INT NOT NULL PRIMARY KEY,
  `note` VARCHAR(200)
);
CREATE TRIGGER `test_trigger_bi`
BEFORE INSERT ON `test_trigger_table`
FOR EACH ROW
SET NEW.note = IFNULL(NEW.note, 'insert');
CREATE TRIGGER `test_trigger_au`
AFTER UPDATE ON `test_trigger_table`
FOR EACH ROW
SET @odc_trigger_au_fired = 1;
