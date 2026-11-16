-- A deployed workflow is now a Step Functions state machine connecting one
-- component function per catalog operation, started through the workflow's
-- entry function. The state machine is recorded with the deployment.
-- Deployments made before this have no state machine and show as out of date.
ALTER TABLE workflow_deployments
    ADD COLUMN IF NOT EXISTS state_machine_arn TEXT;
