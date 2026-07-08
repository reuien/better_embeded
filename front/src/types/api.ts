export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface DetectionRecord {
  id: number;
  filename: string;
  deviceId: string;
  result: "normal" | "defect";
  defectType: string;
  confidence: number;
  imagePath: string;
  imageUrl: string;
  detectTime: string;
  createdAt: string;
}

export interface Statistics {
  totalCount: number;
  normalCount: number;
  defectCount: number;
  defectRate: number;
  todayTotalCount: number;
  todayDefectCount: number;
  todayDefectRate: number;
  defectTypeCounts: Record<string, number>;
  todayTrend: Array<{ hour: string; total: number; defect: number }>;
}

export interface LogEntry {
  id: number;
  level: string;
  type: string;
  message: string;
  deviceId?: string;
  createdAt: string;
}

export interface DeviceStatus {
  deviceId: string;
  status: string;
  online: boolean;
  hostName?: string;
  camera?: string;
  modelName?: string;
  lastHeartbeat?: string;
  updatedAt?: string;
}

export interface SystemStatus {
  availableProcessors: number;
  systemLoadAverage: number;
  freeMemoryBytes: number;
  totalMemoryBytes: number;
  maxMemoryBytes: number;
  diskFreeBytes: number;
  diskTotalBytes: number;
  serverTime: string;
}
