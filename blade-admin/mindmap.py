#!/usr/bin/env python3
"""
BladeProject 业务流程思维导图 - 纯文字版
"""

import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch

plt.rcParams['font.sans-serif'] = ['Arial Unicode MS', 'SimHei', 'STHeiti', 'sans-serif']
plt.rcParams['axes.unicode_minus'] = False

fig, ax = plt.subplots(1, 1, figsize=(22, 30))
ax.set_xlim(0, 100)
ax.set_ylim(0, 150)
ax.axis('off')

def draw_node(ax, x, y, text, color, text_color='white', width=12, height=2.5, fontsize=11, bold=False):
    box = FancyBboxPatch((x - width/2, y - height/2), width, height,
                         boxstyle="round,pad=0.05,rounding_size=0.3",
                         facecolor=color, edgecolor='white', linewidth=2)
    ax.add_patch(box)
    weight = 'bold' if bold else 'normal'
    ax.text(x, y, text, ha='center', va='center', fontsize=fontsize,
            color=text_color, weight=weight)

def draw_line(ax, x1, y1, x2, y2, color='#94a3b8'):
    ax.plot([x1, x2], [y1, y2], color=color, linewidth=1.5, zorder=0)

def draw_sub_node(ax, x, y, text, color='#f1f5f9', text_color='#334155', fontsize=9, width=10):
    box = FancyBboxPatch((x - width/2, y - 1), width, 2,
                         boxstyle="round,pad=0.02,rounding_size=0.2",
                         facecolor=color, edgecolor='#cbd5e1', linewidth=1)
    ax.add_patch(box)
    ax.text(x, y, text, ha='center', va='center', fontsize=fontsize, color=text_color)

# ========== 根节点 ==========
draw_node(ax, 50, 143, 'BladeProject\n服装订单管理系统', '#408aee', 'white', 18, 3, 14, True)

# ========== 一级分支 ==========
# 客户管理
draw_node(ax, 12, 130, '客户管理', '#10b981', 'white', 10, 2.5, 12, True)
draw_line(ax, 44, 141, 17, 131.5)

# 商品管理
draw_node(ax, 35, 130, '商品管理', '#f59e0b', 'white', 10, 2.5, 12, True)
draw_line(ax, 50, 140.5, 35, 131.5)

# 档口/仓库
draw_node(ax, 58, 130, '档口/仓库', '#8b5cf6', 'white', 10, 2.5, 12, True)
draw_line(ax, 56, 140.5, 58, 131.5)

# 订单管理（核心）
draw_node(ax, 82, 130, '订单管理', '#ef4444', 'white', 12, 2.5, 13, True)
draw_line(ax, 62, 140.5, 78, 131.5)

# 库存管理
draw_node(ax, 12, 110, '库存管理', '#06b6d4', 'white', 10, 2.5, 12, True)
draw_line(ax, 20, 127.5, 15, 111.5)

# 数据统计
draw_node(ax, 35, 110, '数据统计', '#64748b', 'white', 10, 2.5, 12, True)
draw_line(ax, 30, 127.5, 35, 111.5)

# ========== 客户管理子节点 ==========
draw_sub_node(ax, 12, 122, '电话搜索客户', '#d1fae5', '#065f46')
draw_sub_node(ax, 12, 117, '新建客户', '#d1fae5', '#065f46')
draw_sub_node(ax, 12, 112, '编辑客户信息', '#d1fae5', '#065f46')
draw_line(ax, 12, 128.5, 12, 123.5)
for y in [121, 116, 111]:
    draw_line(ax, 12, y+1.5, 12, y+2)

# ========== 商品管理子节点 ==========
draw_sub_node(ax, 26, 122, '商品列表', '#fef3c7', '#92400e')
draw_sub_node(ax, 35, 122, 'SKU管理', '#fef3c7', '#92400e', width=8)
draw_sub_node(ax, 44, 122, '设置价格', '#fef3c7', '#92400e')
draw_line(ax, 35, 128.5, 26, 123.5)
draw_line(ax, 35, 128.5, 35, 123.5)
draw_line(ax, 35, 128.5, 44, 123.5)

draw_sub_node(ax, 26, 116, '创建商品', '#fef3c7', '#92400e')
draw_sub_node(ax, 44, 116, '商品图片', '#fef3c7', '#92400e')
draw_line(ax, 26, 120.5, 26, 117.5)
draw_line(ax, 44, 120.5, 44, 117.5)

