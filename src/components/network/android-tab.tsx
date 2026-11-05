"use client";

import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import {
  Smartphone,
  Download,
  Gauge,
  Signal,
  RadioTower,
  Route,
  Layers,
  CheckCircle2,
  FolderGit2,
} from "lucide-react";
import { toFa } from "@/lib/persian";

const FEATURES = [
  {
    icon: Signal,
    title: "قدرت سیگنال واقعی",
    desc: "dBm و ASU لحظه‌ای مودم، سطح ۰ تا ۴، و برای ۴G مقادیر RSRP / RSRQ / RSSI / SNR",
  },
  {
    icon: Gauge,
    title: "تشخیص نوع شبکه",
    desc: "۵G NR، LTE، HSPA+، ۳G، EDGE و… به همراه نسخه (۲/۳/۴/۵G) با نگاشت کامل ثابت‌های TelephonyManager",
  },
  {
    icon: Layers,
    title: "دو سیم‌کارت (Dual SIM)",
    desc: "لیست سابسکریپشن‌های فعال با اپراتور و وضعیت هر سیم‌کارت به‌صورت جداگانه",
  },
  {
    icon: RadioTower,
    title: "مشخصات دکل سروینگ",
    desc: "CID / PCI / TAC / EARFCN، کد کشور و اپراتور (MCC/MNC)، وضعیت رومینگ و رومینگ دیتا + سلول‌های همسایه",
  },
  {
    icon: Route,
    title: "فاصله تا دکل",
    desc: "GPS گوشی + مختصات دکل از OpenCelliD → فاصلهٔ هاورساین به متر و زاویهٔ جغرافیایی",
  },
  {
    icon: Download,
    title: "تست سرعت داخلی",
    desc: "دانلود ۴ جریان موازی ۲۵ مگابایتی، آپلود و پینگ با جیتر — کاملاً مشابه همین صفحه",
  },
];

/** تب اپ اندروید: معرفی، امکانات، دانلود ZIP و راهنمای ساخت */
export function AndroidTab() {
  const [downloading, setDownloading] = useState(false);

  const download = () => {
    setDownloading(true);
    // دانلود مستقیم فایل ZIP از مسیر عمومی
    const a = document.createElement("a");
    a.href = "/downloads/android-signal-app.zip";
    a.download = "android-signal-app.zip";
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => setDownloading(false), 1500);
  };

  return (
    <div className="space-y-4">
      {/* هدر معرفی */}
      <Card className="bg-zinc-900/70 border-zinc-800 overflow-hidden relative">
        <div className="absolute inset-x-0 top-0 h-0.5 bg-emerald-500" />
        <CardContent className="p-6 flex flex-col md:flex-row md:items-center justify-between gap-5">
          <div className="flex items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-emerald-500/10 border border-emerald-500/30">
              <Smartphone size={28} className="text-emerald-400" aria-hidden />
            </div>
            <div>
              <h2 className="text-lg font-bold text-zinc-100">
                مانیتور سیگنال شبکه — اپ اندروید
              </h2>
              <p className="text-xs text-zinc-500 mt-1 leading-relaxed max-w-xl">
                پروژهٔ کامل Android Studio با Kotlin و Material 3، رابط کاربری کاملاً
                فارسی و راست‌به‌چپ، تم تیره — دقیقاً همان چیزی که مرورگر نمی‌تواند
                نشان دهد: dBm واقعی، دکل سروینگ و فاصلهٔ تا دکل.
              </p>
              <div className="flex flex-wrap gap-1.5 mt-3">
                <Badge variant="outline" className="text-[10px] border-zinc-700 text-zinc-400">Kotlin</Badge>
                <Badge variant="outline" className="text-[10px] border-zinc-700 text-zinc-400">Material 3</Badge>
                <Badge variant="outline" className="text-[10px] border-zinc-700 text-zinc-400">minSdk 26 (اندروید ۸+)</Badge>
                <Badge variant="outline" className="text-[10px] border-zinc-700 text-zinc-400">فارسی RTL</Badge>
                <Badge variant="outline" className="text-[10px] border-zinc-700 text-zinc-400">OpenCelliD</Badge>
              </div>
            </div>
          </div>
          <Button
            onClick={download}
            disabled={downloading}
            size="lg"
            className="gap-2 bg-emerald-500 hover:bg-emerald-400 text-zinc-950 font-bold shrink-0"
          >
            <Download size={18} aria-hidden />
            {downloading ? "در حال دانلود…" : "دانلود پروژهٔ ZIP"}
          </Button>
        </CardContent>
      </Card>

      {/* امکانات */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {FEATURES.map((f) => (
          <Card key={f.title} className="bg-zinc-900/70 border-zinc-800 hover:border-emerald-500/30 transition-colors">
            <CardContent className="p-4">
              <div className="flex items-center gap-2 mb-2">
                <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-500/10 border border-emerald-500/25">
                  <f.icon size={15} className="text-emerald-400" aria-hidden />
                </span>
                <h3 className="text-sm font-bold text-zinc-100">{f.title}</h3>
              </div>
              <p className="text-xs text-zinc-500 leading-relaxed">{f.desc}</p>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* راهنمای ساخت */}
      <Card className="bg-zinc-900/70 border-zinc-800">
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-semibold flex items-center gap-2 text-zinc-200">
            <FolderGit2 size={16} className="text-emerald-400" aria-hidden />
            راهنمای ساخت APK در ۵ مرحله
          </CardTitle>
          <CardDescription className="text-xs">
            همین فایل ZIP را در Android Studio باز کنید — نیازی به تنظیمات اضافه نیست
          </CardDescription>
        </CardHeader>
        <CardContent>
          <ol className="space-y-3">
            {[
              "فایل ZIP را دانلود و استخراج کنید.",
              "در Android Studio از منوی File گزینهٔ Open را بزنید و پوشهٔ android-app را انتخاب کنید؛ Sync اولیه خودکار انجام می‌شود (Gradle wrapper هنگام Sync ساخته می‌شود).",
              "کلید رایگان OpenCelliD را از opencellid.org بگیرید و داخل اپ در کادر «کلید API» وارد کنید — برای نمایش مختصات و فاصلهٔ دکل لازم است.",
              "گوشی را با USB وصل کنید، USB Debugging را فعال کنید و دکمهٔ Run را بزنید؛ یا از منوی Build گزینهٔ Build APKs را انتخاب کنید تا فایل APK ساخته شود.",
              "دو مجوز «موقعیت مکانی» و «تلفن» را هنگام اجرا تأیید کنید — اندروید برای خواندن شناسهٔ دکل این مجوزها را الزامی کرده است.",
            ].map((step, i) => (
              <li key={i} className="flex items-start gap-3 text-xs text-zinc-300 leading-relaxed">
                <CheckCircle2 size={15} className="text-emerald-400 mt-0.5 shrink-0" aria-hidden />
                <span>
                  <span className="font-bold text-emerald-300 ml-1">مرحلهٔ {toFa(i + 1)}:</span>
                  {step}
                </span>
              </li>
            ))}
          </ol>
          <Separator className="bg-zinc-800/70 my-4" />
          <p className="text-[11px] text-zinc-500 leading-relaxed">
            مجوزهای موردنیاز: <span dir="ltr" className="text-zinc-400">ACCESS_FINE_LOCATION</span> و{" "}
            <span dir="ltr" className="text-zinc-400">READ_PHONE_STATE</span> — بدون این‌ها
            اندروید حتی شناسهٔ دکل متصل را هم به اپ نمی‌دهد (سیاست حریم خصوصی گوگل از
            اندروید ۸ به بعد).
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
