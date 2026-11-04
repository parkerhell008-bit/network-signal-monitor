"use client";

import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { RadioTower, ShieldAlert, Satellite, Waypoints, Smartphone } from "lucide-react";

const ANDROID_FIELDS: { label: string; desc: string }[] = [
  { label: "CID / NCI", desc: "شناسهٔ یکتای دکل سروینگ (LTE تا ۲۸ رقم، NR تا ۳۶ بیت)" },
  { label: "PCI", desc: "شناسهٔ فیزیکی سلول برای تفکیک دکل‌های هم‌فرکانس" },
  { label: "TAC / LAC", desc: "کد ناحیهٔ ردیابی — نشان می‌دهد در کدام محدودهٔ ثبت‌نام هستید" },
  { label: "EARFCN / NR-ARFCN", desc: "فرکانس دقیق رادیویی که مودم روی آن سینک شده" },
  { label: "RSRP / RSRQ / SNR", desc: "قدرت و کیفیت واقعی سیگنال LTE/5G به dBm و dB" },
  { label: "فاصله تا دکل", desc: "GPS شما + مختصات دکل از OpenCelliD → فاصلهٔ هاورساین به متر" },
];

/** تب دکل مخابراتی: توضیح شفاف محدودیت مرورگر + دعوت به اپ اندروید */
export function TowerTab() {
  return (
    <div className="space-y-4">
      <Card className="bg-zinc-900/70 border-zinc-800">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-semibold flex items-center gap-2 text-zinc-200">
            <RadioTower size={16} className="text-emerald-400" aria-hidden />
            اطلاعات دکل مخابراتی سروینگ
          </CardTitle>
          <CardDescription className="text-xs">
            چرا مرورگر این اطلاعات را نمی‌دهد و راه‌حل واقعی چیست
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-start gap-3 rounded-xl border border-amber-500/25 bg-amber-500/5 p-4">
            <ShieldAlert className="mt-0.5 h-5 w-5 shrink-0 text-amber-400" aria-hidden />
            <div className="space-y-1.5 text-xs leading-relaxed text-zinc-300">
              <p className="font-semibold text-amber-300">محدودیت امنیتی مرورگرها</p>
              <p>
                نه کروم، نه سافاری و نه هیچ مرورگر وبی به شناسهٔ دکل (CID/PCI)،
                قدرت dBm مودم یا اپراتور سیم‌کارت دسترسی نمی‌دهند — این داده‌ها
                فقط از طریق API‌های سیستمی اندروید مثل{" "}
                <code className="rounded bg-zinc-800 px-1 text-[11px]" dir="ltr">TelephonyManager</code>{" "}
                و{" "}
                <code className="rounded bg-zinc-800 px-1 text-[11px]" dir="ltr">SignalStrength</code>{" "}
                در دسترس‌اند.
              </p>
              <p>
                برای همین یک اپ اندروید کامل با همین امکانات ساخته‌ایم که در تب
                «اپ اندروید» می‌توانید سورس کامل و فایل ZIP پروژهٔ Android Studio
                را دانلود و اجرا کنید.
              </p>
            </div>
          </div>

          <div className="flex items-start gap-3 rounded-xl border border-emerald-500/25 bg-emerald-500/5 p-4">
            <Satellite className="mt-0.5 h-5 w-5 shrink-0 text-emerald-400" aria-hidden />
            <div className="text-xs leading-relaxed text-zinc-300">
              <p className="font-semibold text-emerald-300 mb-1">
                در اپ اندروید این فیلدها واقعی و لحظه‌ای نمایش داده می‌شود:
              </p>
              <ul className="space-y-2">
                {ANDROID_FIELDS.map((f) => (
                  <li key={f.label} className="flex flex-col gap-0.5">
                    <span className="flex items-center gap-2">
                      <Badge variant="outline" className="text-[10px] border-emerald-500/40 text-emerald-300 shrink-0" dir="ltr">
                        {f.label}
                      </Badge>
                      <span className="text-zinc-400">{f.desc}</span>
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          </div>

          <Separator className="bg-zinc-800/70" />

          <div className="flex items-start gap-3 text-xs leading-relaxed text-zinc-400">
            <Waypoints className="mt-0.5 h-4 w-4 shrink-0 text-zinc-500" aria-hidden />
            <p>
              نکتهٔ فنی: فاصلهٔ شما تا دکل با «مثلث‌بندی» محاسبه نمی‌شود؛ مودم گوشی
              فقط به یک دکلِ شناس‌شده سینک می‌شود. اپ اندروید مختصات GPS شما را با
              مختصات ثبت‌شدهٔ همان دکل در پایگاه جهانی OpenCelliD مقایسه می‌کند و
              فاصلهٔ خط مستقیم (هاورساین) را به متر گزارش می‌دهد — برای LTE دقت
              معمولاً بین ۵۰ تا ۳۰۰ متر است.
            </p>
          </div>
        </CardContent>
      </Card>

      <Card className="bg-zinc-900/70 border-zinc-800">
        <CardContent className="p-5 flex items-start gap-3">
          <Smartphone className="mt-0.5 h-5 w-5 shrink-0 text-emerald-400" aria-hidden />
          <p className="text-xs leading-relaxed text-zinc-400">
            در تب «اپ اندروید» سورس کامل (Kotlin + Material 3، فارسی و RTL)،
            راهنمای ساخت APK و فایل ZIP آمادهٔ import در Android Studio موجود است.
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
