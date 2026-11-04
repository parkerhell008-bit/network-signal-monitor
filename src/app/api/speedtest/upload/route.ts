import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";
export const maxDuration = 60;

// آپلود: دریافت بایت‌ها و گزارش حجم دریافتی برای سنجش سرعت آپلود
export async function POST(req: Request) {
  const start = Date.now();
  let received = 0;

  const body = req.body;
  if (body) {
    const reader = body.getReader();
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      received += value?.byteLength ?? 0;
    }
  }

  return NextResponse.json(
    { received, serverMs: Date.now() - start },
    {
      headers: {
        "Cache-Control": "no-store, no-cache, must-revalidate",
      },
    }
  );
}
