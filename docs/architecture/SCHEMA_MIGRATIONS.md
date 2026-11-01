# 累计 schema 迁移索引

> 2026-11-02 从当前目录生成，最高 V69；只读索引，不代表待执行迁移。目标库实际版本必须查 schema history。

| 版本 | 源文件 | CREATE / ALTER 涉及表（摘要） |
|---|---|---|
| V1 | [V1__init_schema.sql](../../blade-backend/src/main/resources/db/migration/V1__init_schema.sql) | `sys_dict`, `sys_menu`, `sys_role`, `sys_role_menu`, `sys_tenant`, `sys_user`, `sys_user_role` |
| V2 | [V2__product_order.sql](../../blade-backend/src/main/resources/db/migration/V2__product_order.sql) | `order_item`, `product_order` |
| V3 | [V3__product_module.sql](../../blade-backend/src/main/resources/db/migration/V3__product_module.sql) | `product`, `product_category`, `product_color`, `product_color_rel`, `product_size`, `product_size_rel`, `product_sku` |
| V4 | [V4__inventory_module.sql](../../blade-backend/src/main/resources/db/migration/V4__inventory_module.sql) | `inventory`, `inventory_log`, `warehouse` |
| V5 | [V5__order_refactor.sql](../../blade-backend/src/main/resources/db/migration/V5__order_refactor.sql) | `order_item`, `product_order` |
| V6 | [V6__tenant_login_support.sql](../../blade-backend/src/main/resources/db/migration/V6__tenant_login_support.sql) | 权限/数据修正，见 SQL |
| V7 | [V7__order_table_rename.sql](../../blade-backend/src/main/resources/db/migration/V7__order_table_rename.sql) | 权限/数据修正，见 SQL |
| V8_1 | [V8_1__order_images.sql](../../blade-backend/src/main/resources/db/migration/V8_1__order_images.sql) | `sale_order` |
| V8 | [V8__order_payment_delivery_fields.sql](../../blade-backend/src/main/resources/db/migration/V8__order_payment_delivery_fields.sql) | `sale_order` |
| V9 | [V9__customer_module.sql](../../blade-backend/src/main/resources/db/migration/V9__customer_module.sql) | `crm_customer`, `crm_customer_phone` |
| V10 | [V10__order_salesman.sql](../../blade-backend/src/main/resources/db/migration/V10__order_salesman.sql) | `sale_order` |
| V11 | [V11__inventory_add_version.sql](../../blade-backend/src/main/resources/db/migration/V11__inventory_add_version.sql) | `inventory` |
| V12_1 | [V12_1__add_status_to_size_color.sql](../../blade-backend/src/main/resources/db/migration/V12_1__add_status_to_size_color.sql) | `product_color`, `product_size` |
| V12 | [V12__permission_system.sql](../../blade-backend/src/main/resources/db/migration/V12__permission_system.sql) | `sys_permission`, `sys_role_permission` |
| V13 | [V13__sys_user_role_fix.sql](../../blade-backend/src/main/resources/db/migration/V13__sys_user_role_fix.sql) | `sys_user_role` |
| V14 | [V14__permission_data.sql](../../blade-backend/src/main/resources/db/migration/V14__permission_data.sql) | 权限/数据修正，见 SQL |
| V15 | [V15__add_api_permissions.sql](../../blade-backend/src/main/resources/db/migration/V15__add_api_permissions.sql) | 权限/数据修正，见 SQL |
| V16 | [V16__fix_role_permission_tenant.sql](../../blade-backend/src/main/resources/db/migration/V16__fix_role_permission_tenant.sql) | 权限/数据修正，见 SQL |
| V17 | [V17__order_delivery_tables.sql](../../blade-backend/src/main/resources/db/migration/V17__order_delivery_tables.sql) | `order_delivery`, `order_delivery_item` |
| V18 | [V18__order_item_warehouse.sql](../../blade-backend/src/main/resources/db/migration/V18__order_item_warehouse.sql) | `sale_order_item` |
| V19 | [V19__order_add_salesman_name.sql](../../blade-backend/src/main/resources/db/migration/V19__order_add_salesman_name.sql) | `sale_order` |
| V20 | [V20__inventory_global_reserve.sql](../../blade-backend/src/main/resources/db/migration/V20__inventory_global_reserve.sql) | `inventory`, `inventory_global_reserve` |
| V21 | [V21__order_delivery_plan.sql](../../blade-backend/src/main/resources/db/migration/V21__order_delivery_plan.sql) | `order_adjustment_log`, `order_delivery_plan`, `sale_order`, `sale_order_item` |
| V22 | [V22__inventory_log_warehouse_nullable.sql](../../blade-backend/src/main/resources/db/migration/V22__inventory_log_warehouse_nullable.sql) | `inventory_log` |
| V23 | [V23__product_add_fields.sql](../../blade-backend/src/main/resources/db/migration/V23__product_add_fields.sql) | `product`, `supplier` |
| V24 | [V24__customer_add_country.sql](../../blade-backend/src/main/resources/db/migration/V24__customer_add_country.sql) | `crm_customer` |
| V25 | [V25__customer_phone_unique.sql](../../blade-backend/src/main/resources/db/migration/V25__customer_phone_unique.sql) | `crm_customer_phone` |
| V26 | [V26__customer_tag.sql](../../blade-backend/src/main/resources/db/migration/V26__customer_tag.sql) | `crm_customer_tag`, `crm_customer_tag_rel` |
| V27 | [V27__customer_add_create_by.sql](../../blade-backend/src/main/resources/db/migration/V27__customer_add_create_by.sql) | `crm_customer` |
| V28 | [V28__customer_operation_log.sql](../../blade-backend/src/main/resources/db/migration/V28__customer_operation_log.sql) | `crm_customer_operation_log` |
| V29 | [V29__order_quick_entry_finance.sql](../../blade-backend/src/main/resources/db/migration/V29__order_quick_entry_finance.sql) | `sale_order`, `sale_order_item` |
| V30 | [V30__order_source_shop.sql](../../blade-backend/src/main/resources/db/migration/V30__order_source_shop.sql) | `sale_order` |
| V31 | [V31__analytics_permissions.sql](../../blade-backend/src/main/resources/db/migration/V31__analytics_permissions.sql) | 权限/数据修正，见 SQL |
| V32 | [V32__file_storage.sql](../../blade-backend/src/main/resources/db/migration/V32__file_storage.sql) | `file_storage` |
| V33 | [V33__agent_gateway_keys.sql](../../blade-backend/src/main/resources/db/migration/V33__agent_gateway_keys.sql) | `agent_key` |
| V34 | [V34__agent_call_log.sql](../../blade-backend/src/main/resources/db/migration/V34__agent_call_log.sql) | `agent_call_log` |
| V35 | [V35__file_center_asset_schema.sql](../../blade-backend/src/main/resources/db/migration/V35__file_center_asset_schema.sql) | `file_business_bind`, `file_cleanup_log`, `file_folder`, `file_operation_log`, `file_storage` |
| V36 | [V36__file_center_permissions.sql](../../blade-backend/src/main/resources/db/migration/V36__file_center_permissions.sql) | 权限/数据修正，见 SQL |
| V37 | [V37__catalog_permissions.sql](../../blade-backend/src/main/resources/db/migration/V37__catalog_permissions.sql) | 权限/数据修正，见 SQL |
| V38 | [V38__file_derivative.sql](../../blade-backend/src/main/resources/db/migration/V38__file_derivative.sql) | `file_derivative` |
| V39 | [V39__order_write_off.sql](../../blade-backend/src/main/resources/db/migration/V39__order_write_off.sql) | `sale_order` |
| V40 | [V40__order_delivery_display_columns.sql](../../blade-backend/src/main/resources/db/migration/V40__order_delivery_display_columns.sql) | `order_delivery`, `order_delivery_item` |
| V41 | [V41__owner_api_permissions.sql](../../blade-backend/src/main/resources/db/migration/V41__owner_api_permissions.sql) | 权限/数据修正，见 SQL |
| V42 | [V42__fix_owner_api_permissions_tenant.sql](../../blade-backend/src/main/resources/db/migration/V42__fix_owner_api_permissions_tenant.sql) | 权限/数据修正，见 SQL |
| V43 | [V43__whatsapp_archive.sql](../../blade-backend/src/main/resources/db/migration/V43__whatsapp_archive.sql) | `wa_account`, `wa_contact`, `wa_conversation`, `wa_customer_binding`, `wa_import_batch`, `wa_message`, `wa_message_media`, `wa_message_source_ref`, `wa_sync_cursor` |
| V44 | [V44__whatsapp_collector_workbench.sql](../../blade-backend/src/main/resources/db/migration/V44__whatsapp_collector_workbench.sql) | `wa_collection_issue`, `wa_collector_key`, `wa_scan_job` |
| V45 | [V45__whatsapp_agent_analysis.sql](../../blade-backend/src/main/resources/db/migration/V45__whatsapp_agent_analysis.sql) | `wa_analysis_job`, `wa_customer_analysis`, `wa_followup_recommendation` |
| V46 | [V46__whatsapp_analysis_context_snapshot.sql](../../blade-backend/src/main/resources/db/migration/V46__whatsapp_analysis_context_snapshot.sql) | `wa_analysis_job` |
| V47 | [V47__whatsapp_targeted_rescan.sql](../../blade-backend/src/main/resources/db/migration/V47__whatsapp_targeted_rescan.sql) | `wa_import_batch`, `wa_scan_job` |
| V48 | [V48__order_draft_agent_import.sql](../../blade-backend/src/main/resources/db/migration/V48__order_draft_agent_import.sql) | `order_draft`, `order_draft_item` |
| V49 | [V49__product_sku_types_and_placeholder.sql](../../blade-backend/src/main/resources/db/migration/V49__product_sku_types_and_placeholder.sql) | `product_sku` |
| V50 | [V50__correct_default_sku_classification.sql](../../blade-backend/src/main/resources/db/migration/V50__correct_default_sku_classification.sql) | 权限/数据修正，见 SQL |
| V51 | [V51__order_lifecycle_finance.sql](../../blade-backend/src/main/resources/db/migration/V51__order_lifecycle_finance.sql) | `order_financial_record`, `order_state_transition_log`, `sale_order` |
| V52 | [V52__order_action_permission.sql](../../blade-backend/src/main/resources/db/migration/V52__order_action_permission.sql) | 权限/数据修正，见 SQL |
| V53 | [V53__order_permission_tenant_backfill.sql](../../blade-backend/src/main/resources/db/migration/V53__order_permission_tenant_backfill.sql) | 权限/数据修正，见 SQL |
| V54 | [V54__order_permission_global_model.sql](../../blade-backend/src/main/resources/db/migration/V54__order_permission_global_model.sql) | 权限/数据修正，见 SQL |
| V55 | [V55__order_warehouse_scope.sql](../../blade-backend/src/main/resources/db/migration/V55__order_warehouse_scope.sql) | 权限/数据修正，见 SQL |
| V56 | [V56__placeholder_for_all_variant_products.sql](../../blade-backend/src/main/resources/db/migration/V56__placeholder_for_all_variant_products.sql) | 权限/数据修正，见 SQL |
| V57 | [V57__sales_collection_field_permissions.sql](../../blade-backend/src/main/resources/db/migration/V57__sales_collection_field_permissions.sql) | 权限/数据修正，见 SQL |
| V58 | [V58__agent_key_management.sql](../../blade-backend/src/main/resources/db/migration/V58__agent_key_management.sql) | `agent_key` |
| V59 | [V59__manual_quick_order_drafts.sql](../../blade-backend/src/main/resources/db/migration/V59__manual_quick_order_drafts.sql) | `order_draft`, `order_draft_item` |
| V60 | [V60__agent_customer_creation_audit.sql](../../blade-backend/src/main/resources/db/migration/V60__agent_customer_creation_audit.sql) | `crm_customer`, `crm_customer_operation_log` |
| V61 | [V61__repair_order_file_bindings.sql](../../blade-backend/src/main/resources/db/migration/V61__repair_order_file_bindings.sql) | 权限/数据修正，见 SQL |
| V62 | [V62__repair_draft_source_shop_fallback.sql](../../blade-backend/src/main/resources/db/migration/V62__repair_draft_source_shop_fallback.sql) | 权限/数据修正，见 SQL |
| V63 | [V63__outlet_access_control.sql](../../blade-backend/src/main/resources/db/migration/V63__outlet_access_control.sql) | `agent_key`, `agent_key_outlet`, `order_draft`, `order_outlet_change_log`, `sale_order`, `sales_outlet`, `sys_user_outlet` |
| V64 | [V64__outlet_permissions.sql](../../blade-backend/src/main/resources/db/migration/V64__outlet_permissions.sql) | 权限/数据修正，见 SQL |
| V65 | [V65__outlet_access_policy.sql](../../blade-backend/src/main/resources/db/migration/V65__outlet_access_policy.sql) | `order_draft` |
| V66 | [V66__order_change_outlet_permission.sql](../../blade-backend/src/main/resources/db/migration/V66__order_change_outlet_permission.sql) | 权限/数据修正，见 SQL |
| V67 | [V67__outlet_tenant_default_unique.sql](../../blade-backend/src/main/resources/db/migration/V67__outlet_tenant_default_unique.sql) | `sales_outlet`, `语句内新增` |
| V68 | [V68__outlet_subject_default_unique.sql](../../blade-backend/src/main/resources/db/migration/V68__outlet_subject_default_unique.sql) | `agent_key_outlet`, `sys_user_outlet` |
| V69 | [V69__expand_order_customer_phone.sql](../../blade-backend/src/main/resources/db/migration/V69__expand_order_customer_phone.sql) | `sale_order` |
