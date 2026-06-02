-- 为 node_field_value 表补充字段，解决控件类型丢失问题
-- MySQL 8.0+ 不支持 IF NOT EXISTS for ADD COLUMN，需手动检查或使用存储过程
ALTER TABLE node_field_value
    ADD COLUMN widget_type VARCHAR(32) DEFAULT 'INPUT' COMMENT '控件类型' AFTER value_type;

ALTER TABLE node_field_value
    ADD COLUMN widget_props LONGTEXT DEFAULT NULL COMMENT '控件属性(JSON)' AFTER widget_type;

ALTER TABLE node_field_value
    ADD COLUMN default_value VARCHAR(512) DEFAULT NULL COMMENT '默认值' AFTER widget_props;

ALTER TABLE node_field_value
    ADD COLUMN required_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填' AFTER field_value;

ALTER TABLE node_field_value
    ADD COLUMN description VARCHAR(512) DEFAULT NULL COMMENT '字段描述' AFTER required_flag;
