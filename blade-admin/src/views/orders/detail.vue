<template>
  <div class="order-detail-page">
    <!-- 页面标题区 -->
    <div class="flex items-center justify-between mb-8">
      <div class="flex items-center gap-4">
        <button
          @click="handleBack"
          class="flex items-center gap-2 px-4 py-2 border border-gray-200 rounded-xl text-gray-600 hover:bg-gray-50 transition-colors bg-white font-medium text-sm"
        >
          <span class="material-symbols-outlined text-[20px]">arrow_back</span>
          返回列表
        </button>
        <h2 class="text-2xl font-bold text-gray-900 tracking-tight">订单详情</h2>
      </div>
      <div class="flex items-center gap-3">
        <!-- 状态操作按钮（按后端 allowedActions 白名单展示，历史行回退旧判断） -->
        <template v-if="order">
          <el-button
            v-if="hasAction('recordPayment') && !legacyUnmigrated"
            type="warning"
            plain
            @click="handleAddPayment"
          >
            加收金额
          </el-button>
          <el-button
            v-if="hasAction('recordPayment') && !legacyUnmigrated"
            type="warning"
            class="!bg-amber-500 !border-amber-500"
            @click="handleConfirmPayment"
          >
            确认收款
          </el-button>
          <!-- 履约方式选择：已结清且未选择 -->
          <template v-if="hasAction('chooseFulfillmentMode')">
            <el-button type="success" plain @click="handleChooseFulfillmentMode('STOCK_LINKED')">
              履约：关联库存
            </el-button>
            <el-button type="info" plain @click="handleChooseFulfillmentMode('RECORD_ONLY')">
              履约：仅记录
            </el-button>
          </template>
          <!-- 配货计划按钮 -->
          <template v-if="(hasAction('startAllocation') || (legacyUnmigrated && order.status === 1)) && deliveryPlans.length === 0">
            <el-button
              type="success"
              @click="handleCreateDeliveryPlan"
            >
              创建配货计划
            </el-button>
          </template>
          <el-button
            v-if="hasAction('confirmAllocation') || (legacyUnmigrated && order.status === 2)"
            type="primary"
            @click="handleConfirmAdjustment"
          >
            确认调整方案
          </el-button>
          <el-button
            v-if="order.status === 2"
            type="warning"
            @click="handleEditDeliveryPlan"
          >
            编辑配货计划
          </el-button>
          <el-button
            v-if="order.status === 2"
            type="danger"
            plain
            @click="handleCancelAdjustment"
          >
            取消调整
          </el-button>
          <el-button
            v-if="hasAction('shipOrder') || (legacyUnmigrated && order.status === 3)"
            type="success"
            @click="handleDeliver"
          >
            发货
          </el-button>
          <el-button
            v-if="hasAction('completeOrder') || (legacyUnmigrated && order.status === 4)"
            type="primary"
            @click="handleComplete"
          >
            完成订单
          </el-button>
          <el-button
            v-if="hasAction('cancelOrder') || (legacyUnmigrated && order.status !== 4 && order.status !== 3 && order.status !== 6 && order.status !== 2)"
            type="danger"
            @click="handleCancel"
          >
            取消订单
          </el-button>
          <el-button
            v-if="hasAction('refundPayment')"
            type="danger"
            plain
            @click="showRefundDialog = true"
          >
            现金退款
          </el-button>
        </template>
      </div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="flex items-center justify-center py-20">
      <el-icon class="is-loading text-4xl text-[#408aee]">
        <Loading />
      </el-icon>
    </div>

    <!-- 订单内容 -->
    <div v-else-if="order" class="grid grid-cols-12 gap-8 items-start">
      <!-- 左侧内容 -->
      <div class="col-span-12 lg:col-span-8 space-y-6">
        <!-- 基本信息 -->
        <div class="bg-white rounded-xl p-6 shadow-sm">
          <div class="flex items-center gap-2 mb-6 border-l-4 border-[#408aee] pl-4">
            <h3 class="text-lg font-bold text-gray-900">订单信息</h3>
            <span
              :class="statusTagClass(order.status)"
              class="ml-2 px-2 py-0.5 text-[10px] font-bold rounded-full"
            >
              {{ order.statusName }}
            </span>
            <span
              v-if="order.adjustmentStatus && order.adjustmentStatus !== 'NONE'"
              :class="adjustmentStatusTagClass(order.adjustmentStatus)"
              class="ml-1 px-2 py-0.5 text-[10px] font-bold rounded-full"
            >
              {{ adjustmentStatusName(order.adjustmentStatus) }}
            </span>
          </div>
          <div class="grid grid-cols-2 md:grid-cols-4 gap-6">
            <div>
              <p class="text-[10px] font-black text-gray-500 uppercase tracking-wider mb-1">订单编号</p>
              <p class="font-mono font-bold text-gray-900">{{ order.orderNo }}</p>
            </div>
            <div>
              <p class="text-[10px] font-black text-gray-500 uppercase tracking-wider mb-1">纸质单号</p>
              <p class="font-medium text-gray-700">{{ order.sourceDocNo || '-' }}</p>
            </div>
            <div>
              <p class="text-[10px] font-black text-gray-500 uppercase tracking-wider mb-1">订单日期</p>
              <p class="font-medium text-gray-700">{{ order.orderDate || '-' }}</p>
            </div>
            <div>
              <p class="text-[10px] font-black text-gray-500 uppercase tracking-wider mb-1">订单类型</p>
              <el-tag size="small" :type="order.orderType === 'PREORDER' ? 'warning' : 'success'">
                {{ order.orderTypeName || (order.orderType === 'PREORDER' ? '订货订单' : '现货订单') }}
              </el-tag>
            </div>
            <div>
              <p class="text-[10px] font-black text-gray-500 uppercase tracking-wider mb-1">下单时间</p>
              <p class="font-medium text-gray-700">{{ formatDateTime(order.createTime) }}</p>
            </div>
            <div>
              <p class="text-[10px] font-black text-gray-500 uppercase tracking-wider mb-1">来源档口/店铺</p>
              <p class="font-medium text-gray-700">{{ order.sourceShop || '-' }}</p>
            </div>
            <div>
              <p class="text-[10px] font-black text-gray-500 uppercase tracking-wider mb-1">开单人员</p>
              <p class="font-medium text-gray-700">{{ order.salesmanName || '-' }}</p>
            </div>
          </div>
        </div>

        <!-- 客户信息 -->
        <div class="bg-white rounded-xl p-6 shadow-sm">
          <div class="flex items-center gap-2 mb-6 border-l-4 border-[#408aee] pl-4">
            <h3 class="text-lg font-bold text-gray-900">客户信息</h3>
          </div>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div>
              <p class="text-[10px] font-black text-gray-500 uppercase tracking-wider mb-1">客户名称</p>
              <p class="font-semibold text-gray-900">{{ order.customerName }}</p>
            </div>
            <div>
              <p class="text-[10px] font-black text-gray-500 uppercase tracking-wider mb-1">客户电话</p>
              <p class="font-medium text-gray-700">{{ order.customerPhone || '-' }}</p>
            </div>
            <div>
              <p class="text-[10px] font-black text-gray-500 uppercase tracking-wider mb-1">配送地址</p>
              <p class="font-medium text-gray-700">{{ order.needDelivery === 1 ? order.deliveryAddress || '-' : '无需送货' }}</p>
            </div>
          </div>
        </div>

        <!-- 商品明细 -->
        <div class="bg-white rounded-xl shadow-sm overflow-hidden">
          <div class="p-6 border-b border-gray-100">
            <div class="flex items-center gap-2 border-l-4 border-[#408aee] pl-4">
              <h3 class="text-lg font-bold text-gray-900">商品明细</h3>
              <span class="ml-2 px-2 py-0.5 bg-gray-100 text-[10px] font-bold text-gray-600 rounded-full">
                {{ order.items?.length || 0 }} 种
              </span>
            </div>
          </div>
          <div class="overflow-x-auto">
            <table class="w-full text-left">
              <thead class="bg-gray-50 text-xs font-bold text-gray-500 uppercase tracking-wider">
                <tr>
                  <th class="px-6 py-4">商品名称</th>
                  <th class="px-6 py-4">SKU编码</th>
                  <th class="px-6 py-4">颜色/尺码</th>
                  <th class="px-6 py-4">单价</th>
                  <th class="px-6 py-4">成本价</th>
                  <th class="px-6 py-4">数量</th>
                  <th class="px-6 py-4">小计</th>
                  <th class="px-6 py-4">成本</th>
                  <th class="px-6 py-4">毛利</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                <tr v-for="item in order.items" :key="item.id" class="hover:bg-gray-50/50 transition-colors">
                  <td class="px-6 py-4 font-semibold text-gray-900">{{ item.productName }}</td>
                  <td class="px-6 py-4 font-mono text-sm text-gray-500">{{ item.skuCode || '-' }}</td>
                  <td class="px-6 py-4">
                    <span class="inline-flex items-center gap-1.5 px-2 py-1 rounded-md bg-gray-100 text-xs font-bold text-gray-600">
                      {{ item.colorName || '-' }} / {{ item.sizeName || '-' }}
                    </span>
                  </td>
                  <td class="px-6 py-4 font-medium text-gray-900">¥ {{ item.price?.toFixed(2) }}</td>
                  <td class="px-6 py-4 font-medium text-gray-900">¥ {{ (item.costPrice || 0).toFixed(2) }}</td>
                  <td class="px-6 py-4 font-bold">{{ item.quantity }}</td>
                  <td class="px-6 py-4 font-bold text-[#408aee]">¥ {{ item.subtotal?.toFixed(2) }}</td>
                  <td class="px-6 py-4 font-bold text-gray-700">¥ {{ (item.costAmount || 0).toFixed(2) }}</td>
                  <td class="px-6 py-4 font-bold" :class="Number(item.grossProfit || 0) >= 0 ? 'text-emerald-600' : 'text-red-600'">
                    ¥ {{ (item.grossProfit || 0).toFixed(2) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 配货计划区块 -->
        <div v-if="deliveryPlans.length > 0" class="bg-white rounded-xl shadow-sm overflow-hidden">
          <div class="p-6 border-b border-gray-100">
            <div class="flex items-center gap-2 border-l-4 border-[#408aee] pl-4">
              <h3 class="text-lg font-bold text-gray-900">配货计划</h3>
              <span
                :class="adjustmentStatusTagClass(order.adjustmentStatus)"
                class="ml-2 px-2 py-0.5 text-[10px] font-bold rounded-full"
              >
                {{ adjustmentStatusName(order.adjustmentStatus) }}
              </span>
            </div>
          </div>
          <div class="overflow-x-auto">
            <table class="w-full text-left">
              <thead class="bg-gray-50 text-xs font-bold text-gray-500 uppercase tracking-wider">
                <tr>
                  <th class="px-6 py-4">商品名称</th>
                  <th class="px-6 py-4">SKU编码</th>
                  <th class="px-6 py-4">颜色/尺码</th>
                  <th class="px-6 py-4">仓库</th>
                  <th class="px-6 py-4">计划数量</th>
                  <th class="px-6 py-4">配货数量</th>
                  <th class="px-6 py-4">已出库</th>
                  <th class="px-6 py-4">状态</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                <tr v-for="plan in deliveryPlans" :key="plan.id" class="hover:bg-gray-50/50 transition-colors">
                  <td class="px-6 py-4 font-semibold text-gray-900">{{ plan.productName }}</td>
                  <td class="px-6 py-4 font-mono text-sm text-gray-500">{{ plan.skuCode || '-' }}</td>
                  <td class="px-6 py-4">
                    <span class="inline-flex items-center gap-1.5 px-2 py-1 rounded-md bg-gray-100 text-xs font-bold text-gray-600">
                      {{ plan.colorName || '-' }} / {{ plan.sizeName || '-' }}
                    </span>
                  </td>
                  <td class="px-6 py-4 font-medium text-gray-700">{{ plan.warehouseName || '-' }}</td>
                  <td class="px-6 py-4 font-bold">{{ plan.plannedQty }}</td>
                  <td class="px-6 py-4 font-bold text-[#408aee]">{{ plan.allocatedQty }}</td>
                  <td class="px-6 py-4 font-medium text-gray-700">{{ plan.outQty }}</td>
                  <td class="px-6 py-4">
                    <span
                      :class="deliveryPlanStatusTagClass(plan.status)"
                      class="px-2 py-0.5 text-[10px] font-bold rounded-full"
                    >
                      {{ planStatusName(plan.status) }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 调整记录区块 -->
        <div v-if="adjustmentLogs.length > 0" class="bg-white rounded-xl shadow-sm overflow-hidden">
          <div class="p-6 border-b border-gray-100">
            <div class="flex items-center gap-2 border-l-4 border-amber-500 pl-4">
              <h3 class="text-lg font-bold text-gray-900">调整记录</h3>
            </div>
          </div>
          <div class="p-6">
            <div class="space-y-4">
              <div v-for="log in adjustmentLogs" :key="log.createTime" class="border border-gray-100 rounded-lg p-4">
                <div class="flex items-center justify-between mb-2">
                  <span class="font-bold text-gray-900">{{ adjustmentTypeName(log.adjustmentType) }}</span>
                  <span class="text-xs text-gray-500">{{ log.createTime ? formatDateTime(log.createTime) : '-' }}</span>
                </div>
                <div class="text-sm text-gray-600">
                  <template v-if="log.adjustmentType === 'REDUCE'">
                    原数量: {{ log.originalQuantity }} → 新数量: {{ log.newQuantity }}
                  </template>
                  <template v-else-if="log.adjustmentType === 'REPLACE'">
                    SKU {{ log.originalSkuId }} → SKU {{ log.newSkuId }}（数量: {{ log.newQuantity }}）
                  </template>
                  <template v-else-if="log.adjustmentType === 'REFUND'">
                    退款数量: {{ log.newQuantity }}
                  </template>
                </div>
                <div v-if="log.reason" class="mt-1 text-xs text-gray-400">原因: {{ log.reason }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧内容 -->
      <div class="col-span-12 lg:col-span-4 space-y-6">
        <!-- 金额汇总 -->
        <div class="bg-[#1a1c1e] text-white rounded-2xl p-6 shadow-2xl relative overflow-hidden">
          <div class="absolute -right-8 -top-8 w-24 h-24 bg-[#408aee]/20 rounded-full blur-2xl"></div>
          <div class="absolute -left-8 -bottom-8 w-24 h-24 bg-blue-400/10 rounded-full blur-2xl"></div>
          <div class="relative z-10">
            <h3 class="text-lg font-bold mb-6 flex items-center gap-2">
              <span class="material-symbols-outlined text-blue-400">payments</span>
              金额汇总
            </h3>
            <div class="space-y-4">
              <div class="flex justify-between items-center">
                <span class="text-slate-400 font-medium text-sm">订单总额</span>
                <span class="text-lg font-bold text-white">¥ {{ order.totalAmount?.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',') }}</span>
              </div>
              <div class="flex justify-between items-center">
                <span class="text-slate-400 font-medium text-sm">运费收入</span>
                <span class="text-lg font-bold text-white">¥ {{ (order.freightAmount || 0).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',') }}</span>
              </div>
              <div class="flex justify-between items-center">
                <span class="text-slate-400 font-medium text-sm">已付金额</span>
                <span class="text-lg font-bold text-emerald-400">¥ {{ order.paidAmount?.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',') }}</span>
              </div>
              <div class="flex justify-between items-center">
                <span class="text-slate-400 font-medium text-sm">运费成本</span>
                <span class="text-lg font-bold text-orange-300">¥ {{ (order.freightCost || 0).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',') }}</span>
              </div>
              <div class="flex justify-between items-center">
                <span class="text-slate-400 font-medium text-sm">总成本</span>
                <span class="text-lg font-bold text-orange-300">¥ {{ (order.totalCostAmount || 0).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',') }}</span>
              </div>
              <div class="flex justify-between items-center">
                <span class="text-slate-400 font-medium text-sm">毛利</span>
                <span class="text-lg font-bold" :class="Number(order.grossProfit || 0) >= 0 ? 'text-emerald-400' : 'text-red-400'">
                  ¥ {{ (order.grossProfit || 0).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',') }}
                </span>
              </div>
              <div v-if="order.depositAmount > 0" class="flex justify-between items-center">
                <span class="text-slate-400 font-medium text-sm">定金</span>
                <span class="text-lg font-bold text-amber-400">¥ {{ order.depositAmount?.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',') }}</span>
              </div>
              <div class="pt-4 border-t border-slate-800">
                <div class="flex justify-between items-end">
                  <div>
                    <p class="text-[10px] font-black text-blue-400 uppercase tracking-widest mb-1">应付尾款余额</p>
                    <h4 class="text-3xl font-black tracking-tight">¥ {{ (order.balanceAmount ?? 0).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',') }}</h4>
                  </div>
                  <div class="p-2 bg-[#408aee]/20 rounded-lg">
                    <span class="material-symbols-outlined text-blue-400">account_balance_wallet</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 支付状态 -->
        <div class="bg-white rounded-xl p-6 shadow-sm">
          <div class="flex items-center gap-2 mb-6 border-l-4 border-[#408aee] pl-4">
            <h3 class="text-lg font-bold text-gray-900">支付状态</h3>
          </div>
          <div class="flex items-center gap-3 mb-4">
            <span
              :class="paymentStatusTagClass(order.paymentStatus)"
              class="px-3 py-1.5 text-xs font-bold rounded-full"
            >
              {{ order.paymentStatusName }}
            </span>
          </div>
          <div class="space-y-3 text-sm">
            <div v-if="order.payTime" class="flex justify-between">
              <span class="text-gray-500">付款时间</span>
              <span class="text-gray-900">{{ formatDateTime(order.payTime) }}</span>
            </div>
            <div v-if="order.confirmTime" class="flex justify-between">
              <span class="text-gray-500">确认时间</span>
              <span class="text-gray-900">{{ formatDateTime(order.confirmTime) }}</span>
            </div>
          </div>
          <!-- 抹零/短款结清信息 -->
          <div v-if="(order.writeOffAmount ?? 0) > 0" class="mt-4 p-3 bg-amber-50 border border-amber-200 rounded-lg">
            <div class="flex justify-between text-sm">
              <span class="text-amber-700 font-medium">抹零/短款结清</span>
              <span class="font-bold text-amber-700">¥ {{ order.writeOffAmount?.toFixed(2) }}</span>
            </div>
            <div v-if="order.writeOffReason" class="text-xs text-amber-600 mt-1">{{ order.writeOffReason }}</div>
          </div>
        </div>

        <!-- 配送状态 -->
        <div class="bg-white rounded-xl p-6 shadow-sm">
          <div class="flex items-center gap-2 mb-6 border-l-4 border-[#408aee] pl-4">
            <h3 class="text-lg font-bold text-gray-900">配送状态</h3>
          </div>
          <div class="flex items-center gap-3 mb-4">
            <span
              :class="order.isDelivered === 1 ? 'bg-green-100 text-green-600' : 'bg-gray-100 text-gray-600'"
              class="px-3 py-1.5 text-xs font-bold rounded-full"
            >
              {{ order.isDelivered === 1 ? '已发货' : '未发货' }}
            </span>
          </div>
          <div v-if="order.deliverTime" class="space-y-3 text-sm">
            <div class="flex justify-between">
              <span class="text-gray-500">发货时间</span>
              <span class="text-gray-900">{{ formatDateTime(order.deliverTime) }}</span>
            </div>
          </div>

          <!-- 出库单列表 -->
          <div v-if="deliveries.length > 0" class="mt-6 border-t border-gray-100 pt-4">
            <div class="flex items-center gap-2 mb-4">
              <h4 class="text-sm font-bold text-gray-700">出库单</h4>
              <span class="px-2 py-0.5 bg-gray-100 text-[10px] font-bold text-gray-600 rounded-full">
                {{ deliveries.length }}
              </span>
            </div>
            <div class="space-y-3">
              <div v-for="delivery in deliveries" :key="delivery.id" class="border border-gray-100 rounded-lg p-4">
                <div class="flex items-center justify-between mb-2">
                  <div class="flex items-center gap-2">
                    <span class="font-mono text-sm font-bold text-gray-900">{{ delivery.deliveryNo }}</span>
                    <span :class="deliveryStatusTagClass(delivery.status)" class="px-2 py-0.5 text-[10px] font-bold rounded-full">
                      {{ delivery.statusName }}
                    </span>
                  </div>
                  <el-button
                    v-if="delivery.status === 0"
                    type="success"
                    size="small"
                    @click="handleConfirmDelivery(delivery.id)"
                  >
                    确认发货
                  </el-button>
                </div>
                <div class="grid grid-cols-3 gap-2 text-xs text-gray-500">
                  <div>仓库: {{ delivery.warehouseName }}</div>
                  <div>商品数量: {{ delivery.totalQuantity }}</div>
                  <div>创建时间: {{ formatDateTime(delivery.createTime) }}</div>
                </div>
                <!-- 出库明细 -->
                <div v-if="delivery.items && delivery.items.length > 0" class="mt-2 pt-2 border-t border-gray-50">
                  <div class="text-xs text-gray-400 mb-1">出库明细:</div>
                  <div v-for="item in delivery.items" :key="item.id" class="text-xs text-gray-600 pl-2">
                    {{ item.productName }} - {{ item.colorName }}/{{ item.sizeName }} × {{ item.quantity }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 占位明细拆分引导 -->
        <div v-if="placeholderRows.length > 0" class="bg-amber-50 border border-amber-200 rounded-xl p-4 flex items-center justify-between">
          <div class="text-sm text-amber-700">
            本订单含 {{ placeholderRows.length }} 行未指定颜色/尺码的占位明细，创建配货计划与出库前需先拆分到真实 SKU。
          </div>
          <el-button size="small" type="warning" @click="openSplitDialog(placeholderRows[0])">去拆分</el-button>
        </div>

        <!-- 金额与结清事实（新模型；历史行回退旧字段展示） -->
        <div class="bg-white rounded-xl p-6 shadow-sm">
          <div class="flex items-center gap-2 mb-4 border-l-4 border-[#408aee] pl-4">
            <h3 class="text-lg font-bold text-gray-900">金额与结清</h3>
            <el-tag v-if="legacyUnmigrated" size="small" type="warning">历史未迁移</el-tag>
          </div>
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div>
              <div class="text-gray-500 mb-1">客户实收</div>
              <div class="font-bold text-gray-900">¥ {{ fmt(order.grossReceivedAmount ?? order.paidAmount) }}</div>
            </div>
            <div>
              <div class="text-gray-500 mb-1">现金退款</div>
              <div class="font-bold text-red-500">¥ {{ fmt(order.cashRefundAmount ?? 0) }}</div>
            </div>
            <div>
              <div class="text-gray-500 mb-1">净实收</div>
              <div class="font-bold text-gray-900">¥ {{ fmt(order.netReceivedAmount ?? order.paidAmount) }}</div>
            </div>
            <div>
              <div class="text-gray-500 mb-1">短款核销</div>
              <div class="font-bold text-amber-600">¥ {{ fmt(order.writeOffAmount ?? 0) }}</div>
            </div>
            <div>
              <div class="text-gray-500 mb-1">待收尾款</div>
              <div class="font-bold text-red-500">¥ {{ fmt(order.balanceAmount ?? 0) }}</div>
            </div>
            <div>
              <div class="text-gray-500 mb-1">收款状态</div>
              <div class="font-bold">{{ collectionLabel ?? paymentStatusNameLegacy }}</div>
            </div>
            <div>
              <div class="text-gray-500 mb-1">结清方式</div>
              <div class="font-bold">{{ settlementLabel ?? '—' }}</div>
            </div>
            <div>
              <div class="text-gray-500 mb-1">履约方式</div>
              <div class="font-bold">{{ fulfillmentModeLabel }}</div>
            </div>
          </div>
          <!-- 财务流水 -->
          <div v-if="order.financialRecords && order.financialRecords.length > 0" class="mt-4 pt-4 border-t border-gray-100">
            <div class="text-sm font-bold text-gray-700 mb-2">财务流水</div>
            <el-table :data="order.financialRecords" size="small" class="w-full">
              <el-table-column label="时间" width="160">
                <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
              </el-table-column>
              <el-table-column label="类型" width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="recordTagType(row.recordType)">{{ recordTypeLabel(row.recordType) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="金额" width="110" align="right">
                <template #default="{ row }">¥ {{ fmt(row.amount) }}</template>
              </el-table-column>
              <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
              <el-table-column prop="operatorName" label="操作人" width="100" />
              <el-table-column label="来源" width="90">
                <template #default="{ row }">{{ row.source }}</template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <!-- 备注 -->
        <div v-if="order.remark" class="bg-white rounded-xl p-6 shadow-sm">
          <div class="flex items-center gap-2 mb-4 border-l-4 border-[#408aee] pl-4">
            <h3 class="text-lg font-bold text-gray-900">备注</h3>
          </div>
          <p class="text-gray-700 text-sm">{{ order.remark }}</p>
        </div>

        <!-- 图片 -->
        <div v-if="orderImageSources.length > 0" class="bg-white rounded-xl p-6 shadow-sm">
          <div class="flex items-center gap-2 mb-4 border-l-4 border-[#408aee] pl-4">
            <h3 class="text-lg font-bold text-gray-900">订单图片</h3>
          </div>
          <div class="flex flex-wrap gap-3">
            <button
              v-for="(img, idx) in orderImageSources"
              :key="idx"
              type="button"
              class="order-detail-image-thumb"
              aria-label="查看订单图片"
              @click="openOrderImageViewer(idx)"
            >
              <img :src="img" alt="" />
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 确认收款弹窗 -->
    <el-dialog v-model="showPayDialog" title="确认收款" width="520px" :close-on-click-modal="false">
      <div class="space-y-5 py-2">
        <div class="rounded-xl border border-gray-200 bg-gray-50 p-4 space-y-2.5">
          <div class="flex justify-between text-sm"><span class="text-gray-500">订单总额</span><span class="font-semibold">¥ {{ fmt(order?.totalAmount) }}</span></div>
          <div class="flex justify-between text-sm"><span class="text-gray-500">当前实收金额</span><span class="font-semibold text-blue-600">¥ {{ fmt(currentReceived) }}</span></div>
          <div class="flex justify-between text-sm"><span class="text-gray-500">待收尾款</span><span class="font-semibold text-red-500">¥ {{ fmt(currentBalance) }}</span></div>
          <div v-if="effectiveReceivable !== Number(order?.totalAmount ?? 0)" class="flex justify-between text-sm border-t border-gray-200 pt-2.5">
            <span class="text-gray-500">当前有效应收</span><span class="font-semibold">¥ {{ fmt(effectiveReceivable) }}</span>
          </div>
        </div>
        <div>
          <label class="block text-sm font-bold text-gray-700 mb-2">最终累计实收金额</label>
          <el-input-number
            v-model="payFinalReceived"
            :min="currentReceived"
            :max="effectiveReceivable"
            :precision="2"
            :step="1"
            class="!w-full"
          />
          <p class="mt-2 text-xs text-gray-400">填写订单最终实际收到的累计金额，系统会自动计算本次增收与短款。</p>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div class="rounded-xl border border-blue-100 bg-blue-50 p-3">
            <div class="text-xs text-blue-600">本次新增收款</div>
            <div class="mt-1 text-xl font-bold text-blue-700">¥ {{ fmt(settlementAdditional) }}</div>
          </div>
          <div class="rounded-xl border border-amber-100 bg-amber-50 p-3">
            <div class="text-xs text-amber-700">短款核销金额</div>
            <div class="mt-1 text-xl font-bold text-amber-700">¥ {{ fmt(settlementWriteOff) }}</div>
          </div>
        </div>
        <div v-if="settlementWriteOff > 0" class="rounded-xl border border-amber-200 bg-amber-50 p-4">
          <label class="block text-sm font-bold text-amber-800 mb-2">短款核销原因 <span class="text-red-500">*</span></label>
          <el-input v-model="payWriteOffReason" type="textarea" :rows="2" placeholder="如：客户少付5元，确认不再追收" />
          <p class="mt-2 text-xs text-amber-700">确认后，该短款不再计入应收尾款，订单将变为已结清。</p>
        </div>
        <el-alert
          v-if="settlementWriteOff > 0 && !hasAction('settleWithWriteOff')"
          type="warning"
          :closable="false"
          title="当前账号无短款核销权限；请足额收款或联系有权限的人员。"
        />
        <div class="text-sm text-gray-600">
          本次将新增收款 <strong>¥ {{ fmt(settlementAdditional) }}</strong>
          <template v-if="settlementWriteOff > 0">，并核销短款 <strong>¥ {{ fmt(settlementWriteOff) }}</strong></template>。
        </div>
      </div>
      <template #footer>
        <el-button @click="showPayDialog = false" :disabled="paySubmitting">取消</el-button>
        <el-button type="primary" :loading="paySubmitting" @click="confirmPay">
          {{ settlementWriteOff > 0 ? '确认收款并核销短款' : '确认收齐' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 现金退款弹窗 -->
    <el-dialog v-model="showRefundDialog" title="现金退款" width="420px">
      <div class="py-4 space-y-3">
        <div class="text-xs text-gray-400">现金退款只表示资金流出，不影响销售退货口径。</div>
        <div>
          <label class="block text-sm font-bold text-gray-500 mb-2">退款金额</label>
          <el-input-number v-model="refundAmount" :min="0" :precision="2" class="!w-full" />
        </div>
        <div>
          <label class="block text-sm font-bold text-gray-500 mb-2">退款原因（必填）</label>
          <el-input v-model="refundReason" type="textarea" :rows="2" />
        </div>
      </div>
      <template #footer>
        <el-button @click="showRefundDialog = false">取消</el-button>
        <el-button type="danger" @click="submitRefund">确认退款</el-button>
      </template>
    </el-dialog>

    <!-- 占位明细拆分弹窗 -->
    <el-dialog v-model="showSplitDialog" title="拆分占位明细到真实SKU" width="560px">
      <div class="py-2 space-y-3">
        <div class="text-xs text-gray-400">
          拆分数量合计必须等于占位数量 {{ splitRow?.quantity }}，单价与成本沿用占位行，金额保持守恒。
        </div>
        <div v-for="(row, idx) in splitTargets" :key="idx" class="flex items-center gap-2">
          <el-select
            v-model="row.skuId"
            filterable
            remote
            :remote-method="searchSplitSku"
            :loading="splitSkuLoading"
            placeholder="搜索商品/款号选择真实SKU"
            class="flex-1"
          >
            <el-option
              v-for="opt in splitSkuOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <el-input-number v-model="row.quantity" :min="1" :step="1" class="!w-28" />
          <el-button text type="danger" @click="splitTargets.splice(idx, 1)">删除</el-button>
        </div>
        <el-button text type="primary" @click="splitTargets.push({ skuId: undefined as any, quantity: 1 })">
          + 添加一行
        </el-button>
      </div>
      <template #footer>
        <el-button @click="showSplitDialog = false">取消</el-button>
        <el-button type="primary" @click="submitSplit">确认拆分</el-button>
      </template>
    </el-dialog>

    <!-- 加收金额弹窗 -->
    <el-dialog v-model="showAddPayDialog" title="加收金额" width="480px" :close-on-click-modal="false">
      <div class="py-2 space-y-5">
        <div class="rounded-xl border border-gray-200 bg-gray-50 p-4 space-y-2.5">
          <div class="flex justify-between text-sm"><span class="text-gray-500">订单总额</span><span class="font-semibold">¥ {{ fmt(order?.totalAmount) }}</span></div>
          <div class="flex justify-between text-sm"><span class="text-gray-500">已收款金额</span><span class="font-semibold text-blue-600">¥ {{ fmt(currentReceived) }}</span></div>
          <div class="flex justify-between text-sm"><span class="text-gray-500">待收尾款</span><span class="font-semibold text-red-500">¥ {{ fmt(currentBalance) }}</span></div>
        </div>
        <div>
          <label class="block text-sm font-bold text-gray-700 mb-2">加收金额</label>
          <el-input-number
            v-model="addPayAmount"
            :min="0.01"
            :max="currentBalance"
            :precision="2"
            :step="1"
            class="!w-full"
          />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div class="rounded-xl border border-blue-100 bg-blue-50 p-3">
            <div class="text-xs text-blue-600">加收后累计实收</div>
            <div class="mt-1 text-lg font-bold text-blue-700">¥ {{ fmt(addPayResultReceived) }}</div>
          </div>
          <div class="rounded-xl border border-gray-200 bg-gray-50 p-3">
            <div class="text-xs text-gray-500">加收后剩余尾款</div>
            <div class="mt-1 text-lg font-bold text-gray-800">¥ {{ fmt(addPayResultBalance) }}</div>
          </div>
        </div>
        <p class="text-xs text-gray-400">此操作只记录一笔新增收款，不会自动核销尾款。</p>
      </div>
      <template #footer>
        <el-button @click="showAddPayDialog = false" :disabled="addPaySubmitting">取消</el-button>
        <el-button type="primary" :loading="addPaySubmitting" @click="confirmAddPay">确认加收</el-button>
      </template>
    </el-dialog>

    <!-- 取消订单弹窗 -->
    <el-dialog v-model="showCancelDialog" title="取消订单" width="400px">
      <div class="py-4">
        <p class="text-gray-600 mb-4">确定要取消此订单吗？此操作不可恢复。</p>
        <div>
          <label class="block text-sm font-bold text-gray-500 mb-2">取消原因</label>
          <el-input
            v-model="cancelReason"
            type="textarea"
            :rows="3"
            placeholder="请输入取消原因"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="showCancelDialog = false">取消</el-button>
        <el-button type="danger" @click="confirmCancel">确认取消</el-button>
      </template>
    </el-dialog>

    <!-- 创建/编辑配货计划弹窗 -->
    <el-dialog v-model="showDeliveryPlanDialog" :title="deliveryPlanDialogTitle" width="1050px" class="delivery-plan-dialog">
      <div class="py-2">
        <!-- 提示信息 -->
        <div class="mb-4 p-3 bg-blue-50 border border-blue-200 rounded-lg text-sm text-blue-700">
          为每个商品选择仓库和配货数量。确认后将同步仓库信息到订单明细。
          <span class="text-xs text-blue-500 block mt-1">库存提示仅作软参考，最终库存校验在发货时进行。库存不足不阻断配货方案保存。</span>
        </div>

        <!-- 配货明细表格 -->
        <div class="border border-gray-200 rounded-lg overflow-hidden">
          <div class="delivery-plan-table-wrapper">
            <table class="w-full text-left text-sm">
              <thead class="bg-gray-50 text-gray-500 text-xs">
                <tr>
                  <th class="px-3 py-2.5">商品</th>
                  <th class="px-3 py-2.5">颜色/尺码</th>
                  <th class="px-3 py-2.5 w-[60px]">计划</th>
                  <th class="px-3 py-2.5">仓库</th>
                  <th class="px-3 py-2.5 w-[80px]">配货</th>
                  <th class="px-3 py-2.5 w-[120px]">库存提示</th>
                  <th class="px-3 py-2.5">备注</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                <tr v-for="item in deliveryPlanItems" :key="item.orderItemId" class="hover:bg-gray-50">
                  <td class="px-3 py-2.5 font-medium text-gray-900">{{ item.productName }}</td>
                  <td class="px-3 py-2.5 text-gray-600 text-xs">{{ item.colorName }} / {{ item.sizeName }}</td>
                  <td class="px-3 py-2.5 font-bold text-center">{{ item.plannedQty }}</td>
                  <td class="px-3 py-2.5">
                    <el-select v-model="item.warehouseId" placeholder="选择仓库" size="small" class="!w-28" @change="onWarehouseChange">
                      <el-option v-for="wh in warehouses" :key="wh.id" :label="wh.warehouseName" :value="wh.id" />
                    </el-select>
                  </td>
                  <td class="px-3 py-2.5">
                    <el-input-number
                      v-model="item.allocatedQty"
                      :min="0"
                      :max="item.plannedQty"
                      size="small"
                      class="!w-[72px]"
                    />
                  </td>
                  <td class="px-3 py-2.5">
                    <span :class="getInventoryStatus(item).cssClass" class="text-xs font-medium whitespace-nowrap">
                      {{ getInventoryStatus(item).text }}
                    </span>
                  </td>
                  <td class="px-3 py-2.5">
                    <el-input v-model="item.remark" placeholder="调整/替换说明" size="small" class="!w-28" />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showDeliveryPlanDialog = false">取消</el-button>
        <el-button type="primary" :loading="planLoading" @click="submitDeliveryPlan">保存</el-button>
      </template>
    </el-dialog>

    <ElImageViewer
      v-if="imageViewerVisible"
      :url-list="orderImageOriginalSources"
      :initial-index="imageViewerIndex"
      @close="closeImageViewer"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElImageViewer, ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { getOrderById, confirmSettlement, addPayment, completeOrder, cancelOrder, getDeliveriesByOrderId, confirmDelivery, deliverOrder as deliverOrderApi, createDeliveryPlan, getDeliveryPlan, updateDeliveryPlan, confirmAdjustment as confirmAdjustmentApi, cancelAdjustment as cancelAdjustmentApi, getAdjustmentLogs, type OrderVO, type OrderDeliveryVO, type DeliveryPlanVO, type AdjustmentLogDTO, type AddPaymentDTO, refundPayment, chooseFulfillmentMode, splitPlaceholderItem } from '@/api/order'
import { getAllWarehouses, getInventoryByWarehouse, type WarehouseVO, type InventoryVO } from '@/api/inventory'
import { parseImageSources, parseImageVariantSources } from '@/api/file'

const router = useRouter()
const route = useRoute()

const order = ref<OrderVO | null>(null)
const loading = ref(true)
const showPayDialog = ref(false)
const showCancelDialog = ref(false)
const payFinalReceived = ref(0)
const payWriteOffReason = ref('')
const paySubmitting = ref(false)
const payIdempotencyKey = ref('')
const cancelReason = ref('')
const imageViewerVisible = ref(false)
const imageViewerIndex = ref(0)

// 出库单相关
const deliveries = ref<OrderDeliveryVO[]>([])
const warehouses = ref<WarehouseVO[]>([])

// 配货计划相关
const deliveryPlans = ref<DeliveryPlanVO[]>([])
const adjustmentLogs = ref<AdjustmentLogDTO[]>([])
const showDeliveryPlanDialog = ref(false)
const deliveryPlanDialogTitle = ref('创建配货计划')
const deliveryPlanItems = ref<{
  orderItemId: number
  skuId: number
  productName: string
  colorName: string
  sizeName: string
  plannedQty: number
  allocatedQty: number
  warehouseId: number | undefined
  remark: string
}[]>([])
const planLoading = ref(false)

// Inventory hint cache (per-warehouse, per dialog session)
const inventoryCache = reactive<Record<number, InventoryVO[]>>({})
const inventoryLoadingState = reactive<Record<number, boolean>>({})
const inventoryError = reactive<Record<number, boolean>>({})
const inventoryPromiseMap = new Map<number, Promise<void>>()
let inventorySessionId = 0

function clearInventoryCache() {
  inventorySessionId += 1
  Object.keys(inventoryCache).forEach(k => delete inventoryCache[Number(k)])
  Object.keys(inventoryLoadingState).forEach(k => delete inventoryLoadingState[Number(k)])
  Object.keys(inventoryError).forEach(k => delete inventoryError[Number(k)])
  inventoryPromiseMap.clear()
}

async function ensureWarehouseInventory(warehouseId: number) {
  if (!warehouseId) return
  if (inventoryCache[warehouseId] || inventoryLoadingState[warehouseId]) return
  if (inventoryPromiseMap.has(warehouseId)) {
    await inventoryPromiseMap.get(warehouseId)
    return
  }
  inventoryLoadingState[warehouseId] = true
  inventoryError[warehouseId] = false
  const sessionId = inventorySessionId
  const promise = getInventoryByWarehouse(warehouseId)
    .then((res: any) => {
      if (sessionId !== inventorySessionId) return
      inventoryCache[warehouseId] = res.data || []
      inventoryError[warehouseId] = false
    })
    .catch(() => {
      if (sessionId !== inventorySessionId) return
      inventoryError[warehouseId] = true
    })
    .finally(() => {
      if (sessionId !== inventorySessionId) return
      inventoryLoadingState[warehouseId] = false
      inventoryPromiseMap.delete(warehouseId)
    })
  inventoryPromiseMap.set(warehouseId, promise)
  await promise
}

function onWarehouseChange(warehouseId: number) {
  if (warehouseId) ensureWarehouseInventory(warehouseId)
}

function getInventoryStatus(item: typeof deliveryPlanItems.value[number]): { text: string; cssClass: string } {
  const wid = item.warehouseId
  if (!wid) return { text: '未选仓库', cssClass: 'text-gray-400' }
  if (inventoryLoadingState[wid]) return { text: '查询中...', cssClass: 'text-blue-500' }
  if (inventoryError[wid]) return { text: '查询失败', cssClass: 'text-red-500' }
  const records = inventoryCache[wid]
  if (!records) return { text: '...', cssClass: 'text-gray-400' }
  const inv = records.find((r: InventoryVO) => r.skuId === item.skuId)
  if (!inv) return { text: '无库存记录', cssClass: 'text-amber-600' }
  // Order allocation is advisory only and follows the shipment rule. Historical
  // global reservations must not make an otherwise shippable SKU look short.
  const available = (inv.quantity || 0) - (inv.reservedQty || 0)
  const need = item.allocatedQty
  if (available >= need) return { text: `可用 ${available}`, cssClass: 'text-green-600' }
  return { text: `可用 ${available} / 缺 ${need - available}`, cssClass: 'text-amber-600' }
}

const orderId = Number(route.params.id)


// ==== 新模型辅助（系列 D） ====
const legacyUnmigrated = computed(() => !!order.value?.legacyUnmigrated)

function hasAction(action: string): boolean {
  const list = order.value?.allowedActions
  if (!list || list.length === 0) return false
  return list.includes(action)
}

function fmt(v: number | undefined | null): string {
  return Number(v ?? 0).toFixed(2)
}

const collectionLabel = computed(() => {
  const map: Record<string, string> = { UNPAID: '未收款', PARTIAL: '部分收款', SETTLED: '已结清' }
  return order.value?.collectionStatus ? map[order.value.collectionStatus] ?? order.value.collectionStatus : null
})
const paymentStatusNameLegacy = computed(() => order.value?.paymentStatusName ?? '')
const settlementLabel = computed(() => {
  const map: Record<string, string> = { FULL_RECEIPT: '足额收款', WRITE_OFF: '短款结清', MIGRATION_CONFIRMED: '迁移确认' }
  return order.value?.settlementMethod ? map[order.value.settlementMethod] ?? order.value.settlementMethod : null
})
const fulfillmentModeLabel = computed(() => {
  const map: Record<string, string> = { UNDECIDED: '尚未选择', STOCK_LINKED: '关联库存', RECORD_ONLY: '仅记录订单' }
  return order.value?.fulfillmentMode ? map[order.value.fulfillmentMode] ?? order.value.fulfillmentMode : '—'
})

function recordTypeLabel(t: string): string {
  const map: Record<string, string> = {
    RECEIPT: '收款', WRITE_OFF: '短款核销', REFUND: '现金退款', REVERSAL: '冲销', MIGRATION_OPENING: '迁移期初',
  }
  return map[t] ?? t
}
function recordTagType(t: string): 'success' | 'warning' | 'danger' | 'info' {
  if (t === 'RECEIPT') return 'success'
  if (t === 'REFUND') return 'danger'
  if (t === 'WRITE_OFF') return 'warning'
  return 'info'
}

const showRefundDialog = ref(false)
const refundAmount = ref<number>(0)
const refundReason = ref('')
async function submitRefund() {
  if (!order.value) return
  if (!refundAmount.value || refundAmount.value <= 0) {
    ElMessage.warning('请填写退款金额')
    return
  }
  if (!refundReason.value.trim()) {
    ElMessage.warning('退款必须填写原因')
    return
  }
  try {
    await refundPayment(order.value.id, { amount: refundAmount.value, reason: refundReason.value.trim() })
    ElMessage.success('退款已入账')
    showRefundDialog.value = false
    refundAmount.value = 0
    refundReason.value = ''
    await loadOrder()
  } catch (error: any) {
    ElMessage.error(error.message || '退款失败')
  }
}

async function handleChooseFulfillmentMode(mode: 'STOCK_LINKED' | 'RECORD_ONLY') {
  if (!order.value) return
  const tip = mode === 'RECORD_ONLY'
    ? '仅记录订单完成后直接进入已完成，且不产生任何库存流水。确认选择？'
    : '关联库存订单需经过配货、确认与出库。确认选择？'
  try {
    await ElMessageBox.confirm(tip, '选择履约方式', { type: 'warning' })
  } catch {
    return
  }
  try {
    await chooseFulfillmentMode(order.value.id, mode)
    ElMessage.success('履约方式已确认')
    await loadOrder()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}



// ==== 占位明细拆分（系列 D） ====
const placeholderRows = computed(() =>
  (order.value?.items ?? []).filter(i => i.skuType === 'PLACEHOLDER'))
const showSplitDialog = ref(false)
const splitRow = ref<any>(null)
const splitTargets = ref<{ skuId?: number; quantity: number }[]>([])
const splitSkuOptions = ref<{ value: number; label: string }[]>([])
const splitSkuLoading = ref(false)

function openSplitDialog(row: any) {
  splitRow.value = row
  splitTargets.value = [{ skuId: undefined as any, quantity: row.quantity }]
  splitSkuOptions.value = []
  showSplitDialog.value = true
}

async function searchSplitSku(keyword: string) {
  if (!keyword || !keyword.trim()) return
  splitSkuLoading.value = true
  try {
    const { getProductPage } = await import('@/api/product')
    const res = await getProductPage({ keyword: keyword.trim(), size: 10, status: 1 })
    const options: { value: number; label: string }[] = []
    for (const product of res.data.records ?? []) {
      for (const sku of product.skus ?? []) {
        if (sku.skuType === 'PLACEHOLDER' || sku.status !== 1) continue
        options.push({
          value: sku.id,
          label: `${product.name} / ${sku.colorName ?? ''}-${sku.sizeName ?? ''} / ${sku.skuCode}`,
        })
      }
    }
    splitSkuOptions.value = options
  } catch {
    splitSkuOptions.value = []
  } finally {
    splitSkuLoading.value = false
  }
}

async function submitSplit() {
  if (!order.value || !splitRow.value) return
  const targets = splitTargets.value.filter(t => t.skuId && t.quantity > 0) as { skuId: number; quantity: number }[]
  if (targets.length === 0) {
    ElMessage.warning('请至少选择一个目标 SKU')
    return
  }
  const total = targets.reduce((s, t) => s + Number(t.quantity), 0)
  if (total !== splitRow.value.quantity) {
    ElMessage.warning(`拆分数量合计（${total}）必须等于占位数量（${splitRow.value.quantity}）`)
    return
  }
  try {
    await splitPlaceholderItem(order.value.id, splitRow.value.id, { targets })
    ElMessage.success('拆分完成，金额保持守恒')
    showSplitDialog.value = false
    await loadOrder()
  } catch (error: any) {
    ElMessage.error(error.message || '拆分失败')
  }
}

const orderImageSources = computed(() => parseImageVariantSources(order.value?.images, 'thumb'))
const orderImageOriginalSources = computed(() => parseImageSources(order.value?.images))

function openOrderImageViewer(index = 0) {
  if (orderImageSources.value.length === 0) return
  imageViewerIndex.value = index
  imageViewerVisible.value = true
}

function closeImageViewer() {
  imageViewerVisible.value = false
  imageViewerIndex.value = 0
}

onMounted(async () => {
  await loadOrder()
  await loadDeliveries()
  await loadDeliveryPlans()
  await loadAdjustmentLogs()
})

async function loadOrder() {
  loading.value = true
  try {
    const res = await getOrderById(orderId)
    order.value = res.data
  } catch (error: any) {
    ElMessage.error(error.message || '加载订单详情失败')
  } finally {
    loading.value = false
  }
}

async function loadDeliveries() {
  try {
    const res = await getDeliveriesByOrderId(orderId)
    deliveries.value = res.data || []
  } catch (error: any) {
    console.error('加载出库单失败:', error)
  }
}

function handleBack() {
  router.back()
}

async function handleConfirmPayment() {
  payFinalReceived.value = currentReceived.value
  payWriteOffReason.value = ''
  paySubmitting.value = false
  payIdempotencyKey.value = typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `settle-${orderId}-${Date.now()}`
  showPayDialog.value = true
}

async function confirmPay() {
  if (paySubmitting.value) return
  if (payFinalReceived.value < currentReceived.value || payFinalReceived.value > effectiveReceivable.value) {
    ElMessage.warning('最终实收金额必须在当前实收与有效应收之间')
    return
  }
  if (settlementWriteOff.value > 0 && payFinalReceived.value <= 0) {
    ElMessage.warning('不能将整笔订单作为短款核销')
    return
  }
  if (settlementWriteOff.value > 0 && !hasAction('settleWithWriteOff')) {
    ElMessage.warning('当前账号无短款核销权限')
    return
  }
  if (settlementWriteOff.value > 0 && !payWriteOffReason.value.trim()) {
    ElMessage.warning('请填写短款核销原因')
    return
  }
  paySubmitting.value = true
  try {
    await confirmSettlement(orderId, {
      finalReceivedAmount: payFinalReceived.value,
      writeOffReason: settlementWriteOff.value > 0 ? payWriteOffReason.value.trim() : undefined,
      idempotencyKey: payIdempotencyKey.value,
    })
    ElMessage.success(settlementWriteOff.value > 0 ? '收款及短款核销已确认' : '订单已足额收款')
    showPayDialog.value = false
    await loadOrder()
  } catch (error: any) {
    ElMessage.error(error.message || '收款确认失败')
  } finally {
    paySubmitting.value = false
  }
}

const roundMoney = (value: number) => Math.round((value + Number.EPSILON) * 100) / 100
const currentReceived = computed(() => roundMoney(Number(order.value?.netReceivedAmount ?? order.value?.paidAmount ?? 0)))
const currentBalance = computed(() => roundMoney(Number(order.value?.balanceAmount ?? 0)))
const effectiveReceivable = computed(() => roundMoney(currentReceived.value + currentBalance.value))
const settlementAdditional = computed(() => roundMoney(Math.max(0, payFinalReceived.value - currentReceived.value)))
const settlementWriteOff = computed(() => roundMoney(Math.max(0, effectiveReceivable.value - payFinalReceived.value)))

// 加收金额
const showAddPayDialog = ref(false)
const addPayAmount = ref(0)
const addPaySubmitting = ref(false)
const addPayResultReceived = computed(() => roundMoney(currentReceived.value + Number(addPayAmount.value || 0)))
const addPayResultBalance = computed(() => roundMoney(Math.max(0, currentBalance.value - Number(addPayAmount.value || 0))))

function handleAddPayment() {
  addPayAmount.value = 0
  addPaySubmitting.value = false
  showAddPayDialog.value = true
}

async function confirmAddPay() {
  if (addPaySubmitting.value) return
  if (addPayAmount.value <= 0 || addPayAmount.value > currentBalance.value) {
    ElMessage.warning('加收金额必须大于0且不能超过待收尾款')
    return
  }
  addPaySubmitting.value = true
  try {
    const payload: AddPaymentDTO = {
      additionalAmount: addPayAmount.value,
    }
    await addPayment(orderId, payload)
    ElMessage.success('加收金额已记录')
    showAddPayDialog.value = false
    await loadOrder()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    addPaySubmitting.value = false
  }
}

// 发货（新的配货计划模式）
async function handleDeliver() {
  try {
    await ElMessageBox.confirm('确认发货？将按配货计划扣减库存。', '发货确认', {
      confirmButtonText: '确认发货',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deliverOrderApi(orderId)
    ElMessage.success('发货成功')
    await loadOrder()
    await loadDeliveryPlans()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '发货失败')
    }
  }
}

// ============ 配货计划相关函数 ============

async function loadDeliveryPlans() {
  try {
    const res = await getDeliveryPlan(orderId)
    deliveryPlans.value = res.data || []
  } catch (error: any) {
    console.error('加载配货计划失败:', error)
  }
}

async function loadAdjustmentLogs() {
  try {
    const res = await getAdjustmentLogs(orderId)
    adjustmentLogs.value = res.data || []
  } catch (error: any) {
    console.error('加载调整记录失败:', error)
  }
}

// 打开创建配货计划弹窗
async function handleCreateDeliveryPlan() {
  // 确保订单数据加载完成
  if (!order.value?.items || order.value.items.length === 0) {
    await loadOrder()
  }

  // 加载仓库列表
  try {
    const res = await getAllWarehouses()
    warehouses.value = res.data || []
  } catch (error: any) {
    ElMessage.error('加载仓库列表失败')
    return
  }

  // 清除会话内库存缓存
  clearInventoryCache()

  // 初始化配货明细（从订单商品）
  deliveryPlanItems.value = (order.value?.items || []).map(item => ({
    orderItemId: item.id,
    skuId: item.skuId,
    productName: item.productName,
    colorName: item.colorName || '',
    sizeName: item.sizeName || '',
    plannedQty: item.quantity,
    allocatedQty: item.quantity,
    warehouseId: order.value?.warehouseId || undefined,
    remark: ''
  }))

  // 加载当前已选仓库的库存
  const selectedIds = [...new Set(deliveryPlanItems.value.map(item => item.warehouseId).filter(Boolean))] as number[]
  selectedIds.forEach(wid => ensureWarehouseInventory(wid))

  deliveryPlanDialogTitle.value = '创建配货计划'
  showDeliveryPlanDialog.value = true
}

// 打开编辑配货计划弹窗
async function handleEditDeliveryPlan() {
  // 加载仓库列表
  try {
    const res = await getAllWarehouses()
    warehouses.value = res.data || []
  } catch (error: any) {
    ElMessage.error('加载仓库列表失败')
    return
  }

  // 清除会话内库存缓存
  clearInventoryCache()

  // 初始化配货明细（从现有配货计划）
  deliveryPlanItems.value = deliveryPlans.value.map(plan => ({
    orderItemId: plan.orderItemId,
    skuId: plan.skuId,
    productName: plan.productName,
    colorName: plan.colorName || '',
    sizeName: plan.sizeName || '',
    plannedQty: plan.plannedQty,
    allocatedQty: plan.allocatedQty,
    warehouseId: plan.warehouseId || undefined,
    remark: plan.remark || ''
  }))

  // 加载当前已选仓库的库存
  const selectedIds = [...new Set(deliveryPlanItems.value.map(item => item.warehouseId).filter(Boolean))] as number[]
  selectedIds.forEach(wid => ensureWarehouseInventory(wid))

  deliveryPlanDialogTitle.value = '编辑配货计划'
  showDeliveryPlanDialog.value = true
}

// 提交配货计划
async function submitDeliveryPlan() {
  planLoading.value = true
  try {
    const payload = {
      orderId,
      items: deliveryPlanItems.value.map(item => ({
        orderItemId: item.orderItemId,
        skuId: item.skuId,
        warehouseId: item.warehouseId,
        plannedQty: item.plannedQty,
        allocatedQty: item.allocatedQty,
        remark: item.remark
      }))
    }

    // 判断是创建还是更新
    if (deliveryPlans.value.length === 0) {
      // 先创建默认计划，再立即按弹窗编辑结果覆盖，避免首次创建时丢失仓库和数量调整
      await createDeliveryPlan(orderId)
      await updateDeliveryPlan(orderId, payload)
      ElMessage.success('配货计划创建成功')
    } else {
      // 更新配货计划
      await updateDeliveryPlan(orderId, payload)
      ElMessage.success('配货计划更新成功')
    }
    showDeliveryPlanDialog.value = false
    await loadDeliveryPlans()
    await loadOrder()
  } catch (error: any) {
    ElMessage.error(error.message || '保存失败')
  } finally {
    planLoading.value = false
  }
}

// 确认调整方案
async function handleConfirmAdjustment() {
  try {
    await ElMessageBox.confirm('确认调整方案？确认后将同步仓库信息到订单明细。', '确认调整', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await confirmAdjustmentApi(orderId)
    ElMessage.success('调整方案已确认')
    await loadDeliveryPlans()
    await loadOrder()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

// 取消调整
async function handleCancelAdjustment() {
  try {
    await ElMessageBox.confirm('取消调整将删除配货计划和调整记录，确定要取消吗？', '取消调整', {
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      type: 'warning',
    })
    await cancelAdjustmentApi(orderId)
    ElMessage.success('已取消调整')
    deliveryPlans.value = []
    adjustmentLogs.value = []
    await loadOrder()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

async function handleConfirmDelivery(deliveryId: number) {
  try {
    await ElMessageBox.confirm('确认该出库单已发货？', '发货确认', {
      confirmButtonText: '确认发货',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await confirmDelivery(deliveryId)
    ElMessage.success('发货确认成功')
    await loadDeliveries()
    await loadOrder()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '发货确认失败')
    }
  }
}

async function handleComplete() {
  try {
    await ElMessageBox.confirm('确认完成此订单？', '完成确认', {
      confirmButtonText: '确认完成',
      cancelButtonText: '取消',
      type: 'success',
    })
    await completeOrder(orderId)
    ElMessage.success('订单已完成')
    await loadOrder()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

async function handleCancel() {
  showCancelDialog.value = true
  cancelReason.value = ''
}

async function confirmCancel() {
  if (!cancelReason.value.trim()) {
    ElMessage.warning('请输入取消原因')
    return
  }
  try {
    await cancelOrder(orderId, cancelReason.value)
    ElMessage.success('订单已取消')
    showCancelDialog.value = false
    await loadOrder()
  } catch (error: any) {
    ElMessage.error(error.message || '取消失败')
  }
}

function statusTagClass(status: number) {
  switch (status) {
    case 0: return 'bg-blue-100 text-blue-600'
    case 1: return 'bg-amber-100 text-amber-600'
    case 2: return 'bg-purple-100 text-purple-600'
    case 3: return 'bg-green-100 text-green-600'
    case 4: return 'bg-red-100 text-red-600'
    case 5: return 'bg-orange-100 text-orange-600'
    case 6: return 'bg-gray-100 text-gray-600'
    default: return 'bg-gray-100 text-gray-600'
  }
}

function paymentStatusTagClass(status: number) {
  switch (status) {
    case 0: return 'bg-gray-100 text-gray-600'
    case 1: return 'bg-amber-100 text-amber-600'
    case 2: return 'bg-green-100 text-green-600'
    default: return 'bg-gray-100 text-gray-600'
  }
}

function deliveryStatusTagClass(status: number) {
  switch (status) {
    case 0: return 'bg-gray-100 text-gray-600'
    case 1: return 'bg-amber-100 text-amber-600'
    case 2: return 'bg-green-100 text-green-600'
    case 3: return 'bg-red-100 text-red-600'
    default: return 'bg-gray-100 text-gray-600'
  }
}

function adjustmentStatusName(status: string) {
  const map: Record<string, string> = {
    'NONE': '无调整',
    'PENDING': '待确认',
    'APPROVED': '已确认',
    'COMPLETED': '已完成'
  }
  return map[status] || status
}

function adjustmentStatusTagClass(status: string) {
  switch (status) {
    case 'NONE': return 'bg-gray-100 text-gray-600'
    case 'PENDING': return 'bg-amber-100 text-amber-600'
    case 'APPROVED': return 'bg-green-100 text-green-600'
    case 'COMPLETED': return 'bg-blue-100 text-blue-600'
    default: return 'bg-gray-100 text-gray-600'
  }
}

function planStatusName(status: string) {
  const map: Record<string, string> = {
    'PENDING': '待配',
    'ALLOCATED': '已配',
    'OUT': '已出库'
  }
  return map[status] || status
}

function deliveryPlanStatusTagClass(status: string) {
  switch (status) {
    case 'PENDING': return 'bg-amber-100 text-amber-600'
    case 'ALLOCATED': return 'bg-green-100 text-green-600'
    case 'OUT': return 'bg-blue-100 text-blue-600'
    default: return 'bg-gray-100 text-gray-600'
  }
}

function adjustmentTypeName(type: string) {
  const map: Record<string, string> = {
    'REDUCE': '减数量',
    'REPLACE': '替换',
    'REFUND': '退款'
  }
  return map[type] || type
}

function formatDateTime(dateStr: string) {
  if (!dateStr) return '-'
  return dateStr.split('T')[0]
}
</script>

<style scoped>
.order-detail-page {
  padding: 0;
}

.order-detail-image-thumb {
  width: 80px;
  height: 80px;
  padding: 0;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  background: #f9fafb;
  cursor: zoom-in;
  transition: transform 0.16s ease, box-shadow 0.16s ease, border-color 0.16s ease;
}

.order-detail-image-thumb:hover {
  transform: scale(1.04);
  border-color: #408aee;
  box-shadow: 0 10px 24px rgb(64 138 238 / 16%);
}

.order-detail-image-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

:global(.delivery-plan-dialog) {
  max-width: 94vw;
}

.delivery-plan-table-wrapper {
  overflow-x: auto;
  max-height: 50vh;
  overflow-y: auto;
}
</style>
