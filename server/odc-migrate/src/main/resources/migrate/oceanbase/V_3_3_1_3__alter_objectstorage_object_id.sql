ALTER TABLE `objectstorage_object_block` MODIFY COLUMN `object_id` VARCHAR(512);
ALTER TABLE `objectstorage_object_metadata` MODIFY COLUMN `object_id` VARCHAR(512);
ALTER TABLE `script_meta` MODIFY COLUMN `object_id` VARCHAR(512);