CREATE USER IF NOT EXISTS 'ycsopen_migrator'@'%' IDENTIFIED BY 'ycsopen_migrator';
GRANT ALL PRIVILEGES ON `ycsopen_sms`.* TO 'ycsopen_migrator'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
