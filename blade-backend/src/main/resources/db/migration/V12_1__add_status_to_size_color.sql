-- 给尺码表添加状态字段
ALTER TABLE product_size ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用';

-- 给颜色表添加状态字段
ALTER TABLE product_color ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用';
