<template>
  <div class="draft-entry-page space-y-6">
    <header class="flex flex-wrap items-center justify-between gap-4">
      <div class="flex items-start gap-3">
        <el-button aria-label="返回草稿订单列表" class="!min-h-11 !rounded-xl" @click="router.push('/orders/drafts')">
          <span class="material-symbols-outlined">arrow_back</span>
        </el-button>
        <div>
        <div class="flex items-center gap-3">
          <h2 class="text-2xl font-bold tracking-tight text-gray-900">草稿订单详情</h2>
          <el-tag type="warning" effect="light">编辑中</el-tag>
        </div>
        <p class="mt-1 text-sm text-gray-500">可继续填写手工暂存或 Agent 导入的草稿，确认后生成正式订单。</p>
        </div>
      </div>
      <div class="flex flex-wrap gap-3">
        <el-button class="!rounded-xl !font-bold" @click="router.push('/orders/quick')">
          <span class="material-symbols-outlined mr-1 text-sm">edit_note</span>
          手工快速录单
        </el-button>
        <el-button v-if="current" class="!rounded-xl !font-bold" @click="togglePaperImages">
          <span class="material-symbols-outlined mr-1 text-sm">image</span>
          {{ imagePanelVisible ? '隐藏图片' : '查看图片' }}
          <span v-if="paperFileIds.length" class="ml-1 text-xs text-gray-400">({{ paperFileIds.length }})</span>
        </el-button>
        <el-button
          v-if="current?.confirmedOrderId"
          type="success"
          plain
          class="!rounded-xl !font-bold"
          @click="router.push(`/orders/${current.confirmedOrderId}`)"
        >
          查看正式订单
        </el-button>
        <template v-if="current?.status === 'EDITING'">
          <el-button class="!rounded-xl !font-bold" :loading="saving" @click="saveDraft">
            <span class="material-symbols-outlined mr-1 text-sm">save</span>
            存为草稿
          </el-button>
          <el-button
            type="primary"
            class="!border-none !bg-[#408aee] !rounded-xl !font-bold"
            :loading="confirming"
            @click="confirmDraft"
          >
            <span class="material-symbols-outlined mr-1 text-sm">task_alt</span>
            确认并生成订单
          </el-button>
        </template>
      </div>
    </header>

    <section class="draft-switcher">
      <div class="grid grid-cols-1 items-end gap-4 lg:grid-cols-[220px_minmax(340px,1fr)_minmax(240px,0.7fr)_auto]">
        <label class="field-block">
          <span>单据批次</span>
          <el-select
            v-model="selectedBatchKey"
            filterable
            :loading="batchLoading"
            placeholder="选择单据批次"
            class="!w-full"
            @change="onBatchChange"
          >
            <el-option
              v-for="batch in batches"
              :key="batchKey(batch.sourceBatchNo)"
              :value="batchKey(batch.sourceBatchNo)"
              :label="`${batchLabel(batch.sourceBatchNo)}（${batch.draftCount} 张）`"
            />
          </el-select>
        </label>
        <label class="field-block">
          <span>当前批次子单</span>
          <el-select
            v-model="selectedId"
            filterable
            :loading="listLoading"
            placeholder="选择纸质单号 / 客户"
            class="!w-full"
            @change="onDraftSelect"
          >
            <el-option
              v-for="draft in drafts"
              :key="draft.id"
              :value="draft.id"
              :label="`${draft.sourceOrderNo || draft.externalRefNo} · ${draft.customerName || '散客'}`"
            >
              <div class="flex items-center justify-between gap-6">
                <span class="font-bold text-gray-900">{{ draft.sourceOrderNo || draft.externalRefNo }} · {{ draft.customerName || '散客' }}</span>
                <span class="text-xs" :class="draft.unresolvedCount ? 'text-amber-600' : 'text-emerald-600'">
                  {{ draft.unresolvedCount ? `${draft.unresolvedCount} 行待匹配` : `${draft.itemCount} 行 · ${money(draft.paperTotalAmount)}` }}
                </span>
              </div>
            </el-option>
          </el-select>
        </label>
        <label class="field-block">
          <span>搜索本批次子单</span>
          <el-input v-model="keyword" clearable placeholder="纸质单号或客户名称" @keyup.enter="searchBatchDrafts">
            <template #prefix><span class="material-symbols-outlined text-base text-gray-400">search</span></template>
          </el-input>
        </label>
        <div class="flex gap-2">
          <el-button aria-label="上一张草稿" :disabled="currentDraftIndex <= 0" @click="moveDraft(-1)">
            <span class="material-symbols-outlined">chevron_left</span>
          </el-button>
          <el-button aria-label="下一张草稿" :disabled="currentDraftIndex < 0 || currentDraftIndex >= drafts.length - 1" @click="moveDraft(1)">
            <span class="material-symbols-outlined">chevron_right</span>
          </el-button>
          <el-button aria-label="刷新当前批次" @click="refreshWorkspace">
            <span class="material-symbols-outlined">refresh</span>
          </el-button>
        </div>
      </div>
    </section>

    <main v-loading="detailLoading" class="min-w-0">
      <el-empty v-if="!current" class="form-panel py-24" description="暂无符合条件的订单草稿" />

      <div v-else class="draft-workspace" :class="{ 'paper-hidden': !imagePanelVisible }">
        <div class="draft-editor space-y-6">
        <section v-if="current.warnings.length || unresolvedCount || totalMismatch" class="review-strip">
          <div class="flex flex-wrap items-center gap-2">
            <span class="material-symbols-outlined text-amber-600">warning</span>
            <strong class="text-sm text-gray-900">录单前请检查</strong>
            <el-tag v-if="unresolvedCount" type="warning">{{ unresolvedCount }} 行 SKU 待匹配</el-tag>
            <el-tag v-if="totalMismatch" type="danger">纸单总额与计算金额不一致</el-tag>
            <el-tag v-for="warning in current.warnings" :key="warning" type="warning" effect="plain">
              {{ warningLabel(warning) }}
            </el-tag>
          </div>
        </section>

        <div class="grid grid-cols-1 gap-6 xl:grid-cols-2">
          <section class="form-panel">
            <div class="panel-title">
              <span class="material-symbols-outlined text-[#408aee]">receipt_long</span>
              <h3>单据信息</h3>
              <el-tag class="ml-auto" type="warning">可编辑草稿</el-tag>
              <el-tag effect="plain" :type="manualDraft ? 'primary' : 'info'">
                {{ manualDraft ? '手工暂存' : 'Agent 导入' }}
              </el-tag>
            </div>
            <div class="grid grid-cols-1 gap-5 md:grid-cols-2">
              <label class="field-block">
                <span>单据批次 <em class="required-mark">*</em></span>
                <el-input v-model="current.sourceBatchNo" maxlength="20" :disabled="readonly" placeholder="如 41" />
              </label>
              <label class="field-block">
                <span>单据号 <em class="required-mark">*</em></span>
                <el-input v-model="current.sourceOrderNo" maxlength="29" :disabled="readonly" placeholder="如 0135" />
                <small>外部编号：{{ current.externalRefNo }}</small>
              </label>
              <label class="field-block">
                <span>订单日期</span>
                <el-date-picker v-model="current.orderDate" value-format="YYYY-MM-DD" type="date" class="!w-full" :disabled="readonly" />
                <small>识别原文：{{ current.rawOrderDate || '空' }}</small>
              </label>
              <label class="field-block">
                <span>交货日期</span>
                <el-date-picker v-model="current.deliveryDate" value-format="YYYY-MM-DD" type="date" class="!w-full" :disabled="readonly" />
              </label>
              <label class="field-block">
                <span>订单类型</span>
                <el-segmented v-model="current.orderType" :options="orderTypeOptions" :disabled="readonly" class="!w-full" />
              </label>
              <div class="field-block">
                <span>来源档口</span>
                <OutletSelect
                  v-model="current.sourceOutletId"
                  :pending="current.sourceOutletId == null"
                  :allow-pending="canUseUnassigned"
                  :disabled="readonly"
                  :fallback-label="current.sourceShop"
                  test-id="draft-outlet"
                />
              </div>
            </div>
          </section>

          <section class="form-panel">
            <div class="panel-title">
              <span class="material-symbols-outlined text-[#408aee]">person_search</span>
              <h3>客户信息</h3>
            </div>
            <div class="grid grid-cols-1 gap-5 md:grid-cols-2">
              <label class="field-block">
                <span>客户名称</span>
                <el-autocomplete
                  v-model="current.customerName"
                  :fetch-suggestions="queryCustomerSuggestions"
                  value-key="name"
                  placeholder="输入客户名称搜索；新客户确认时自动建档"
                  clearable
                  class="!w-full"
                  :disabled="readonly"
                  @select="onCustomerSelect"
                  @input="onCustomerNameInput"
                >
                  <template #default="{ item }">
                    <div class="flex items-center justify-between gap-5 py-1">
                      <span class="font-medium text-gray-900">{{ item.name }}</span>
                      <span class="text-xs text-gray-400">{{ item.phones?.[0] || '暂无电话' }}</span>
                    </div>
                  </template>
                </el-autocomplete>
                <small>识别原文：{{ current.rawCustomerName || '空' }}</small>
              </label>
              <label class="field-block">
                <span>客户电话</span>
                <el-input v-model="current.customerPhone" :disabled="readonly" clearable placeholder="客户联系电话" @input="onCustomerPhoneInput" />
                <small>识别原文：{{ current.rawCustomerPhone || '空' }}</small>
              </label>
              <label class="field-block">
                <span>{{ manualDraft ? '实收金额' : '定金' }}</span>
                <el-input-number v-if="manualDraft" v-model="current.paidAmount" :min="0" :precision="2" :controls="false" class="!w-full" :disabled="readonly" />
                <el-input-number v-else v-model="current.deposit" :min="0" :precision="2" :controls="false" class="!w-full" :disabled="readonly" />
                <small v-if="!manualDraft">识别原文：{{ current.rawDeposit || '空' }}</small>
              </label>
              <label class="field-block">
                <span>{{ manualDraft ? '订单应收' : '纸单总金额' }}</span>
                <el-input-number v-if="!manualDraft" v-model="current.paperTotalAmount" :min="0" :precision="2" :controls="false" class="!w-full" :disabled="readonly" />
                <el-input v-else :model-value="money(currentOrderTotal)" disabled />
                <small :class="totalMismatch ? '!text-orange-600' : ''">系统计算：{{ money(currentOrderTotal) }}</small>
              </label>
              <label class="field-block">
                <span>国家区号</span>
                <CountryCodeSelect v-model="current.customerCountryCode" :disabled="readonly" class="!w-full" />
              </label>
              <label class="field-block">
                <span>客户地址</span>
                <el-input v-model="current.customerAddress" :disabled="readonly" clearable placeholder="客户地址" />
              </label>
              <p class="customer-entry-hint md:col-span-2">
                选择已有客户会直接关联；新客户填写名称和电话后，在确认订单时自动加入客户列表。客户信息留空则按散客处理。
              </p>
            </div>
          </section>
        </div>

        <section class="overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm">
          <div class="flex flex-wrap items-center justify-between gap-4 border-b border-gray-100 px-6 py-5">
            <div>
              <h3 class="text-lg font-bold text-gray-900">商品明细</h3>
              <p class="mt-1 text-xs text-gray-500">和快速录单一样直接修改 SKU、数量和售价；灰色文字是 Agent 识别原文。</p>
            </div>
            <el-button v-if="!readonly" type="primary" plain class="!rounded-xl !font-bold" @click="addItem">
              <span class="material-symbols-outlined mr-1 text-sm">add</span>
              添加一行
            </el-button>
          </div>

          <div class="overflow-x-auto draft-table-wrap">
            <el-table :data="current.items" row-key="id" class="draft-table">
              <el-table-column label="#" width="48" align="center">
                <template #default="{ $index }">{{ $index + 1 }}</template>
              </el-table-column>
              <el-table-column label="款号 / SKU" min-width="330">
                <template #default="{ row }">
                  <el-select
                    v-model="row.skuId"
                    filterable
                    remote
                    reserve-keyword
                    clearable
                    :disabled="readonly"
                    placeholder="搜索款号、商品名、颜色或 SKU"
                    class="!w-full"
                    :remote-method="filterSku"
                    @change="onSkuSelect(row)"
                  >
                    <template v-if="row.skuId" #label><span>{{ lineSkuLabel(row) }}</span></template>
                    <el-option v-for="sku in filteredSkuOptions" :key="sku.skuId" :label="sku.label" :value="sku.skuId">
                      <div class="flex items-center justify-between gap-5">
                        <span class="font-medium text-gray-900">{{ sku.label }}</span>
                        <span class="text-xs text-gray-400">参考价 {{ money(sku.price) }}</span>
                      </div>
                    </el-option>
                  </el-select>
                  <div class="recognition-note">
                    <span class="material-symbols-outlined">document_scanner</span>
                    <span>{{ recognitionText(row) }}</span>
                  </div>
                  <div class="mt-1 flex flex-wrap gap-2">
                    <span class="match-state" :class="row.skuId ? 'matched' : 'unmatched'">{{ row.skuId ? '已匹配' : '待匹配' }}</span>
                    <span v-if="hasPriceDifference(row)" class="difference-state">纸单价不同于系统参考价</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="数量" width="105">
                <template #default="{ row }">
                  <el-input-number v-model="row.quantity" :min="1" :controls="false" :disabled="readonly" class="!w-full" />
                  <p class="cell-hint">识别 {{ row.rawQuantity || '-' }}</p>
                </template>
              </el-table-column>
              <el-table-column label="销售单价" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.salePrice" :min="0" :precision="2" :controls="false" :disabled="readonly" class="!w-full" />
                  <p class="cell-hint">系统参考 {{ money(row.systemReferencePrice) }}</p>
                </template>
              </el-table-column>
              <el-table-column v-if="manualDraft" label="成本价" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.costPrice" :min="0" :precision="2" :controls="false" :disabled="readonly" class="!w-full" />
                </template>
              </el-table-column>
              <el-table-column label="纸单金额" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.paperAmount" :min="0" :precision="2" :controls="false" :disabled="readonly" class="!w-full" />
                  <p class="cell-hint">识别 {{ row.rawAmount || '-' }}</p>
                </template>
              </el-table-column>
              <el-table-column label="计算金额" width="110" align="right">
                <template #default="{ row }">
                  <p class="pt-2 text-base font-bold" :class="hasAmountDifference(row) ? 'text-orange-600' : 'text-gray-900'">
                    {{ money(lineAmount(row)) }}
                  </p>
                  <p v-if="hasAmountDifference(row)" class="cell-hint !text-orange-600">与纸单不同</p>
                </template>
              </el-table-column>
              <el-table-column v-if="!readonly" label="操作" width="108" align="center">
                <template #default="{ row, $index }">
                  <div class="flex items-center justify-center gap-1">
                    <el-tooltip content="拆成新行">
                      <el-button circle aria-label="拆分该行" @click="splitItem(row)">
                        <span class="material-symbols-outlined text-lg">call_split</span>
                      </el-button>
                    </el-tooltip>
                    <el-tooltip content="删除该行">
                      <el-button circle type="danger" plain aria-label="删除该行" @click="removeItem($index)">
                        <span class="material-symbols-outlined text-lg">delete</span>
                      </el-button>
                    </el-tooltip>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <section v-if="manualDraft" class="form-panel">
          <div class="panel-title">
            <span class="material-symbols-outlined text-[#408aee]">local_shipping</span>
            <h3>结算与配送</h3>
          </div>
          <div class="grid grid-cols-1 gap-5 md:grid-cols-3">
            <label class="field-block">
              <span>客户运费收入</span>
              <el-input-number v-model="current.freightAmount" :min="0" :precision="2" :controls="false" :disabled="readonly" class="!w-full" />
            </label>
            <label class="field-block">
              <span>实际运费成本</span>
              <el-input-number v-model="current.freightCost" :min="0" :precision="2" :controls="false" :disabled="readonly" class="!w-full" />
            </label>
            <div class="field-block">
              <span>配送方式</span>
              <el-switch v-model="current.needDelivery" :active-value="1" :inactive-value="0" active-text="需要送货" inactive-text="自取" :disabled="readonly" />
            </div>
            <label v-if="current.needDelivery === 1" class="field-block md:col-span-3">
              <span>送货地址</span>
              <el-input v-model="current.deliveryAddress" type="textarea" :rows="2" :disabled="readonly" />
            </label>
          </div>
        </section>

        <div class="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_360px]">
          <section class="form-panel">
            <div class="panel-title">
              <span class="material-symbols-outlined text-[#408aee]">notes</span>
              <h3>备注</h3>
            </div>
            <el-input v-model="current.note" type="textarea" :rows="4" :disabled="readonly" placeholder="补充订单说明、识别疑问或人工核对结果" />
          </section>

          <section class="summary-panel">
            <h3 class="mb-5 text-lg font-bold">金额汇总</h3>
            <div class="space-y-3 text-sm">
              <div class="summary-row"><span>{{ manualDraft ? '订单应收' : '纸单总额' }}</span><strong>{{ money(currentOrderTotal) }}</strong></div>
              <div class="summary-row"><span>商品明细</span><strong :class="totalMismatch ? 'text-orange-300' : 'text-emerald-400'">{{ money(calculatedTotal) }}</strong></div>
              <div v-if="manualDraft" class="summary-row"><span>客户运费</span><strong>{{ money(current.freightAmount) }}</strong></div>
              <div class="summary-row"><span>{{ manualDraft ? '实收金额' : '已收定金' }}</span><strong class="text-blue-300">{{ money(currentReceivedAmount) }}</strong></div>
              <div class="summary-divider"></div>
              <div class="summary-row"><span>待收余额</span><strong class="text-xl text-white">{{ money(balanceAmount) }}</strong></div>
            </div>
          </section>
        </div>
        </div>

        <aside v-show="imagePanelVisible" class="paper-preview-aside" :aria-label="manualDraft ? '订单图片对照栏' : '纸单原图对照栏'">
          <section class="paper-preview-card">
            <div class="paper-preview-header">
              <div class="min-w-0">
                <div class="flex items-center gap-2">
                  <span class="material-symbols-outlined text-[#408aee]">document_scanner</span>
                  <h3>{{ manualDraft ? '订单图片' : '纸单原图' }}</h3>
                  <el-tag v-if="paperFileIds.length" size="small" effect="plain">
                    {{ activePaperIndex + 1 }} / {{ paperFileIds.length }}
                  </el-tag>
                </div>
                <p>{{ current.sourceOrderNo || current.externalRefNo }} · 批次 {{ current.sourceBatchNo || '-' }}</p>
              </div>
              <el-tooltip content="隐藏对照栏">
                <el-button circle aria-label="隐藏纸单原图对照栏" @click="imagePanelVisible = false">
                  <span class="material-symbols-outlined">close</span>
                </el-button>
              </el-tooltip>
            </div>

            <div v-if="!readonly" class="paper-edit-toolbar">
              <input
                ref="paperUploadInput"
                type="file"
                multiple
                accept="image/jpeg,image/png,image/webp"
                class="sr-only"
                aria-label="选择订单图片"
                @change="handlePaperUpload"
              />
              <el-button
                type="primary"
                plain
                class="!font-bold"
                :loading="paperUploading"
                :disabled="paperFileIds.length >= MAX_DRAFT_IMAGES"
                @click="openPaperUpload"
              >
                <span class="material-symbols-outlined mr-1 text-base">add_photo_alternate</span>
                添加图片
              </el-button>
              <div v-if="paperFileIds.length" class="paper-edit-actions" aria-label="当前图片排序操作">
                <el-tooltip content="设为第一张图片">
                  <el-button
                    class="paper-icon-button"
                    aria-label="设为首图"
                    :disabled="activePaperIndex === 0"
                    @click="makeActivePaperPrimary"
                  >
                    <span class="material-symbols-outlined">first_page</span>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="向前移动一位">
                  <el-button
                    class="paper-icon-button"
                    aria-label="图片前移"
                    :disabled="activePaperIndex === 0"
                    @click="reorderActivePaper(-1)"
                  >
                    <span class="material-symbols-outlined">arrow_back</span>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="向后移动一位">
                  <el-button
                    class="paper-icon-button"
                    aria-label="图片后移"
                    :disabled="activePaperIndex >= paperFileIds.length - 1"
                    @click="reorderActivePaper(1)"
                  >
                    <span class="material-symbols-outlined">arrow_forward</span>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="从草稿移除，不删除文件中心原文件">
                  <el-button
                    type="danger"
                    plain
                    class="paper-icon-button"
                    aria-label="移除当前图片"
                    @click="removeActivePaper"
                  >
                    <span class="material-symbols-outlined">delete</span>
                  </el-button>
                </el-tooltip>
              </div>
              <p>最多 10 张；调整后点击“存为草稿”生效。</p>
            </div>

            <div v-if="paperFileIds.length" class="paper-canvas">
              <el-image
                :key="activePaperFileId"
                :src="activePaperImageUrl"
                :preview-src-list="paperImageUrls"
                :initial-index="activePaperIndex"
                fit="contain"
                class="paper-main-image"
                preview-teleported
                :alt="`${manualDraft ? '订单图片' : '纸单'} ${current.sourceOrderNo || current.externalRefNo} 第 ${activePaperIndex + 1} 张`"
              >
                <template #placeholder>
                  <div class="paper-image-state">原图加载中…</div>
                </template>
                <template #error>
                  <div class="paper-image-state text-red-300">原图加载失败，请检查文件权限</div>
                </template>
              </el-image>
              <div class="paper-canvas-hint">
                <span class="material-symbols-outlined">zoom_in</span>
                点击图片可放大、旋转和查看细节
              </div>
            </div>
            <el-empty v-else class="paper-empty" :description="manualDraft ? '该草稿尚未上传订单图片' : '该草稿尚未上传纸单原图'">
              <template #image>
                <span class="material-symbols-outlined text-5xl text-slate-500">image_not_supported</span>
              </template>
            </el-empty>

            <div v-if="paperFileIds.length > 1" class="paper-navigation">
              <el-button aria-label="上一张纸单" :disabled="activePaperIndex === 0" @click="movePaper(-1)">
                <span class="material-symbols-outlined">chevron_left</span>
              </el-button>
              <div class="paper-thumbnails" aria-label="纸单缩略图列表">
                <button
                  v-for="(url, index) in paperImageUrls"
                  :key="paperFileIds[index]"
                  type="button"
                  class="paper-thumbnail"
                  :class="{ active: index === activePaperIndex }"
                  :aria-label="`查看第 ${index + 1} 张纸单`"
                  :aria-current="index === activePaperIndex ? 'true' : undefined"
                  @click="activePaperIndex = index"
                >
                  <img :src="url" :alt="`第 ${index + 1} 张纸单缩略图`" loading="lazy" />
                  <span>{{ index === 0 ? '首图' : index + 1 }}</span>
                </button>
              </div>
              <el-button aria-label="下一张纸单" :disabled="activePaperIndex >= paperFileIds.length - 1" @click="movePaper(1)">
                <span class="material-symbols-outlined">chevron_right</span>
              </el-button>
            </div>
          </section>
        </aside>
      </div>
    </main>

  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { filePreviewUrl, uploadFile } from '@/api/file'
