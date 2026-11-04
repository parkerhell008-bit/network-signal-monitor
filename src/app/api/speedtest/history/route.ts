import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/db";

export const dynamic = "force-dynamic";

const MAX_ITEMS = 30;

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
  } catch {
    return NextResponse.json({ items: [] }, { status: 200 });
  }
}

// POST: ذخیرهٔ نتیجهٔ تست سرعت تازه
export async function POST(req: NextRequest) {
  try {
    const body = (await req.json()) as {
      downloadMbps?: number;
      uploadMbps?: number;
      pingMs?: number;
      jitterMs?: number;
      connectionType?: string;
      effectiveType?: string;
      qualityScore?: number;
      ipAddress?: string;
    };

    const download = Number(body.downloadMbps);
    if (!Number.isFinite(download) || download < 0) {
      return NextResponse.json(
        { error: "مقدار سرعت دانلود نامعتبر است" },
        { status: 400 }
      );
    }

    const saved = await db.speedTestResult.create({
      data: {
        downloadMbps: download,
        uploadMbps: clampOrZero(body.uploadMbps),
        pingMs: clampOrZero(body.pingMs),
        jitterMs: clampOrZero(body.jitterMs),
        connectionType: body.connectionType?.slice(0, 32) ?? null,
        effectiveType: body.effectiveType?.slice(0, 16) ?? null,
        qualityScore: Number.isInteger(body.qualityScore)
          ? Math.min(100, Math.max(0, body.qualityScore as number))
          : null,
        ipAddress: body.ipAddress?.slice(0, 64) ?? null,
      },
    });

    return NextResponse.json({ item: saved }, { status: 201 });
  } catch {
    return NextResponse.json(
      { error: "ذخیرهٔ نتیجه ناموفق بود" },
      { status: 500 }
    );
  }
}

function clampOrZero(v: unknown): number {
  const n = Number(v);
  return Number.isFinite(n) && n >= 0 ? n : 0;
}
