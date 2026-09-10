INSERT INTO statistics_metric_registry(metric_code, metric_name, source_tables, formula, freshness_rule, permission_scope, formula_version)
SELECT 'FINANCIAL_SOURCE', '财务成本收入毛利指标', 'message_tasks,delivery_reports,tenant_contracts,tenant_price_books',
       'provider_cost=sum(message_tasks.cost), revenue=billable_final_count*tenant_price_books.unit_price_mil, profit=revenue-provider_cost',
       'freshness_at >= latest message_tasks.updated_at or delivery_reports.report_time', 'PLATFORM', 'v1'
WHERE NOT EXISTS (SELECT 1 FROM statistics_metric_registry WHERE metric_code='FINANCIAL_SOURCE');
