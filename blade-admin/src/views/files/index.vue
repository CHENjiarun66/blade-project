<template>
  <div class="files-page">
    <!-- 隐藏的多文件上传 input -->
    <input
      ref="uploadInputRef"
      type="file"
      multiple
      accept="image/*,video/*"
      class="hidden"
      @change="handleUploadInput"
    />

    <!-- 页面标题区 -->
    <div class="flex justify-between items-end mb-6">
      <div>
        <h2 class="text-2xl font-bold text-gray-900 tracking-tight mb-1">文件中心</h2>
        <p class="text-gray-500 text-sm">统一管理所有上传的图片、视频等数字资产。</p>
      </div>
      <div class="flex gap-2">
        <el-button
          class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200"
          @click="handleRefresh"
        >
          <span class="material-symbols-outlined text-sm mr-1">refresh</span>
          刷新
        </el-button>
        <el-button
          class="!bg-[#408aee] !text-white !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-[#3576d3] disabled:!opacity-50"
          :disabled="uploading"
          @click="openUpload"
        >
          <span class="material-symbols-outlined text-sm mr-1">{{ uploading ? 'hourglass_top' : 'upload' }}</span>
          {{ uploading ? '上传中...' : '上传' }}
        </el-button>
        <el-button
          class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200"
          @click="showCleanupPanel = true"
        >
          <span class="material-symbols-outlined text-sm mr-1">cleaning_services</span>
          清理
        </el-button>
      </div>
    </div>

    <!-- 批量操作工具栏 -->
    <div
      v-if="selectedIds.size > 0"
      class="bg-[#408aee]/5 border border-[#408aee]/20 rounded-xl px-5 py-3 mb-4 flex items-center gap-3"
    >
      <span class="text-sm font-bold text-[#408aee]">
        已选择 <span class="text-base">{{ selectedIds.size }}</span> 个文件
      </span>
      <div class="flex gap-2 ml-auto">
        <el-button
          size="small"
          class="!bg-white !border-gray-200 !text-gray-700 !rounded-lg hover:!border-[#408aee]"
          @click="openMoveDialog"
        >
          <span class="material-symbols-outlined text-sm mr-1">drive_file_move</span>
          移动
        </el-button>
        <el-button
          size="small"
          class="!bg-white !border-gray-200 !text-gray-700 !rounded-lg hover:!border-[#408aee]"
          @click="openBindDialogForSelected"
        >
          <span class="material-symbols-outlined text-sm mr-1">link</span>
          绑定
        </el-button>
        <el-button
          size="small"
          class="!bg-white !border-gray-200 !text-red-600 !rounded-lg hover:!border-red-400"
          @click="handleBatchDelete"
        >
          <span class="material-symbols-outlined text-sm mr-1">delete</span>
          删除
        </el-button>
        <el-button
          size="small"
          class="!bg-white !border-gray-200 !text-gray-500 !rounded-lg"
          @click="clearSelection"
        >
          取消选择
        </el-button>
      </div>
    </div>

    <div class="flex gap-0">
      <!-- 左侧：虚拟入口 + 文件夹树 -->
      <aside class="w-[220px] flex-shrink-0">
        <div class="bg-white rounded-xl shadow-sm p-4 sticky top-20">
          <h3 class="text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-3 ml-1">快捷入口</h3>
          <nav class="flex flex-col gap-0.5 mb-6">
            <button
              v-for="entry in virtualEntries"
              :key="entry.key"
              @click="selectVirtualEntry(entry.key)"
              class="text-left rounded-lg flex items-center gap-3 py-2.5 px-3 transition-all text-sm"
              :class="activeVirtualEntry === entry.key
                ? 'bg-[#408aee] text-white font-medium shadow-sm'
                : 'text-gray-600 hover:bg-gray-100'"
            >
              <span class="material-symbols-outlined text-[18px] flex-shrink-0">{{ entry.icon }}</span>
              <span class="truncate">{{ entry.label }}</span>
              <span
                v-if="entry.key === 'all' && total > 0"
                class="ml-auto text-[10px] px-1.5 py-0.5 rounded-full"
                :class="activeVirtualEntry === entry.key ? 'bg-white/20 text-white' : 'bg-gray-100 text-gray-400'"
              >{{ total }}</span>
            </button>
          </nav>

          <!-- 用户自建文件夹 -->
          <template v-if="folderTree.length > 0">
            <h3 class="text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-3 ml-1">我的文件夹</h3>
            <nav class="flex flex-col gap-0.5">
              <button
                v-for="folder in flattenFolderTree(folderTree)"
                :key="'f-' + folder.id"
                @click="selectFolder(folder.id)"
                class="text-left rounded-lg flex items-center gap-3 py-2.5 px-3 transition-all text-sm"
                :class="activeFolderId === folder.id
                  ? 'bg-[#408aee] text-white font-medium shadow-sm'
                  : 'text-gray-600 hover:bg-gray-100'"
                :style="{ paddingLeft: (folder._depth || 0) * 12 + 12 + 'px' }"
              >
                <span class="material-symbols-outlined text-[18px] flex-shrink-0">folder</span>
                <span class="truncate text-xs">{{ folder.folderName }}</span>
              </button>
            </nav>
          </template>

          <!-- 空文件夹提示 -->
          <div v-if="folderTree.length === 0 && !folderTreeLoading" class="text-xs text-gray-400 px-3 py-4">
            暂无文件夹
          </div>
        </div>
      </aside>

      <!-- 右侧：筛选 + 内容 -->
      <div class="flex-1 min-w-0 pl-6">
        <!-- 筛选区域 -->
        <div class="bg-white rounded-xl p-4 mb-4 shadow-sm">
          <div class="flex flex-wrap items-center gap-4">
            <!-- 关键字搜索 -->
            <div class="w-[260px]">
              <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">关键字搜索</label>
              <el-input
                v-model="searchKeyword"
                placeholder="搜索文件名、ID"
                class="file-search-input"
                clearable
                @keyup.enter="handleSearch"
                @clear="handleSearch"
              >
                <template #prefix>
                  <span class="material-symbols-outlined text-gray-400 text-sm">search</span>
                </template>
              </el-input>
            </div>

            <!-- 文件类型 -->
            <div class="flex-1 min-w-[130px]">
              <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">文件类型</label>
              <el-select v-model="fileTypeFilter" placeholder="全部" class="file-select" @change="handleFilterChange">
                <el-option label="全部类型" :value="null" />
                <el-option label="图片" value="IMAGE" />
                <el-option label="视频" value="VIDEO" />
                <el-option label="文档" value="DOCUMENT" />
                <el-option label="压缩包" value="ARCHIVE" />
                <el-option label="其他" value="OTHER" />
              </el-select>
            </div>

            <!-- 业务类型 -->
            <div class="flex-1 min-w-[130px]">
              <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">业务类型</label>
              <el-select v-model="businessTypeFilter" placeholder="全部" class="file-select" @change="handleFilterChange">
                <el-option label="全部业务" :value="null" />
                <el-option label="商品" value="product" />
                <el-option label="SKU" value="sku" />
                <el-option label="订单" value="order" />
                <el-option label="入库" value="inventory_log" />
              </el-select>
            </div>

            <!-- 视图切换 -->
            <div class="ml-auto flex items-end">
              <div class="inline-flex rounded-lg bg-gray-100 p-0.5">
                <button
                  @click="viewMode = 'grid'"
                  class="rounded-md p-2 transition-all"
                  :class="viewMode === 'grid' ? 'bg-white shadow-sm text-[#408aee]' : 'text-gray-400 hover:text-gray-600'"
                  title="网格视图"
                >
                  <span class="material-symbols-outlined text-[18px]">grid_view</span>
                </button>
                <button
                  @click="viewMode = 'list'"
                  class="rounded-md p-2 transition-all"
                  :class="viewMode === 'list' ? 'bg-white shadow-sm text-[#408aee]' : 'text-gray-400 hover:text-gray-600'"
                  title="列表视图"
                >
                  <span class="material-symbols-outlined text-[18px]">list</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 加载状态 -->
        <div v-if="loading" class="flex items-center justify-center py-32">
          <el-icon class="is-loading text-2xl text-[#408aee]"><Loading /></el-icon>
          <span class="ml-2 text-gray-500">加载中...</span>
        </div>

        <!-- 空状态 -->
        <div v-else-if="files.length === 0" class="flex flex-col items-center justify-center py-32 text-gray-400">
          <span class="material-symbols-outlined text-5xl mb-4">folder_open</span>
          <p class="text-sm font-medium">暂无文件</p>
          <p class="text-xs mt-1">当前筛选条件下没有找到文件</p>
        </div>

        <!-- 网格视图 -->
        <template v-else>
          <div v-if="viewMode === 'grid'" class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4 mb-6">
            <div
              v-for="file in files"
              :key="file.id"
              class="group bg-white rounded-xl shadow-sm hover:shadow-md transition-all cursor-pointer overflow-hidden border border-gray-100 hover:border-[#408aee]/30 relative"
              :class="{ 'ring-2 ring-[#408aee]': selectedIds.has(file.id) }"
            >
              <!-- 选择复选框 -->
              <div
                class="absolute top-2 left-2 z-10"
                @click.stop
              >
                <el-checkbox
                  :model-value="selectedIds.has(file.id)"
                  @change="(val: boolean) => toggleSelect(file.id, val)"
                />
              </div>

              <!-- 缩略图区域 -->
              <div
                class="aspect-square bg-gray-100 relative overflow-hidden flex items-center justify-center"
                @click="handlePreview(file)"
              >
                <!-- 图片 -->
                <img
                  v-if="isImageFile(file.fileType)"
                  :src="filePreviewUrl(file.id)"
                  :alt="file.originalName"
                  class="w-full h-full object-cover"
                  loading="lazy"
                  @error="($event.target as HTMLImageElement).style.display = 'none'"
                />
                <!-- 视频占位 -->
                <div v-else-if="isVideoFile(file.fileType)" class="flex flex-col items-center justify-center text-gray-400">
                  <span class="material-symbols-outlined text-4xl">play_circle</span>
                  <span v-if="file.durationSeconds" class="text-xs mt-1">{{ formatDuration(file.durationSeconds) }}</span>
                </div>
                <!-- 文档占位 -->
                <div v-else class="flex flex-col items-center justify-center text-gray-400">
                  <span class="material-symbols-outlined text-4xl">description</span>
                  <span class="text-[10px] mt-1 uppercase">{{ file.fileExt || 'FILE' }}</span>
                </div>

                <!-- 文件类型角标 -->
                <span
                  class="absolute top-2 right-2 text-[10px] font-bold px-1.5 py-0.5 rounded-md"
                  :class="fileTypeBadgeClass(file.fileType)"
                >
                  {{ getFileTypeLabel(file.fileType) }}
                </span>

                <!-- 绑定数量 -->
                <span
                  v-if="file.bindCount > 0"
                  class="absolute top-10 right-2 text-[10px] font-bold px-1.5 py-0.5 rounded-md bg-emerald-500 text-white"
                >
                  已绑定
                </span>
              </div>

              <!-- 信息区域 -->
              <div class="p-3" @click="handlePreview(file)">
                <p class="text-xs font-medium text-gray-900 truncate" :title="file.originalName">
                  {{ file.originalName }}
                </p>
                <div class="flex items-center justify-between mt-1.5">
                  <span class="text-[10px] text-gray-400">{{ formatFileSize(file.fileSize) }}</span>
                  <span class="text-[10px] text-gray-400">{{ formatDateShort(file.createTime) }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 列表视图 -->
          <div v-else class="bg-white rounded-xl shadow-sm mb-6">
            <el-table
              :data="files"
              class="file-table"
              @row-click="handlePreview"
              @selection-change="handleTableSelectionChange"
              ref="listTableRef"
            >
              <el-table-column type="selection" width="48" />

              <el-table-column label="预览" width="64" align="center">
                <template #default="{ row }">
                  <!-- 图片缩略图 -->
                  <img
                    v-if="isImageFile(row.fileType)"
                    :src="filePreviewUrl(row.id)"
                    class="w-10 h-10 rounded-lg object-cover bg-gray-100"
                    loading="lazy"
                    @error="($event.target as HTMLImageElement).style.display = 'none'"
                  />
                  <!-- 视频图标 -->
                  <span v-else-if="isVideoFile(row.fileType)" class="material-symbols-outlined text-gray-400 text-2xl">play_circle</span>
                  <!-- 文档图标 -->
                  <span v-else class="material-symbols-outlined text-gray-400 text-2xl">description</span>
                </template>
              </el-table-column>

              <el-table-column label="文件名" min-width="200">
                <template #default="{ row }">
                  <div class="max-w-[280px]">
                    <p class="text-sm font-medium text-gray-900 truncate" :title="row.originalName">{{ row.originalName }}</p>
                    <p class="text-[10px] text-gray-400 mt-0.5">ID: {{ row.id }}</p>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="类型" width="80">
                <template #default="{ row }">
                  <span
                    class="text-[10px] font-bold px-1.5 py-0.5 rounded-md"
                    :class="fileTypeBadgeClass(row.fileType)"
                  >{{ getFileTypeLabel(row.fileType) }}</span>
                </template>
              </el-table-column>

              <el-table-column label="大小" width="90" align="right">
                <template #default="{ row }">
                  <span class="text-xs text-gray-600">{{ formatFileSize(row.fileSize) }}</span>
                </template>
              </el-table-column>

              <el-table-column label="业务类型" width="90">
                <template #default="{ row }">
                  <span class="text-xs text-gray-500">{{ getBusinessTypeLabel(row.businessType) }}</span>
                </template>
              </el-table-column>

              <el-table-column label="绑定" width="70" align="center">
                <template #default="{ row }">
                  <span
                    v-if="row.bindCount > 0"
                    class="inline-flex items-center justify-center text-[10px] font-bold px-1.5 py-0.5 rounded-md bg-emerald-100 text-emerald-700"
                  >{{ row.bindCount }}</span>
                  <span v-else class="text-xs text-gray-300">-</span>
                </template>
              </el-table-column>

              <el-table-column label="来源" width="70">
                <template #default="{ row }">
                  <span class="text-xs text-gray-500">{{ row.source || '-' }}</span>
                </template>
              </el-table-column>

              <el-table-column label="上传时间" width="120">
                <template #default="{ row }">
                  <span class="text-xs text-gray-500">{{ formatDate(row.createTime) }}</span>
                </template>
              </el-table-column>

              <el-table-column label="状态" width="80">
                <template #default="{ row }">
                  <span :class="statusClass(row)">
                    <span :class="statusDotClass(row)"></span>
                    {{ row.deletedTime ? '已删除' : '正常' }}
                  </span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 分页 -->
          <div class="flex items-center justify-between bg-white rounded-xl shadow-sm px-6 py-4 mb-6">
            <p class="text-xs text-gray-500 font-medium">
              显示第 <span class="text-gray-900">{{ (currentPage - 1) * pageSize + 1 }}</span>-<span class="text-gray-900">{{ Math.min(currentPage * pageSize, total) }}</span> 条，共 <span class="text-gray-900">{{ total }}</span> 条
            </p>
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="pageSize"
              :total="total"
              layout="prev, pager, next"
              background
              @current-change="loadFiles"
            />
          </div>
        </template>
      </div>
    </div>

    <!-- 图片预览弹窗 -->
    <el-dialog
      v-model="showPreview"
      :title="previewFile?.originalName || '文件预览'"
      width="800px"
      :close-on-click-modal="true"
      destroy-on-close
    >
      <div class="flex flex-col items-center">
        <img
          v-if="previewFile && isImageFile(previewFile.fileType)"
          :src="filePreviewUrl(previewFile.id)"
          :alt="previewFile.originalName"
          class="max-w-full max-h-[70vh] object-contain rounded-lg"
        />
        <div v-else-if="previewFile && isVideoFile(previewFile.fileType)" class="w-full flex flex-col items-center">
          <video
            :src="filePreviewUrl(previewFile.id)"
            class="max-w-full max-h-[70vh] rounded-lg bg-black"
            controls
            preload="metadata"
            playsinline
          >
            当前浏览器不支持视频播放。
          </video>
          <a
            v-if="previewFile.accessUrl"
            :href="filePreviewUrl(previewFile.id)"
            target="_blank"
            class="mt-3 text-[#408aee] text-sm hover:underline"
          >打开原文件</a>
        </div>
        <div v-else class="flex flex-col items-center py-16 text-gray-400">
          <span class="material-symbols-outlined text-6xl mb-4">description</span>
          <p class="text-sm">不支持在线预览</p>
        </div>

        <!-- 文件信息 -->
        <div v-if="previewFile" class="w-full mt-6 p-4 bg-gray-50 rounded-lg">
          <div class="grid grid-cols-2 gap-3 text-xs">
            <div>
              <span class="text-gray-400">文件ID：</span>
              <span class="text-gray-700">{{ previewFile.id }}</span>
            </div>
            <div>
              <span class="text-gray-400">文件类型：</span>
              <span class="text-gray-700">{{ getFileTypeLabel(previewFile.fileType) }}</span>
            </div>
            <div>
              <span class="text-gray-400">文件大小：</span>
              <span class="text-gray-700">{{ formatFileSize(previewFile.fileSize) }}</span>
            </div>
            <div>
              <span class="text-gray-400">业务类型：</span>
              <span class="text-gray-700">{{ getBusinessTypeLabel(previewFile.businessType) }}</span>
            </div>
            <div>
              <span class="text-gray-400">绑定数量：</span>
              <span class="text-gray-700">{{ previewFile.bindCount }}</span>
            </div>
            <div>
              <span class="text-gray-400">上传时间：</span>
              <span class="text-gray-700">{{ formatDate(previewFile.createTime) }}</span>
            </div>
            <div v-if="previewFile.imageWidth">
              <span class="text-gray-400">尺寸：</span>
              <span class="text-gray-700">{{ previewFile.imageWidth }} × {{ previewFile.imageHeight }}</span>
            </div>
            <div v-if="previewFile.durationSeconds">
              <span class="text-gray-400">时长：</span>
              <span class="text-gray-700">{{ formatDuration(previewFile.durationSeconds) }}</span>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showPreview = false">关闭</el-button>
        <el-button
          v-if="previewFile?.accessUrl"
          type="primary"
          @click="openPreviewFile"
        >
          <span class="material-symbols-outlined text-sm mr-1">open_in_new</span>
          打开原文件
        </el-button>
      </template>
    </el-dialog>

    <!-- 移动文件弹窗 -->
    <el-dialog
      v-model="showMoveDialog"
      title="移动到文件夹"
      width="480px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="space-y-4">
        <p class="text-sm text-gray-500">
          将已选择的 <span class="font-bold text-gray-700">{{ selectedIds.size }}</span> 个文件移动到：
        </p>
        <el-radio-group v-model="moveTarget" class="flex flex-col gap-2">
          <el-radio :value="null" class="!mr-0">
            <span class="text-sm">未归档（从文件夹中移出）</span>
          </el-radio>
          <el-radio
            v-for="folder in flattenFolderTree(folderTree)"
            :key="'move-' + folder.id"
            :value="folder.id"
            class="!mr-0"
            :style="{ paddingLeft: (folder._depth || 0) * 16 + 'px' }"
          >
            <span class="material-symbols-outlined text-sm mr-1 text-amber-500">folder</span>
            <span class="text-sm">{{ folder.folderName }}</span>
          </el-radio>
        </el-radio-group>
      </div>
      <template #footer>
        <el-button @click="showMoveDialog = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="moving"
          @click="handleMoveFiles"
        >
          {{ moving ? '移动中...' : '确认移动' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 删除确认弹窗 -->
    <el-dialog
      v-model="showDeleteDialog"
      title="删除文件"
      width="560px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="space-y-4">
        <!-- 加载绑定信息 -->
        <div v-if="deleteCheckLoading" class="flex items-center justify-center py-8">
          <el-icon class="is-loading text-xl text-[#408aee]"><Loading /></el-icon>
          <span class="ml-2 text-sm text-gray-500">正在检查文件绑定关系...</span>
        </div>

        <!-- 绑定风险提示 -->
        <template v-else>
          <div class="bg-amber-50 border border-amber-200 rounded-lg p-4">
            <div class="flex items-start gap-2">
              <span class="material-symbols-outlined text-amber-500 text-xl flex-shrink-0">warning</span>
              <div>
                <p class="text-sm font-bold text-amber-800 mb-1">删除确认</p>
                <p class="text-xs text-amber-700">
                  即将删除 <span class="font-bold">{{ selectedIds.size }}</span> 个文件。
                  文件删除采用软删除，可在回收站中查看。
                </p>
              </div>
            </div>
          </div>

          <!-- 已绑定文件列表 -->
          <div v-if="deleteBoundFiles.length > 0" class="bg-red-50 border border-red-200 rounded-lg p-4">
            <div class="flex items-start gap-2 mb-2">
              <span class="material-symbols-outlined text-red-500 text-xl flex-shrink-0">link_off</span>
              <div>
                <p class="text-sm font-bold text-red-800">以下文件存在有效绑定</p>
                <p class="text-xs text-red-600 mt-0.5">后端会拒绝删除已绑定且有业务关联的文件</p>
              </div>
            </div>
            <div class="max-h-[200px] overflow-y-auto space-y-1 mt-2">
              <div
                v-for="b in deleteBoundFiles"
                :key="b.fileId"
                class="text-xs bg-white/50 rounded px-2 py-1 text-red-700 flex items-center justify-between"
              >
                <span class="truncate mr-2">{{ b.fileName || 'ID: ' + b.fileId }}</span>
                <span class="text-[10px] text-red-400 flex-shrink-0">{{ b.bindCount }} 个绑定</span>
              </div>
            </div>
          </div>

          <!-- 未绑定文件数量 -->
          <div v-if="deleteUnboundCount > 0" class="text-xs text-gray-500">
            <span class="text-emerald-600 font-medium">{{ deleteUnboundCount }}</span> 个未绑定文件可被删除
          </div>

          <!-- 全部有绑定提示 -->
          <div v-if="deleteUnboundCount === 0 && deleteBoundFiles.length > 0" class="bg-red-50 border border-red-200 rounded-lg p-3">
            <p class="text-xs text-red-600 font-medium">所有选定文件均存在有效绑定，后端将拒绝删除。请先解绑后再试。</p>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="showDeleteDialog = false">取消</el-button>
        <el-button
          type="danger"
          :disabled="deleting || deleteUnboundCount === 0"
          @click="confirmDeleteFiles"
        >
          {{ deleting ? '删除中...' : '确认删除 (' + deleteUnboundCount + ' 个)' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 文件绑定弹窗 -->
    <FileBindDialog
      v-if="showBindDialog"
      v-model="showBindDialog"
      :file-ids="bindFileIds"
      @success="onBindSuccess"
    />

    <!-- 清理面板 -->
    <FileCleanupPanel
      v-if="showCleanupPanel"
      v-model="showCleanupPanel"
      @go-trash="goToTrash"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  getFilePage,
  getFileFoldersTree,
  batchDeleteFiles,
  batchMoveFiles,
  getFileBindings,
  uploadFile,
  filePreviewUrl,
  formatFileSize,
  isImageFile,
  isVideoFile,
  getFileTypeLabel,
  getBusinessTypeLabel,
  type FileVO,
  type FileFolderVO,
} from '@/api/file'
import { ElMessage } from 'element-plus'
import FileBindDialog from './FileBindDialog.vue'
import FileCleanupPanel from './FileCleanupPanel.vue'

// ========== 虚拟入口 ==========

interface VirtualEntry {
  key: string
  label: string
  icon: string
}

const virtualEntries: VirtualEntry[] = [
  { key: 'all', label: '全部文件', icon: 'inventory_2' },
  { key: 'unbound', label: '未绑定', icon: 'link_off' },
  { key: 'product', label: '商品素材', icon: 'checkroom' },
  { key: 'sku', label: 'SKU 图片', icon: 'style' },
  { key: 'order', label: '订单图片', icon: 'receipt_long' },
  { key: 'inventory_log', label: '入库凭证', icon: 'inventory' },
  { key: 'video', label: '视频', icon: 'videocam' },
  { key: 'trash', label: '回收站', icon: 'delete' },
]

// ========== 状态 ==========

const viewMode = ref<'grid' | 'list'>('grid')
const loading = ref(false)
const files = ref<FileVO[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

// 筛选
const searchKeyword = ref('')
const fileTypeFilter = ref<string | null>(null)
const businessTypeFilter = ref<string | null>(null)

// 当前选中的虚拟入口 / 文件夹
const activeVirtualEntry = ref('all')
const activeFolderId = ref<number | null>(null)

// 文件夹树
const folderTree = ref<FileFolderVO[]>([])
const folderTreeLoading = ref(false)

// 预览
const showPreview = ref(false)
const previewFile = ref<FileVO | null>(null)

// 上传
const uploading = ref(false)
const uploadInputRef = ref<HTMLInputElement | null>(null)

// 批量选择
const selectedIds = ref<Set<number>>(new Set())
const listTableRef = ref<any>(null)

// 移动弹窗
const showMoveDialog = ref(false)
const moveTarget = ref<number | null>(null)
const moving = ref(false)

// 删除流程
const showDeleteDialog = ref(false)
const deleteCheckLoading = ref(false)
const deleting = ref(false)
const deleteBoundFiles = ref<{ fileId: number; fileName: string; bindCount: number }[]>([])
const deleteUnboundCount = ref(0)

// 绑定弹窗
const showBindDialog = ref(false)
const bindFileIds = ref<number[]>([])

// 清理面板
const showCleanupPanel = ref(false)
const MAX_UPLOAD_SIZE_MB = 200
const MAX_UPLOAD_SIZE_BYTES = MAX_UPLOAD_SIZE_MB * 1024 * 1024

// ========== 虚拟入口映射 ==========

interface FilterOverrides {
  folderId?: null
  fileType?: string | null
  businessType?: string | null
  bound?: boolean | null
  purpose?: string | null
  status?: number | null
}

function getVirtualEntryFilters(key: string): FilterOverrides {
  switch (key) {
    case 'all':
      return { folderId: null, status: 1, bound: null, businessType: null, fileType: null, purpose: null }
    case 'unbound':
      return { folderId: null, status: 1, bound: false, businessType: null, fileType: null, purpose: null }
    case 'product':
      return { folderId: null, status: 1, bound: null, businessType: 'product', fileType: null, purpose: null }
    case 'sku':
      return { folderId: null, status: 1, bound: null, businessType: 'sku', fileType: null, purpose: null }
    case 'order':
      return { folderId: null, status: 1, bound: null, businessType: 'order', fileType: null, purpose: null }
    case 'inventory_log':
      return { folderId: null, status: 1, bound: null, businessType: 'inventory_log', fileType: null, purpose: null }
    case 'video':
      return { folderId: null, status: 1, bound: null, businessType: null, fileType: 'VIDEO', purpose: null }
    case 'trash':
      return { folderId: null, status: 0, bound: null, businessType: null, fileType: null, purpose: null }
    default:
      return { folderId: null, status: 1, bound: null, businessType: null, fileType: null, purpose: null }
  }
}

// ========== 文件夹 ==========

function flattenFolderTree(tree: FileFolderVO[], depth: number = 0): (FileFolderVO & { _depth: number })[] {
  const result: (FileFolderVO & { _depth: number })[] = []
  for (const node of tree) {
    result.push({ ...node, _depth: depth })
    if (node.children && node.children.length > 0) {
      result.push(...flattenFolderTree(node.children, depth + 1))
    }
  }
  return result
}

async function loadFolderTree() {
  folderTreeLoading.value = true
  try {
    const res = await getFileFoldersTree()
    folderTree.value = res.data || []
  } catch {
    folderTree.value = []
  } finally {
    folderTreeLoading.value = false
  }
}

function selectVirtualEntry(key: string) {
  activeVirtualEntry.value = key
  activeFolderId.value = null
  searchKeyword.value = ''
  fileTypeFilter.value = null
  businessTypeFilter.value = null
  currentPage.value = 1
  clearSelection()
  loadFiles()
}

function selectFolder(folderId: number) {
  activeFolderId.value = folderId
  activeVirtualEntry.value = ''
  currentPage.value = 1
  clearSelection()
  loadFiles()
}

// ========== 文件列表 ==========

async function loadFiles() {
  loading.value = true
  try {
    const overrides = activeVirtualEntry.value
      ? getVirtualEntryFilters(activeVirtualEntry.value)
      : { folderId: null, status: 1 as (number | null), bound: null as (boolean | null), businessType: null as (string | null), fileType: null as (string | null), purpose: null as (string | null) }

    const params: Record<string, unknown> = {
      current: currentPage.value,
      size: pageSize.value,
    }

    if (searchKeyword.value.trim()) {
      params.keyword = searchKeyword.value.trim()
    }

    if (activeFolderId.value != null) {
      params.folderId = activeFolderId.value
    } else if (overrides.folderId !== undefined) {
      params.folderId = overrides.folderId
    }

    if (fileTypeFilter.value) {
      params.fileType = fileTypeFilter.value
    } else if (overrides.fileType) {
      params.fileType = overrides.fileType
    }

    if (businessTypeFilter.value) {
      params.businessType = businessTypeFilter.value
    } else if (overrides.businessType) {
      params.businessType = overrides.businessType
    }

    if (overrides.bound != null) {
      params.bound = overrides.bound
    }

    if (overrides.status != null) {
      params.status = overrides.status
    }

    const res = await getFilePage(params as any)
    files.value = res.data.records
    total.value = res.data.total
  } catch (error: any) {
    ElMessage.error(error.message || '加载文件列表失败')
    files.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  clearSelection()
  loadFiles()
}

function handleFilterChange() {
  currentPage.value = 1
  clearSelection()
  loadFiles()
}

function handleRefresh() {
  clearSelection()
  loadFiles()
  loadFolderTree()
}

// ========== 上传 ==========

function openUpload() {
  uploadInputRef.value?.click()
}

async function handleUploadInput(event: Event) {
  const input = event.target as HTMLInputElement
  const fileList = input.files
  if (!fileList || fileList.length === 0) return

  uploading.value = true
  let successCount = 0
  let failCount = 0

  for (let i = 0; i < fileList.length; i++) {
    const file = fileList[i]
    if (file.size > MAX_UPLOAD_SIZE_BYTES) {
      failCount++
      ElMessage.error(`${file.name} 超过 ${MAX_UPLOAD_SIZE_MB}MB，暂不支持上传`)
      continue
    }
    try {
      await uploadFile(file, 'temp')
      successCount++
    } catch (error: any) {
      failCount++
      console.error('上传失败:', file.name, error)
    }
  }

  uploading.value = false
  // 清空 input 以便重新选择同一文件
  input.value = ''

  if (successCount > 0) {
    ElMessage.success(`成功上传 ${successCount} 个文件`)
    loadFiles()
  }
  if (failCount > 0) {
    ElMessage.warning(`${failCount} 个文件上传失败`)
  }
}

// ========== 批量选择 ==========

function toggleSelect(fileId: number, checked: boolean) {
  const next = new Set(selectedIds.value)
  if (checked) {
    next.add(fileId)
  } else {
    next.delete(fileId)
  }
  selectedIds.value = next
}

function clearSelection() {
  selectedIds.value = new Set()
  if (listTableRef.value) {
    listTableRef.value.clearSelection()
  }
}

function handleTableSelectionChange(rows: FileVO[]) {
  const next = new Set<number>()
  for (const row of rows) {
    next.add(row.id)
  }
  selectedIds.value = next
}

// ========== 移动文件 ==========

function openMoveDialog() {
  if (selectedIds.value.size === 0) {
    ElMessage.warning('请先选择文件')
    return
  }
  moveTarget.value = null
  showMoveDialog.value = true
}

async function handleMoveFiles() {
  moving.value = true
  try {
    await batchMoveFiles(Array.from(selectedIds.value), moveTarget.value)
    ElMessage.success(`已移动 ${selectedIds.value.size} 个文件`)
    showMoveDialog.value = false
    clearSelection()
    loadFiles()
    loadFolderTree()
  } catch (error: any) {
    ElMessage.error(error.message || '移动失败')
  } finally {
    moving.value = false
  }
}

// ========== 删除文件 ==========

async function handleBatchDelete() {
  if (selectedIds.value.size === 0) {
    ElMessage.warning('请先选择文件')
    return
  }

  showDeleteDialog.value = true
  deleteCheckLoading.value = true
  deleteBoundFiles.value = []
  deleteUnboundCount.value = 0

  const fileIds = Array.from(selectedIds.value)
  const boundList: typeof deleteBoundFiles.value = []

  // 并行查询所有文件的绑定关系
  await Promise.allSettled(
    fileIds.map(async (id) => {
      const file = files.value.find(f => f.id === id)
      try {
        const res = await getFileBindings(id)
        const bindings = res.data || []
        if (bindings.length > 0) {
          boundList.push({
            fileId: id,
            fileName: file?.originalName || '',
            bindCount: bindings.length,
          })
        }
      } catch {
        // 查询绑定失败，保守处理为可能有绑定
        if (file && file.bindCount > 0) {
          boundList.push({
            fileId: id,
            fileName: file.originalName,
            bindCount: file.bindCount,
          })
        }
      }
    })
  )

  deleteBoundFiles.value = boundList
  deleteUnboundCount.value = fileIds.length - boundList.length
  deleteCheckLoading.value = false
}

async function confirmDeleteFiles() {
  const unboundIds = Array.from(selectedIds.value).filter(
    id => !deleteBoundFiles.value.some(b => b.fileId === id)
  )
  if (unboundIds.length === 0) {
    ElMessage.warning('没有可删除的文件（所有选定文件均存在绑定）')
    return
  }

  deleting.value = true
  try {
    await batchDeleteFiles(unboundIds)
    ElMessage.success(`已删除 ${unboundIds.length} 个文件`)
    showDeleteDialog.value = false
    clearSelection()
    loadFiles()
  } catch (error: any) {
    ElMessage.error(error.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

// ========== 绑定文件 ==========

function openBindDialogForSelected() {
  if (selectedIds.value.size === 0) {
    ElMessage.warning('请先选择文件')
    return
  }
  bindFileIds.value = Array.from(selectedIds.value)
  showBindDialog.value = true
}

function onBindSuccess() {
  showBindDialog.value = false
  clearSelection()
  loadFiles()
}

// ========== 清理/回收站 ==========

function goToTrash() {
  showCleanupPanel.value = false
  selectVirtualEntry('trash')
}

// ========== 预览 ==========

function handlePreview(file: FileVO) {
  previewFile.value = file
  showPreview.value = true
}

function openPreviewFile() {
  if (previewFile.value) {
    window.open(filePreviewUrl(previewFile.value.id), '_blank')
  }
}

// ========== 工具函数 ==========

function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return '-'
  try {
    const d = new Date(dateStr)
    return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }) +
      ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  } catch {
    return dateStr
  }
}

function formatDateShort(dateStr: string | null | undefined): string {
  if (!dateStr) return '-'
  try {
    const d = new Date(dateStr)
    return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
  } catch {
    return dateStr
  }
}

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

function fileTypeBadgeClass(fileType: string | null | undefined): string {
  switch (fileType) {
    case 'IMAGE': return 'bg-blue-100 text-blue-700'
    case 'VIDEO': return 'bg-purple-100 text-purple-700'
    case 'DOCUMENT': return 'bg-amber-100 text-amber-700'
    case 'ARCHIVE': return 'bg-gray-100 text-gray-700'
    default: return 'bg-gray-100 text-gray-500'
  }
}

function statusClass(row: FileVO): string {
  return row.status === 0 || row.deletedTime
    ? 'inline-flex items-center gap-1 text-xs text-red-600 font-medium'
    : 'inline-flex items-center gap-1 text-xs text-emerald-600 font-medium'
}

function statusDotClass(row: FileVO): string {
  return row.status === 0 || row.deletedTime
    ? 'inline-block w-1.5 h-1.5 rounded-full bg-red-500'
    : 'inline-block w-1.5 h-1.5 rounded-full bg-emerald-500'
}

// ========== 初始化 ==========

onMounted(() => {
  loadFiles()
  loadFolderTree()
})
</script>

<style scoped>
.file-search-input :deep(.el-input__wrapper) {
  border-radius: 12px;
}
.file-select {
  width: 100%;
}
.file-table :deep(td) {
  cursor: pointer;
}
</style>
