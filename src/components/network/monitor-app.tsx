"use client";

import { useState } from "react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { DashboardTab } from "@/components/network/dashboard-tab";
import { SpeedTestTab } from "@/components/network/speedtest-tab";
import { DeviceTab } from "@/components/network/device-tab";
import { TowerTab } from "@/components/network/tower-tab";
import { AndroidTab } from "@/components/network/android-tab";
import { SignalBars } from "@/components/network/signal-bars";
import { useNetworkInfo } from "@/hooks/use-network-info";
import { qualityMeta, connectionLabel } from "@/lib/persian";
import { RadioTower, Activity, Smartphone, Info, LayoutDashboard, Gauge } from "lucide-react";

/** پوستهٔ اصلی مانیتور شبکه */
export function MonitorApp() {
  const [tab, setTab] = useState("dashboard");
  const net = useNetworkInfo();
  const meta = qualityMeta(net.qualityScore);

  return (
    <div className="flex min-h-screen flex-col bg-background">
      {/* هدر */}
      <header className="sticky top-0 z-40 border-b border-zinc-800/80 bg-background/80 backdrop-blur-md">
        <div className="mx-auto flex w-full max-w-6xl items-center justify-between gap-3 px-4 py-3">
          <div className="flex items-center gap-3 min-w-0">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-500/10 border border-emerald-500/30">
              <RadioTower size={20} className="text-emerald-400" aria-hidden />
            </span>
            <div className="min-w-0">
              <h1 className="truncate text-base font-bold text-zinc-100 leading-tight sm:text-lg">
                مانیتور سیگنال شبکه
              </h1>
              <p className="truncate text-[11px] text-zinc-500">
                وضعیت سیم‌کارت، آنتن، سرعت اینترنت و دکل مخابراتی
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3 shrink-0">
            <div className="hidden sm:block">
              <SignalBars bars={meta.bars} color={meta.color} size="sm" />
            </div>
            <span
              className="flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-semibold"
              style={{
                borderColor: net.online ? `${meta.color}55` : "#ef444455",
                color: net.online ? meta.color : "#ef4444",
                background: net.online ? `${meta.color}11` : "#ef444411",
              }}
              aria-live="polite"
            >
              <span className="relative flex h-2 w-2">
                {net.online && (
                  <span
                    className="live-ping absolute inline-flex h-full w-full rounded-full"
                    style={{ background: meta.color }}
                  />
                )}
                <span
                  className="relative inline-flex h-2 w-2 rounded-full"
                  style={{ background: net.online ? meta.color : "#ef4444" }}
                />
              </span>
              {net.online
                ? net.type
                  ? connectionLabel(net.type)
                  : "متصل"
                : "قطع"}
            </span>
          </div>
        </div>
      </header>

      {/* محتوای اصلی */}
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-5">
        <Tabs value={tab} onValueChange={setTab} className="w-full">
          <TabsList className="mb-4 flex w-full gap-1 overflow-x-auto custom-scroll bg-zinc-900/80 border border-zinc-800 p-1 h-auto rounded-xl">
            <TabsTrigger value="dashboard" className="gap-1.5 text-xs data-[state=active]:bg-emerald-500/15 data-[state=active]:text-emerald-300 rounded-lg py-2 shrink-0">
              <LayoutDashboard size={14} aria-hidden />
              داشبورد
            </TabsTrigger>
            <TabsTrigger value="speedtest" className="gap-1.5 text-xs data-[state=active]:bg-emerald-500/15 data-[state=active]:text-emerald-300 rounded-lg py-2 shrink-0">
              <Gauge size={14} aria-hidden />
              تست سرعت
            </TabsTrigger>
            <TabsTrigger value="device" className="gap-1.5 text-xs data-[state=active]:bg-emerald-500/15 data-[state=active]:text-emerald-300 rounded-lg py-2 shrink-0">
              <Info size={14} aria-hidden />
              اطلاعات دستگاه
            </TabsTrigger>
            <TabsTrigger value="tower" className="gap-1.5 text-xs data-[state=active]:bg-emerald-500/15 data-[state=active]:text-emerald-300 rounded-lg py-2 shrink-0">
              <RadioTower size={14} aria-hidden />
              دکل مخابراتی
            </TabsTrigger>
            <TabsTrigger value="android" className="gap-1.5 text-xs data-[state=active]:bg-emerald-500/15 data-[state=active]:text-emerald-300 rounded-lg py-2 shrink-0">
              <Smartphone size={14} aria-hidden />
              اپ اندروید
            </TabsTrigger>
          </TabsList>

          <TabsContent value="dashboard" className="mt-0 focus-visible:outline-none">
            <DashboardTab />
          </TabsContent>
          <TabsContent value="speedtest" className="mt-0 focus-visible:outline-none">
            <SpeedTestTab />
          </TabsContent>
          <TabsContent value="device" className="mt-0 focus-visible:outline-none">
            <DeviceTab />
          </TabsContent>
          <TabsContent value="tower" className="mt-0 focus-visible:outline-none">
            <TowerTab />
          </TabsContent>
          <TabsContent value="android" className="mt-0 focus-visible:outline-none">
            <AndroidTab />
          </TabsContent>
        </Tabs>
      </main>

      {/* فوتر چسبان */}
      <footer className="mt-auto border-t border-zinc-800/80 bg-zinc-950/60">
        <div className="mx-auto flex w-full max-w-6xl flex-col items-center justify-between gap-2 px-4 py-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:flex-row">
          <p className="flex items-center gap-1.5 text-[11px] text-zinc-500">
            <Activity size={12} className="text-emerald-500" aria-hidden />
            مانیتور سیگنال شبکه — سنجش زندهٔ کیفیت اتصال
          </p>
          <p className="text-[11px] text-zinc-600">
            dBm و دکل در مرورگر تخمینی است · اندازه‌گیری دقیق با اپ اندروید
          </p>
        </div>
      </footer>
    </div>
  );
}
