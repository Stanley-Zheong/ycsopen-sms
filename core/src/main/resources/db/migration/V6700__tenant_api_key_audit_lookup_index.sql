-- Issue 121 tenant API Key audit read path; namespace SCHEMA-C121 / V6700-V6799.
CREATE INDEX idx_audit_tenant_resource_id
    ON privileged_operation_audits (tenant_id, resource_type, id);
