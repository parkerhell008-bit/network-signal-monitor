import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

const MAX_ITEMS = 30;
/** سقف منطقی مقادیر (Mbps / ms) — از ورود داده‌های غیرواقعی جلوگیری می‌کند */
const MAX_METRIC = 100_000;

// GET: آخرین نتایج تست سرعت برای نمودار و تاریخچه
export async function GET() {
  try {
    const items = await db.speedTestResult.findMany({
      orderBy: { createdAt: "desc" },
      take: MAX_ITEMS,
    });
    return NextResponse.json(
      { items: items.reverse() },
      { headers: { "Cache-Control": "no-store" } }
    );
  } catch (e) {
    // خطای دیتابیس پنهان نمی‌شود ولی UI همچنان خالی نشان داده می‌شود
    console.error("[history] GET failed:", e);
    return NextResponse.json({ items: [] }, { status: 200 });
  }
}

// POST: ذخیرهٔ نتیجهٔ تست سرعت تازه
export async function POST(req: NextRequest) {
  // پارس JSON جدا از منطق دیتابیس تا خطای بدنهٔ نامعتبر 400 بگیرد نه 500
  let body: Record<string, unknown>;
  try {
    body = (await req.json()) as Record<string, unknown>;
  } catch {
    return NextResponse.json(
      { error: "بدنهٔ درخواست JSON معتبر نیست" },
      { status: 400 }
    );
  }

  try {
    const download = Number(body.downloadMbps);
    if (!Number.isFinite(download) || download < 0) {
      return NextResponse.json(
        { error: "مقدار سرعت دانلود نامعتبر است" },
        { status: 400 }
      );
    }

    const saved = await db.speedTestResult.create({
      data: {
        downloadMbps: Math.min(download, MAX_METRIC),
        uploadMbps: clampOrZero(body.uploadMbps),
        pingMs: clampOrZero(body.pingMs),
        jitterMs: clampOrZero(body.jitterMs),
        connectionType: strOrNull(body.connectionType, 32),
        effectiveType: strOrNull(body.effectiveType, 16),
        qualityScore: Number.isInteger(body.qualityScore)
          ? Math.min(100, Math.max(0, body.qualityScore as number))
          : null,
        ipAddress: strOrNull(body.ipAddress, 64),
      },
    });

    return NextResponse.json({ item: saved }, { status: 201 });
  } catch (e) {
    console.error("[history] POST failed:", e);
    return NextResponse.json(
      { error: "ذخیرهٔ نتیجه ناموفق بود" },
      { status: 500 }
    );
  }
}

function clampOrZero(v: unknown): number {
  const n = Number(v);
  return Number.isFinite(n) && n >= 0 ? Math.min(n, MAX_METRIC) : 0;
}

function strOrNull(v: unknown, max: number): string | null {
  return typeof v === "string" && v.length > 0 ? v.slice(0, max) : null;
}
