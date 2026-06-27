# Pkg2Mpkg

一个安卓小工具，尝试把 Wallpaper Engine 桌面版的 `.pkg` 文件重新打包成 `.mpkg`，供安卓版 Wallpaper Engine 导入。

## 构建方式

1. 用 Android Studio（ Hedgehog 或更高）打开 `Pkg2Mpkg` 文件夹。
2. 等待 Gradle 同步完成。
3. 点击 **Run** 或执行 **Build → Generate Signed Bundle / APK**。

命令行构建（需配置 `ANDROID_HOME`）：

```bash
./gradlew assembleDebug
```

## 使用方式

1. 安装并打开 App。
2. 点击「选择 PKG 文件」，从文件管理器里选中 `.pkg`。
3. App 会解析文件并显示类型分析。
4. 点击「转换为 MPKG」，生成的文件会放到应用缓存目录。
5. 点击「分享到 Wallpaper Engine」即可导入。

## 能转换什么？

| 壁纸类型 | 是否可尝试 | 说明 |
|---|---|---|
| 视频壁纸（MP4/WebM 等） | ✅ | 直接重打包，通常可用 |
| 图片/GIF 壁纸 | ✅ | 直接重打包，通常可用 |
| 场景（Scene）壁纸 | ⚠️ | 只能重新打包原文件，无法真正渲染为移动版视频 |
| 网页壁纸（HTML/JS） | ⚠️ | 安卓 Wallpaper Engine 可能不支持 |
| 应用程序壁纸（EXE/DLL） | ❌ | 安卓无法运行 Windows 程序 |

## 技术原理

`.pkg` 与 `.mpkg` 的容器结构均基于 Wallpaper Engine 的私有 PKG 格式（由 [RePKG](https://github.com/notscuffed/repkg) 逆向得出）：

```
[int32 magic_len][magic_string]
[int32 entry_count]
[int32 path_len][path_string][int32 offset][int32 length]...
[raw data...]
```

本工具把 `.pkg` 中的条目原样读出，再按同样结构写入 `.mpkg`。对于已经就是视频/图片资源的壁纸，这种「改扩展名+重新打包」通常能被安卓版 Wallpaper Engine 接受；对于场景类壁纸，真正的转换需要在 PC 端运行 Wallpaper Engine 的渲染管线，把手机上的 `.pkg` 转成视频后再打包，这部分无法在安卓端独立完成。

## 注意

- 本工具仅供学习交流，请尊重壁纸原作者版权。
- 部分 `.pkg` 可能有加密或特殊版本头，解析失败时请反馈样本结构。