import { getCustomerPage, type CustomerVO } from '@/api/customer'
import { getProductPage, type ProductVO } from '@/api/product'
import { hasFriendlySkuName, skuFriendlyName } from '@/utils/skuDisplay'
import CountryCodeSelect from '@/components/CountryCodeSelect.vue'
import OutletSelect from '@/components/OutletSelect.vue'
import { useAuthStore } from '@/stores/auth'
import {
  confirmOrderDraft,
  getOrderDraft,
  getOrderDraftBatches,
  getOrderDraftPage,
  saveOrderDraft,
  type DraftSaveRequest,
  type OrderDraftBatchSummary,
  type OrderDraftItem,
  type OrderDraftSummary,
  type OrderDraftView,
} from '@/api/orderDraft'

interface SkuOption {
  skuId: number
  productId: number
  productCode: string
  skuCode: string
  skuType: 'NORMAL' | 'DEFAULT' | 'PLACEHOLDER'
  placeholder: boolean
  productName: string
  colorName: string
  sizeName: string
  label: string
  price: number
  costPrice: number
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const canUseUnassigned = computed(() => authStore.permissions.includes('data:outlet:unassigned'))
const UNBATCHED = '__UNBATCHED__'
const drafts = ref<OrderDraftSummary[]>([])
const batches = ref<OrderDraftBatchSummary[]>([])
const current = ref<OrderDraftView | null>(null)
// 服务端按“未传保留、传入则必须可用”处理草稿档口；记录载入值便于仅在变更时提交
const loadedOutletId = ref<number | null>(null)
const selectedId = ref<number>()
const selectedBatchKey = ref<string>()
const keyword = ref('')
const batchLoading = ref(false)
const listLoading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const confirming = ref(false)
const imagePanelVisible = ref(true)
const activePaperIndex = ref(0)
const paperUploadInput = ref<HTMLInputElement | null>(null)
const paperUploading = ref(false)
const MAX_DRAFT_IMAGES = 10
const skuOptions = ref<SkuOption[]>([])
const filteredSkuOptions = ref<SkuOption[]>([])
const orderTypeOptions = [
  { label: '现货订单', value: 'SPOT' },
  { label: '订货订单', value: 'PREORDER' },
]

const readonly = computed(() => current.value?.status !== 'EDITING')
const manualDraft = computed(() => current.value?.entrySource === 'MANUAL')
const currentDraftIndex = computed(() => drafts.value.findIndex(draft => draft.id === selectedId.value))
const unresolvedCount = computed(() => current.value?.items.filter(item => !item.skuId).length || 0)
const calculatedTotal = computed(() =>
  (current.value?.items || []).reduce((sum, item) => sum + lineAmount(item), 0)
)
const currentOrderTotal = computed(() => manualDraft.value
  ? calculatedTotal.value + Number(current.value?.freightAmount || 0)
  : Number(current.value?.paperTotalAmount ?? calculatedTotal.value)
)
const currentReceivedAmount = computed(() => manualDraft.value
  ? Number(current.value?.paidAmount || 0)
  : Number(current.value?.deposit || 0)
)
const totalMismatch = computed(() =>
  !manualDraft.value
  && current.value?.paperTotalAmount != null
  && Math.abs(calculatedTotal.value - Number(current.value.paperTotalAmount)) > 0.01
)
const balanceAmount = computed(() => Math.max(
  currentOrderTotal.value - currentReceivedAmount.value,
  0,
))
const paperFileIds = computed(() => {
  const ids = current.value?.sourceFileIds?.length
    ? current.value.sourceFileIds
    : current.value?.sourceFileId ? [current.value.sourceFileId] : []
  return [...new Set(ids)]
})
const paperImageUrls = computed(() => paperFileIds.value.map(filePreviewUrl))
const activePaperFileId = computed(() => paperFileIds.value[activePaperIndex.value])
const activePaperImageUrl = computed(() => activePaperFileId.value ? filePreviewUrl(activePaperFileId.value) : '')

function batchKey(value?: string) {
  return value?.trim() || UNBATCHED
}

function batchLabel(value?: string) {
  const normalized = value?.trim()
  if (!normalized) return '未分批'
  return /^\d+$/.test(normalized) ? `第 ${normalized} 单` : normalized
}

async function loadBatches() {
  batchLoading.value = true
  try {
    const response = await getOrderDraftBatches()
    batches.value = response.data || []
  } finally {
    batchLoading.value = false
  }
}

async function loadDrafts(selectFirst = false) {
  if (!selectedBatchKey.value) {
    drafts.value = []
    return
  }
  listLoading.value = true
  try {
    const response = await getOrderDraftPage({
      current: 1,
      size: 100,
      status: 'EDITING',
      keyword: keyword.value || undefined,
      sourceBatchNo: selectedBatchKey.value,
    })
    drafts.value = response.data.records || []
    if (selectFirst && drafts.value.length) navigateDraft(drafts.value[0].id)
  } finally {
    listLoading.value = false
  }
}

async function onBatchChange() {
  keyword.value = ''
  selectedId.value = undefined
  current.value = null
  await loadDrafts(true)
}

async function searchBatchDrafts() {
  await loadDrafts(false)
  if (!drafts.value.length) return
  if (!selectedId.value || !drafts.value.some(draft => draft.id === selectedId.value)) {
    navigateDraft(drafts.value[0].id)
  }
}

async function refreshWorkspace() {
  await Promise.all([loadBatches(), loadDrafts(false)])
  if (selectedId.value) await loadDraftDetail(selectedId.value)
}

function onDraftSelect(id: number) {
  if (id) navigateDraft(id)
}

function moveDraft(offset: number) {
  const target = drafts.value[currentDraftIndex.value + offset]
  if (target) navigateDraft(target.id)
}

function navigateDraft(id: number) {
  if (Number(route.params.id) === id) return
  router.push(`/orders/drafts/${id}`)
}

function togglePaperImages() {
  imagePanelVisible.value = !imagePanelVisible.value
}

function movePaper(offset: number) {
  activePaperIndex.value = Math.min(
    Math.max(activePaperIndex.value + offset, 0),
    Math.max(paperFileIds.value.length - 1, 0),
  )
}

function setPaperFileIds(fileIds: number[]) {
  if (!current.value) return
  const normalized = [...new Set(fileIds)]
  current.value.sourceFileIds = normalized
  current.value.sourceFileId = normalized[0]
  activePaperIndex.value = Math.min(activePaperIndex.value, Math.max(normalized.length - 1, 0))
}

function openPaperUpload() {
  if (readonly.value) return
  if (paperFileIds.value.length >= MAX_DRAFT_IMAGES) {
    ElMessage.warning(`每张草稿最多上传 ${MAX_DRAFT_IMAGES} 张图片`)
    return
  }
  paperUploadInput.value?.click()
}

async function handlePaperUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const selectedFiles = Array.from(input.files || [])
  input.value = ''
  if (!current.value || !selectedFiles.length) return

