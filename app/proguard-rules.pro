# قواعد ProGuard — چون minifyEnabled=false است این فایل حداقلی نگه داشته شده است.
-keepattributes Signature, *Annotation*
-keep class ir.netsignal.monitor.SpeedTester$Listener { *; }
-keep class ir.netsignal.monitor.SpeedTester$SpeedResult { *; }
-dontwarn org.json.**
