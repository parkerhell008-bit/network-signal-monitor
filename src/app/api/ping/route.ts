import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

// پینگ: پاسخ فوری برای اندازه‌گیری تأخیر رفت‌وبرگشت و جیتر
export async function GET() {
  return NextResponse.json(
    { t: Date.now() },
    {
      headers: {
        "Cache-Control": "no-store, no-cache, must-revalidate",
      },
    }
  );
}
