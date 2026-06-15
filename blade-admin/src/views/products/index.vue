<template>
  <div class="products-page">
    <!-- 页面标题区 -->
    <div class="flex justify-between items-end mb-8">
      <div>
        <h2 class="text-2xl font-bold text-gray-900 tracking-tight mb-1">商品列表</h2>
        <p class="text-gray-500 text-sm">管理您的服装商品信息。</p>
      </div>
      <div class="flex">
        <el-button class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200 mr-3" @click="handleRefresh">
          <span class="material-symbols-outlined text-sm mr-1">refresh</span>
          刷新
        </el-button>
        <el-button type="primary" class="!bg-[#408aee] !border-none !px-6 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-[#2d7be0] shadow-lg shadow-primary/20" @click="handleCreate">
          <span class="material-symbols-outlined text-sm mr-1">add_circle</span>
          新建商品
        </el-button>
      </div>
    </div>

    <!-- 筛选区域 -->
    <div class="bg-white rounded-xl p-6 mb-6 shadow-sm flex flex-wrap items-center gap-6">
      <div class="w-[280px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">关键字搜索</label>
        <el-input
          v-model="searchQuery"
          placeholder="搜索商品名称/编码"
          class="product-search-input"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <span class="material-symbols-outlined text-gray-400 text-sm">search</span>
          </template>
        </el-input>
      </div>

      <div class="flex-1 min-w-[160px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">商品分类</label>
        <el-select v-model="categoryFilter" placeholder="全部" class="product-select" clearable>
          <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.categoryName" :value="cat.id" />
        </el-select>
      </div>

      <div class="flex-1 min-w-[120px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">状态</label>
        <el-select v-model="statusFilter" placeholder="全部" class="product-select" clearable>
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </div>

      <div class="ml-auto flex items-end">
        <el-button class="!bg-[#408aee] !text-white !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-[#2d7be0]" @click="handleSearch">
          <span class="material-symbols-outlined text-sm mr-1">search</span>
          搜索
        </el-button>
        <el-button class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200 ml-3" @click="handleReset">
          <span class="material-symbols-outlined text-sm mr-1">filter_list</span>
          重置筛选
        </el-button>
      </div>
    </div>

    <!-- 商品表格 -->
    <div class="bg-white rounded-xl shadow-sm mb-6">
      <el-table :data="tableData" class="product-table" v-loading="loading" empty-text="暂无商品数据">
        <el-table-column label="商品名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <img
                v-if="getProductImage(row.imageUrl)"
                :src="getProductImage(row.imageUrl)"
                alt=""
                class="w-10 h-10 rounded-lg object-cover border border-gray-200"
              />
              <div v-else class="w-10 h-10 rounded-lg bg-[#408aee]/10 flex items-center justify-center">
                <span class="material-symbols-outlined text-[#408aee]">inventory_2</span>
              </div>
              <div>
                <div class="text-sm font-semibold text-gray-900">{{ row.name }}</div>
                <div class="text-xs text-gray-400">{{ row.productCode }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="分类" min-width="100">
          <template #default="{ row }">
            <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider bg-[#408aee]/10 text-[#408aee]">
              {{ row.categoryName || '未分类' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="进货价" min-width="90" align="right">
          <template #default="{ row }">
            <span class="text-sm text-gray-600">¥{{ row.costPrice || 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="批发价" min-width="90" align="right">
          <template #default="{ row }">
            <span class="text-sm font-bold text-gray-900">¥{{ row.wholesalePrice || row.price || 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="SKU数量" min-width="80" align="center">
          <template #default="{ row }">
            <span class="text-sm font-bold text-[#408aee]">{{ row.skus?.length || 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="颜色/尺码" min-width="120">
          <template #default="{ row }">
            <div class="text-xs text-gray-500">
              <span v-if="row.colors?.length">颜色: {{ row.colors.length }}</span>
              <span v-if="row.colors?.length && row.sizes?.length"> / </span>
              <span v-if="row.sizes?.length">尺码: {{ row.sizes.length }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" min-width="80">
          <template #default="{ row }">
            <span :class="row.status === 1 ? 'bg-green-100 text-green-600' : 'bg-gray-100 text-gray-500'" class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" min-width="100">
          <template #default="{ row }">
            <span class="text-sm text-gray-500">{{ row.createTime?.split('T')[0] }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" min-width="120" fixed="right" align="center">
          <template #default="{ row }">
            <div class="flex items-center justify-center gap-3">
              <el-button type="default" link size="small" class="!text-gray-500 hover:!text-[#408aee]" @click="handleEdit(row)">编辑</el-button>
              <el-button type="default" link size="small" class="!text-gray-500 hover:!text-red-500" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="flex items-center justify-between px-6 py-4 bg-gray-50/50">
        <p class="text-xs text-gray-500 font-medium">
          显示第 <span class="text-gray-900">{{ (currentPage - 1) * pageSize + 1 }}</span>-<span class="text-gray-900">{{ Math.min(currentPage * pageSize, total) }}</span> 条，共 <span class="text-gray-900">{{ total }}</span> 条商品
        </p>
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          background
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- 商品编辑弹窗（v2 分区Tab） -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑商品' : '新建商品'"
      width="1180px"
      class="product-edit-dialog"
      :close-on-click-modal="false"
      @opened="onDialogOpened"
    >
      <div class="edit-overview">
        <div class="edit-overview-main">
          <div class="edit-avatar">
            <img v-if="mainImagePreview || getProductImage(form.imageUrl)" :src="mainImagePreview || getProductImage(form.imageUrl)" alt="" />
            <span v-else class="material-symbols-outlined">checkroom</span>
          </div>
          <div class="min-w-0">
            <div class="flex items-center gap-2 mb-1">
              <h3 class="edit-title">{{ form.name || '未命名商品' }}</h3>
              <span :class="form.status === 1 ? 'status-pill is-on' : 'status-pill is-off'">
                {{ form.status === 1 ? '启用' : '禁用' }}
              </span>
            </div>
            <div class="edit-subtitle">
              <span>{{ form.productCode || '暂无编码' }}</span>
              <span>颜色 {{ form.colorIds.length }}</span>
              <span>尺码 {{ form.sizeIds.length }}</span>
              <span v-if="isEdit">SKU {{ skuEditList.length }}</span>
            </div>
          </div>
        </div>
        <div class="edit-overview-metrics">
          <div class="metric-chip">
            <span>进货价</span>
            <strong>¥{{ formatNumber(form.costPrice) }}</strong>
          </div>
          <div class="metric-chip">
            <span>批发价</span>
            <strong>¥{{ formatNumber(form.wholesalePrice) }}</strong>
          </div>
          <div class="metric-chip" v-if="isEdit">
            <span>素材</span>
            <strong>{{ materialCount }}</strong>
          </div>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="product-edit-tabs">
        <!-- Tab 1: 基础信息 -->
        <el-tab-pane label="基础信息" name="basic">
          <el-form ref="formRef" :model="form" :rules="formRules" label-position="top" class="product-form">
            <div class="edit-panel-grid">
              <section class="edit-panel edit-panel-main">
                <div class="section-head">
                  <div>
                    <h4>商品资料</h4>
                    <p>用于列表、订单和选款页面展示</p>
                  </div>
                </div>
                <div class="form-grid two">
                  <el-form-item label="商品编码" prop="productCode">
                    <el-input v-model="form.productCode" placeholder="如: 6000#" :disabled="isEdit" />
                  </el-form-item>
                  <el-form-item label="商品名称" prop="name">
                    <el-input v-model="form.name" placeholder="商品名称" />
                  </el-form-item>
                  <el-form-item label="商品分类">
                    <el-select v-model="form.categoryId" placeholder="选择分类" class="w-full" clearable>
                      <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.categoryName" :value="cat.id" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="单位">
                    <el-input v-model="form.unit" placeholder="如: 件/套" />
                  </el-form-item>
                </div>
                <el-form-item label="商品描述">
                  <el-input v-model="form.description" type="textarea" :rows="3" placeholder="商品描述" />
                </el-form-item>
                <el-form-item label="备注">
                  <el-input v-model="form.remark" placeholder="内部备注信息" />
                </el-form-item>
              </section>

              <section class="edit-panel">
                <div class="section-head">
                  <div>
                    <h4>价格与状态</h4>
                    <p>SKU 可在明细页单独维护</p>
                  </div>
                </div>
                <div class="form-grid one">
                  <el-form-item label="进货价">
                    <el-input-number v-model="form.costPrice" :min="0" :precision="2" :controls="false" class="w-full" placeholder="成本价" />
                  </el-form-item>
                  <el-form-item label="批发价">
                    <el-input-number v-model="form.wholesalePrice" :min="0" :precision="2" :controls="false" class="w-full" placeholder="批发价" />
                  </el-form-item>
                  <el-form-item label="重量(kg)">
                    <el-input-number v-model="form.weight" :min="0" :precision="2" :controls="false" class="w-full" placeholder="用于运费计算" />
                  </el-form-item>
                  <el-form-item label="状态">
                    <el-radio-group v-model="form.status" class="segmented-radio">
                      <el-radio-button :value="1">启用</el-radio-button>
                      <el-radio-button :value="0">禁用</el-radio-button>
                    </el-radio-group>
                  </el-form-item>
                </div>
              </section>
            </div>
          </el-form>
        </el-tab-pane>

        <!-- Tab 2: 颜色尺码 -->
        <el-tab-pane label="颜色尺码" name="colorsize">
          <el-form label-position="top" class="product-form">
            <div class="edit-panel-grid">
              <section class="edit-panel">
                <div class="section-head">
                  <div>
                    <h4>颜色</h4>
                    <p>已选 {{ form.colorIds.length }} 个颜色</p>
                  </div>
                </div>
                <el-form-item label="">
                  <el-checkbox-group v-model="form.colorIds" class="option-grid">
                    <el-checkbox v-for="color in colorOptions" :key="color.id" :value="color.id" :label="color.id">
                      <span class="option-name">{{ color.colorName }}</span>
                      <span class="option-code">{{ color.colorCode }}</span>
                    </el-checkbox>
                  </el-checkbox-group>
                </el-form-item>
              </section>

              <section class="edit-panel">
                <div class="section-head">
                  <div>
                    <h4>尺码</h4>
                    <p>已选 {{ form.sizeIds.length }} 个尺码</p>
                  </div>
                </div>
                <el-form-item label="">
                  <el-checkbox-group v-model="form.sizeIds" class="option-grid size-grid">
                    <el-checkbox v-for="size in sizeOptions" :key="size.id" :value="size.id" :label="size.id">
                      <span class="option-name">{{ size.sizeCode }}</span>
                    </el-checkbox>
                  </el-checkbox-group>
                </el-form-item>
              </section>
            </div>

            <section class="edit-panel mt-4">
              <div class="section-head">
                <div>
                  <h4>SKU 预览</h4>
                  <p>颜色 × 尺码会自动生成 SKU，移除组合时已有 SKU 将禁用而不是删除</p>
                </div>
                <span class="summary-badge">{{ form.colorIds.length * form.sizeIds.length }} 个组合</span>
              </div>
              <div v-if="form.colorIds.length && form.sizeIds.length" class="sku-preview-grid">
                <span v-for="(sku, index) in previewSkus" :key="index" class="sku-preview-chip">{{ sku }}</span>
              </div>
              <div v-else class="empty-inline">
                请先选择颜色和尺码
              </div>
            </section>
          </el-form>
        </el-tab-pane>

        <!-- Tab 3: SKU 明细（仅编辑时显示） -->
        <el-tab-pane label="SKU 明细" name="skus" v-if="isEdit">
          <div v-if="skuEditList.length === 0" class="text-center py-12 text-gray-400">
            <span class="material-symbols-outlined text-4xl mb-2 block">inventory_2</span>
            <p>该商品暂无 SKU</p>
          </div>
          <div v-else>
            <div class="table-toolbar">
              <div>
                <h4>SKU 价格与状态</h4>
                <p>共 {{ skuEditList.length }} 个 SKU，{{ skuActiveCount }} 个启用，{{ skuDisabledCount }} 个禁用</p>
              </div>
              <el-button size="small" type="primary" :disabled="!hasSkuChanges" :loading="skuSaving" @click="saveSkuChanges">
                保存 SKU 修改
              </el-button>
            </div>
            <div class="sku-table-wrapper">
              <el-table :data="skuEditList" class="sku-table" size="small" max-height="360">
                <el-table-column label="SKU 编码" min-width="140" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="text-xs font-mono text-gray-700">{{ row.skuCode }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="颜色" width="80">
                  <template #default="{ row }">
                    <span class="text-xs">{{ row.colorName }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="尺码" width="70">
                  <template #default="{ row }">
                    <span class="text-xs">{{ row.sizeName }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="售价" width="120">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.price"
                      :min="0"
                      :precision="2"
                      :controls="false"
                      size="small"
                      class="sku-inline-input"
                      @change="markSkuDirty(row.id)"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="成本价" width="120">
                  <template #default="{ row }">
                    <el-input-number
                      v-model="row.costPrice"
                      :min="0"
                      :precision="2"
                      :controls="false"
                      size="small"
                      class="sku-inline-input"
                      @change="markSkuDirty(row.id)"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="条码" width="140">
                  <template #default="{ row }">
                    <el-input
                      v-model="row.barCode"
                      size="small"
                      placeholder="条形码"
                      class="sku-inline-input"
                      @change="markSkuDirty(row.id)"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="80" align="center">
                  <template #default="{ row }">
                    <div class="status-cell">
                      <el-switch
                        v-model="row.status"
                        :active-value="1"
                        :inactive-value="0"
                        size="small"
                        @change="markSkuDirty(row.id)"
                      />
                      <span>{{ row.status === 1 ? '启用' : '禁用' }}</span>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <!-- Tab 4: 商品素材（仅编辑时显示） -->
        <el-tab-pane label="商品素材" name="materials" v-if="isEdit">
          <div v-loading="fileBindingsLoading" class="materials-content">
            <div class="material-layout">
              <!-- 商品主图 -->
              <section class="edit-panel hero-panel">
                <div class="section-head">
                  <div>
                    <h4>商品主图</h4>
                    <p>用于商品列表、订单录入和 Catalog 封面展示</p>
                  </div>
                </div>
                <div class="hero-media">
                  <div class="hero-preview">
                    <img v-if="mainImagePreview" :src="mainImagePreview" alt="商品主图" />
                    <span v-else class="material-symbols-outlined">image</span>
                  </div>
                  <div class="media-actions">
                    <label class="upload-button">
                      <span class="material-symbols-outlined">upload</span>
                      上传主图
                      <input type="file" accept="image/*" @change="handleMainImageUpload" />
                    </label>
                    <el-button v-if="mainImagePreview" size="small" plain type="danger" @click="clearMainImage">移除主图</el-button>
                  </div>
                </div>
                <div class="file-id-row">
                  <el-input v-model="mainFileIdInput" size="small" placeholder="输入已有 fileId" />
                  <el-button size="small" @click="applyMainFileId">设置</el-button>
                </div>
              </section>

              <!-- 商品图集 -->
              <section class="edit-panel gallery-panel">
                <div class="section-head">
                  <div>
                    <h4>商品图集</h4>
                    <p>维护商品细节图，支持多张图片绑定</p>
                  </div>
                  <span class="summary-badge">{{ galleryImages.length }} 张</span>
                </div>
                <div v-if="galleryImages.length" class="material-thumb-grid">
                  <div v-for="(img, idx) in galleryImages" :key="img.fileId || idx" class="material-thumb">
                    <img :src="img.previewUrl" alt="" />
                    <button type="button" class="thumb-remove" @click="removeGalleryImage(idx)">
                      <span class="material-symbols-outlined">close</span>
                    </button>
                  </div>
                </div>
                <div v-else class="empty-material">
                  <span class="material-symbols-outlined">collections</span>
                  <p>暂无图集图片</p>
                </div>
                <div class="gallery-actions">
                  <label class="upload-button">
                    <span class="material-symbols-outlined">add_photo_alternate</span>
                    上传图集
                    <input type="file" accept="image/*" multiple @change="handleGalleryUpload" />
                  </label>
                  <div class="file-id-row compact">
                    <el-input v-model="galleryFileIdInput" size="small" placeholder="输入 fileId" />
                    <el-button size="small" @click="addGalleryFileId">添加</el-button>
                  </div>
                </div>
              </section>
            </div>

            <!-- SKU 图片 -->
            <section class="edit-panel sku-media-panel">
              <div class="section-head">
                <div>
                  <h4>SKU 图片</h4>
                  <p>为不同颜色/尺码维护单独展示图</p>
                </div>
                <span class="summary-badge">{{ skuEditList.length }} 个 SKU</span>
              </div>
              <div v-if="skuEditList.length === 0" class="empty-material sku-empty">
                <span class="material-symbols-outlined">inventory_2</span>
                <p>暂无 SKU</p>
              </div>
              <div v-else class="sku-media-list">
                <div v-for="sku in skuEditList" :key="sku.id" class="sku-media-row">
                  <div class="sku-media-info">
                    <strong>{{ sku.skuCode }}</strong>
                    <span>{{ sku.colorName }} / {{ sku.sizeName }}</span>
                  </div>
                  <div class="sku-image-strip">
                    <div
                      v-for="(f, idx) in (skuImageFiles[sku.id] || [])"
                      :key="f.fileId"
                      class="sku-image-thumb"
                      :title="'fileId: ' + f.fileId"
                    >
                      <img :src="f.previewUrl" alt="" />
                      <button type="button" @click="removeSkuImage(sku.id, idx)">
                        <span class="material-symbols-outlined">close</span>
                      </button>
                    </div>
                    <span v-if="!(skuImageFiles[sku.id] || []).length" class="sku-no-image">无图</span>
                  </div>
                  <div class="sku-media-actions">
                    <input
                      type="file"
                      accept="image/*"
                      class="hidden"
                      :id="'sku-img-upload-' + sku.id"
                      @change="e => handleSkuImageUpload(sku.id, e)"
                    />
                    <label
                      :for="'sku-img-upload-' + sku.id"
                      class="upload-mini"
                    >
                      上传
                    </label>
                    <el-input
                      v-model="skuFileIdInputs[sku.id]"
                      size="small"
                      placeholder="fileId"
                      class="sku-file-input"
                      @keyup.enter="addSkuImageByFileId(sku.id)"
                    />
                    <el-button size="small" @click="addSkuImageByFileId(sku.id)">添加</el-button>
                  </div>
                </div>
              </div>
            </section>

            <!-- 素材保存按钮 -->
            <div class="material-savebar">
              <el-button type="primary" :loading="materialSaving" @click="saveFileBindings">保存素材绑定</el-button>
              <span>素材修改独立保存，不影响基础信息和颜色尺码</span>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProductPage, getProductById, createProduct, updateProduct, deleteProduct, getAllColors, getAllSizes, getAllCategories, getProductFileBindings, setProductFileBindings, batchUpdateSkus, type ProductVO, type ProductColor, type ProductSize, type ProductSku, type ProductCreateDTO, type ProductCategory, type FileBindingItem, type SkuImageBindingDTO, type SkuUpdateDTO } from '@/api/product'
import { parseImageSources, uploadFile, filePreviewUrl } from '@/api/file'

const searchQuery = ref('')
const categoryFilter = ref<number | undefined>(undefined)
const statusFilter = ref<number | undefined>(undefined)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)

const tableData = ref<ProductVO[]>([])

// 弹窗相关
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref()
const editingId = ref<number | null>(null)

// 颜色和尺码选项
const colorOptions = ref<ProductColor[]>([])
const sizeOptions = ref<ProductSize[]>([])
const categoryOptions = ref<ProductCategory[]>([])

// 表单
const form = ref<{
  productCode: string
  name: string
  categoryId: number | undefined
  unit: string
  costPrice: number | undefined
  wholesalePrice: number | undefined
  weight: number | undefined
  status: number
  description: string
  imageUrl: string
  remark: string
  colorIds: number[]
  sizeIds: number[]
}>({
  productCode: '',
  name: '',
  categoryId: undefined,
  unit: '件',
  costPrice: undefined,
  wholesalePrice: undefined,
  weight: undefined,
  status: 1,
  description: '',
  imageUrl: '',
  remark: '',
  colorIds: [],
  sizeIds: []
})

// v2 编辑 Tab 状态
const activeTab = ref('basic')

// SKU 明细状态
const skuEditList = ref<(ProductSku & { _original: string })[]>([])
const skuDirtyIds = ref(new Set<number>())
const skuSaving = ref(false)
const hasSkuChanges = ref(false)

// 商品素材状态
const fileBindingsLoading = ref(false)
const materialSaving = ref(false)
const mainImagePreview = ref('')
const mainFileIdInput = ref('')
const galleryImages = ref<FileBindingItem[]>([])
const galleryFileIdInput = ref('')
const skuImageFiles = ref<Record<number, FileBindingItem[]>>({})
const skuFileIdInputs = ref<Record<number, string>>({})

const formRules = {
  productCode: [{ required: true, message: '请输入商品编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  colorIds: [{ required: true, message: '请至少选择一个颜色', trigger: 'change', type: 'array', min: 1 }],
  sizeIds: [{ required: true, message: '请至少选择一个尺码', trigger: 'change', type: 'array', min: 1 }]
}

// SKU 预览
const previewSkus = computed(() => {
  const skus: string[] = []
  const selectedColors = colorOptions.value.filter(c => form.value.colorIds.includes(c.id))
  const selectedSizes = sizeOptions.value.filter(s => form.value.sizeIds.includes(s.id))
  for (const color of selectedColors) {
    for (const size of selectedSizes) {
      skus.push(`${form.value.productCode || 'CODE'}-${color.colorCode}-${size.sizeCode}`)
    }
  }
  return skus.slice(0, 20) // 最多显示20个
})

const skuActiveCount = computed(() => skuEditList.value.filter(sku => sku.status === 1).length)
const skuDisabledCount = computed(() => skuEditList.value.filter(sku => sku.status !== 1).length)
const materialCount = computed(() => {
  const skuImageCount = Object.values(skuImageFiles.value).reduce((sum, files) => sum + files.length, 0)
  return (mainFileIdInput.value ? 1 : 0) + galleryImages.value.length + skuImageCount
})

function formatNumber(value?: number) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '0'
  return num.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function normalizeBindingPreview(item: FileBindingItem): FileBindingItem {
  return {
    ...item,
    previewUrl: filePreviewUrl(item.fileId)
  }
}

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const res = await getProductPage({
      current: currentPage.value,
      size: pageSize.value,
      keyword: searchQuery.value || undefined,
      categoryId: categoryFilter.value,
      status: statusFilter.value
    })
    if (res.code === 200) {
      tableData.value = res.data.records
      total.value = res.data.total
    }
  } catch (error) {
    console.error('加载商品列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载颜色和尺码选项
async function loadOptions() {
  try {
    const [colorsRes, sizesRes, categoriesRes] = await Promise.all([
      getAllColors(),
      getAllSizes(),
      getAllCategories()
    ])
    if (colorsRes.code === 200) {
      colorOptions.value = colorsRes.data
    }
    if (sizesRes.code === 200) {
      sizeOptions.value = sizesRes.data.sort((a: ProductSize, b: ProductSize) => a.sort - b.sort)
    }
    if (categoriesRes.code === 200) {
      categoryOptions.value = categoriesRes.data
    }
  } catch (error) {
    console.error('加载选项失败:', error)
  }
}

// 搜索
function handleSearch() {
  currentPage.value = 1
  loadData()
}

// 重置
function handleReset() {
  searchQuery.value = ''
  categoryFilter.value = undefined
  statusFilter.value = undefined
  currentPage.value = 1
  loadData()
}

// 刷新
function handleRefresh() {
  loadData()
}

// 新建
function handleCreate() {
  isEdit.value = false
  editingId.value = null
  activeTab.value = 'basic'
  form.value = {
    productCode: '',
    name: '',
    categoryId: undefined,
    unit: '件',
    costPrice: undefined,
    wholesalePrice: undefined,
    weight: undefined,
    status: 1,
    description: '',
    imageUrl: '',
    remark: '',
    colorIds: [],
    sizeIds: []
  }
  // 重置 v2 状态
  skuEditList.value = []
  skuDirtyIds.value = new Set()
  hasSkuChanges.value = false
  mainImagePreview.value = ''
  mainFileIdInput.value = ''
  galleryImages.value = []
  skuImageFiles.value = {}
  skuFileIdInputs.value = {}
  dialogVisible.value = true
}

// 编辑
async function handleEdit(row: ProductVO) {
  isEdit.value = true
  editingId.value = row.id
  activeTab.value = 'basic'
  try {
    const res = await getProductById(row.id)
    if (res.code === 200) {
      const product = res.data
      form.value = {
        productCode: product.productCode,
        name: product.name,
        categoryId: product.categoryId,
        unit: product.unit,
        costPrice: product.costPrice,
        wholesalePrice: product.wholesalePrice,
        weight: product.weight,
        status: product.status,
        description: product.description || '',
        imageUrl: product.imageUrl || '',
        remark: product.remark || '',
        colorIds: product.colors?.map((c: ProductColor) => c.id) || [],
        sizeIds: product.sizes?.map((s: ProductSize) => s.id) || []
      }
      // 初始化 SKU 明细编辑数据
      initSkuEditList(product.skus || [])
      dialogVisible.value = true
    }
  } catch (error) {
    console.error('加载商品详情失败:', error)
  }
}

// 弹窗打开后加载素材
function onDialogOpened() {
  if (isEdit.value && editingId.value) {
    loadFileBindings()
  }
}

// 提交
async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitLoading.value = true
  try {
    const data: ProductCreateDTO = {
      productCode: form.value.productCode,
      name: form.value.name,
      categoryId: form.value.categoryId,
      unit: form.value.unit,
      costPrice: form.value.costPrice,
      wholesalePrice: form.value.wholesalePrice,
      weight: form.value.weight,
      status: form.value.status,
      description: form.value.description,
      imageUrl: form.value.imageUrl || undefined,
      remark: form.value.remark,
      colorIds: form.value.colorIds,
      sizeIds: form.value.sizeIds
    }

    let res
    if (isEdit.value && editingId.value) {
      res = await updateProduct({ ...data, id: editingId.value })
    } else {
      res = await createProduct(data)
    }

    if (res.code === 200) {
      ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
      dialogVisible.value = false
      loadData()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

function getProductImage(imageUrl?: string) {
  return parseImageSources(imageUrl)[0] || ''
}

onMounted(() => {
  loadData()
  loadOptions()
})

// 删除
async function handleDelete(row: ProductVO) {
  try {
    await ElMessageBox.confirm(
      `确定要删除商品「${row.name}」吗？若商品存在订单、库存等业务引用则无法删除，建议改为禁用。`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const res = await deleteProduct(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadData()
    } else {
      // 显示后端引用保护消息，并建议禁用
      ElMessageBox.alert(
        res.message || '删除失败，可能存在业务引用',
        '无法删除',
        {
          confirmButtonText: '知道了',
          type: 'warning'
        }
      )
    }
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessageBox.alert(
        error?.message || error?.response?.data?.message || '删除失败，可能存在业务引用',
        '无法删除',
        { confirmButtonText: '知道了', type: 'warning' }
      )
    }
  }
}

// ==================== v2: SKU 明细 ====================

function initSkuEditList(skus: ProductSku[]) {
  skuEditList.value = skus.map(s => ({
    ...s,
    _original: JSON.stringify({ price: s.price, costPrice: s.costPrice, barCode: s.barCode, status: s.status })
  }))
  skuDirtyIds.value = new Set()
  hasSkuChanges.value = false
}

function markSkuDirty(skuId: number) {
  const sku = skuEditList.value.find(s => s.id === skuId)
  if (!sku) return
  const original = JSON.parse(sku._original)
  const changed =
    original.price !== sku.price ||
    original.costPrice !== sku.costPrice ||
    original.barCode !== sku.barCode ||
    original.status !== sku.status
  if (changed) {
    skuDirtyIds.value.add(skuId)
  } else {
    skuDirtyIds.value.delete(skuId)
  }
  hasSkuChanges.value = skuDirtyIds.value.size > 0
}

async function saveSkuChanges() {
  if (skuDirtyIds.value.size === 0) return
  skuSaving.value = true
  try {
    const updates: SkuUpdateDTO[] = []
    for (const sku of skuEditList.value) {
      if (skuDirtyIds.value.has(sku.id)) {
        updates.push({
          id: sku.id,
          price: sku.price,
          costPrice: sku.costPrice,
          barCode: sku.barCode || undefined,
          status: sku.status
        })
      }
    }
    const res = await batchUpdateSkus(updates)
    if (res.code === 200) {
      ElMessage.success(`已保存 ${updates.length} 个 SKU 修改`)
      // 更新原始快照
      for (const sku of skuEditList.value) {
        if (skuDirtyIds.value.has(sku.id)) {
          sku._original = JSON.stringify({ price: sku.price, costPrice: sku.costPrice, barCode: sku.barCode, status: sku.status })
        }
      }
      skuDirtyIds.value = new Set()
      hasSkuChanges.value = false
    } else {
      ElMessage.error(res.message || 'SKU 保存失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || 'SKU 保存失败')
  } finally {
    skuSaving.value = false
  }
}

// ==================== v2: 商品素材 ====================

async function loadFileBindings() {
  if (!editingId.value) return
  fileBindingsLoading.value = true
  try {
    // Initialize per-SKU structures for all SKUs in skuEditList
    const files: Record<number, FileBindingItem[]> = {}
    const inputs: Record<number, string> = {}
    for (const sku of skuEditList.value) {
      files[sku.id] = []
      inputs[sku.id] = ''
    }

    const res = await getProductFileBindings(editingId.value)
    if (res.code === 200) {
      const data = res.data
      mainImagePreview.value = data.main ? filePreviewUrl(data.main.fileId) : ''
      mainFileIdInput.value = data.main?.fileId ? String(data.main.fileId) : ''
      galleryImages.value = (data.gallery || []).map(normalizeBindingPreview)
      // Fill SKU image files from backend response
      for (const group of (data.skuImages || [])) {
        files[group.skuId] = (group.files || []).map(normalizeBindingPreview)
      }
    }
    skuImageFiles.value = files
    skuFileIdInputs.value = inputs
  } catch (error) {
    console.error('加载素材绑定失败:', error)
  } finally {
    fileBindingsLoading.value = false
  }
}

async function handleMainImageUpload(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file || !editingId.value) return
  try {
    const res = await uploadFile(file, 'product', editingId.value)
    const fileId = res.data.id as number
    // 直接设置为主图
    mainFileIdInput.value = String(fileId)
    mainImagePreview.value = filePreviewUrl(fileId)
    ElMessage.success('主图上传成功，请点击「保存素材绑定」生效')
  } catch (error: any) {
    ElMessage.error(error?.message || '图片上传失败')
  } finally {
    target.value = ''
  }
}

async function applyMainFileId() {
  const id = parseInt(mainFileIdInput.value)
  if (!id || isNaN(id)) {
    ElMessage.warning('请输入有效的 fileId')
    return
  }
  mainImagePreview.value = filePreviewUrl(id)
  ElMessage.success('主图已设置，请点击「保存素材绑定」生效')
}

function clearMainImage() {
  mainImagePreview.value = ''
  mainFileIdInput.value = ''
}

async function handleGalleryUpload(e: Event) {
  const target = e.target as HTMLInputElement
  const files = target.files
  if (!files || !editingId.value) return
  try {
    for (const file of Array.from(files)) {
      const res = await uploadFile(file, 'product', editingId.value)
      const fileId = res.data.id as number
      galleryImages.value.push({
        fileId,
        previewUrl: filePreviewUrl(fileId),
        sort: galleryImages.value.length,
        isPrimary: 0
      })
    }
    ElMessage.success(`已上传 ${files.length} 张图集图片，请点击「保存素材绑定」生效`)
  } catch (error: any) {
    ElMessage.error(error?.message || '图集上传失败')
  } finally {
    target.value = ''
  }
}

async function addGalleryFileId() {
  const id = parseInt(galleryFileIdInput.value)
  if (!id || isNaN(id)) {
    ElMessage.warning('请输入有效的 fileId')
    return
  }
  // 避免重复
  if (galleryImages.value.some(g => g.fileId === id)) {
    ElMessage.warning('该 fileId 已在图集中')
    return
  }
  galleryImages.value.push({
    fileId: id,
    previewUrl: filePreviewUrl(id),
    sort: galleryImages.value.length,
    isPrimary: 0
  })
  galleryFileIdInput.value = ''
}

function removeGalleryImage(index: number) {
  galleryImages.value.splice(index, 1)
}

async function saveFileBindings() {
  if (!editingId.value) return
  materialSaving.value = true
  try {
    const mainId = mainFileIdInput.value ? parseInt(mainFileIdInput.value) : null
    const skuImageBindings: SkuImageBindingDTO[] = skuEditList.value.map(sku => ({
      skuId: sku.id,
      fileIds: (skuImageFiles.value[sku.id] || []).map(f => f.fileId)
    }))
    const res = await setProductFileBindings(editingId.value, {
      mainFileId: mainId,
      galleryFileIds: galleryImages.value.map(g => g.fileId),
      skuImageBindings
    })
    if (res.code === 200) {
      ElMessage.success('素材绑定保存成功')
      // 同步更新 form.imageUrl
      if (mainId) {
        form.value.imageUrl = String(mainId)
      }
      loadFileBindings()
    } else {
      ElMessage.error(res.message || '素材保存失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '素材保存失败')
  } finally {
    materialSaving.value = false
  }
}

// SKU 图片管理辅助函数
async function handleSkuImageUpload(skuId: number, e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file || !editingId.value) return
  try {
    const res = await uploadFile(file, 'product', editingId.value)
    const fileId = res.data.id as number
    if (!skuImageFiles.value[skuId]) {
      skuImageFiles.value[skuId] = []
    }
    skuImageFiles.value[skuId].push({
      fileId,
      previewUrl: filePreviewUrl(fileId),
      sort: skuImageFiles.value[skuId].length,
      isPrimary: 0
    })
    ElMessage.success('SKU 图片上传成功，请点击「保存素材绑定」生效')
  } catch (error: any) {
    ElMessage.error(error?.message || 'SKU 图片上传失败')
  } finally {
    target.value = ''
  }
}

async function addSkuImageByFileId(skuId: number) {
  const inputVal = skuFileIdInputs.value[skuId]
  if (!inputVal) return
  const id = parseInt(inputVal)
  if (!id || isNaN(id)) {
    ElMessage.warning('请输入有效的 fileId')
    return
  }
  if (!skuImageFiles.value[skuId]) {
    skuImageFiles.value[skuId] = []
  }
  if (skuImageFiles.value[skuId].some(f => f.fileId === id)) {
    ElMessage.warning('该 fileId 已在该 SKU 图片中')
    return
  }
  skuImageFiles.value[skuId].push({
    fileId: id,
    previewUrl: filePreviewUrl(id),
    sort: skuImageFiles.value[skuId].length,
    isPrimary: 0
  })
  skuFileIdInputs.value[skuId] = ''
}

function removeSkuImage(skuId: number, index: number) {
  if (skuImageFiles.value[skuId]) {
    skuImageFiles.value[skuId].splice(index, 1)
  }
}
</script>

<style scoped>
.products-page {
  padding: 0;
}

.product-table {
  width: 100%;
  overflow-x: auto;
}

.product-table :deep(.el-table__body-wrapper) {
  overflow-x: auto;
}

.product-search-input :deep(.el-input__wrapper) {
  background-color: rgb(255 255 255) !important;
  border-radius: 12px !important;
  border: 1px solid #e5e7eb !important;
  height: 44px !important;
}

.product-search-input :deep(.el-input__inner) {
  font-size: 14px !important;
  color: #1a1a2e !important;
}

.product-select {
  width: 100%;
}

.product-select :deep(.el-input__wrapper) {
  background-color: rgb(255 255 255) !important;
  border-radius: 12px !important;
  border: 1px solid #e5e7eb !important;
  height: 44px !important;
}

.product-table :deep(.el-table__header th) {
  background-color: rgb(249 250 251 / 50%) !important;
  font-size: 11px !important;
  font-weight: 900 !important;
  text-transform: uppercase !important;
  letter-spacing: 0.05em !important;
  color: rgb(107 114 128) !important;
  padding: 20px 24px !important;
}

.product-table :deep(.el-table__body td) {
  padding: 20px 24px !important;
}

.product-table :deep(.el-table__row:hover > td) {
  background-color: rgb(249 250 251 / 30%) !important;
}

.product-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: #374151;
}

.product-form :deep(.el-input__wrapper),
.product-form :deep(.el-textarea__inner) {
  background-color: rgb(255 255 255) !important;
  border-radius: 8px !important;
  border: 1px solid #e5e7eb !important;
}

.product-form :deep(.el-input__inner),
.product-form :deep(.el-textarea__inner) {
  color: #1a1a2e !important;
}

/* 分页样式 */
.products-page :deep(.el-pagination.is-background .el-pager li.is-active) {
  background-color: #408aee !important;
}

/* v2 Tabs 编辑页样式 */
.product-edit-dialog :deep(.el-dialog) {
  border-radius: 14px;
}

.product-edit-dialog :deep(.el-dialog__body) {
  padding: 0 24px 16px;
}

.product-edit-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid #eef2f7;
  padding: 14px 24px 18px;
}

.edit-overview {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 18px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #f8fbff 0%, #ffffff 52%, #f7fafc 100%);
  border: 1px solid #e8eef7;
  border-radius: 12px;
}

.edit-overview-main {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.edit-avatar {
  width: 64px;
  height: 64px;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 12px;
  background: #eef5ff;
  border: 1px solid #dbeafe;
  color: #408aee;
}

.edit-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.edit-avatar .material-symbols-outlined {
  font-size: 30px;
}

.edit-title {
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 18px;
  line-height: 1.3;
  font-weight: 800;
  color: #111827;
}

.edit-subtitle {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
}

.edit-subtitle span {
  padding: 3px 8px;
  border-radius: 999px;
  background: #f1f5f9;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill.is-on {
  color: #047857;
  background: #d1fae5;
}

.status-pill.is-off {
  color: #9f1239;
  background: #ffe4e6;
}

.edit-overview-metrics {
  display: flex;
  align-items: stretch;
  gap: 10px;
}

.metric-chip {
  min-width: 112px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #ffffff;
  border: 1px solid #e5eaf2;
  box-shadow: 0 8px 20px rgb(15 23 42 / 4%);
}

.metric-chip span {
  display: block;
  margin-bottom: 4px;
  color: #94a3b8;
  font-size: 12px;
  font-weight: 700;
}

.metric-chip strong {
  color: #111827;
  font-size: 18px;
  font-weight: 850;
}

.product-edit-tabs {
  min-height: 420px;
}

.product-edit-tabs :deep(.el-tabs__content) {
  padding: 18px 0 0;
  max-height: 560px;
  overflow-y: auto;
}

.product-edit-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.product-edit-tabs :deep(.el-tabs__item) {
  font-weight: 700;
}

.edit-panel-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.8fr);
  gap: 16px;
}

.edit-panel {
  padding: 18px;
  border: 1px solid #e8eef7;
  border-radius: 12px;
  background: #ffffff;
  box-shadow: 0 10px 26px rgb(15 23 42 / 4%);
}

.edit-panel-main {
  min-width: 0;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.section-head h4 {
  margin: 0;
  color: #111827;
  font-size: 15px;
  line-height: 1.35;
  font-weight: 850;
}

.section-head p {
  margin: 4px 0 0;
  color: #8a95a8;
  font-size: 12px;
}

.form-grid {
  display: grid;
  gap: 14px 16px;
}

.form-grid.two {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.form-grid.one {
  grid-template-columns: 1fr;
}

.segmented-radio {
  width: 100%;
}

.segmented-radio :deep(.el-radio-button) {
  flex: 1;
}

.segmented-radio :deep(.el-radio-button__inner) {
  width: 100%;
}

.option-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(128px, 1fr));
  gap: 10px;
  width: 100%;
}

.size-grid {
  grid-template-columns: repeat(auto-fill, minmax(88px, 1fr));
}

.option-grid :deep(.el-checkbox) {
  height: auto;
  margin: 0;
  padding: 10px 12px;
  border: 1px solid #e5eaf2;
  border-radius: 10px;
  background: #fbfdff;
}

.option-grid :deep(.el-checkbox.is-checked) {
  border-color: #408aee;
  background: #f0f7ff;
}

.option-grid :deep(.el-checkbox__label) {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
  padding-left: 8px;
}

.option-name {
  color: #1f2937;
  font-size: 13px;
  font-weight: 700;
}

.option-code {
  color: #94a3b8;
  font-size: 11px;
}

.summary-badge {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  background: #eef5ff;
  color: #408aee;
  font-size: 12px;
  font-weight: 800;
  white-space: nowrap;
}

.sku-preview-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sku-preview-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 5px 10px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #e5eaf2;
  color: #475569;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.empty-inline {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 72px;
  border: 1px dashed #d5dde8;
  border-radius: 10px;
  color: #94a3b8;
  background: #fafcff;
  font-size: 13px;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 14px 16px;
  border: 1px solid #e8eef7;
  border-radius: 12px;
  background: #fbfdff;
}

.table-toolbar h4 {
  margin: 0;
  color: #111827;
  font-size: 15px;
  font-weight: 850;
}

.table-toolbar p {
  margin: 3px 0 0;
  color: #8a95a8;
  font-size: 12px;
}

/* SKU 明细表格 */
.sku-table-wrapper {
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  overflow: hidden;
}

.sku-table :deep(.el-table__header th) {
  background-color: rgb(249 250 251) !important;
  font-size: 11px !important;
  font-weight: 700 !important;
  color: rgb(107 114 128) !important;
  padding: 10px 12px !important;
}

.sku-table :deep(.el-table__body td) {
  padding: 6px 12px !important;
}

.sku-inline-input {
  width: 100%;
}

.sku-inline-input :deep(.el-input__wrapper) {
  background-color: rgb(255 255 255) !important;
  border-radius: 6px !important;
}

.status-cell {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: #64748b;
  font-size: 11px;
}

/* 素材内容区 */
.materials-content {
  min-height: 300px;
}

.material-layout {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.hero-panel,
.gallery-panel,
.sku-media-panel {
  min-width: 0;
}

.hero-media {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.hero-preview {
  width: 116px;
  height: 116px;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid #dfe7f2;
  background: #f7faff;
  color: #94a3b8;
}

.hero-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-preview .material-symbols-outlined {
  font-size: 34px;
}

.media-actions,
.gallery-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.upload-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border-radius: 8px;
  background: #408aee;
  color: #ffffff;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
  transition: background-color 0.15s ease, transform 0.15s ease;
}

.upload-button:hover {
  background: #2f78dc;
}

.upload-button:active {
  transform: translateY(1px);
}

.upload-button input {
  display: none;
}

.upload-button .material-symbols-outlined {
  font-size: 17px;
}

.file-id-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.file-id-row.compact {
  width: min(360px, 100%);
}

.material-thumb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(88px, 1fr));
  gap: 10px;
  min-height: 96px;
  margin-bottom: 14px;
}

.material-thumb {
  position: relative;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border-radius: 10px;
  border: 1px solid #dfe7f2;
  background: #f8fafc;
}

.material-thumb img,
.sku-image-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-remove,
.sku-image-thumb button {
  position: absolute;
  top: 6px;
  right: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 999px;
  background: rgb(15 23 42 / 72%);
  color: #ffffff;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.material-thumb:hover .thumb-remove,
.sku-image-thumb:hover button {
  opacity: 1;
}

.thumb-remove .material-symbols-outlined,
.sku-image-thumb button .material-symbols-outlined {
  font-size: 15px;
}

.empty-material {
  min-height: 116px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-bottom: 14px;
  border: 1px dashed #d5dde8;
  border-radius: 12px;
  background: #fbfdff;
  color: #94a3b8;
}

.empty-material .material-symbols-outlined {
  font-size: 28px;
}

.empty-material p {
  margin: 0;
  font-size: 12px;
}

.sku-empty {
  margin-bottom: 0;
}

.sku-media-list {
  display: grid;
  gap: 10px;
}

.sku-media-row {
  display: grid;
  grid-template-columns: 190px minmax(220px, 1fr) 300px;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  border: 1px solid #e8eef7;
  border-radius: 10px;
  background: #fbfdff;
}

.sku-media-info {
  min-width: 0;
}

.sku-media-info strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1f2937;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.sku-media-info span {
  display: block;
  margin-top: 3px;
  color: #94a3b8;
  font-size: 11px;
}

.sku-image-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  overflow-x: auto;
  padding-bottom: 2px;
}

.sku-image-thumb {
  position: relative;
  width: 46px;
  height: 46px;
  flex: 0 0 auto;
  overflow: hidden;
  border-radius: 8px;
  border: 1px solid #dfe7f2;
  background: #ffffff;
}

.sku-no-image {
  color: #94a3b8;
  font-size: 12px;
}

.sku-media-actions {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
}

.upload-mini {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  padding: 0 10px;
  border: 1px solid #cfe0f8;
  border-radius: 7px;
  background: #ffffff;
  color: #408aee;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.sku-file-input {
  width: 100%;
}

.material-savebar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
  padding: 14px 16px;
  border: 1px solid #e8eef7;
  border-radius: 12px;
  background: #fbfdff;
}

.material-savebar span {
  color: #8a95a8;
  font-size: 12px;
}

@media (max-width: 1120px) {
  .edit-overview,
  .edit-panel-grid,
  .material-layout {
    grid-template-columns: 1fr;
  }

  .edit-overview {
    display: grid;
  }

  .sku-media-row {
    grid-template-columns: 1fr;
  }

  .sku-media-actions {
    max-width: 360px;
  }
}
</style>