# ========== 档口/仓库子节点 ==========
draw_sub_node(ax, 50, 122, '档口列表', '#ede9fe', '#5b21b6')
draw_sub_node(ax, 58, 122, '档口设置', '#ede9fe', '#5b21b6')
draw_sub_node(ax, 66, 122, '库存关联', '#ede9fe', '#5b21b6')
draw_line(ax, 58, 128.5, 50, 123.5)
draw_line(ax, 58, 128.5, 58, 123.5)
draw_line(ax, 58, 128.5, 66, 123.5)

# ========== 订单管理 - 创建订单 ==========
draw_node(ax, 65, 108, '创建订单', '#fecaca', '#991b1b', 12, 2.5, 11, True)
draw_line(ax, 82, 128.5, 65, 110)

draw_sub_node(ax, 52, 100, '客户信息\n电话搜索/新建', '#fee2e2', '#7f1d1d', 8, 9)
draw_sub_node(ax, 65, 100, '选择档口', '#fee2e2', '#7f1d1d', 8, 7)
draw_sub_node(ax, 78, 100, '添加商品\nSKU+数量', '#fee2e2', '#7f1d1d', 8, 9)
draw_line(ax, 65, 105.5, 52, 102)
draw_line(ax, 65, 105.5, 65, 102)
draw_line(ax, 65, 105.5, 78, 102)

draw_sub_node(ax, 52, 91, '支付状态\n未付/定金/全款', '#fee2e2', '#7f1d1d', 8, 9)
draw_sub_node(ax, 65, 91, '送货设置', '#fee2e2', '#7f1d1d', 8, 7)
draw_sub_node(ax, 78, 91, '备注/图片', '#fee2e2', '#7f1d1d', 8, 7)
draw_line(ax, 52, 98.5, 52, 93)
draw_line(ax, 65, 98.5, 65, 93)
draw_line(ax, 78, 98.5, 78, 93)

# ========== 订单管理 - 订单状态操作 ==========
draw_node(ax, 92, 108, '订单状态操作', '#fecaca', '#991b1b', 12, 2.5, 11, True)
draw_line(ax, 90, 127.5, 92, 110)

draw_sub_node(ax, 82, 100, '确认收款', '#bbf7d0', '#166534', 9)
draw_sub_node(ax, 92, 100, '发货', '#bbf7d0', '#166534', 9)
draw_sub_node(ax, 100, 100, '完成订单', '#bbf7d0', '#166534', 9)
draw_line(ax, 92, 105.5, 82, 102)
draw_line(ax, 92, 105.5, 92, 102)
draw_line(ax, 92, 105.5, 100, 102)

draw_sub_node(ax, 92, 91, '取消订单', '#fee2e2', '#991b1b', 9)
draw_line(ax, 92, 98.5, 92, 93)

# ========== 订单管理 - 订单查询 ==========
draw_node(ax, 92, 75, '订单查询', '#fecaca', '#991b1b', 12, 2.5, 11, True)
draw_line(ax, 92, 89.5, 92, 77)

draw_sub_node(ax, 82, 67, '订单列表/筛选', '#fef3c7', '#92400e', 9)
draw_sub_node(ax, 100, 67, '订单详情', '#fef3c7', '#92400e', 9)
draw_line(ax, 92, 72.5, 82, 69)
draw_line(ax, 92, 72.5, 100, 69)

# ========== 库存管理子节点 ==========
draw_sub_node(ax, 5, 102, '库存查询', '#cffafe', '#155e75', 9)
draw_sub_node(ax, 12, 102, '库存预警', '#cffafe', '#155e75', 9)
draw_sub_node(ax, 19, 102, '变动记录', '#cffafe', '#155e75', 9)
draw_line(ax, 12, 108.5, 5, 103.5)
draw_line(ax, 12, 108.5, 12, 103.5)
draw_line(ax, 12, 108.5, 19, 103.5)

# ========== 数据统计子节点 ==========
draw_sub_node(ax, 28, 102, '今日订单', '#f1f5f9', '#334155', 9)
draw_sub_node(ax, 35, 102, '销售统计', '#f1f5f9', '#334155', 9)
draw_sub_node(ax, 42, 102, '库存概览', '#f1f5f9', '#334155', 9)
draw_line(ax, 35, 108.5, 28, 103.5)
draw_line(ax, 35, 108.5, 35, 103.5)
draw_line(ax, 35, 108.5, 42, 103.5)

# ========== 流程说明 ==========
ax.text(50, 5, 'BladeProject 业务流程思维导图', ha='center', va='center',
        fontsize=16, color='#1e293b', weight='bold')

plt.tight_layout()
plt.savefig('/Users/chenjiarun/Desktop/blade_business_flow.png', dpi=150, bbox_inches='tight',
            facecolor='white', edgecolor='none')
print('思维导图已生成: /Users/chenjiarun/Desktop/blade_business_flow.png')
plt.close()
