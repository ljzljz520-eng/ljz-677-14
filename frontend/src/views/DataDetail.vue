<template>
  <div class="space-y-6">
    <!-- 页面标题 -->
    <div class="card">
      <div class="flex items-center justify-between">
        <div>
          <div class="flex items-center mb-2">
            <el-button link @click="goBack" class="mr-2">
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
              </svg>
            </el-button>
            <h1 class="text-2xl font-bold text-gray-800">数据详情</h1>
          </div>
          <p class="text-gray-500">批次号：{{ batchNo }}</p>
        </div>
        <el-button type="primary" @click="reportData" :loading="reporting">
          <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
          </svg>
          上报未发送数据
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="grid grid-cols-4 gap-4">
      <div class="card flex items-center">
        <div class="w-12 h-12 bg-blue-100 rounded-xl flex items-center justify-center mr-4">
          <svg class="w-6 h-6 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
          </svg>
        </div>
        <div>
          <p class="text-2xl font-bold text-gray-800">{{ stats.total }}</p>
          <p class="text-gray-500 text-sm">总记录数</p>
        </div>
      </div>
      <div class="card flex items-center">
        <div class="w-12 h-12 bg-yellow-100 rounded-xl flex items-center justify-center mr-4">
          <svg class="w-6 h-6 text-yellow-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <div>
          <p class="text-2xl font-bold text-gray-800">{{ stats.pending }}</p>
          <p class="text-gray-500 text-sm">待上报/待重送</p>
        </div>
      </div>
      <div class="card flex items-center">
        <div class="w-12 h-12 bg-green-100 rounded-xl flex items-center justify-center mr-4">
          <svg class="w-6 h-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <div>
          <p class="text-2xl font-bold text-gray-800">{{ stats.success }}</p>
          <p class="text-gray-500 text-sm">已上报</p>
        </div>
      </div>
      <div class="card flex items-center cursor-pointer" @click="activeTab = 'errors'">
        <div class="w-12 h-12 bg-red-100 rounded-xl flex items-center justify-center mr-4">
          <svg class="w-6 h-6 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </div>
        <div>
          <p class="text-2xl font-bold" :class="stats.failed > 0 ? 'text-red-600' : 'text-gray-800'">{{ stats.failed }}</p>
          <p class="text-gray-500 text-sm">平台异常（点击处理）</p>
        </div>
      </div>
    </div>

    <!-- 页签 -->
    <div class="card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- ========== 异常处理页签 ========== -->
        <el-tab-pane name="errors">
          <template #label>
            <span class="font-medium">国家平台异常
              <el-badge v-if="stats.failed > 0" :value="stats.failed" :max="99999" class="ml-1" type="danger" />
            </span>
          </template>

          <div class="flex items-center justify-between mb-4 flex-wrap gap-2">
            <div class="flex items-center gap-3 flex-wrap">
              <el-select
                v-model="errorCodeFilter"
                placeholder="按错误码筛选"
                clearable
                style="width: 320px"
                @change="onErrorFilterChange"
              >
                <el-option
                  v-for="item in errorCodeOptions"
                  :key="item.errorCode"
                  :label="`${item.errorCode}（${item.count}条）`"
                  :value="item.errorCode"
                >
                  <span class="font-mono">{{ item.errorCode }}</span>
                  <span class="text-gray-400 ml-2">{{ errorCodeText(item.errorCode) }}</span>
                  <span class="float-right text-gray-400">{{ item.count }}</span>
                </el-option>
              </el-select>
              <el-input
                v-model="keywordFilter"
                placeholder="医保编号 / 数据编号 / 行号"
                clearable
                style="width: 240px"
              />
            </div>
            <div class="flex items-center gap-2">
              <el-button @click="exportErrors">导出当前异常</el-button>
              <el-button
                type="primary"
                :disabled="selectedErrorIds.length === 0"
                :loading="resending"
                @click="retrySelected"
              >
                重送选中 ({{ selectedErrorIds.length }})
              </el-button>
              <el-button type="success" :loading="resending" @click="retryAll">
                一键重送全部异常
              </el-button>
            </div>
          </div>

          <el-table
            v-loading="errorLoading"
            :data="pagedErrors"
            stripe
            style="width: 100%"
            @selection-change="onSelectionChange"
            row-key="dataId"
            empty-text="没有平台异常，数据均已上报成功"
          >
            <el-table-column type="selection" width="45" reserve-selection />
            <el-table-column prop="rowNo" label="行号" width="80" align="center">
              <template #default="{ row }">
                <span class="font-mono text-red-600">{{ row.rowNo ?? '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="medicalInsuranceNo" label="医保编号" width="170">
              <template #default="{ row }">
                <span class="font-mono">{{ row.medicalInsuranceNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="dataCode" label="数据编号" width="140" />
            <el-table-column prop="name" label="姓名" width="90" />
            <el-table-column prop="errorCode" label="错误码" width="100" align="center">
              <template #default="{ row }">
                <el-tag type="danger" size="small" class="font-mono">{{ row.errorCode }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="errorDesc" label="错误描述" min-width="180" show-overflow-tooltip />
            <el-table-column prop="suggestion" label="处理建议" min-width="240" show-overflow-tooltip />
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openCorrect(row)">修正</el-button>
                <el-button type="success" link size="small" :loading="resending" @click="retrySingle(row)">
                  重送
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="mt-4 flex justify-end">
            <el-pagination
              v-model:current-page="errorPagination.pageNum"
              v-model:page-size="errorPagination.pageSize"
              :total="filteredErrors.length"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
            />
          </div>
        </el-tab-pane>

        <!-- ========== 全部数据页签 ========== -->
        <el-tab-pane name="data">
          <template #label><span class="font-medium">全部数据</span></template>
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-semibold text-gray-700">数据列表</h2>
            <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width: 160px" @change="fetchData">
              <el-option label="全部" value="" />
              <el-option label="待上报/待重送" :value="0" />
              <el-option label="已上报" :value="1" />
              <el-option label="上报失败" :value="2" />
            </el-select>
          </div>

          <el-table v-loading="loading" :data="dataList" stripe style="width: 100%">
            <el-table-column prop="rowNo" label="行号" width="80" align="center" />
            <el-table-column prop="medicalInsuranceNo" label="医保编号" width="160">
              <template #default="{ row }">
                <span class="font-mono">{{ row.medicalInsuranceNo || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="dataCode" label="数据编号" width="140" />
            <el-table-column prop="name" label="姓名" width="100" />
            <el-table-column prop="idCard" label="身份证号" width="180">
              <template #default="{ row }">{{ maskIdCard(row.idCard) }}</template>
            </el-table-column>
            <el-table-column prop="phone" label="手机号" width="130">
              <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
            </el-table-column>
            <el-table-column prop="amount" label="金额" width="120" align="right">
              <template #default="{ row }">
                <span class="font-medium">{{ formatAmount(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip />
            <el-table-column prop="reportStatus" label="上报状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="getReportStatusType(row.reportStatus)" size="small">
                  {{ getReportStatusText(row.reportStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reportErrorCode" label="错误码" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.reportErrorCode" type="danger" size="small" class="font-mono">
                  {{ row.reportErrorCode }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="mt-4 flex justify-end">
            <el-pagination
              v-model:current-page="pagination.pageNum"
              v-model:page-size="pagination.pageSize"
              :total="pagination.total"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 修正数据弹窗 -->
    <el-dialog v-model="correctDialogVisible" title="修正异常行数据" width="560px" :close-on-click-modal="false">
      <div class="bg-red-50 rounded-lg p-3 mb-4 text-sm">
        <p><span class="font-mono text-red-600">{{ correctingRow?.errorCode }}</span>
          <span class="text-gray-700 ml-2">{{ correctingRow?.errorDesc }}</span></p>
        <p class="text-gray-500 mt-1">处理建议：{{ correctingRow?.suggestion }}</p>
        <p class="text-gray-400 mt-1">原始位置：第 {{ correctingRow?.rowNo }} 行，医保编号 {{ correctingRow?.medicalInsuranceNo || '-' }}</p>
      </div>
      <el-form :model="correctForm" label-width="90px">
        <el-form-item label="姓名" required>
          <el-input v-model="correctForm.name" maxlength="50" />
        </el-form-item>
        <el-form-item label="医保编号">
          <el-input v-model="correctForm.medicalInsuranceNo" placeholder="请输入国家平台登记的医保编号" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="correctForm.idCard" maxlength="18" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="correctForm.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="correctForm.amount" :min="0" :precision="2" :step="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="correctForm.address" maxlength="200" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="correctForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex justify-end gap-2">
          <el-button @click="correctDialogVisible = false">取消</el-button>
          <el-button @click="submitCorrect(false)" :loading="correcting">仅保存修正</el-button>
          <el-button type="primary" :loading="correcting" @click="submitCorrect(true)">保存并重送该行</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 上报结果弹窗 -->
    <el-dialog v-model="reportDialogVisible" title="数据上报结果" width="640px" :close-on-click-modal="false">
      <div v-if="reportResult">
        <div class="grid grid-cols-3 gap-4 mb-6">
          <div class="bg-blue-50 rounded-lg p-4 text-center">
            <p class="text-2xl font-bold text-blue-600">{{ reportResult.totalCount }}</p>
            <p class="text-gray-500 text-sm">本次发送</p>
          </div>
          <div class="bg-green-50 rounded-lg p-4 text-center">
            <p class="text-2xl font-bold text-green-600">{{ reportResult.successCount }}</p>
            <p class="text-gray-500 text-sm">成功</p>
          </div>
          <div class="bg-red-50 rounded-lg p-4 text-center">
            <p class="text-2xl font-bold text-red-600">{{ reportResult.failCount }}</p>
            <p class="text-gray-500 text-sm">异常</p>
          </div>
        </div>
        <p class="text-gray-600 text-sm mb-3">{{ reportResult.message }}</p>

        <div v-if="reportResult.errorList && reportResult.errorList.length > 0">
          <h4 class="font-medium text-gray-700 mb-3">本次异常明细（可在“国家平台异常”页签按错误码筛选处理）</h4>
          <el-table :data="reportResult.errorList" stripe max-height="280" size="small">
            <el-table-column prop="rowNo" label="行号" width="70" align="center" />
            <el-table-column prop="medicalInsuranceNo" label="医保编号" width="150" />
            <el-table-column prop="errorCode" label="错误码" width="90" align="center">
              <template #default="{ row }">
                <el-tag type="danger" size="small">{{ row.errorCode }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="errorDesc" label="错误描述" min-width="150" show-overflow-tooltip />
            <el-table-column prop="suggestion" label="处理建议" min-width="180" show-overflow-tooltip />
          </el-table>
        </div>
      </div>

      <template #footer>
        <div class="flex justify-end gap-2">
          <el-button @click="reportDialogVisible = false">关闭</el-button>
          <el-button
            v-if="reportResult?.failCount > 0"
            type="primary"
            @click="goHandleErrors"
          >
            去处理异常
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { excelApi } from '@/api'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const batchNo = computed(() => route.params.batchNo)
const activeTab = ref('errors')

// ---------- 全部数据 ----------
const loading = ref(false)
const dataList = ref([])
const statusFilter = ref('')
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

const stats = reactive({ total: 0, pending: 0, success: 0, failed: 0 })

// ---------- 国家平台异常 ----------
const errorLoading = ref(false)
const errorList = ref([])
const errorCodeOptions = ref([])
const errorCodeDict = ref([])
const errorCodeFilter = ref('')
const keywordFilter = ref('')
const selectedErrorIds = ref([])
const errorPagination = reactive({ pageNum: 1, pageSize: 10 })

// ---------- 上报 / 修正 ----------
const reporting = ref(false)
const resending = ref(false)
const correcting = ref(false)
const reportDialogVisible = ref(false)
const reportResult = ref(null)
const correctDialogVisible = ref(false)
const correctingRow = ref(null)
const correctForm = reactive({
  name: '', medicalInsuranceNo: '', idCard: '', phone: '',
  amount: 0, address: '', remark: ''
})

const getReportStatusType = (status) => ({ 0: 'info', 1: 'success', 2: 'danger' }[status] || 'info')
const getReportStatusText = (status) => ({ 0: '待上报', 1: '已上报', 2: '上报失败' }[status] || '未知')

const errorCodeText = (code) => errorCodeDict.value.find(e => e.code === code)?.desc || code

const maskIdCard = (v) => v ? v.replace(/^(.{6})(.*)(.{4})$/, '$1********$3') : '-'
const maskPhone = (v) => v ? v.replace(/^(.{3})(.*)(.{4})$/, '$1****$3') : '-'
const formatAmount = (v) => (v === null || v === undefined)
  ? '-'
  : '¥' + Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

// ---------- 异常筛选（前端关键字 + 后端错误码） ----------
const filteredErrors = computed(() => {
  const kw = keywordFilter.value.trim().toLowerCase()
  if (!kw) return errorList.value
  return errorList.value.filter(e =>
    (e.medicalInsuranceNo || '').toLowerCase().includes(kw) ||
    (e.dataCode || '').toLowerCase().includes(kw) ||
    String(e.rowNo || '') === kw
  )
})

const pagedErrors = computed(() => {
  const start = (errorPagination.pageNum - 1) * errorPagination.pageSize
  return filteredErrors.value.slice(start, start + errorPagination.pageSize)
})

const onSelectionChange = (rows) => {
  selectedErrorIds.value = rows.map(r => r.dataId)
}

const onErrorFilterChange = () => {
  errorPagination.pageNum = 1
  fetchErrors()
}

// ---------- 数据获取 ----------
const fetchStats = async () => {
  try {
    const res = await excelApi.getBatchStats(batchNo.value)
    Object.assign(stats, res.data)
  } catch (e) { /* 拦截器已提示 */ }
}

const fetchData = async () => {
  loading.value = true
  try {
    const params = { pageNum: pagination.pageNum, pageSize: pagination.pageSize }
    if (statusFilter.value !== '') params.reportStatus = statusFilter.value
    const res = await excelApi.getDataByBatch(batchNo.value, params)
    dataList.value = res.data.records || []
    pagination.total = res.data.total || 0
  } finally {
    loading.value = false
  }
}

const fetchErrorCodes = async () => {
  try {
    const [statsRes, dictRes] = await Promise.all([
      excelApi.getErrorCodeStats(batchNo.value),
      excelApi.getErrorCodeDict()
    ])
    errorCodeOptions.value = (statsRes.data || []).map(r => ({
      errorCode: r.errorCode,
      count: Number(r.count)
    }))
    errorCodeDict.value = dictRes.data || []
  } catch (e) { /* 拦截器已提示 */ }
}

const fetchErrors = async () => {
  errorLoading.value = true
  try {
    const res = await excelApi.getReportErrors(batchNo.value, {
      errorCode: errorCodeFilter.value || undefined,
      pageNum: 1,
      pageSize: 100000
    })
    errorList.value = res.data.records || []
  } finally {
    errorLoading.value = false
  }
}

const refreshAll = async () => {
  await fetchStats()
  await fetchErrorCodes()
  if (activeTab.value === 'errors') {
    await fetchErrors()
  } else {
    await fetchData()
  }
}

const onTabChange = (name) => {
  if (name === 'errors') fetchErrors()
  else fetchData()
}

const handleSizeChange = (size) => { pagination.pageSize = size; fetchData() }
const handleCurrentChange = (page) => { pagination.pageNum = page; fetchData() }
const goBack = () => router.push('/records')

// ---------- 上报 ----------
const showReportResult = (res) => {
  reportResult.value = res.data
  reportDialogVisible.value = true
  if (res.data.failCount === 0) ElMessage.success('上报成功')
  else ElMessage.warning(`上报完成，${res.data.failCount} 条异常，请到“国家平台异常”页签处理`)
}

const reportData = async () => {
  try {
    await ElMessageBox.confirm(
      '将本批次所有尚未成功的数据上报到国家平台，已上报成功的数据不会重复发送。是否继续？',
      '确认上报', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    reporting.value = true
    ElMessage.info('正在上报数据，请稍候...')
    const res = await excelApi.reportData(batchNo.value)
    showReportResult(res)
    await refreshAll()
  } catch (e) { /* 取消或拦截器处理 */ } finally {
    reporting.value = false
  }
}

// 只重送异常行：后端只会处理状态为“上报失败”的行
const doRetry = async (ids) => {
  resending.value = true
  try {
    const res = await excelApi.retryRows(batchNo.value, ids)
    showReportResult(res)
    await refreshAll()
  } finally {
    resending.value = false
  }
}

const retrySingle = (row) => doRetry([row.dataId])

const retrySelected = () => {
  if (selectedErrorIds.value.length === 0) return
  ElMessageBox.confirm(
    `确定只重送选中的 ${selectedErrorIds.value.length} 条异常行吗？其他数据不受影响。`,
    '重送确认', { type: 'warning', confirmButtonText: '重送', cancelButtonText: '取消' }
  ).then(() => doRetry(selectedErrorIds.value)).catch(() => {})
}

const retryAll = () => {
  const ids = filteredErrors.value.map(e => e.dataId)
  if (ids.length === 0) {
    ElMessage.info('当前筛选下没有需要重送的异常行')
    return
  }
  ElMessageBox.confirm(
    `将重送当前异常列表中的 ${ids.length} 条行（已上报成功的数据不会重复发送）。是否继续？`,
    '一键重送', { type: 'warning', confirmButtonText: '重送', cancelButtonText: '取消' }
  ).then(() => doRetry(ids)).catch(() => {})
}

const goHandleErrors = () => {
  reportDialogVisible.value = false
  activeTab.value = 'errors'
  fetchErrors()
}

// ---------- 修正 ----------
const openCorrect = (row) => {
  correctingRow.value = row
  Object.assign(correctForm, {
    name: row.name || '',
    medicalInsuranceNo: row.medicalInsuranceNo || '',
    idCard: '', phone: '', amount: 0, address: '', remark: ''
  })
  // 拉取该行完整业务数据回填表单
  excelApi.getRow(row.dataId)
    .then(res => {
      const full = res.data
      if (full) {
        Object.assign(correctForm, {
          name: full.name || '',
          medicalInsuranceNo: full.medicalInsuranceNo || '',
          idCard: full.idCard || '',
          phone: full.phone || '',
          amount: full.amount !== null && full.amount !== undefined ? Number(full.amount) : 0,
          address: full.address || '',
          remark: full.remark || ''
        })
      }
    }).catch(() => {})
  correctDialogVisible.value = true
}

const submitCorrect = async (andRetry) => {
  if (!correctForm.name?.trim()) {
    ElMessage.warning('姓名不能为空')
    return
  }
  correcting.value = true
  try {
    const res = await excelApi.correctData(correctingRow.value.dataId, { ...correctForm })
    ElMessage.success(andRetry ? '修正成功，正在重送该行...' : '修正成功，该行已加入待重送列表')
    correctDialogVisible.value = false
    await refreshAll()
    if (andRetry && res.data?.reportStatus === 0) {
      await doRetry([correctingRow.value.dataId])
    }
  } finally {
    correcting.value = false
  }
}

// ---------- 导出 ----------
const exportErrors = () => {
  const url = excelApi.exportErrors(batchNo.value, errorCodeFilter.value)
  window.open(`${url}${url.includes('?') ? '&' : '?'}token=${userStore.token}`, '_blank')
}

onMounted(() => {
  refreshAll()
})
</script>
