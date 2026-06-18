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
        <!-- 状态操作按钮 -->
        <template v-if="order">
          <el-button
            v-if="order.status === 0"
            type="warning"
            class="!bg-amber-500 !border-amber-500"
            @click="handleConfirmPayment"
          >
            确认收款
          </el-button>
          <el-button
            v-if="canAddPayment"
            type="warning"
            plain
            @click="handleAddPayment"
          >
            追加收款
          </el-button>
          <!-- 配货计划按钮 -->
          <template v-if="order.status === 1 && deliveryPlans.length === 0">
            <el-button
              type="success"
              @click="handleCreateDeliveryPlan"
            >
              创建配货计划
            </el-button>
          </template>
          <el-button
            v-if="order.status === 2"
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
            v-if="order.status === 3"
            type="success"
            @click="handleDeliver"
          >
            发货
          </el-button>
          <el-button
            v-if="order.status === 4"
            type="primary"
            @click="handleComplete"
          >
            完成订单
          </el-button>
          <el-button
            v-if="order.status !== 4 && order.status !== 3 && order.status !== 6 && order.status !== 2"
            type="danger"
            @click="handleCancel"
          >
            取消订单
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
                    <h4 class="text-3xl font-black tracking-tight">¥ {{ (order.totalAmount - order.paidAmount).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',') }}</h4>
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
    <el-dialog v-model="showPayDialog" title="确认收款" width="400px">
      <div class="py-4">
        <p class="text-gray-600 mb-4">订单总额：<span class="font-bold text-gray-900">¥ {{ order?.totalAmount?.toFixed(2) }}</span></p>
        <div>
          <label class="block text-sm font-bold text-gray-500 mb-2">实收金额</label>
          <el-input-number
            v-model="payAmount"
            :min="0"
            :precision="2"
            :step="100"
            class="!w-full"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="showPayDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmPay">确认收款</el-button>
      </template>
    </el-dialog>

    <!-- 追加收款弹窗 -->
    <el-dialog v-model="showAddPayDialog" title="追加收款" width="400px">
      <div class="py-4 space-y-3">
        <div class="flex justify-between text-sm">
          <span class="text-gray-500">订单总额</span>
          <span class="font-bold">¥ {{ order?.totalAmount?.toFixed(2) }}</span>
        </div>
        <div class="flex justify-between text-sm">
          <span class="text-gray-500">已付金额</span>
          <span class="font-bold text-blue-600">¥ {{ order?.paidAmount?.toFixed(2) }}</span>
        </div>
        <div class="flex justify-between text-sm">
          <span class="text-gray-500">待付余额</span>
          <span class="font-bold text-red-500">
            ¥ {{ ((order?.totalAmount ?? 0) - (order?.paidAmount ?? 0)).toFixed(2) }}
          </span>
        </div>
        <el-divider />
        <div>
          <label class="block text-sm font-bold text-gray-500 mb-2">本次收款金额</label>
          <el-input-number
            v-model="addPayAmount"
            :min="0.01"
            :max="(order?.totalAmount ?? 0) - (order?.paidAmount ?? 0)"
            :precision="2"
            :step="100"
            class="!w-full"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="showAddPayDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmAddPay">确认收款</el-button>
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
    <el-dialog v-model="showDeliveryPlanDialog" :title="deliveryPlanDialogTitle" width="800px">
      <div class="py-4">
        <!-- 提示信息 -->
        <div class="mb-4 p-3 bg-blue-50 border border-blue-200 rounded-lg text-sm text-blue-700">
          为每个商品选择仓库和配货数量。确认后将同步仓库信息到订单明细。
        </div>

        <!-- 配货明细表格 -->
        <div class="border border-gray-200 rounded-lg overflow-hidden">
          <table class="w-full text-left text-sm">
            <thead class="bg-gray-50 text-gray-500">
              <tr>
                <th class="px-4 py-3">商品</th>
                <th class="px-4 py-3">颜色/尺码</th>
                <th class="px-4 py-3">计划数量</th>
                <th class="px-4 py-3">仓库</th>
                <th class="px-4 py-3">配货数量</th>
                <th class="px-4 py-3">备注</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100">
              <tr v-for="item in deliveryPlanItems" :key="item.orderItemId" class="hover:bg-gray-50">
                <td class="px-4 py-3 font-medium text-gray-900">{{ item.productName }}</td>
                <td class="px-4 py-3 text-gray-600">{{ item.colorName }} / {{ item.sizeName }}</td>
                <td class="px-4 py-3 font-bold">{{ item.plannedQty }}</td>
                <td class="px-4 py-3">
                  <el-select v-model="item.warehouseId" placeholder="选择仓库" size="small" class="!w-32">
                    <el-option v-for="wh in warehouses" :key="wh.id" :label="wh.warehouseName" :value="wh.id" />
                  </el-select>
                </td>
                <td class="px-4 py-3">
                  <el-input-number
                    v-model="item.allocatedQty"
                    :min="0"
                    :max="item.plannedQty"
                    size="small"
                    class="!w-24"
                  />
                </td>
                <td class="px-4 py-3">
                  <el-input v-model="item.remark" placeholder="备注" size="small" class="!w-28" />
                </td>
              </tr>
            </tbody>
          </table>
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
import { computed, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElImageViewer, ElMessage, ElMessageBox } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { getOrderById, confirmPayment, addPayment, completeOrder, cancelOrder, getDeliveriesByOrderId, confirmDelivery, deliverOrder as deliverOrderApi, createDeliveryPlan, getDeliveryPlan, updateDeliveryPlan, confirmAdjustment as confirmAdjustmentApi, cancelAdjustment as cancelAdjustmentApi, getAdjustmentLogs, type OrderVO, type OrderDeliveryVO, type DeliveryPlanVO, type AdjustmentLogDTO } from '@/api/order'
import { getAllWarehouses, type WarehouseVO } from '@/api/inventory'
import { parseImageSources, parseImageVariantSources } from '@/api/file'

const router = useRouter()
const route = useRoute()

const order = ref<OrderVO | null>(null)
const loading = ref(true)
const showPayDialog = ref(false)
const showCancelDialog = ref(false)
const payAmount = ref(0)
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

const orderId = Number(route.params.id)
const canAddPayment = computed(() => {
  if (!order.value || order.value.paymentStatus === 2) return false
  return ![5, 6, 7, 8].includes(order.value.status)
})
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
  showPayDialog.value = true
  payAmount.value = order.value?.paidAmount || 0
}

async function confirmPay() {
  try {
    await confirmPayment(orderId, payAmount.value)
    ElMessage.success('收款确认成功')
    showPayDialog.value = false
    await loadOrder()
  } catch (error: any) {
    ElMessage.error(error.message || '收款确认失败')
  }
}

// 追加收款
const showAddPayDialog = ref(false)
const addPayAmount = ref(0)

function handleAddPayment() {
  addPayAmount.value = 0
  showAddPayDialog.value = true
}

async function confirmAddPay() {
  try {
    await addPayment(orderId, addPayAmount.value)
    ElMessage.success('收款记录已更新')
    showAddPayDialog.value = false
    await loadOrder()
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
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
</style>
