<template>
  <el-container class="shell">
    <el-aside class="sidebar" width="236px">
      <div class="brand">
        <span class="brand-mark"></span>
        <div>
          <strong>PLC Vision</strong>
          <small>缺陷检测管理后台</small>
        </div>
      </div>
      <el-menu :default-active="activeView" class="nav" @select="activeView = String($event)">
        <el-menu-item index="dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>首页统计</span>
        </el-menu-item>
        <el-menu-item index="records">
          <el-icon><Picture /></el-icon>
          <span>检测记录</span>
        </el-menu-item>
        <el-menu-item index="logs">
          <el-icon><Document /></el-icon>
          <span>日志管理</span>
        </el-menu-item>
        <el-menu-item index="status">
          <el-icon><Monitor /></el-icon>
          <span>系统状态</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div>
          <h1>{{ title }}</h1>
          <p>Windows 发送端上传检测图像，macOS 接收端负责管理与统计。</p>
        </div>
        <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
      </el-header>

      <el-main class="workspace">
        <section v-if="activeView === 'dashboard'" class="view">
          <div class="metrics">
            <article class="metric">
              <span>总检测数</span>
              <strong>{{ stats?.totalCount ?? 0 }}</strong>
            </article>
            <article class="metric normal">
              <span>正常数量</span>
              <strong>{{ stats?.normalCount ?? 0 }}</strong>
            </article>
            <article class="metric danger">
              <span>缺陷数量</span>
              <strong>{{ stats?.defectCount ?? 0 }}</strong>
            </article>
            <article class="metric rate">
              <span>缺陷率</span>
              <strong>{{ stats?.defectRate ?? 0 }}%</strong>
            </article>
          </div>
          <div class="chart-grid">
            <div ref="trendChartRef" class="chart"></div>
            <div ref="typeChartRef" class="chart"></div>
          </div>
        </section>

        <section v-if="activeView === 'records'" class="view">
          <el-form class="filters" inline>
            <el-form-item label="设备">
              <el-input v-model="recordFilters.deviceId" placeholder="deviceId" clearable />
            </el-form-item>
            <el-form-item label="结果">
              <el-select v-model="recordFilters.result" clearable placeholder="全部" style="width: 120px">
                <el-option label="正常" value="normal" />
                <el-option label="缺陷" value="defect" />
              </el-select>
            </el-form-item>
            <el-form-item label="缺陷类型">
              <el-input v-model="recordFilters.defectType" placeholder="缺陷类型" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadRecords">查询</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="records" border height="520">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="deviceId" label="设备" min-width="140" />
            <el-table-column label="结果" width="100">
              <template #default="{ row }">
                <el-tag :type="row.result === 'defect' ? 'danger' : 'success'">
                  {{ row.result === "defect" ? "缺陷" : "正常" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="defectType" label="缺陷类型" min-width="160" />
            <el-table-column label="置信度" width="110">
              <template #default="{ row }">{{ percent(row.confidence) }}</template>
            </el-table-column>
            <el-table-column prop="detectTime" label="检测时间" min-width="180" />
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button size="small" :icon="View" @click="previewRecord(row)">预览</el-button>
                <el-button size="small" type="danger" :icon="Delete" @click="removeRecord(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="pager"
            layout="total, prev, pager, next"
            :total="recordTotal"
            :page-size="recordPageSize"
            @current-change="(page: number) => { recordPage = page - 1; loadRecords(); }"
          />
        </section>

        <section v-if="activeView === 'logs'" class="view">
          <el-form class="filters" inline>
            <el-form-item label="等级">
              <el-select v-model="logFilters.level" clearable placeholder="全部" style="width: 120px">
                <el-option label="INFO" value="INFO" />
                <el-option label="WARN" value="WARN" />
                <el-option label="ERROR" value="ERROR" />
              </el-select>
            </el-form-item>
            <el-form-item label="关键字">
              <el-input v-model="logFilters.keyword" placeholder="日志关键字" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="loadLogs">查询</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="logs" border height="560">
            <el-table-column prop="createdAt" label="时间" min-width="180" />
            <el-table-column prop="level" label="等级" width="100" />
            <el-table-column prop="type" label="类型" width="130" />
            <el-table-column prop="deviceId" label="设备" min-width="140" />
            <el-table-column prop="message" label="消息" min-width="360" />
          </el-table>
        </section>

        <section v-if="activeView === 'status'" class="view">
          <div class="status-grid">
            <article class="status-panel">
              <h2>在线设备</h2>
              <el-table :data="devices" border>
                <el-table-column prop="deviceId" label="设备" min-width="160" />
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.online ? 'success' : 'info'">{{ row.online ? "在线" : "离线" }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="hostName" label="主机" min-width="160" />
                <el-table-column prop="lastHeartbeat" label="心跳时间" min-width="180" />
              </el-table>
            </article>
            <article class="status-panel">
              <h2>服务器资源</h2>
              <dl class="kv">
                <dt>CPU 核心</dt>
                <dd>{{ systemStatus?.availableProcessors ?? "-" }}</dd>
                <dt>系统负载</dt>
                <dd>{{ systemStatus?.systemLoadAverage?.toFixed(2) ?? "-" }}</dd>
                <dt>JVM 内存</dt>
                <dd>{{ bytes(systemStatus?.totalMemoryBytes) }} / {{ bytes(systemStatus?.maxMemoryBytes) }}</dd>
                <dt>磁盘剩余</dt>
                <dd>{{ bytes(systemStatus?.diskFreeBytes) }} / {{ bytes(systemStatus?.diskTotalBytes) }}</dd>
              </dl>
            </article>
          </div>
        </section>
      </el-main>
    </el-container>

    <el-dialog v-model="previewVisible" title="图片详情" width="860px">
      <img v-if="selectedRecord" class="preview-image" :src="selectedRecord.imageUrl" :alt="selectedRecord.filename" />
      <el-descriptions v-if="selectedRecord" :column="2" border>
        <el-descriptions-item label="设备">{{ selectedRecord.deviceId }}</el-descriptions-item>
        <el-descriptions-item label="检测时间">{{ selectedRecord.detectTime }}</el-descriptions-item>
        <el-descriptions-item label="结果">{{ selectedRecord.result }}</el-descriptions-item>
        <el-descriptions-item label="缺陷类型">{{ selectedRecord.defectType }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { DataAnalysis, Delete, Document, Monitor, Picture, Refresh, Search, View } from "@element-plus/icons-vue";
import * as echarts from "echarts";
import { ElMessage, ElMessageBox } from "element-plus";
import { deleteRecord, getDevices, getLogs, getRecords, getStatistics, getSystemStatus } from "./api/client";
import type { DetectionRecord, DeviceStatus, LogEntry, Statistics, SystemStatus } from "./types/api";

const activeView = ref("dashboard");
const stats = ref<Statistics | null>(null);
const records = ref<DetectionRecord[]>([]);
const logs = ref<LogEntry[]>([]);
const devices = ref<DeviceStatus[]>([]);
const systemStatus = ref<SystemStatus | null>(null);
const trendChartRef = ref<HTMLElement | null>(null);
const typeChartRef = ref<HTMLElement | null>(null);
const recordTotal = ref(0);
const recordPage = ref(0);
const recordPageSize = ref(12);
const recordFilters = ref({ deviceId: "", result: "", defectType: "" });
const logFilters = ref({ level: "", keyword: "" });
const previewVisible = ref(false);
const selectedRecord = ref<DetectionRecord | null>(null);

const title = computed(() => ({
  dashboard: "首页统计",
  records: "检测记录",
  logs: "日志管理",
  status: "系统状态",
}[activeView.value] || "PLC 缺陷检测"));

onMounted(refreshAll);
watch(activeView, () => nextTick(refreshAll));

async function refreshAll() {
  if (activeView.value === "dashboard") await loadStatistics();
  if (activeView.value === "records") await loadRecords();
  if (activeView.value === "logs") await loadLogs();
  if (activeView.value === "status") await loadStatus();
}

async function loadStatistics() {
  const response = await getStatistics();
  stats.value = response.data;
  await nextTick();
  drawCharts();
}

async function loadRecords() {
  const response = await getRecords({
    page: recordPage.value,
    size: recordPageSize.value,
    deviceId: recordFilters.value.deviceId || undefined,
    result: recordFilters.value.result || undefined,
    defectType: recordFilters.value.defectType || undefined,
  });
  records.value = response.data.content;
  recordTotal.value = response.data.totalElements;
}

async function loadLogs() {
  const response = await getLogs({
    page: 0,
    size: 80,
    level: logFilters.value.level || undefined,
    keyword: logFilters.value.keyword || undefined,
  });
  logs.value = response.data.content;
}

async function loadStatus() {
  const [deviceResponse, systemResponse] = await Promise.all([getDevices(), getSystemStatus()]);
  devices.value = deviceResponse.data;
  systemStatus.value = systemResponse.data;
}

function drawCharts() {
  if (!stats.value || !trendChartRef.value || !typeChartRef.value) return;
  const trendChart = echarts.init(trendChartRef.value);
  trendChart.setOption({
    title: { text: "今日趋势", left: 12, top: 8 },
    tooltip: { trigger: "axis" },
    legend: { right: 16, top: 12 },
    grid: { left: 40, right: 24, top: 58, bottom: 36 },
    xAxis: { type: "category", data: stats.value.todayTrend.map((item) => item.hour) },
    yAxis: { type: "value" },
    series: [
      { name: "总数", type: "line", smooth: true, data: stats.value.todayTrend.map((item) => item.total) },
      { name: "缺陷", type: "line", smooth: true, data: stats.value.todayTrend.map((item) => item.defect) },
    ],
  });
  const typeEntries = Object.entries(stats.value.defectTypeCounts);
  const typeChart = echarts.init(typeChartRef.value);
  typeChart.setOption({
    title: { text: "缺陷类别", left: 12, top: 8 },
    tooltip: { trigger: "item" },
    series: [{
      type: "pie",
      radius: ["44%", "72%"],
      center: ["50%", "56%"],
      data: typeEntries.length ? typeEntries.map(([name, value]) => ({ name, value })) : [{ name: "暂无缺陷", value: 1 }],
    }],
  });
}

function previewRecord(record: DetectionRecord) {
  selectedRecord.value = record;
  previewVisible.value = true;
}

async function removeRecord(id: number) {
  await ElMessageBox.confirm("删除后会同步删除图片文件，确认继续？", "删除记录", { type: "warning" });
  await deleteRecord(id);
  ElMessage.success("删除成功");
  await loadRecords();
}

function percent(value: number) {
  return `${(Number(value || 0) * 100).toFixed(1)}%`;
}

function bytes(value?: number) {
  if (!value) return "-";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let size = value;
  let index = 0;
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index += 1;
  }
  return `${size.toFixed(1)} ${units[index]}`;
}
</script>
