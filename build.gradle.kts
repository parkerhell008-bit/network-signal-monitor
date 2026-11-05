// فایل Gradle سطح پروژه — نسخه پلاگین‌ها از طریق بلاک plugins {} مدیریت می‌شود
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
