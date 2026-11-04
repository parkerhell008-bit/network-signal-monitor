import type { Metadata, Viewport } from "next";
import "./globals.css";
import { Toaster } from "@/components/ui/sonner";

export const metadata: Metadata = {
  title: "مانیتور سیگنال شبکه | وضعیت سیم‌کارت، آنتن و سرعت اینترنت",
  description:
    "نمایش لحظه‌ای وضعیت اتصال شبکه، قدرت سیگنال، نوع شبکه (4G/5G)، تست سرعت واقعی دانلود و آپلود، تأخیر پینگ و دانلود اپ اندروید نمایش اطلاعات کامل دکل مخابراتی",
  keywords: [
    "تست سرعت",
    "سیگنال",
    "آنتن دهی",
    "سیم کارت",
    "دکل مخابراتی",
    "4G",
    "5G",
    "speed test",
    "signal monitor",
  ],
  manifest: "/manifest.webmanifest",
  openGraph: {
    title: "مانیتور سیگنال شبکه",
    description:
      "تست سرعت واقعی، کیفیت سیگنال، آنتن و اپ اندروید نمایش دکل مخابراتی و فاصله تا دکل",
    type: "website",
    locale: "fa_IR",
  },
  icons: {
    icon: "/icon-192.png",
    apple: "/icon-192.png",
  },
};

export const viewport: Viewport = {
  themeColor: "#0b1210",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="fa" dir="rtl" className="dark" suppressHydrationWarning>
      <head>
        <link rel="preconnect" href="https://fonts.googleapis.com" />
        <link
          rel="preconnect"
          href="https://fonts.gstatic.com"
          crossOrigin="anonymous"
        />
        {/* فونت فارسی وزیرمتن - در صورت قطع CDN، فونت سیستمی جایگزین می‌شود */}
        {/* eslint-disable-next-line @next/next/no-page-custom-font -- در App Router این لینک سراسری است */}
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Vazirmatn:wght@300;400;500;600;700;800&display=swap"
        />
      </head>
      <body className="antialiased bg-background text-foreground min-h-screen">
        {children}
        <Toaster position="top-center" richColors closeButton />
      </body>
    </html>
  );
}
