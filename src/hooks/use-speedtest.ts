"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export type TestPhase = "idle" | "ping" | "download" | "upload" | "done" | "error";

export interface SpeedTestState {
  phase: TestPhase;
  /** تأخیر نهایی (میانگین بهترین‌ها) */
  pingMs: number | null;
  jitterMs: number | null;
  downloadMbps: number | null;
  uploadMbps: number | null;
  /** مقدار زندهٔ فاز جاری برای گیج */
  liveMbps: number;
  /** پیشرفت کلی 0..100 */
  progress: number;
  error: string | null;
  startedAt: number | null;
}

const PING_ROUNDS = 8;
const DOWNLOAD_BYTES = 12_582_912; // 12MB
const UPLOAD_BYTES = 4_194_304; // 4MB

const initial: SpeedTestState = {
  phase: "idle",
  pingMs: null,
  jitterMs: null,
  downloadMbps: null,
  uploadMbps: null,
  liveMbps: 0,
  progress: 0,
  error: null,
  startedAt: null,
};

/** موتور تست سرعت سمت کلاینت: پینگ → دانلود جریانی → آپلود */
export function useSpeedTest() {
  const [state, setState] = useState<SpeedTestState>(initial);
  const abortRef = useRef<AbortController | null>(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      abortRef.current?.abort();
    };
  }, []);

  const run = useCallback(async () => {
    abortRef.current?.abort();
    const ac = new AbortController();
    abortRef.current = ac;
    const signal = ac.signal;

    if (!mounted.current) return;
    setState({ ...initial, phase: "ping", startedAt: Date.now() });

    try {
      /* ---------- فاز ۱: پینگ و جیتر ---------- */
      const samples: number[] = [];
      for (let i = 0; i < PING_ROUNDS; i++) {
        if (signal.aborted) return;
        const t0 = performance.now();
        await fetch("/api/ping", {
          cache: "no-store",
          signal,
        });
        const dt = performance.now() - t0;
        samples.push(dt);
        if (mounted.current) {
          setState((s) => ({
            ...s,
            liveMbps: dt, // موقتاً تأخیر نمایش داده می‌شود
            progress: ((i + 1) / PING_ROUNDS) * 15,
          }));
        }
        await new Promise((r) => setTimeout(r, 120));
      }
      const best = [...samples].sort((a, b) => a - b).slice(0, 4);
      const avgPing = best.reduce((a, b) => a + b, 0) / best.length;
      let jitterSum = 0;
      for (let i = 1; i < samples.length; i++) {
        jitterSum += Math.abs(samples[i] - samples[i - 1]);
      }
      const jitter = jitterSum / (samples.length - 1);
      if (!mounted.current) return;
      setState((s) => ({ ...s, pingMs: avgPing, jitterMs: jitter, phase: "download" }));

      /* ---------- فاز ۲: دانلود جریانی ---------- */
      const dlStart = performance.now();
      let received = 0;
      const res = await fetch(
        `/api/speedtest/download?bytes=${DOWNLOAD_BYTES}&t=${Date.now()}`,
        { cache: "no-store", signal }
      );
      if (!res.ok || !res.body) throw new Error("خطا در شروع تست دانلود");
      const reader = res.body.getReader();
      let lastTick = dlStart;
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        if (signal.aborted) return;
        received += value?.byteLength ?? 0;
        const now = performance.now();
        if (now - lastTick > 100 && mounted.current) {
          const secs = (now - dlStart) / 1000;
          setState((s) => ({
            ...s,
            liveMbps: (received * 8) / secs / 1_000_000,
            progress: 15 + Math.min(60, ((now - dlStart) / 9000) * 60),
          }));
          lastTick = now;
        }
        // سقف ۱۵ ثانیه برای فاز دانلود
        if (performance.now() - dlStart > 15_000) {
          await reader.cancel();
          break;
        }
      }
      const dlSecs = (performance.now() - dlStart) / 1000;
      const downloadMbps = (received * 8) / dlSecs / 1_000_000;
      if (!mounted.current) return;
      setState((s) => ({ ...s, downloadMbps, phase: "upload", liveMbps: 0 }));

      /* ---------- فاز ۳: آپلود ---------- */
      const upBlob = new Blob([new Uint8Array(UPLOAD_BYTES)], {
        type: "application/octet-stream",
      });
      const upStart = performance.now();
      await fetch(`/api/speedtest/upload?t=${Date.now()}`, {
        method: "POST",
        body: upBlob,
        cache: "no-store",
        signal,
        headers: { "Content-Type": "application/octet-stream" },
      });
      const upSecs = (performance.now() - upStart) / 1000;
      const uploadMbps = (UPLOAD_BYTES * 8) / upSecs / 1_000_000;
      if (!mounted.current) return;
      setState((s) => ({
        ...s,
        uploadMbps,
        phase: "done",
        progress: 100,
        liveMbps: 0,
      }));
    } catch (e) {
      if (!mounted.current || signal.aborted) return;
      const msg =
        e instanceof DOMException && e.name === "AbortError"
          ? "تست لغو شد"
          : "خطا در تست سرعت — اتصال شبکه را بررسی کنید";
      setState((s) => ({ ...s, phase: "error", error: msg, liveMbps: 0 }));
    }
  }, []);

  const cancel = useCallback(() => {
    abortRef.current?.abort();
    if (mounted.current) setState(initial);
  }, []);

  return { state, run, cancel };
}
