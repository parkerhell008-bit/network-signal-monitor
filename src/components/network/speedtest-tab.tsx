"use client";

import { useCallback, useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { StatCard } from "@/components/network/stat-card";
import { HistoryChart } from "@/components/network/history-chart";
import { type useSpeedTest, type SpeedTestState } from "@/hooks/use-speedtest";
import { useNetworkInfo } from "@/hooks/use-network-info";
import { faNum, faDateTime, effectiveTypeLabel, connectionLabel, toFa } from "@/lib/persian";
import {
  ArrowDown,
  ArrowUp,
  Timer,
  Waves,
  Play,
  Square,
  History,
  Trophy,
  TrendingUp,
} from "lucide-react";

interface HistoryItem {
  id: string;
  downloadMbps: number;
  uploadMbps: number;
  pingMs: number;
  jitterMs: number;
  connectionType: string | null;
  effectiveType: string | null;
  createdAt: string;
}

interface SpeedTestTabProps {
  /** state تست در سطح پوستهٔ اصلی نگه داشته می‌شود تا با تعویض تب از دست نرود */
  speedTest: ReturnType<typeof useSpeedTest>;
  /** با هر تغییر، تاریخچه دوباره بارگذاری می‌شود */
  historyVersion: number;
}

/** تب تست سرعت: اجرای تست زنده + تاریخچهٔ نتایج */
export function SpeedTestTab({ speedTest, historyVersion }: SpeedTestTabProps) {
  const { state, run, cancel } = speedTest;
  const net = useNetworkInfo();
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(true);

  const loadHistory = useCallback(async () => {
    try {
      const res = await fetch("/api/speedtest/history", { cache: "no-store" });
      if (!res.ok) throw new Error("history fetch failed");
      const data = (await res.json()) as { items: HistoryItem[] };
      setHistory(data.items ?? []);
    } catch {
      /* بی‌صدا */
    } finally {
      setLoadingHistory(false);
    }
  }, []);

  useEffect(() => {
    loadHistory();
  }, [loadHistory, historyVersion]);

  const running = state.phase === "ping" || state.phase === "download" || state.phase === "upload";

  // مقدار نمایش داده شده روی گیج بزرگ
  const gaugeValue = useGaugeValue(state);

  return (
    <div className="space-y-4">
      <div className="grid gap-4 lg:grid-cols-3">
        {/* گیج زنده */}
        <Card className="bg-zinc-900/70 border-zinc-800 flex flex-col items-center py-6 lg:row-span-2">
          <CardHeader className="px-0 pt-0 pb-2">
            <CardTitle className="text-sm text-zinc-400 font-medium text-center">
              {phaseLabel(state)}
            </CardTitle>
          </CardHeader>
          <SpeedGauge value={gaugeValue} state={state} />
          <div className="mt-4 flex gap-2">
            {running ? (
              <Button variant="destructive" size="sm" onClick={cancel} className="gap-1.5">
                <Square size={14} aria-hidden />
                توقف
              </Button>
            ) : (
              <Button
                size="sm"
                onClick={run}
                disabled={net.online === false}
                className="gap-1.5 bg-emerald-500 hover:bg-emerald-400 text-zinc-950 font-bold"
              >
                <Play size={14} aria-hidden />
                {state.phase === "done" || state.phase === "error" ? "تست مجدد" : "شروع تست سرعت"}
              </Button>
            )}
          </div>
          {state.error && (
            <p className="mt-3 text-xs text-red-400 px-4 text-center">{state.error}</p>
          )}
        </Card>

        {/* نتایج */}
        <StatCard
          icon={ArrowDown}
          title="سرعت دانلود"
          value={state.downloadMbps !== null ? faNum(state.downloadMbps, 2) : running && state.phase === "download" ? faNum(state.liveMbps, 2) : "—"}
          unit="Mbps"
          hint="دانلود جریانی ۱۲ مگابایتی از سرور"
          iconColor="#10b981"
        />
        <StatCard
          icon={ArrowUp}
          title="سرعت آپلود"
          value={state.uploadMbps !== null ? faNum(state.uploadMbps, 2) : "—"}
          unit="Mbps"
          hint="ارسال ۴ مگابایت داده به سرور"
          iconColor="#a3e635"
        />
        <StatCard
          icon={Timer}
          title="پینگ"
          value={state.pingMs !== null ? faNum(state.pingMs, 1) : "—"}
          unit="ms"
          hint="میانگین بهترین ۴ نمونه از ۸ درخواست"
          iconColor="#eab308"
        />
        <StatCard
          icon={Waves}
          title="جیتر"
          value={state.jitterMs !== null ? faNum(state.jitterMs, 1) : "—"}
          unit="ms"
          hint="ناپایداری تأخیر — هرچه کمتر، تماس تصویری پایدارتر"
          iconColor="#f59e0b"
        />
        <StatCard
          icon={Trophy}
          title="بهترین دانلود ثبت‌شده"
          value={history.length ? faNum(Math.max(...history.map((h) => h.downloadMbps)), 2) : "—"}
          unit="Mbps"
          hint="از میان تاریخچهٔ تست‌های ذخیره‌شده"
          iconColor="#84cc16"
        />
      </div>

      {/* نمودار روند */}
      {history.length >= 2 && (
        <Card className="bg-zinc-900/70 border-zinc-800">
          <CardHeader className="pb-0">
            <CardTitle className="text-sm font-semibold flex items-center gap-2 text-zinc-200">
              <TrendingUp size={16} className="text-emerald-400" aria-hidden />
              روند سرعت در تست‌های اخیر
            </CardTitle>
          </CardHeader>
          <CardContent>
            <HistoryChart data={history} />
          </CardContent>
        </Card>
      )}

      {/* تاریخچه */}
      <Card className="bg-zinc-900/70 border-zinc-800">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-semibold flex items-center gap-2 text-zinc-200">
            <History size={16} className="text-emerald-400" aria-hidden />
            تاریخچهٔ تست‌های سرعت
          </CardTitle>
          <CardDescription className="text-xs">
            {toFa(history.length)} تست ذخیره‌شده — جدیدترین نتایج
          </CardDescription>
        </CardHeader>
        <CardContent className="max-h-96 overflow-y-auto custom-scroll">
          {loadingHistory ? (
            <div className="space-y-2">
              {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} className="h-12 rounded-lg bg-zinc-800/60 animate-pulse" />
              ))}
            </div>
          ) : history.length === 0 ? (
            <p className="text-xs text-zinc-500 text-center py-8">
              هنوز تستی ثبت نشده است — با دکمهٔ «شروع تست سرعت» اولین نتیجه را ثبت کنید.
            </p>
          ) : (
            <ul className="space-y-2">
              {history
                .slice()
                .reverse()
                .map((h) => (
                  <li
                    key={h.id}
                    className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-zinc-800 bg-zinc-900/60 px-3 py-2.5 hover:border-emerald-500/30 transition-colors"
                  >
                    <div className="flex items-center gap-2 flex-wrap">
                      <Badge variant="outline" className="text-[10px] border-emerald-500/40 text-emerald-300">
                        ↓ {faNum(h.downloadMbps, 1)}
                      </Badge>
                      <Badge variant="outline" className="text-[10px] border-lime-500/40 text-lime-300">
                        ↑ {faNum(h.uploadMbps, 1)}
                      </Badge>
                      <Badge variant="outline" className="text-[10px] border-yellow-500/40 text-yellow-300">
                        {faNum(h.pingMs, 0)}ms
                      </Badge>
                      <span className="text-[11px] text-zinc-500">
                        {effectiveTypeLabel(h.effectiveType ?? undefined)} ·{" "}
                        {connectionLabel(h.connectionType ?? undefined)}
                      </span>
                    </div>
                    <span className="text-[11px] text-zinc-500 tabular-nums">
                      {faDateTime(h.createdAt)}
                    </span>
                  </li>
                ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

/* ---------- گیج زندهٔ سرعت ---------- */

function useGaugeValue(state: SpeedTestState): number {
  const [smooth, setSmooth] = useState(0);
  useEffect(() => {
    let target = 0;
    if (state.phase === "ping" && state.liveMbps > 0) {
      // در فاز پینگ، تأخیر (ms) روی مقیاس 0..300 نمایش داده می‌شود
      target = Math.min(100, (state.liveMbps / 300) * 100);
    } else if (state.phase === "download" || state.phase === "upload") {
      // مقیاس لگاریتمی 0.1 تا 200 مگابیت
      const v = Math.max(0.1, state.liveMbps);
      target = Math.min(100, (Math.log10(v / 0.1) / Math.log10(2000)) * 100);
    } else if (state.phase === "done") {
      const v = Math.max(0.1, state.downloadMbps ?? 0);
      target = Math.min(100, (Math.log10(v / 0.1) / Math.log10(2000)) * 100);
    } else if (state.phase === "idle" || state.phase === "error") {
      target = 0;
    }
    // هموارسازی مقدار فقط داخل تیک‌های زمان‌بند (بدون setState همگام)
    // وقتی به هدف رسید دقیقاً روی همان مقدار می‌نشیند تا React از رندر اضافی صرف‌نظر کند
    const i = setInterval(() => {
      setSmooth((prev) => {
        const next = prev + (target - prev) * 0.25;
        return Math.abs(target - next) < 0.05 ? target : next;
      });
    }, 80);
    return () => clearInterval(i);
  }, [state.phase, state.liveMbps, state.downloadMbps]);
  return smooth;
}

function SpeedGauge({ value, state }: { value: number; state: SpeedTestState }) {
  const isPing = state.phase === "ping";
  const display = isPing
    ? faNum(state.liveMbps > 0 ? state.liveMbps : 0, 0)
    : state.phase === "download" || state.phase === "upload"
      ? faNum(state.liveMbps, 1)
      : state.phase === "done"
        ? faNum(state.downloadMbps ?? 0, 2)
        : "۰";

  const color =
    value >= 75 ? "#10b981" : value >= 45 ? "#84cc16" : value >= 20 ? "#eab308" : "#f97316";

  const unit = isPing
    ? "ms تأخیر"
    : state.phase === "upload"
      ? "Mbps آپلود"
      : "Mbps دانلود";

  return (
    <div
      role="img"
      aria-label={
        isPing
          ? `تأخیر فعلی: ${display} میلی‌ثانیه`
          : `سرعت فعلی: ${display} مگابیت بر ثانیه`
      }
    >
      <SpeedRing value={value} color={color} size={230} />
      <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
        <span className="text-4xl font-extrabold text-zinc-100 tabular-nums" dir="ltr">
          {display}
        </span>
        <span className="text-xs text-zinc-500 mt-1">{unit}</span>
        <span className="text-[10px] text-zinc-600 mt-2">
          {state.progress > 0 ? `پیشرفت ${toFa(Math.round(state.progress))}٪` : "آماده"}
        </span>
      </div>
    </div>
  );
}

function SpeedRing({
  value,
  color,
  size,
}: {
  value: number;
  color: string;
  size: number;
}) {
  const r = size / 2 - 14;
  const c = 2 * Math.PI * r;
  const dash = (Math.max(0, Math.min(100, value)) / 100) * c;

  return (
    <svg width={size} height={size} className="-rotate-90">
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="#27272a" strokeWidth={12} />
      <circle
        cx={size / 2}
        cy={size / 2}
        r={r}
        fill="none"
        stroke={color}
        strokeWidth={12}
        strokeLinecap="round"
        strokeDasharray={`${dash} ${c - dash}`}
        style={{ transition: "stroke-dasharray 120ms linear, stroke 300ms" }}
      />
    </svg>
  );
}

function phaseLabel(state: SpeedTestState): string {
  switch (state.phase) {
    case "idle":
      return "تست سرعت اینترنت";
    case "ping":
      return "در حال اندازه‌گیری پینگ…";
    case "download":
      return "در حال سنجش سرعت دانلود…";
    case "upload":
      return "در حال سنجش سرعت آپلود…";
    case "done":
      return "تست کامل شد ✓";
    case "error":
      return "خطا در اجرای تست";
  }
}
