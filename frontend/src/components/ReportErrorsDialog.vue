<template>
  <el-dialog
    v-model="visible"
    title="上报异常明细"
    width="1000px"
    :close-on-click-modal="false"
    top="5vh"
  >
    <div v-if="batchNo" class="space-y-4">
      <!-- 错误码统计筛选区 -->
      <div class="bg-gray-50 rounded-lg p-4">
        <div class="flex items-center justify-between mb-3">
          <h4 class="font-medium text-gray-700">按错误码筛选</h4>
          <span class="text-sm text-gray-500">
            共 {{ totalAll }} 条待处理异常<template v-if="errorCodeFilter">，当前筛选 {{ total }} 条</template>
          </span>
        </div>
        <div class="flex flex-wrap gap-2">
          <el-check-tag
            :checked="errorCodeFilter === ''"
            @change="selectErrorCode('')"
            class="cursor-pointer"
          >
            全部（{{ totalAll }}）
          </el-check-tag>
          <el-check-tag
            v-for="stat in codeStats"
            :key="stat.errorCode"
            :checked="errorCodeFilter === stat.errorCode"
            @change="selectErrorCode(stat.errorCode)"
            class="cursor-pointer"
          >
            {{ stat.errorCode }} {{ stat.errorMsg }}（{{ stat.count }}）
          </el-check-tag>
        </div>
        <!-- 当前选中错误码的处理建议 -->
        <div v-if="currentStat" class="mt-3 p-3 bg-blue-50 rounded-lg flex items-start">
          <svg class="w-4 h-4 text-blue-500 mt-0.5 mr-2 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div class="text-sm">
            <span class="font-medium text-blue-700">{{ currentStat.errorCode }} 处理建议：</span>
            <span class="text-blue-600">{{ currentStat.suggestion }}</span>
          </div>
        </div>
      </div>

      <!-- 异常列表 -->
      <el-table v-loading="loading" :data="errorList" stripe max-height="380" size="small">
        <el-table-column prop="rowIndex" label="行号" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">第{{ row.rowIndex ?? '-' }}行</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dataCode" label="医保编号" width="130">
          <template #default="{ row }">
            <span class="font-mono text-sm">{{ row.dataCode }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column prop="errorCode" label="错误码" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="isServiceError(row.errorCode) ? 'warning' : 'danger'">
              {{ row.errorCode || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="错误描述" min-width="160" show-overflow-tooltip />
        <el-table-column prop="suggestion" label="处理建议" min-width="220" show-overflow-tooltip />
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openCorrect(row)">修正</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无待处理异常" :image-size="60" />
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="flex justify-end">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          small
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <template #footer>
      <div class="flex justify-between">
        <el-button @click="exportErrors">
          <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
          </svg>
          导出异常数据
        </el-button>
        <div class="flex space-x-3">
          <el-button @click="visible = false">关闭</el-button>
          <el-button
            v-if="totalAll > 0"
            type="primary"
            :loading="retrying"
            @click="retryReport"
          >
            重送异常行（{{ totalAll }}条）
          </el-button>
        </div>
      </div>
    </template>

    <!-- 数据修正弹窗 -->
    <el-dialog
      v-model="correctDialogVisible"
      title="修正异常数据"
      width="560px"
      append-to-body
      :close-on-click-modal="false"
    >
      <div v-if="correctingRow" class="mb-4 p-3 bg-red-50 rounded-lg text-sm">
        <p class="text-red-700">
          <span class="font-medium">{{ correctingRow.errorCode }}</span>：{{ correctingRow.errorMsg }}
        </p>
        <p class="text-red-500 mt-1">建议：{{ correctingRow.suggestion }}</p>
      </div>
      <el-form ref="correctFormRef" :model="correctForm" :rules="correctRules" label-width="90px">
        <el-form-item label="医保编号">
          <el-input :model-value="correctingRow?.dataCode" disabled />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="correctForm.name" maxlength="50" />
        </el-form-item>
        <el-form-item label="身份证号" prop="idCard">
          <el-input v-model="correctForm.idCard" maxlength="18" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="correctForm.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="correctForm.amount" :min="0" :precision="2" class="w-full" />
        </el-form-item>
        <el-form-item label="地址" prop="address">
          <el-input v-model="correctForm.address" maxlength="200" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="correctForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="correctDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="correcting" @click="submitCorrect">保存修正</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { excelApi } from '@/api'
import { useUserStore } from '@/stores/user'

const emit = defineEmits(['refreshed'])
const userStore = useUserStore()

const visible = ref(false)
const batchNo = ref('')
const loading = ref(false)
const errorList = ref([])
const codeStats = ref([])
const total = ref(0)
const errorCodeFilter = ref('')

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// 全部异常数（不受筛选影响）
const totalAll = computed(() => codeStats.value.reduce((sum, s) => sum + (s.count || 0), 0))

// 当前选中错误码的统计信息
const currentStat = computed(() =>
  codeStats.value.find(s => s.errorCode === errorCodeFilter.value)
)

// 平台服务类错误（2开头）无需修正数据，直接重试即可
const isServiceError = (code) => code && code.startsWith('E2')

const fetchErrors = async () => {
  if (!batchNo.value) return
  loading.value = true
  try {
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    if (errorCodeFilter.value) {
      params.errorCode = errorCodeFilter.value
    }
    const res = await excelApi.getReportErrors(batchNo.value, params)
    errorList.value = res.data.list || []
    pagination.total = res.data.total || 0
    total.value = res.data.total || 0
    codeStats.value = res.data.codeStats || []
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

const selectErrorCode = (code) => {
  errorCodeFilter.value = code
  pagination.pageNum = 1
  fetchErrors()
}

const handleSizeChange = (size) => {
  pagination.pageSize = size
  pagination.pageNum = 1
  fetchErrors()
}

const handleCurrentChange = (page) => {
  pagination.pageNum = page
  fetchErrors()
}

// 打开弹窗
const open = (batch) => {
  batchNo.value = batch
  errorCodeFilter.value = ''
  pagination.pageNum = 1
  visible.value = true
  fetchErrors()
}

// 数据修正
const correctDialogVisible = ref(false)
const correcting = ref(false)
const correctingRow = ref(null)
const correctFormRef = ref(null)
const correctForm = reactive({
  name: '',
  idCard: '',
  phone: '',
  amount: null,
  address: '',
  remark: ''
})

const correctRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  idCard: [
    {
      pattern: /^\d{17}[\dXx]$/,
      message: '身份证号应为18位',
      trigger: 'blur'
    }
  ],
  phone: [
    {
      pattern: /^1\d{10}$/,
      message: '手机号应为11位有效号码',
      trigger: 'blur'
    }
  ]
}

const openCorrect = (row) => {
  correctingRow.value = row
  // 异常明细已携带完整业务字段，直接回显
  correctForm.name = row.name || ''
  correctForm.idCard = row.idCard || ''
  correctForm.phone = row.phone || ''
  correctForm.amount = row.amount ?? null
  correctForm.address = row.address || ''
  correctForm.remark = row.remark || ''
  correctDialogVisible.value = true
}

const submitCorrect = async () => {
  if (!correctFormRef.value) return
  await correctFormRef.value.validate(async (valid) => {
    if (!valid) return
    correcting.value = true
    try {
      await excelApi.correctData(correctingRow.value.id, { ...correctForm })
      ElMessage.success('修正成功，可点击"重送异常行"重新上报')
      correctDialogVisible.value = false
      fetchErrors()
      emit('refreshed')
    } catch (error) {
      // 错误已在拦截器中处理
    } finally {
      correcting.value = false
    }
  })
}

// 重送异常行（仅发送失败的异常行）
const retrying = ref(false)
const retryReport = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要重送 ${totalAll.value} 条异常数据吗？已上报成功的数据不会重复发送。`,
      '确认重送',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
    retrying.value = true
    const res = await excelApi.retryReport(batchNo.value)
    if (res.data.failCount === 0) {
      ElMessage.success('异常数据已全部上报成功')
    } else {
      ElMessage.warning(`重送完成，仍有 ${res.data.failCount} 条失败，请根据处理建议修正后重试`)
    }
    fetchErrors()
    emit('refreshed')
  } catch (error) {
    if (error !== 'cancel') {
      // 错误已在拦截器中处理
    }
  } finally {
    retrying.value = false
  }
}

// 导出异常数据
const exportErrors = () => {
  const token = userStore.token
  const url = excelApi.exportErrors(batchNo.value)
  window.open(`${url}?token=${token}`, '_blank')
}

defineExpose({ open })
</script>
