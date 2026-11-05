import { NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

/**
 * POST /api/speedtest/upload
 * موتور تست سرعت کلاینت یک Blob باینری (۴ مگابایت) ارسال می‌کند.
 * سرور بدنه را کامل مصرف می‌کند و حجم واقعی دریافتی را برمی‌گرداند
 * تا کلاینت بتواند سرعت آپلود را دقیق محاسبه کند.
 */
export async function POST(req: NextRequest) {
  const start = Date.now();
  let received = 0;

  try {
    if (!req.body) {
      return NextResponse.json(
        { error: "بدنه درخواست خالی است" },
        { status: 400 }
      );
    }

    const reader = req.body.getReader();
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      received += value?.byteLength ?? 0;
    }

    return NextResponse.json(
      {
        receivedBytes: received,
        elapsedMs: Date.now() - start,
      },
      {
        status: 200,
        headers: { "Cache-Control": "no-store" },
      }
    );
  } catch {
    return NextResponse.json(
      { error: "دریافت دادهٔ آپلود ناموفق بود" },
      { status: 500 }
    );
  }
}
