-- Bluetooth/device features were removed from the product.
-- Keep historical V1 immutable and drop legacy tables through a new migration.

DROP TABLE IF EXISTS device_binding;
DROP TABLE IF EXISTS device_brand;
DROP TABLE IF EXISTS user_device;
