"use client";

import { useCallback, useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { StatCard } from "@/components/network/stat-card";
import { useNetworkInfo } from "@/hooks/use-network-info";
import { toFa, faNum, connectionLabel, effectiveTypeLabel } from "@/lib/persian";
import {
  Globe,
  Cpu,
  BatteryCharging,
  MapPin,
  MonitorSmartphone,
  Globe2,
  Clock,
  Loader2,
  Navigation,
} from "lucide-react";

interface ServerInfo {
  ip: string | null;
  browser: string;
  os: string;
  isMobile: boolean;
  languages: string | null;
  serverTime: string;
}

interface GeoPos {
  lat: number;
  lng: number;
  accuracy: number;
}

/** تب اطلاعات دستگاه: جزئیات کامل شبکه، مرورگر، باتری و موقعیت */
export function DeviceTab() {
  const net = useNetworkInfo();
  const [server, setServer] = useState<ServerInfo | null>(null);
  const [loadingServer, setLoadingServer] = useState(true);
  const [battery, setBattery] = useState<{
    level: number;
    charging: boolean;
  } | null>(null);
  const [geo, setGeo] = useState<GeoPos | null>(null);
  const [geoState, setGeoState] = useState<"idle" | "loading" | "error" | "done">("idle");
  const [geoError, setGeoError] = useState<string>("");

  // اطلاعات سمت سرور (IP و ...)
  const loadServer = useCallback(async () => {
    setLoadingServer(true);
    try {
      const res = await fetch("/api/network-info", { cache: "no-store" });
      setServer((await res.json()) as ServerInfo);
    } catch {
      setServer(null);
    } finally {
      setLoadingServer(false);
    }
  }, []);

  useEffect(() => {
    loadServer();
  }, [loadServer]);

  // Battery API (کروم/اندروید)
  useEffect(() => {
    type BatteryLike = {
      level: number;
      charging: boolean;
      addEventListener: (t: string, cb: () => void) => void;
      removeEventListener: (t: string, cb: () => void) => void;
    };
    let bat: BatteryLike | null = null;
    let disposed = false;
    const update = () => {
      if (bat && !disposed) setBattery({ level: bat.level, charging: bat.charging });
    };
    (navigator as Navigator & { getBattery?: () => Promise<BatteryLike> })
      .getBattery?.()
      .then((b) => {
        // اگر تب پیش از پاسخ باتری بسته شده باشد، listener ثبت نمی‌شود
        if (disposed) return;
        bat = b;
        update();
        b.addEventListener("levelchange", update);
        b.addEventListener("chargingchange", update);
      })
      .catch(() => {});
    return () => {
      disposed = true;
      if (bat) {
        bat.removeEventListener("levelchange", update);
        bat.removeEventListener("chargingchange", update);
      }
    };
  }, []);

  const locate = useCallback(() => {
    setGeoState("loading");
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setGeo({
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
          accuracy: pos.coords.accuracy,
        });
        setGeoState("done");
      },
      (err) => {
        setGeoError(
          err.code === err.PERMISSION_DENIED
            ? "اجازهٔ دسترسی به موقعیت رد شد"
            : "دریافت موقعیت ناموفق بود"
        );
        setGeoState("error");
      },
      { enableHighAccuracy: true, timeout: 12000, maximumAge: 30000 }
    );
  }, []);

  return (
    <div className="space-y-4">
      {/* کارت‌های خلاصه */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          icon={Globe}
          title="آدرس IP شما"
          value={
            loadingServer ? (
              <Skeleton className="h-7 w-24 bg-zinc-800" />
            ) : (
              <span dir="ltr" className="text-base">{server?.ip ?? "نامشخص"}</span>
            )
          }
          hint="آی‌پی عمومی ثبت‌شده روی سرور"
        />
        <StatCard
          icon={BatteryCharging}
          title="باتری دستگاه"
          value={battery ? toFa(Math.round(battery.level * 100)) : "—"}
          unit={battery ? "٪" + (battery.charging ? " ⚡" : "") : undefined}
          hint={
            battery
              ? battery.charging
                ? "در حال شارژ"
                : "باتری API پشتیبانی می‌شود"
              : "مرورگر از Battery API پشتیبانی نمی‌کند"
          }
          iconColor="#eab308"
        />
        <StatCard
          icon={MonitorSmartphone}
          title="سیستم‌عامل"
          value={server?.os ?? "—"}
          hint={server?.browser ? `مرورگر: ${server.browser}` : "در حال دریافت…"}
        />
        <StatCard
          icon={Globe2}
          title="زبان مرورگر"
          value={
            <span className="text-sm" dir="ltr">
              {server?.languages?.split(",")[0] ?? "—"}
            </span>
          }
          hint="زبان تعیین‌شده در تنظیمات سیستم"
        />
      </div>

      {/* جدول جزئیات شبکه */}
      <Card className="bg-zinc-900/70 border-zinc-800">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-semibold flex items-center gap-2 text-zinc-200">
            <Cpu size={16} className="text-emerald-400" aria-hidden />
            جزئیات اتصال شبکه
          </CardTitle>
          <CardDescription className="text-xs">
            مقادیر زنده از Network Information API و سرور
          </CardDescription>
        </CardHeader>
        <CardContent>
          <dl className="text-sm">
            <Row label="وضعیت اتصال" value={net.online ? "متصل ✓" : "قطع ✗"} />
            <Separator className="bg-zinc-800/70 my-1" />
            <Row label="نوع اتصال" value={connectionLabel(net.type)} />
            <Separator className="bg-zinc-800/70 my-1" />
            <Row label="نسخهٔ مؤثر شبکه" value={effectiveTypeLabel(net.effectiveType)} />
            <Separator className="bg-zinc-800/70 my-1" />
            <Row
              label="پهنای باند تخمینی"
              value={net.downlinkMbps !== undefined ? `${faNum(net.downlinkMbps, 1)} Mbps` : "—"}
            />
            <Separator className="bg-zinc-800/70 my-1" />
            <Row
              label="تأخیر تخمینی (RTT)"
              value={net.rttMs !== undefined ? `${toFa(net.rttMs)} ms` : "—"}
            />
            <Separator className="bg-zinc-800/70 my-1" />
            <Row
              label="حالت صرفه‌جویی داده"
              value={net.saveData === undefined ? "نامشخص" : net.saveData ? "فعال" : "غیرفعال"}
            />
            <Separator className="bg-zinc-800/70 my-1" />
            <Row label="پشتیبانی از Network Info API" value={net.supported ? "دارد ✓" : "ندارد ✗"} />
            <Separator className="bg-zinc-800/70 my-1" />
            <Row
              label="زمان سرور"
              value={
                server ? (
                  <span dir="ltr">{new Date(server.serverTime).toLocaleTimeString("fa-IR")}</span>
                ) : (
                  "—"
                )
              }
            />
          </dl>
        </CardContent>
      </Card>

      {/* موقعیت جغرافیایی */}
      <Card className="bg-zinc-900/70 border-zinc-800">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-semibold flex items-center gap-2 text-zinc-200">
            <MapPin size={16} className="text-emerald-400" aria-hidden />
            موقعیت جغرافیایی (GPS)
          </CardTitle>
          <CardDescription className="text-xs">
            مبنای محاسبهٔ فاصله تا دکل در اپ اندروید — با فراخوان دکل سروینگ شما (MCC/MNC/CID) در پایگاه OpenCelliD جفت می‌شود
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          <Button
            size="sm"
            variant="outline"
            onClick={locate}
            disabled={geoState === "loading"}
            className="gap-1.5 border-emerald-500/40 text-emerald-300 hover:bg-emerald-500/10"
          >
            {geoState === "loading" ? (
              <Loader2 size={14} className="animate-spin" aria-hidden />
            ) : (
              <Navigation size={14} aria-hidden />
            )}
            {geoState === "loading" ? "در حال دریافت…" : "دریافت موقعیت فعلی"}
          </Button>

          {geoState === "error" && (
            <p className="text-xs text-red-400">{geoError}</p>
          )}

          {geo && (
            <div className="grid gap-3 sm:grid-cols-3">
              <StatCard icon={Navigation} title="عرض جغرافیایی" value={<span dir="ltr" className="text-base">{geo.lat.toFixed(5)}</span>} iconColor="#84cc16" />
              <StatCard icon={Navigation} title="طول جغرافیایی" value={<span dir="ltr" className="text-base">{geo.lng.toFixed(5)}</span>} iconColor="#84cc16" />
              <StatCard icon={Clock} title="دقت تخمینی" value={toFa(Math.round(geo.accuracy))} unit="متر" iconColor="#eab308" />
            </div>
          )}
          {geo && (
            <p className="text-[11px] text-zinc-500 leading-relaxed">
              مختصات فعلی شما دریافت شد. برای دیدن دکل‌های اطراف می‌توانید این مختصات را در
              نقشهٔ عمومی OpenCelliD جست‌وجو کنید؛ اپ اندروید همین کار را خودکار با شناسهٔ
              دکل سروینگ انجام می‌دهد.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function Row({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between py-2 gap-3">
      <dt className="text-zinc-400 text-xs shrink-0">{label}</dt>
      <dd className="text-zinc-100 font-medium text-xs text-left tabular-nums">{value}</dd>
    </div>
  );
}
