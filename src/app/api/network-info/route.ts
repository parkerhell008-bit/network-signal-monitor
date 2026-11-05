import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

// اطلاعات شبکهٔ سمت سرور: IP واقعی کلاینت، هدرها و زمان سرور
export async function GET(req: Request) {
  const h = req.headers;
  const forwarded = h.get("x-forwarded-for");
  // آخرین (سمت راست‌ترین) مقدار X-Forwarded-For توسط پراکسی معتمد ما اضافه شده
  // و قابل جعل توسط کلاینت نیست؛ اولین مقدار قابل جعل است.
  const ip =
    forwarded?.split(",").pop()?.trim() ||
    h.get("x-real-ip") ||
    h.get("cf-connecting-ip") ||
    null;

  const ua = h.get("user-agent") ?? "";
  const isMobile = /android|iphone|ipad|mobile/i.test(ua);
  const browser = ua.includes("Firefox")
    ? "فایرفاکس"
    : ua.includes("Edg")
      ? "Edge"
      : ua.includes("Chrome")
        ? "کروم"
        : ua.includes("Safari")
          ? "سافاری"
          : "نامشخص";
  const os = /android/i.test(ua)
    ? "اندروید"
    : /iphone|ipad|mac/i.test(ua)
      ? "iOS / macOS"
      : /windows/i.test(ua)
        ? "ویندوز"
        : /linux/i.test(ua)
          ? "لینوکس"
          : "نامشخص";

  return NextResponse.json(
    {
      ip,
      ipSource: forwarded ? "x-forwarded-for" : "header",
      userAgent: ua,
      browser,
      os,
      isMobile,
      languages: h.get("accept-language") ?? null,
      serverTime: new Date().toISOString(),
      timezone: h.get("x-vercel-ip-timezone") ?? null,
    },
    { headers: { "Cache-Control": "no-store" } }
  );
}
