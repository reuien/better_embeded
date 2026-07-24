import axios from "axios";
import type { ApiResponse, DetectionRecord, DeviceStatus, LogEntry, PageResponse, Statistics, SystemStatus } from "../types/api";

export const http = axios.create({
  baseURL: "",
  timeout: 10000,
});

http.interceptors.response.use((response) => response.data);

type Query = Record<string, string | number | undefined>;

export async function getStatistics(): Promise<ApiResponse<Statistics>> {
  return http.get("/api/statistics");
}

export async function getRecords(params: Query): Promise<ApiResponse<PageResponse<DetectionRecord>>> {
  return http.get("/api/detection/list", { params });
}

export async function deleteRecord(id: number): Promise<ApiResponse<void>> {
  return http.delete(`/api/detection-records/${id}`);
}

export async function getLatestRecord(): Promise<ApiResponse<DetectionRecord>> {
  return http.get("/api/detection/latest");
}

export async function getLogs(params: Query): Promise<ApiResponse<PageResponse<LogEntry>>> {
  return http.get("/api/logs", { params });
}

export async function getDevices(): Promise<ApiResponse<DeviceStatus[]>> {
  return http.get("/api/devices");
}

export async function getSystemStatus(): Promise<ApiResponse<SystemStatus>> {
  return http.get("/api/system-status");
}