  const supportedTypes = new Set(['image/jpeg', 'image/png', 'image/webp'])
  const supportedFiles = selectedFiles.filter(file => supportedTypes.has(file.type))
  if (supportedFiles.length !== selectedFiles.length) {
    ElMessage.warning('仅支持 JPG、PNG、WebP 图片')
  }
  const available = MAX_DRAFT_IMAGES - paperFileIds.value.length
  const pendingFiles = supportedFiles.slice(0, available)
  if (supportedFiles.length > available) {
    ElMessage.warning(`每张草稿最多上传 ${MAX_DRAFT_IMAGES} 张图片，本次只添加前 ${available} 张`)
  }
  if (!pendingFiles.length) return

  paperUploading.value = true
  const originalCount = paperFileIds.value.length
  const nextIds = [...paperFileIds.value]
  try {
    for (const file of pendingFiles) {
      const response = await uploadFile(file, 'order_draft')
      nextIds.push(Number(response.data.id))
      setPaperFileIds(nextIds)
    }
    activePaperIndex.value = originalCount
    ElMessage.success(`已添加 ${pendingFiles.length} 张图片，保存草稿后生效`)
  } catch (error: any) {
    ElMessage.error(error.message || '订单图片上传失败')
  } finally {
    paperUploading.value = false
  }
}

