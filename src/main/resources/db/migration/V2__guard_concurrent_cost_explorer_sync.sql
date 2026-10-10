-- At most one Cost Explorer synchronization may run at a time, across all application
-- instances. A second attempt fails on this index instead of issuing duplicate (billed)
-- Cost Explorer API requests. Interrupted jobs are marked FAILED at startup.
CREATE UNIQUE INDEX ux_cost_import_job_running_sync
    ON cost_import_job (source)
    WHERE status = 'RUNNING' AND source = 'AWS_COST_EXPLORER';
