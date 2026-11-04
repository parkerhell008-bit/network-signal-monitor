// ابزارهای نمایش فارسی: ارقام فارسی، واحدها، برچسب‌های شبکه

const FA_DIGITS = ["۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹"];

/** تبدیل رشته/عدد به ارقام فارسی */
export function toFa(input: string | number | null | undefined): string {
  if (input === null || input === undefined) return "—";
  return String(input).replace(/\d/g, (d) => FA_DIGITS[Number(d)]);
}

/** عدد اعشاری با ارقام فارسی و کنترل ارقام اعشار */
export function faNum(value: number | null | undefined, decimals = 1): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return "—";
  return toFa(value.toFixed(decimals));
}

/** نمایش تاریخ و ساعت فارسی */
export function faDateTime(iso: string): string {
  try {
    return new Intl.DateTimeFormat("fa-IR", {
      hour: "2-digit",
      minute: "2-digit",
      month: "short",
      day: "numeric",
    }).format(new Date(iso));
  } catch {
    return toFa(iso.slice(5, 16).replace("T", " "));
  }
}

/** برچسب فارسی نوع اتصال */
export function connectionLabel(type: string | undefined): string {
  switch (type) {
    case "wifi":
      return "وای‌فای";
    case "cellular":
      return "دادهٔ سیم‌کارت";
    case "ethernet":
      return "اترنت";
    case "none":
      return "قطع";
    case "bluetooth":
      return "بلوتوث";
    case "wimax":
      return "وایمکس";
    default:
      return "نامشخص";
  }
}

/** برچسب فارسی کیفیت تخمینی (effectiveType) */
export function effectiveTypeLabel(t: string | undefined): string {
  switch (t) {
    case "slow-2g":
      return "۲G بسیار کند";
    case "2g":
      return "۲G";
    case "3g":
      return "۳G";
    case "4g":
      return "۴G / ۵G";
    default:
      return "نامشخص";
  }
}

/** برچسب وضعیت اتصال داده در اپ اندروید */
export const DATA_STATE_LABEL: Record<string, string> = {
  DISCONNECTED: "قطع",
  CONNECTING: "در حال اتصال",
  CONNECTED: "متصل",
  SUSPENDED: "معلق",
};

/** امتیاز کیفیت سیگنال 0..100 بر اساس downlink و rtt مرورگر */
export function computeQualityScore(
  downlinkMbps: number | undefined,
  rttMs: number | undefined,
  effectiveType: string | undefined
): number {
  if (!navigator.onLine) return 0;

  let score = 0;

  // پهنای باند تخمینی (لگاریتمی تا 50Mbps)
  if (Number.isFinite(downlinkMbps) && (downlinkMbps as number) > 0) {
    const dl = Math.min(50, downlinkMbps as number);
    score += Math.round((Math.log10(1 + dl * 9) / Math.log10(451)) * 55);
  } else if (effectiveType) {
    score += { "4g": 45, "3g": 25, "2g": 8, "slow-2g": 3 }[effectiveType] ?? 10;
  } else {
    score += 30;
  }

  // تأخیر (کمتر = بهتر)
  if (Number.isFinite(rttMs) && (rttMs as number) > 0) {
    const r = rttMs as number;
    if (r <= 50) score += 35;
    else if (r <= 100) score += 28;
    else if (r <= 200) score += 18;
    else if (r <= 400) score += 8;
    else score += 2;
  } else {
    score += 15;
  }

  return Math.max(0, Math.min(100, score));
}

/** ارزیابی کیفی از امتیاز: برچسب فارسی + رنگ */
export function qualityMeta(score: number): {
  label: string;
  bars: number;
  color: string;
  text: string;
} {
  if (score >= 80)
    return { label: "عالی", bars: 5, color: "#10b981", text: "text-emerald-400" };
  if (score >= 60)
    return { label: "خوب", bars: 4, color: "#84cc16", text: "text-lime-400" };
  if (score >= 40)
    return { label: "متوسط", bars: 3, color: "#eab308", text: "text-yellow-400" };
  if (score >= 20)
    return { label: "ضعیف", bars: 2, color: "#f97316", text: "text-orange-400" };
  if (score > 0)
    return { label: "بسیار ضعیف", bars: 1, color: "#ef4444", text: "text-red-400" };
  return { label: "قطع", bars: 0, color: "#71717a", text: "text-zinc-500" };
}