function reorderActivePaper(offset: number) {
  const currentIndex = activePaperIndex.value
  const targetIndex = currentIndex + offset
  if (targetIndex < 0 || targetIndex >= paperFileIds.value.length) return
  const nextIds = [...paperFileIds.value]
  const [moving] = nextIds.splice(currentIndex, 1)
  nextIds.splice(targetIndex, 0, moving)
  setPaperFileIds(nextIds)
  activePaperIndex.value = targetIndex
}

function makeActivePaperPrimary() {
  if (activePaperIndex.value <= 0) return
  const nextIds = [...paperFileIds.value]
  const [moving] = nextIds.splice(activePaperIndex.value, 1)
  nextIds.unshift(moving)
  setPaperFileIds(nextIds)
  activePaperIndex.value = 0
}

async function removeActivePaper() {
  if (!current.value || !paperFileIds.value.length) return
  try {
    await ElMessageBox.confirm(
      '图片将从当前草稿中移除，但文件中心原文件不会被删除。保存草稿后生效。',
      '移除订单图片',
      {
        type: 'warning',
        confirmButtonText: '移除图片',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }
  const nextIds = [...paperFileIds.value]
  nextIds.splice(activePaperIndex.value, 1)
  setPaperFileIds(nextIds)
}

async function loadDraftDetail(id: number) {
  selectedId.value = id
  detailLoading.value = true
  try {
    const response = await getOrderDraft(id)
    if (response.data.status !== 'EDITING') {
      ElMessage.info('这张草稿已经生成正式订单，已从草稿箱移除')
      if (response.data.confirmedOrderId) {
        await router.replace(`/orders/${response.data.confirmedOrderId}`)
      } else {
        await router.replace('/orders/drafts')
      }
      return
    }
    const normalized = normalizeLegacyDocumentIdentity(response.data)
    current.value = {
      ...normalized,
      needDelivery: response.data.needDelivery ?? 0,
    }
    loadedOutletId.value = normalized.sourceOutletId ?? null
    const incomingBatchKey = batchKey(normalized.sourceBatchNo)
    const batchChanged = selectedBatchKey.value !== incomingBatchKey
    selectedBatchKey.value = incomingBatchKey
    activePaperIndex.value = 0
    if (batchChanged || !drafts.value.some(draft => draft.id === id)) {
      await loadDrafts(false)
    }
  } finally {
    detailLoading.value = false
  }
}

function normalizeLegacyDocumentIdentity(draft: OrderDraftView): OrderDraftView {
  if (draft.sourceBatchNo?.trim() || !draft.sourceOrderNo?.trim()) return draft
  const legacy = draft.sourceOrderNo.trim().match(/^(\d+)[_-](.+)$/)
  if (!legacy) return draft
  return {
    ...draft,
    sourceBatchNo: legacy[1],
    sourceOrderNo: legacy[2],
  }
}

function validateDocumentIdentity(draft: OrderDraftView) {
  if (!draft.sourceBatchNo?.trim()) {
    ElMessage.warning('请填写单据批次')
    return false
  }
  if (!draft.sourceOrderNo?.trim()) {
    ElMessage.warning('请填写单据号')
    return false
  }
  if (`${draft.sourceBatchNo.trim()}_${draft.sourceOrderNo.trim()}`.length > 50) {
    ElMessage.warning('单据批次与单据号组合后不能超过 50 个字符')
    return false
  }
  return true
}

function validateCustomerForConfirmation(draft: OrderDraftView) {
  if (draft.customerId) return true
  const name = draft.customerName?.trim() || ''
  const phone = draft.customerPhone?.trim() || ''
  const address = draft.customerAddress?.trim() || ''
  const countryCode = draft.customerCountryCode?.trim() || ''
  const isEmpty = !name && !phone && !address && !countryCode
  if (isEmpty || name === '散客') return true
  if (!name) {
    ElMessage.warning('请填写客户名称；如不建立客户，请清空客户信息后按散客保存')
    return false
  }
  if (!phone) {
    ElMessage.warning('新客户请填写客户电话；如不建立客户，请清空客户信息后按散客保存')
    return false
  }
  return true
}

async function loadProducts() {
  const response = await getProductPage({ current: 1, size: 1000, status: 1 })
  const products: ProductVO[] = response.data?.records || response.data?.data?.records || []
  skuOptions.value = products.flatMap(product =>
    (product.skus || [])
      .filter(sku => sku.status === 1)
      .map(sku => ({
        skuId: sku.id,
        productId: product.id,
        productCode: product.productCode,
        skuCode: sku.skuCode,
        productName: product.name,
        skuType: sku.skuType || 'NORMAL',
        placeholder: sku.placeholder || sku.skuType === 'PLACEHOLDER',
        colorName: sku.colorName || '',
        sizeName: sku.sizeName || '',
        label: `${product.productCode} / ${product.name} · ${hasFriendlySkuName(sku)
          ? skuFriendlyName(sku)
          : [sku.colorName, sku.sizeName].filter(Boolean).join(' · ') || sku.skuCode}`,
        price: Number(sku.price || product.wholesalePrice || 0),
        costPrice: Number(sku.costPrice || product.costPrice || 0),
      }))
  ).sort((a, b) => Number(b.placeholder) - Number(a.placeholder))
  filteredSkuOptions.value = skuOptions.value.slice(0, 50)
}

function filterSku(keyword: string) {
  const normalized = keyword.trim().toLowerCase()
  if (!normalized) {
    filteredSkuOptions.value = skuOptions.value.slice(0, 50)
    return
  }
  filteredSkuOptions.value = skuOptions.value
    .filter(sku => [sku.productCode, sku.productName, sku.skuCode, sku.colorName, sku.sizeName]
      .some(value => value.toLowerCase().includes(normalized)))
    .slice(0, 80)
}

function lineSkuLabel(row: OrderDraftItem) {
  const catalogLabel = skuOptions.value.find(option => option.skuId === row.skuId)?.label
  if (catalogLabel) return catalogLabel
  return [row.rawProductCode, row.rawDescription || row.rawColor]
    .filter(Boolean)
    .join(' · ') || '已选择商品规格'
}

function recognitionText(row: OrderDraftItem) {
  const product = row.rawProductCode || '未识别货号'
  const detail = row.rawDescription || row.rawColor || '无品名/颜色原文'
  return `识别：${product} · ${detail}`
}

function onSkuSelect(row: OrderDraftItem) {
  const sku = skuOptions.value.find(option => option.skuId === row.skuId)
  if (!sku) {
    row.productId = undefined
    row.matchStatus = 'UNMATCHED'
    return
  }
  row.productId = sku.productId
  row.systemReferencePrice = sku.price
  if (manualDraft.value && row.costPrice == null) row.costPrice = sku.costPrice
  row.matchStatus = 'MATCHED'
}

async function queryCustomerSuggestions(query: string, callback: (items: CustomerVO[]) => void) {
  const keyword = query.trim()
  if (!keyword) {
    callback([])
    return
  }
  try {
    const response = await getCustomerPage({ current: 1, size: 10, keyword })
    callback(response.data.records || [])
  } catch {
    callback([])
  }
}

function onCustomerSelect(customer: CustomerVO) {
  if (!current.value) return
  current.value.customerId = customer.id
  current.value.customerName = customer.name
  current.value.customerPhone = customer.phones?.[0] || current.value.customerPhone
  current.value.customerCountryCode = customer.countryCode || current.value.customerCountryCode
  current.value.customerAddress = customer.address || current.value.customerAddress
}

function onCustomerNameInput() {
  if (current.value) current.value.customerId = undefined
}

function onCustomerPhoneInput() {
  // 修改已有客户的电话后，订单只保留当前填写的客户快照，避免错误关联到原客户主档。
  if (current.value) current.value.customerId = undefined
}

function addItem() {
  if (!current.value) return
  const nextRow = Math.max(0, ...current.value.items.map(item => Number(item.sourceRowNo || 0))) + 1
  current.value.items.push({
    sourceRowNo: nextRow,
    matchStatus: 'UNMATCHED',
    matchCandidates: [],
    warnings: [],
  })
}

function splitItem(row: OrderDraftItem) {
  if (!current.value) return
  current.value.items.push({
    ...row,
    id: undefined,
    skuId: undefined,
    productId: undefined,
    matchStatus: 'UNMATCHED',
    quantity: undefined,
    paperAmount: undefined,
  })
}

function removeItem(index: number) {
  current.value?.items.splice(index, 1)
}

function toSaveRequest(draft: OrderDraftView): DraftSaveRequest {
  return {
    externalRefNo: draft.externalRefNo,
    sourceBatchNo: draft.sourceBatchNo,
    sourceOrderNo: draft.sourceOrderNo,
    // 未变更不提交：避免把已禁用历史档口重新送服务端校验（未传=保留）
    sourceOutletId: draft.sourceOutletId != null && draft.sourceOutletId !== loadedOutletId.value
      ? draft.sourceOutletId
      : undefined,
    orderType: draft.orderType,
    sourceFileId: draft.sourceFileId,
    sourceFileIds: draft.sourceFileIds,
    rawCustomerName: draft.rawCustomerName,
    rawCustomerPhone: draft.rawCustomerPhone,
    customerId: draft.customerId,
    customerName: draft.customerName || '散客',
    customerPhone: draft.customerPhone,
    customerCountryCode: draft.customerCountryCode,
    customerAddress: draft.customerAddress,
    rawOrderDate: draft.rawOrderDate,
    orderDate: draft.orderDate,
    deliveryDate: draft.deliveryDate,
    rawDeposit: draft.rawDeposit,
    deposit: draft.deposit,
    paidAmount: draft.paidAmount,
    paperTotalAmount: draft.paperTotalAmount,
    freightAmount: draft.freightAmount,
    freightCost: draft.freightCost,
    needDelivery: draft.needDelivery,
    deliveryAddress: draft.needDelivery === 1 ? draft.deliveryAddress : undefined,
    note: draft.note,
    warnings: draft.warnings,
    items: draft.items,
  }
}

async function saveDraft(showMessage = true) {
  if (!current.value) return
  if (!validateDocumentIdentity(current.value)) return
  if (!current.value.items.length) {
    ElMessage.warning('请至少保留一行商品明细')
    return
  }
  saving.value = true
  try {
    const draftId = current.value.id
    await saveOrderDraft(draftId, toSaveRequest(current.value))
    await loadDraftDetail(draftId)
    await Promise.all([loadBatches(), loadDrafts(false)])
    if (showMessage) ElMessage.success('草稿已保存')
  } finally {
    saving.value = false
  }
}

async function confirmDraft() {
  if (!current.value) return
  if (!validateDocumentIdentity(current.value)) return
  if (!validateCustomerForConfirmation(current.value)) return
  if (current.value.sourceOutletId == null) {
    ElMessage.warning('该草稿待归档档口，请先选择档口再确认')
    return
  }
  const unresolved = current.value.items.filter(item => !item.skuId)
  if (unresolved.length) {
    ElMessage.warning(`还有 ${unresolved.length} 行没有选择 SKU`)
    return
  }
  const invalid = current.value.items.filter(item => !item.quantity || !item.salePrice)
  if (invalid.length) {
    ElMessage.warning('每行都需要填写数量和纸单销售价')
    return
  }
  await saveDraft(false)
  if (!current.value) return

  const warningText = totalMismatch.value || current.value.warnings.length
    ? '当前仍有金额或识别警告。确认后将使用纸单销售价和人工确认的总金额生成正式订单。'
    : '确认后将生成正式订单，草稿不能继续修改。'
  await ElMessageBox.confirm(warningText, '确认订单', {
    type: totalMismatch.value ? 'warning' : 'info',
    confirmButtonText: '确认生成正式订单',
    cancelButtonText: '继续检查',
  })

  confirming.value = true
  try {
    const confirmedDraftId = current.value.id
    const previousIndex = drafts.value.findIndex(draft => draft.id === confirmedDraftId)
    const response = await confirmOrderDraft(confirmedDraftId, true)
    await Promise.all([loadBatches(), loadDrafts(false)])
    const nextDraft = drafts.value[Math.min(Math.max(previousIndex, 0), drafts.value.length - 1)]
    try {
      await ElMessageBox.confirm(
        nextDraft
          ? `正式订单已生成。本批次还有 ${drafts.value.length} 张草稿待处理。`
          : '正式订单已生成，当前批次已经处理完成。',
        '草稿处理完成',
        {
          type: 'success',
          distinguishCancelAndClose: true,
          confirmButtonText: '查看正式订单',
          cancelButtonText: nextDraft ? '继续下一张' : '返回草稿列表',
        },
      )
      await router.push(`/orders/${response.data.orderId}`)
    } catch (action) {
      if (action === 'cancel' && nextDraft) {
        await router.push(`/orders/drafts/${nextDraft.id}`)
      } else {
        await router.push('/orders/drafts')
      }
    }
  } finally {
    confirming.value = false
  }
}

function lineAmount(item: OrderDraftItem) {
  return Number(item.quantity || 0) * Number(item.salePrice || 0)
}

function hasPriceDifference(item: OrderDraftItem) {
  return item.salePrice != null
    && item.systemReferencePrice != null
    && Math.abs(Number(item.salePrice) - Number(item.systemReferencePrice)) > 0.01
}

function hasAmountDifference(item: OrderDraftItem) {
  return item.paperAmount != null
    && Math.abs(lineAmount(item) - Number(item.paperAmount)) > 0.01
}

function money(value?: number) {
  return `¥${Number(value || 0).toFixed(2)}`
}

function warningLabel(value: string) {
  if (value === 'SOURCE_IMAGE_MISSING') return '缺少纸单原图'
  if (value === 'ORDER_DATE_UNPARSED') return '日期待确认'
  if (value === 'DEPOSIT_UNPARSED') return '定金待确认'
  if (value === 'ORDER_TOTAL_MISMATCH') return '总金额不一致'
  if (value.includes('SKU_UNMATCHED')) return 'SKU待匹配'
  if (value.includes('QUANTITY_MISSING')) return '数量待补'
  if (value.includes('SALE_PRICE_MISSING')) return '销售价待补'
  if (value.includes('AMOUNT_MISMATCH')) return '明细金额不一致'
  return value
}

onBeforeRouteUpdate(async to => {
  const nextId = Number(to.params.id)
  if (Number.isFinite(nextId) && nextId > 0) await loadDraftDetail(nextId)
})

onMounted(async () => {
  const draftId = Number(route.params.id)
  if (!Number.isFinite(draftId) || draftId <= 0) {
    await router.replace('/orders/drafts')
    return
  }
  const [detailResult, productResult, batchResult] = await Promise.allSettled([
    loadDraftDetail(draftId),
    loadProducts(),
    loadBatches(),
  ])

  if (detailResult.status === 'rejected') {
    ElMessage.error(detailResult.reason?.message || '加载草稿详情失败')
    return
  }
  if (productResult.status === 'rejected') {
    ElMessage.warning('商品目录加载失败，草稿仍可查看；刷新页面后可重试商品匹配')
  }
  if (batchResult.status === 'rejected') {
    ElMessage.warning('批次导航加载失败，当前草稿仍可继续编辑')
  }
})
</script>

<style scoped>
.draft-entry-page {
  min-width: 1040px;
}

.draft-switcher,
.form-panel {
  border: 1px solid #eef0f3;
  border-radius: 12px;
  background: #fff;
  padding: 24px;
  box-shadow: 0 1px 2px rgb(15 23 42 / 4%);
}

.draft-switcher {
  padding-top: 18px;
  padding-bottom: 18px;
}

.draft-workspace,
.draft-editor {
  min-width: 0;
}

.draft-workspace {
  display: grid;
  grid-template-columns: minmax(640px, 1fr) minmax(360px, 34%);
  gap: 24px;
  align-items: start;
}

.paper-preview-aside {
  position: sticky;
  top: 80px;
  display: block;
  min-width: 0;
  align-self: start;
}

.draft-workspace.paper-hidden {
  grid-template-columns: minmax(0, 1fr);
}

.paper-preview-card {
  overflow: hidden;
  border: 1px solid #dbe3ee;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 10px 28px rgb(15 23 42 / 10%);
}

.paper-preview-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 18px 15px;
  border-bottom: 1px solid #e5e7eb;
}

.paper-preview-header h3 {
  color: #111827;
  font-size: 16px;
  font-weight: 800;
}

.paper-preview-header p {
  overflow: hidden;
  margin-top: 5px;
  color: #64748b;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.paper-edit-toolbar {
  display: flex;
  min-height: 68px;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid #e5e7eb;
  background: #f8fafc;
}

.paper-edit-toolbar > p {
  flex: 1 0 100%;
  color: #64748b;
  font-size: 11px;
  line-height: 1.4;
  text-align: left;
}

.paper-edit-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.paper-icon-button {
  width: 44px;
  min-width: 44px;
  height: 44px;
  margin-left: 0 !important;
  padding: 0;
}

.paper-icon-button .material-symbols-outlined {
  font-size: 19px;
}

.paper-canvas {
  position: relative;
  display: flex;
  min-height: 560px;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background:
    linear-gradient(45deg, rgb(255 255 255 / 3%) 25%, transparent 25%),
    linear-gradient(-45deg, rgb(255 255 255 / 3%) 25%, transparent 25%),
    #151a22;
  background-position: 0 0, 12px 12px;
  background-size: 24px 24px;
}

.paper-main-image {
  width: 100%;
  height: min(720px, calc(100vh - 260px));
  min-height: 560px;
  cursor: zoom-in;
}

.paper-main-image :deep(.el-image__inner) {
  padding: 14px;
}

.paper-image-state {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: #cbd5e1;
  font-size: 13px;
}

.paper-canvas-hint {
  position: absolute;
  right: 12px;
  bottom: 12px;
  display: flex;
  align-items: center;
  gap: 5px;
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 999px;
  padding: 6px 10px;
  color: #e2e8f0;
  background: rgb(15 23 42 / 78%);
  font-size: 11px;
  backdrop-filter: blur(8px);
  pointer-events: none;
}

.paper-canvas-hint .material-symbols-outlined {
  font-size: 16px;
}

.paper-empty {
  min-height: 560px;
  background: #f8fafc;
}

.paper-navigation {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 42px;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border-top: 1px solid #e5e7eb;
}

.paper-thumbnails {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: 2px;
  scrollbar-width: thin;
}

.paper-thumbnail {
  position: relative;
  flex: 0 0 58px;
  width: 58px;
  height: 64px;
  overflow: hidden;
  border: 2px solid transparent;
  border-radius: 8px;
  padding: 0;
  background: #e2e8f0;
  cursor: pointer;
  transition: border-color 180ms ease, box-shadow 180ms ease;
}

.paper-thumbnail.active {
  border-color: #408aee;
  box-shadow: 0 0 0 2px rgb(64 138 238 / 16%);
}

.paper-thumbnail:focus-visible {
  outline: 3px solid rgb(64 138 238 / 30%);
  outline-offset: 2px;
}

.paper-thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.paper-thumbnail span {
  position: absolute;
  right: 3px;
  bottom: 3px;
  min-width: 18px;
  border-radius: 999px;
  padding: 1px 4px;
  color: #fff;
  background: rgb(15 23 42 / 80%);
  font-size: 10px;
  font-weight: 800;
}

.review-strip {
  border: 1px solid #fde68a;
  border-radius: 12px;
  background: #fffbeb;
  padding: 14px 18px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 20px;
  padding-left: 12px;
  border-left: 4px solid #408aee;
}

.panel-title h3 {
  color: #111827;
  font-size: 16px;
  font-weight: 800;
  line-height: 1.2;
}

.field-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-block > span {
  color: #6b7280;
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}

.required-mark {
  color: #ef4444;
  font-style: normal;
}

.field-block > small {
  color: #94a3b8;
  font-size: 11px;
}

.customer-entry-hint {
  margin-top: -8px;
  border-radius: 8px;
  padding: 8px 12px;
  color: #1d4ed8;
  background: #eff6ff;
  font-size: 12px;
  line-height: 1.6;
}

.draft-entry-page :deep(.el-input__wrapper),
.draft-entry-page :deep(.el-select__wrapper),
.draft-entry-page :deep(.el-input-number .el-input__wrapper) {
  min-height: 42px;
  border-radius: 8px;
}

.draft-entry-page :deep(.el-input-number .el-input__inner) {
  text-align: left;
}

.draft-table-wrap {
  min-height: 360px;
}

.draft-table :deep(.el-table__cell) {
  padding-top: 14px;
  padding-bottom: 14px;
  vertical-align: top;
}

.recognition-note {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 7px;
  color: #6b7280;
  font-size: 11px;
  line-height: 1.4;
}

.recognition-note .material-symbols-outlined {
  color: #9ca3af;
  font-size: 15px;
}

.match-state,
.difference-state {
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 10px;
  font-weight: 800;
}

.match-state.matched {
  color: #047857;
  background: #ecfdf5;
}

.match-state.unmatched {
  color: #b45309;
  background: #fffbeb;
}

.difference-state {
  color: #c2410c;
  background: #fff7ed;
}

.cell-hint {
  margin-top: 6px;
  color: #9ca3af;
  font-size: 10px;
  white-space: nowrap;
}

.summary-panel {
  min-height: 100%;
  border-radius: 12px;
  padding: 24px;
  color: #fff;
  background: #1a1c1e;
  box-shadow: 0 14px 32px rgb(15 23 42 / 16%);
}

.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.summary-row span {
  color: #94a3b8;
}

.summary-row strong {
  font-weight: 800;
}

.summary-divider {
  margin: 14px 0;
  border-top: 1px solid #334155;
}

@media (min-width: 1500px) {
  .draft-workspace {
    grid-template-columns: minmax(760px, 1fr) minmax(420px, 34%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .draft-entry-page * {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
