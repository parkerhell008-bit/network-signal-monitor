"use client";

import { useSyncExternalStore } from "react";
import { computeQualityScore } from "@/lib/persian";

/** اطلاعات اتصال از Network Information API + وضعیت آنلاین */
export interface NetworkState {
  online: boolean;
  /** نوع اتصال: wifi / cellular / ethernet / none */
  type: string | undefined;
  /** کیفیت تخمینی: slow-2g / 2g / 3g / 4g */
  effectiveType: string | undefined;
  /** پهنای باند تخمینی مگابیت‌برثانیه */
  downlinkMbps: number | undefined;
  /** تأخیر تخمینی میلی‌ثانیه */
  rttMs: number | undefined;
  /** حالت صرفه‌جویی داده فعال است؟ */
  saveData: boolean | undefined;
  /** امتیاز کیفیت 0..100 */
  qualityScore: number;
  /** پشتیبانی مرورگر از Network Information API */
  supported: boolean;
}

type ConnectionLike = {
  type?: string;
  effectiveType?: string;
  downlink?: number;
  rtt?: number;
  saveData?: boolean;
  addEventListener?: (t: string, cb: () => void) => void;
  removeEventListener?: (t: string, cb: () => void) => void;
};

function readConnection(): NetworkState {
  const c = (navigator as Navigator & { connection?: ConnectionLike }).connection;
  const online = navigator.onLine;
  const base: NetworkState = {
    online,
    type: c?.type,
    effectiveType: c?.effectiveType,
    downlinkMbps: c?.downlink,
    rttMs: c?.rtt,
    saveData: c?.saveData,
    qualityScore: online
      ? computeQualityScore(c?.downlink, c?.rtt, c?.effectiveType)
      : 0,
    supported: Boolean(c),
  };
  return base;
}

/* --- store خارجی برای useSyncExternalStore --- */

const SERVER_SNAPSHOT: NetworkState = {
  online: true,
  type: undefined,
  effectiveType: undefined,
  downlinkMbps: undefined,
  rttMs: undefined,
  saveData: undefined,
  qualityScore: 30,
  supported: false,
};

let cached: NetworkState | null = null;

function getSnapshot(): NetworkState {
  const next = readConnection();
  if (
    cached &&
    cached.online === next.online &&
    cached.type === next.type &&
    cached.effectiveType === next.effectiveType &&
    cached.downlinkMbps === next.downlinkMbps &&
    cached.rttMs === next.rttMs &&
    cached.saveData === next.saveData &&
    cached.qualityScore === next.qualityScore
  ) {
    return cached;
  }
  cached = next;
  return cached;
}

function subscribe(onChange: () => void): () => void {
  const c = (navigator as Navigator & { connection?: ConnectionLike }).connection;
  window.addEventListener("online", onChange);
  window.addEventListener("offline", onChange);
  c?.addEventListener?.("change", onChange);
  // پایش دوره‌ای تغییر آنتن/شبکه
  const interval = window.setInterval(onChange, 5000);
  return () => {
    window.removeEventListener("online", onChange);
    window.removeEventListener("offline", onChange);
    c?.removeEventListener?.("change", onChange);
    window.clearInterval(interval);
  };
}

/** هوک وضعیت شبکه — به‌روزرسانی خودکار با رویدادها و هر ۵ ثانیه */
export function useNetworkInfo(): NetworkState {
  return useSyncExternalStore(subscribe, getSnapshot, () => SERVER_SNAPSHOT);
}
