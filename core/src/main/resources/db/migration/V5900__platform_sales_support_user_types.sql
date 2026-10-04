ALTER TABLE users
    MODIFY COLUMN user_type ENUM(
        'ADMIN',
        'OPERATOR',
        'FINANCE',
        'SALES',
        'TECH_SUPPORT',
        'TENANT_ADMIN',
        'TENANT_USER',
        'TENANT_DEV'
    ) NOT NULL;
