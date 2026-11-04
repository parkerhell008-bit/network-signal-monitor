"use client";

import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  SignalGauge,
} from "@/components/network/signal-gauge";
import { SignalBars } from "@/components/network/signal-bars";
import { StatCard } from "@/components/network/stat-card";
import { useNetworkInfo } from "@/hooks/use-network-info";
import {
  toFa,
  faNum,
  connectionLabel,
  effectiveTypeLabel,
  qualityMeta,
} from "@/lib/persian";
import {
  Wifi,
  WifiOff,
  Gauge,
  Timer,
  ArrowDownUp,
  Signal,
  Info,
  RefreshCw,
} from "lucide-react";

/** تب داشبورد: وضعیت لحظه‌ای اتصال، آنتن و کیفیت */
export function DashboardTab() {
  const net = useNetworkInfo();
  const [clock, setClock] = useState<string>("");

  // ساعت زنده
  useEffect(() => {
    const tick = () =>
      setClock(
        new Intl.DateTimeFormat("fa-IR", {
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
        }).format(new Date())
      );
    tick();
    const i = setInterval(tick, 1000);
    return () => clearInterval(i);
  }, []);

  const meta = qualityMeta(net.qualityScore);
  // dBm تخمینی از امتیاز (فقط نمایشی): 0 امتیاز = -113 ، 100 امتیاز = -44
  const dbmEstimate = Math.round(-113 + (net.qualityScore / 100) * 69);

  return (
    <div className="space-y-4">
      {/* نوار وضعیت زنده */}
      <Card className="bg-zinc-900/70 border-zinc-800 overflow-hidden relative">
        <div
          className="absolute inset-x-0 top-0 h-0.5 transition-colors"
          style={{ background: net.online ? meta.color : "#ef4444" }}
        />
        <CardContent className="p-5 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="relative flex h-3.5 w-3.5">
              {net.online && (
                <span
                  className="live-ping absolute inline-flex h-full w-full rounded-full"
                  style={{ background: meta.color }}
                />
              )}
              <span
                className="relative inline-flex rounded-full h-3.5 w-3.5"
                style={{ background: net.online ? meta.color : "#ef4444" }}
              />
            </span>
            <div>
              <p className="font-bold text-lg text-zinc-100 leading-tight">
                {net.online ? "اتصال برقرار است" : "اتصال قطع است"}
              </p>
              <p className="text-xs text-zinc-500 mt-0.5">
                {net.online
                  ? net.type
                    ? `${connectionLabel(net.type)} · ${effectiveTypeLabel(net.effectiveType)}`
                    : effectiveTypeLabel(net.effectiveType)
                  : "دستگاه به شبکه‌ای وصل نیست"}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-4">
            <SignalBars
              bars={meta.bars}
              color={meta.color}
              size="lg"
            />
            <div className="text-left" dir="ltr">
              <p className="text-xl font-bold text-zinc-100 tabular-nums">{clock}</p>
              <p className="text-[11px] text-zinc-500">به‌روزرسانی هر ۵ ثانیه</p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* گیج اصلی + کارت‌ها */}
      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="bg-zinc-900/70 border-zinc-800 flex flex-col items-center justify-center py-6">
          <CardHeader className="pb-1 pt-0 px-0">
            <CardTitle className="text-sm text-zinc-400 font-medium flex items-center gap-1.5">
              <Signal size={15} className="text-emerald-400" aria-hidden />
              کیفیت سیگنال (تخمینی)
            </CardTitle>
          </CardHeader>
          <CardContent className="px-0 pb-0 flex flex-col items-center">
            <SignalGauge
              score={net.qualityScore}
              label={meta.label}
              dbmEstimate={net.online ? dbmEstimate : null}
              size={250}
              subtitle="بر پایهٔ تأخیر و پهنای باند مرورگر"
            />
          </CardContent>
        </Card>

        <div className="lg:col-span-2 grid grid-cols-2 gap-4 content-start">
          <StatCard
            icon={net.online ? Wifi : WifiOff}
            title="نوع اتصال"
            value={net.online ? (net.type ? connectionLabel(net.type) : "متصل") : "قطع"}
            hint={
              net.online
                ? "نحوهٔ اتصال دستگاه شما به اینترنت"
                : "دستگاه به هیچ شبکه‌ای متصل نیست"
            }
          />
          <StatCard
            icon={Gauge}
            title="پهنای باند تخمینی"
            value={net.downlinkMbps !== undefined ? faNum(net.downlinkMbps, 1) : "—"}
            unit="Mbps"
            hint="تخمین مرورگر از سرعت لینک فعلی"
          />
          <StatCard
            icon={Timer}
            title="تأخیر تخمینی (RTT)"
            value={net.rttMs !== undefined ? toFa(net.rttMs) : "—"}
            unit="ms"
            hint="زمان رفت‌وبرگشت تخمینی شبکه"
          />
          <StatCard
            icon={ArrowDownUp}
            title="نسخهٔ مؤثر شبکه"
            value={effectiveTypeLabel(net.effectiveType)}
            hint="دسته‌بندی کیفیت لینک بر اساس استاندارد W3C"
          />
        </div>
      </div>

      {/* توضیح محدودیت مرورگر */}
      {!net.supported && (
        <Alert className="bg-zinc-900/70 border-amber-500/30 text-zinc-200">
          <Info className="h-4 w-4 text-amber-400" aria-hidden />
          <AlertDescription className="text-xs leading-relaxed">
            مرورگر فعلی از Network Information API پشتیبانی نمی‌کند؛ مقادیر پس از
            اولین تست سرعت دقیق‌تر می‌شوند. برای اطلاعات کامل سیگنال، dBm واقعی و
            دکل مخابراتی، اپ اندروید ارائه‌شده در تب «اپ اندروید» را نصب کنید.
          </AlertDescription>
        </Alert>
      )}

      {/* راهنمای ارقام */}
      <div className="flex flex-wrap items-center gap-2 text-xs text-zinc-500">
        <RefreshCw size={12} aria-hidden />
        <span>
          امتیاز کیفیت از ترکیب پهنای باند تخمینی و تأخیر محاسبه می‌شود؛ dBm
          نمایش‌داده‌شده تقریبی است و جایگزین اندازه‌گیری سخت‌افزاری نیست.
        </span>
        {net.saveData && (
          <Badge variant="outline" className="border-amber-500/40 text-amber-300 text-[10px]">
            حالت صرفه‌جویی داده فعال
          </Badge>
        )}
      </div>
    </div>
  );
}
