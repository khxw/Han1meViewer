# 玻璃态 UI（Glassmorphism）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Han1meViewer 增加可切换的「界面风格」主题（經典 / 玻璃態），玻璃态使用 Haze 库实现真·毛玻璃：氛围光背景 + 顶栏/卡片/底栏/抽屉半透明模糊。

**Architecture:** 新增 `ui/theme/Glass.kt` 承载全部玻璃基础设施：`GlassTheme`（参数对象）+ `LocalGlassTheme`（CompositionLocal）+ `GlassRoot`（HazeState 创建、hazeSource 挂载、氛围光背景、对话框色阶覆写）+ `Modifier.hanimeGlass()`（hazeEffect + tint + 描边）。经典模式（默认）下所有玻璃 modifier 均为 no-op，与 v1.1.55 视觉完全一致。对话框/底部弹层为独立 window，Haze 无法跨窗口模糊，通过 GlassRoot 内嵌套 MaterialTheme 覆写 `surfaceContainerHigh/Low` 半透明实现"轻玻璃"（零侵入）。视频页（VideoTabScreens 自带 HanimeTheme、无 GlassRoot）自动排除玻璃。

**Tech Stack:** Kotlin、Jetpack Compose（BOM 2026.05.01、Material3 1.5.0-alpha20）、Haze 1.7.3（dev.chrisbanes.haze:haze + haze-materials）、DataStore/SharedPreferences。

**规格来源:** `docs/superpowers/specs/2026-08-31-glassmorphism-design.md`

**约定（用户偏好，必须遵守）:**
- 不做任何 git 提交；每任务以编译验证为检查点
- 不主动构建 APK；全部实现完成后等待用户下达"构建"指令再递增 patch 版本号（v1.1.55 → v1.1.56）构建 arm64-v8a
- 对话使用简体中文；代码注释/UI 字符串沿用项目繁体风格（4 语言同步）

---

## 文件结构

| 文件 | 责任 | 动作 |
|---|---|---|
| `gradle/libs.versions.toml` | 依赖版本表 | 修改（+haze 1.7.3） |
| `app/build.gradle.kts` | 模块依赖 | 修改（+haze 依赖） |
| `app/src/main/java/com/yenaly/han1meviewer/ui/theme/Glass.kt` | 玻璃基础设施（GlassTheme/GlassRoot/hanimeGlass/GlassAmbientBackground） | 新建 |
| `app/src/main/java/com/yenaly/han1meviewer/ui/theme/Theme.kt` | 主题入口（AMOLED 让位玻璃态） | 修改 |
| `app/src/main/java/com/yenaly/han1meviewer/ui/navigation/settings/SettingsPreferenceKeys.kt` | 偏好键常量 | 修改 |
| `app/src/main/java/com/yenaly/han1meviewer/Preferences.kt` | 偏好读取（+uiStyle） | 修改 |
| `app/src/main/res/values/strings.xml` 等 4 语言 | 设置项文案 | 修改 ×4 |
| `app/src/main/java/com/yenaly/han1meviewer/ui/screen/settings/model/HomeSettingsUiState.kt` | 设置页状态（+uiStyleKey/uiStyleName） | 修改 |
| `app/src/main/java/com/yenaly/han1meviewer/ui/screen/settings/HomeSettingsScreen.kt` | 设置页 UI（+「界面風格」项与选择对话框） | 修改 |
| `app/src/main/java/com/yenaly/han1meviewer/ui/navigation/settings/HomeSettingsRoute.kt` | 设置页路由（保存 + recreate） | 修改 |
| `app/src/main/java/com/yenaly/han1meviewer/ui/screen/main/MainActivityContent.kt` | 主界面（GlassRoot 包裹） | 修改 |
| `app/src/main/java/com/yenaly/han1meviewer/ui/screen/main/MainActivityScaffold.kt` | 主框架（氛围光背景、底栏、抽屉玻璃） | 修改 |
| `app/src/main/java/com/yenaly/han1meviewer/ui/component/appbar/HanimeTopAppBar.kt` | 顶栏玻璃 | 修改 |
| `app/src/main/java/com/yenaly/han1meviewer/ui/component/VideoCardItem.kt` | 视频卡片玻璃 | 修改 |

---

## Task 1: Haze 依赖 + 玻璃基础设施（Glass.kt）

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/yenaly/han1meviewer/ui/theme/Glass.kt`
- Modify: `app/src/main/java/com/yenaly/han1meviewer/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/yenaly/han1meviewer/ui/navigation/settings/SettingsPreferenceKeys.kt`
- Modify: `app/src/main/java/com/yenaly/han1meviewer/Preferences.kt`

- [ ] **Step 1: 添加 Haze 版本与依赖声明**

`gradle/libs.versions.toml`，在 `[versions]` 段 `mpv = "v0.1.7"` 行后追加：

```toml
haze = "1.7.3"
```

同一文件 `[libraries]` 段末尾（`androidx-ui-test-junit4` 行后）追加：

```toml
haze = { group = "dev.chrisbanes.haze", name = "haze", version.ref = "haze" }
haze-materials = { group = "dev.chrisbanes.haze", name = "haze-materials", version.ref = "haze" }
```

`app/build.gradle.kts`，在 `implementation(libs.compose.material3)` 行后追加：

```kotlin
    implementation(libs.haze)
    implementation(libs.haze.materials)
```

- [ ] **Step 2: 新增偏好键与读取属性**

`app/src/main/java/com/yenaly/han1meviewer/ui/navigation/settings/SettingsPreferenceKeys.kt`，在 `const val THEME_COLOR = "theme_color"` 行后追加：

```kotlin
    const val UI_STYLE = "ui_style"
```

`app/src/main/java/com/yenaly/han1meviewer/Preferences.kt`，在 `val themeColor` 属性（`get() = preferenceSp.getString(SettingsPreferenceKeys.THEME_COLOR, null)` 行）后追加：

```kotlin
    val uiStyle: String
        get() = preferenceSp.getString(SettingsPreferenceKeys.UI_STYLE, "classic") ?: "classic"
```

- [ ] **Step 3: 新建 Glass.kt（玻璃基础设施）**

Create: `app/src/main/java/com/yenaly/han1meviewer/ui/theme/Glass.kt`

```kotlin
package com.yenaly.han1meviewer.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.Preferences
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * 玻璃态主题参数：由 GlassRoot 根据深浅色计算，通过 CompositionLocal 下发。
 * enabled = false（經典模式）时所有玻璃 modifier 均为 no-op。
 *
 * 参数表（规格 §4.2）：
 * - 深色：surfaceContainer 55% 透明 / 模糊 18dp / 白色描边 12%
 * - 浅色：surfaceContainer 72% 透明 / 模糊 16dp / 白色描边 45%
 */
@Stable
class GlassTheme(
    val enabled: Boolean,
    val hazeState: HazeState,
    val containerTint: Color,
    val blurRadius: Dp,
    val borderColor: Color,
)

val LocalGlassTheme = compositionLocalOf {
    GlassTheme(
        enabled = false,
        hazeState = HazeState(),
        containerTint = Color.Transparent,
        blurRadius = 0.dp,
        borderColor = Color.Transparent,
    )
}

/**
 * 玻璃容器效果：真模糊（hazeEffect）+ 半透明容器色 + 细描边。
 * 經典模式（enabled=false）或未处于 GlassRoot 内（如视频页、预览）时 no-op。
 */
@Composable
fun Modifier.hanimeGlass(shape: Shape): Modifier {
    val theme = LocalGlassTheme.current
    if (!theme.enabled) return this
    return this
        .clip(shape)
        .hazeEffect(
            state = theme.hazeState,
            style = HazeStyle(
                backgroundColor = theme.containerTint,
                tints = listOf(HazeTint(theme.containerTint)),
                blurRadius = theme.blurRadius,
                noiseFactor = 0.03f,
            ),
        )
        .border(width = 1.dp, color = theme.borderColor, shape = shape)
}

/**
 * 玻璃态根容器：创建 HazeState 并挂载 hazeSource，铺氛围光背景。
 * 经典模式下直接渲染 content，不产生任何效果。
 *
 * 对话框/BottomSheet 为独立 window，Haze 无法跨窗口模糊：
 * 通过嵌套 MaterialTheme 覆写 surfaceContainerHigh/Low 半透明实现「轻玻璃」，
 * 主界面内所有 AlertDialog / ModalBottomSheet 自动生效，零侵入。
 */
@Composable
fun GlassRoot(content: @Composable () -> Unit) {
    val enabled = Preferences.uiStyle == "glass"
    val darkTheme = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    val hazeState = remember { HazeState() }
    val theme = GlassTheme(
        enabled = enabled,
        hazeState = hazeState,
        containerTint = if (darkTheme) {
            scheme.surfaceContainer.copy(alpha = 0.55f)
        } else {
            scheme.surfaceContainer.copy(alpha = 0.72f)
        },
        blurRadius = if (darkTheme) 18.dp else 16.dp,
        borderColor = Color.White.copy(alpha = if (darkTheme) 0.12f else 0.45f),
    )
    CompositionLocalProvider(LocalGlassTheme provides theme) {
        if (!enabled) {
            content()
            return@CompositionLocalProvider
        }
        MaterialTheme(
            colorScheme = scheme.copy(
                surfaceContainerHigh = scheme.surfaceContainerHigh.copy(alpha = 0.86f),
                surfaceContainerLow = scheme.surfaceContainerLow.copy(alpha = 0.86f),
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            ) {
                GlassAmbientBackground(modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
    }
}

/**
 * 氛围光背景：主题色对角渐变 + 左上/右下两处径向光斑。
 * 仅玻璃态使用；经典模式保持纯色 background。
 */
@Composable
fun GlassAmbientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val darkTheme = isSystemInDarkTheme()
    val base = Brush.linearGradient(
        colors = if (darkTheme) {
            listOf(
                scheme.primary.copy(alpha = 0.20f),
                scheme.background,
                scheme.tertiary.copy(alpha = 0.12f),
            )
        } else {
            listOf(
                scheme.primary.copy(alpha = 0.22f),
                scheme.background,
                scheme.tertiary.copy(alpha = 0.16f),
            )
        },
        start = Offset(0f, Float.POSITIVE_INFINITY),
        end = Offset(Float.POSITIVE_INFINITY, 0f),
    )
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(base),
        )
        // 光斑 1：左上，主题色
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(scheme.primary.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(wPx * 0.12f, hPx * 0.05f),
                        radius = wPx * 0.9f,
                    )
                ),
        )
        // 光斑 2：右下，第三色
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(scheme.tertiary.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(wPx * 0.92f, hPx * 0.88f),
                        radius = wPx * 0.8f,
                    )
                ),
        )
        content()
    }
}
```

- [ ] **Step 4: AMOLED 让位玻璃态（Theme.kt）**

`app/src/main/java/com/yenaly/han1meviewer/ui/theme/Theme.kt`，将第 29 行：

```kotlin
    // AMOLED 純黑模式：僅在深色主題下生效
    val amoled = Preferences.useDarkMode == "always_amoled" && darkTheme
```

替换为：

```kotlin
    // AMOLED 純黑模式：僅在深色主題下生效；玻璃態優先，AMOLED 讓位（規格 §3.2）
    val amoled = Preferences.useDarkMode == "always_amoled" && darkTheme && Preferences.uiStyle != "glass"
```

- [ ] **Step 5: 编译验证**

在项目根目录 `F:\Han1me\Han1meViewer-Remastered-Org-Han1meViewer-Remastered-8abff7d` 运行（PowerShell，分号分隔）：

```powershell
$env:JAVA_HOME="C:\Program Files\Zulu\zulu-21"; $env:HOME="F:\Han1me"; .\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL（首次会下载 Haze 依赖）。

---

## Task 2: 设置项「界面風格」

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-ja/strings.xml`
- Modify: `app/src/main/java/com/yenaly/han1meviewer/ui/screen/settings/model/HomeSettingsUiState.kt`
- Modify: `app/src/main/java/com/yenaly/han1meviewer/ui/screen/settings/HomeSettingsScreen.kt`
- Modify: `app/src/main/java/com/yenaly/han1meviewer/ui/navigation/settings/HomeSettingsRoute.kt`

- [ ] **Step 1: 4 语言字符串**

4 个 strings.xml 均在 `<string name="theme_color_high_contrast">…</string>` 行后追加（值各自翻译；默认 values 为繁体）：

`values/strings.xml`:

```xml
    <string name="ui_style">界面風格</string>
    <string name="ui_style_classic">經典</string>
    <string name="ui_style_glass">玻璃態</string>
```

`values-zh-rCN/strings.xml`:

```xml
    <string name="ui_style">界面风格</string>
    <string name="ui_style_classic">经典</string>
    <string name="ui_style_glass">玻璃态</string>
```

`values-en/strings.xml`:

```xml
    <string name="ui_style">UI Style</string>
    <string name="ui_style_classic">Classic</string>
    <string name="ui_style_glass">Glass</string>
```

`values-ja/strings.xml`:

```xml
    <string name="ui_style">UIスタイル</string>
    <string name="ui_style_classic">クラシック</string>
    <string name="ui_style_glass">ガラス</string>
```

- [ ] **Step 2: UiState 增加字段**

`app/src/main/java/com/yenaly/han1meviewer/ui/screen/settings/model/HomeSettingsUiState.kt`，在 `themeColorName: String,` 行后追加：

```kotlin
    val uiStyleKey: String,
    val uiStyleName: String,
```

- [ ] **Step 3: HomeSettingsScreen 增加选项对话框与设置项**

`app/src/main/java/com/yenaly/han1meviewer/ui/screen/settings/HomeSettingsScreen.kt`：

a) enum 增加项，将：

```kotlin
private enum class HomeSettingsChoiceDialog {
    VideoLanguage,
    VideoQuality,
    DarkMode,
    AppLanguage,
    ThemeColor,
    AnnouncementFetchInterval,
}
```

替换为：

```kotlin
private enum class HomeSettingsChoiceDialog {
    VideoLanguage,
    VideoQuality,
    DarkMode,
    AppLanguage,
    ThemeColor,
    UiStyle,
    AnnouncementFetchInterval,
}
```

b) 参数列表，在 `onThemeColorChange: (String) -> Unit,` 行后追加：

```kotlin
    onUiStyleChange: (String) -> Unit,
```

c) 在 ThemeColor 的 ChoiceDialog（以 `onThemeColorChange(it)` 结尾的那个块）之后新增：

```kotlin
    ChoiceDialog(
        visible = activeDialog == HomeSettingsChoiceDialog.UiStyle,
        title = stringResource(R.string.ui_style),
        options = listOf(
            stringResource(R.string.ui_style_classic) to "classic",
            stringResource(R.string.ui_style_glass) to "glass",
        ),
        selectedValue = state.uiStyleKey,
        onDismiss = { activeDialog = null },
        onSelect = {
            activeDialog = null
            onUiStyleChange(it)
        },
    )
```

d) 设置项列表：在 theme_color 的 `SettingNavigationItem` item 块之后（`app_lang` item 之前）新增：

```kotlin
        item {
            SettingNavigationItem(
                title = stringResource(R.string.ui_style),
                valueText = state.uiStyleName,
                iconRes = R.drawable.ic_baseline_switch_24,
                onClick = { activeDialog = HomeSettingsChoiceDialog.UiStyle },
            )
        }
```

e) Preview（约 736-788 行区域）：在 `themeColorName = "預設（暖紅）",` 行后追加：

```kotlin
                uiStyleKey = "classic",
                uiStyleName = "經典",
```

并在 Preview 参数列表 `onThemeColorChange = {},` 行后追加：

```kotlin
            onUiStyleChange = {},
```

- [ ] **Step 4: HomeSettingsRoute 保存与重建**

`app/src/main/java/com/yenaly/han1meviewer/ui/navigation/settings/HomeSettingsRoute.kt`：

a) 常量，在 `private const val HOME_THEME_COLOR = "theme_color"` 行后追加：

```kotlin
private const val HOME_UI_STYLE = "ui_style"
```

b) 回调，在 `onThemeColorChange = { key -> ... activity.recreate() },` 块后追加：

```kotlin
        onUiStyleChange = { key ->
            saveString(HOME_UI_STYLE, key)
            refreshKey++
            activity.recreate()
        },
```

c) UiState 构建，在 `themeColorName = context.getString(ThemeColorPreset.fromKey(Preferences.themeColor).displayNameRes),` 行后追加：

```kotlin
        uiStyleKey = Preferences.uiStyle,
        uiStyleName = context.getString(
            if (Preferences.uiStyle == "glass") R.string.ui_style_glass else R.string.ui_style_classic
        ),
```

- [ ] **Step 5: 编译验证**

```powershell
$env:JAVA_HOME="C:\Program Files\Zulu\zulu-21"; $env:HOME="F:\Han1me"; .\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL。

---

## Task 3: 主框架接入（GlassRoot + 氛围光 + 顶栏/底栏/抽屉玻璃）

**Files:**
- Modify: `app/src/main/java/com/yenaly/han1meviewer/ui/screen/main/MainActivityContent.kt`
- Modify: `app/src/main/java/com/yenaly/han1meviewer/ui/screen/main/MainActivityScaffold.kt`
- Modify: `app/src/main/java/com/yenaly/han1meviewer/ui/component/appbar/HanimeTopAppBar.kt`

- [ ] **Step 1: MainActivityContent 包 GlassRoot**

`app/src/main/java/com/yenaly/han1meviewer/ui/screen/main/MainActivityContent.kt`：

a) import 区（`import com.yenaly.han1meviewer.ui.theme.HanimeTheme` 附近）追加：

```kotlin
import com.yenaly.han1meviewer.ui.theme.GlassRoot
```

b) 第 66 行 `HanimeTheme {` 之后插入 `GlassRoot {`（其余代码不变）。

c) 文件末尾（当前 262-265 行，注意该四行序列在文件中唯一；MainActivityScaffoldPreview 尾部为 `            )` 闭合参数、结构不同）：

```kotlin
            }
        }
    }
}
```

替换为：

```kotlin
            }
            }
        }
    }
}
```

（即 MainActivityScaffold 的闭合 `}` 之后、HanimeTheme 闭合 `}` 之前，多一层 GlassRoot 闭合 `}`。替换后从外到内为：函数 → HanimeTheme → GlassRoot → MainActivityScaffold → Box。）

- [ ] **Step 2: MainActivityScaffold 玻璃（氛围光背景 + 底栏 + 抽屉）**

`app/src/main/java/com/yenaly/han1meviewer/ui/screen/main/MainActivityScaffold.kt`：

a) import 追加：

```kotlin
import androidx.compose.material3.NavigationBarDefaults
import com.yenaly.han1meviewer.ui.theme.GlassAmbientBackground
import com.yenaly.han1meviewer.ui.theme.LocalGlassTheme
import com.yenaly.han1meviewer.ui.theme.hanimeGlass
```

b) `MainActivityScaffold` 函数体开头（`val scope = rememberCoroutineScope()` 行后）追加：

```kotlin
    val glassTheme = LocalGlassTheme.current
    val glassEnabled = glassTheme.enabled
```

c) 抽屉，将：

```kotlin
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
```

替换为：

```kotlin
            ModalDrawerSheet(
                modifier = if (glassEnabled) {
                    Modifier.hanimeGlass(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))
                } else {
                    Modifier
                },
                drawerContainerColor = if (glassEnabled) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
            ) {
```

d) 内容层背景，将：

```kotlin
                // 内容层填满全屏（含悬浮导航栏后方区域）：页面内容从导航栏缝隙/后方穿过；
                // 各 Tab 页面列表已通过底部 contentPadding 保证末尾内容可滚至导航栏上方。
                // 铺上与页面一致的背景色兜底（位于内容之下），防止底部/导航条区域透出
                // 系统 windowBackground（默认纯黑）形成色差「遮罩层」；不遮挡内容穿透
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    content()
                }
```

替换为：

```kotlin
                // 内容层填满全屏（含悬浮导航栏后方区域）：页面内容从导航栏缝隙/后方穿过；
                // 各 Tab 页面列表已通过底部 contentPadding 保证末尾内容可滚至导航栏上方。
                // 玻璃態：铺氛围光背景（渐变+光斑）；經典：铺纯色兜底（防止色差「遮罩层」）
                if (glassEnabled) {
                    GlassAmbientBackground(modifier = Modifier.fillMaxSize()) {
                        content()
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        content()
                    }
                }
```

e) 底栏，将：

```kotlin
                    NavigationBar(
                        modifier = Modifier
                            .width(240.dp)
                            .height(60.dp)
                            .clip(RoundedCornerShape(30.dp)),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        tonalElevation = 0.dp,
                    ) {
```

替换为：

```kotlin
                    NavigationBar(
                        modifier = Modifier
                            .width(240.dp)
                            .height(60.dp)
                            .then(
                                if (glassEnabled) {
                                    Modifier.hanimeGlass(RoundedCornerShape(30.dp))
                                } else {
                                    Modifier.clip(RoundedCornerShape(30.dp))
                                }
                            ),
                        containerColor = if (glassEnabled) {
                            Color.Transparent
                        } else {
                            NavigationBarDefaults.containerColor
                        },
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        tonalElevation = 0.dp,
                    ) {
```

- [ ] **Step 3: 顶栏玻璃（HanimeTopAppBar）**

`app/src/main/java/com/yenaly/han1meviewer/ui/component/appbar/HanimeTopAppBar.kt`：

a) import 追加：

```kotlin
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yenaly.han1meviewer.ui.theme.LocalGlassTheme
import com.yenaly.han1meviewer.ui.theme.hanimeGlass
```

b) 函数体，将：

```kotlin
    TopAppBar(
        modifier = modifier,
        colors = if (scrollBehavior != null) {
            topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            TopAppBarDefaults.topAppBarColors()
        },
```

替换为：

```kotlin
    val glassTheme = LocalGlassTheme.current
    val glassEnabled = glassTheme.enabled
    TopAppBar(
        modifier = modifier.then(
            if (glassEnabled) {
                Modifier.hanimeGlass(
                    RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                )
            } else {
                Modifier
            }
        ),
        colors = if (glassEnabled) {
            topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface,
            )
        } else if (scrollBehavior != null) {
            topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            TopAppBarDefaults.topAppBarColors()
        },
```

（其余 title/navigationIcon/actions/scrollBehavior 参数保持不变。）

- [ ] **Step 4: 编译验证**

```powershell
$env:JAVA_HOME="C:\Program Files\Zulu\zulu-21"; $env:HOME="F:\Han1me"; .\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL。

---

## Task 4: 视频卡片玻璃（VideoCardItem）

**Files:**
- Modify: `app/src/main/java/com/yenaly/han1meviewer/ui/component/VideoCardItem.kt`

- [ ] **Step 1: 卡片玻璃**

`app/src/main/java/com/yenaly/han1meviewer/ui/component/VideoCardItem.kt`：

a) import 追加：

```kotlin
import com.yenaly.han1meviewer.ui.theme.LocalGlassTheme
import com.yenaly.han1meviewer.ui.theme.hanimeGlass
```

b) 函数体，将：

```kotlin
    val interactionSource = remember { MutableInteractionSource() }
    val isCompact = Preferences.useCompactMode
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                enabled = !isPlaying,
                onClick = { onClickVideosItem(videoItem.videoCode) },
                onLongClick = { onLongClickVideosItem(videoItem.videoCode, videoItem.title) },
            ),
        shape = RoundedCornerShape(if (isCompact) 8.dp else 12.dp),
        tonalElevation = if (isCompact) 1.dp else 2.dp,
        shadowElevation = if (isCompact) 0.dp else 1.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
```

替换为：

```kotlin
    val interactionSource = remember { MutableInteractionSource() }
    val isCompact = Preferences.useCompactMode
    val glassTheme = LocalGlassTheme.current
    val glassEnabled = glassTheme.enabled
    val cardShape = RoundedCornerShape(if (isCompact) 8.dp else 12.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                enabled = !isPlaying,
                onClick = { onClickVideosItem(videoItem.videoCode) },
                onLongClick = { onLongClickVideosItem(videoItem.videoCode, videoItem.title) },
            )
            .then(
                if (glassEnabled) {
                    Modifier.hanimeGlass(cardShape)
                } else {
                    Modifier
                }
            ),
        shape = cardShape,
        tonalElevation = if (isCompact) 1.dp else 2.dp,
        // 玻璃態下阴影会破坏玻璃感
        shadowElevation = if (glassEnabled || isCompact) 0.dp else 1.dp,
        color = if (glassEnabled) Color.Transparent else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
```

- [ ] **Step 2: 编译验证**

```powershell
$env:JAVA_HOME="C:\Program Files\Zulu\zulu-21"; $env:HOME="F:\Han1me"; .\gradlew.bat :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL。

---

## Task 5: 全量编译 + 回归检查清单

**Files:** 无新改动（验证任务）

- [ ] **Step 1: 全量编译（含所有变体）**

```powershell
$env:JAVA_HOME="C:\Program Files\Zulu\zulu-21"; $env:HOME="F:\Han1me"; .\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 代码审查要点（自查）**

Task 1 与 Task 3 代码质量审查（均 approved）遗留的 Minor 优化项，本任务统一处理：

1. **Glass.kt 注释统一为繁体中文**（Minor 3）：项目注释惯例为繁体（如 Theme.kt「純黑模式/讓位」），Glass.kt 当前注释多为简体（玻璃态、氛围光、色阶覆写等），逐处改为繁体。
2. **GlassTheme 实例 remember 化**（Minor 2）：`GlassRoot` 每次重组新建 `GlassTheme`（普通 class 引用相等），导致全部 `LocalGlassTheme` 读者失效重组。用 `remember(enabled, darkTheme, scheme, hazeState) { GlassTheme(...) }` 缓存；同时将 `LocalGlassTheme` 默认值中的 `HazeState()` 提升为顶层 `val` 避免每次读取分配。
3. **有效不透明度真机确认**（Minor 1）：`HazeStyle` 的 `backgroundColor` 与 `tints` 同色叠加（SrcOver）使有效不透明度为 1-(1-α)²：深色 55%→约 80%、浅色 72%→约 92%。真机验收时确认通透感；若偏实，将 `backgroundColor` 改为 `Color.Transparent`（仅保留 tints）并微调 alpha。
4. **色阶覆写作用域抽查**（Minor 5）：`surfaceContainerHigh/Low` 86% 覆写作用于整个主窗口子树（非仅弹层）。验收时抽查 SearchBar/Tooltip/卡片等使用这两个色阶的组件，确认半透明化均为预期。
5. **haze-materials 依赖评估**：目前未被任何代码引用（Glass.kt 使用自定义 HazeStyle）。若本任务完成后仍无引用，从 toml 与 build.gradle.kts 移除，仅保留 `haze`。
6. **抽屉 drawerShape 对齐**（Task 3 Minor 1）：`MainActivityScaffold.kt` 的 `ModalDrawerSheet` 未传 `drawerShape`，内部 Surface 默认 `DrawerDefaults.shape`（16dp 圆角，LTR 下位于 start 侧）与外层 `hanimeGlass(RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp))` 不一致，抽屉右上/右下内侧角（end 侧）出现只有模糊底没有内容的"新月形"缝隙。修复：给 `ModalDrawerSheet` 追加参数 `drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)`，与玻璃圆角完全一致（start 侧同为直角）。此为 Task 3 审查中唯一有可见视觉影响的缺陷。
7. **GlassRoot 块重缩进**（Task 3 Minor 2）：`MainActivityContent.kt` 中 `GlassRoot` 包裹后内部代码未重新缩进（功能无影响）。修复：将 `GlassRoot {` 与闭合 `}` 之间的全部代码整体多缩进 4 空格。先检查项目是否有 ktlint 任务（`.\gradlew.bat tasks --all | findstr -i ktlint`）；若无则手动重缩进。
8. **玻璃态按压 ripple 圆角裁剪**（Task 4 Minor，可选）：`VideoCardItem.kt` 的 `combinedClickable` 位于 `hanimeGlass` 之前（外侧），玻璃态下 color 变 Transparent 后 ripple 首次可见，按压时以矩形轮廓超出圆角（haze 模糊会软化，观感影响小）。修复（可选）：将 `.then(if (glassEnabled) Modifier.hanimeGlass(cardShape) else Modifier)` 移到 `combinedClickable(...)` 之前（ripple 一并被裁剪；经典模式 no-op 不受影响；hanimeGlass 的 border 将画于 ripple 之下，按压时边框仍可见）。若评估后认为观感可接受可不改，但需在交付说明中注明。

逐项确认：
1. `hanimeGlass` 所有调用点均传入了与组件自身一致的 `shape`（顶栏 bottomStart/bottomEnd 16dp、底栏 30dp、抽屉 topEnd/bottomEnd 28dp、卡片 8/12dp），无形状错位。
2. 视频页（`VideoTabScreens.kt` 自带 `HanimeTheme`、无 `GlassRoot`）内所有组件因 `LocalGlassTheme` 默认 `enabled=false` 自动保持经典——播放器 UI、评论栏（`CommentReplyBar`）、视频页弹层均不受玻璃影响（规格"排除播放器 UI"）。
3. 登录页（`LoginActivity`）、手动 Cookie 页（`ManualInputCookiesActivity`）、Cloudflare 验证页（`CloudflareActivity`）各自独立 `HanimeTheme`、无 `GlassRoot`——自动排除玻璃（规格"排除登录/关于页"）。
4. 经典模式（默认）下：所有 `if (glassEnabled)` 分支走原逻辑，与 v1.1.55 渲染路径一致（仅 `Modifier` 链上多一个空 `then(Modifier)`）。
5. `GlassRoot` 内嵌套 `MaterialTheme` 覆写仅影响玻璃态主界面子树，不影响视频页/登录页的对话框颜色。
6. AMOLED 纯黑 + 玻璃态同时开启时，`Theme.kt` 中 `amoled` 为 false，玻璃态优先（规格 §3.2）。

- [ ] **Step 3: 与规格文档对照（spec coverage）**

| 规格条目 | 实现位置 |
|---|---|
| §3.1 设置项「界面風格」經典/玻璃態，默认經典 | Task 2 |
| §3.2 玻璃态 × 10 主题色 × 深/浅开关正交；AMOLED 让位 | Task 1 Step 4 + GlassRoot 按 isSystemInDarkTheme 取深浅参数 |
| §3.3 数据流（GlassTheme CompositionLocal） | Task 1 Step 3 |
| §4.1 氛围光背景（主题色对角渐变 + 光斑） | Task 1 Step 3 + Task 3 Step 2d |
| §4.2 玻璃参数（深 55%/18px/白12%；浅 72%/16px/白45%） | Task 1 Step 3（GlassRoot 参数计算） |
| §4.3 组件清单：顶栏/卡片/底栏/抽屉 | Task 3、Task 4 |
| §4.3 组件清单：AlertDialog/BottomSheet | GlassRoot 色阶覆写（轻玻璃，零侵入）；排除项（评论栏/设置分组卡/播放器/登录关于页）见下 |
| §5.1 Haze 1.7.3 依赖（实施核实：无 2.x 稳定版） | Task 1 Step 1 |
| §5.2 挂载结构（hazeSource 根 + hazeEffect 容器） | Task 1 Step 3 |
| §5.3 降级策略（Android 12 以下轻玻璃） | Haze 在 API < 31 自动跳过 RenderEffect 仅保留 tint，即规格所述"半透明+描边无模糊" |
| §5.4 实施顺序 | Task 1→4 顺序 |

实现裁剪说明（已探索确认，与规格合理偏差）：
- **对话框**：独立 window，Haze 无法跨窗模糊，采用色阶覆写"轻玻璃"（半透明 86%），而非逐一加 hazeEffect。
- **评论栏**：仅存在于视频页（视频页整体排除玻璃），故不改造。
- **设置分组卡**：设置页条目平铺于 LazyColumn、无分组卡容器（`SettingItem.kt` 仅有行组件），为避免为玻璃新增容器改变布局结构，设置页依靠氛围光背景呈现玻璃氛围，不新增分组卡。

- [ ] **Step 4: 真机验收准备（等待用户"构建"指令）**

不主动构建。用户下达"构建"指令时执行标准流程：`app/build.gradle.kts` 中 `createVersion` 的 patch 从 55 递增为 56，构建 arm64-v8a 正式版，交付 `Han1meViewer-v1.1.56-final-arm64-v8a.apk`。

真机验收清单（交付后请用户验证）：
1. 默认启动 = 经典 UI（与 v1.1.55 一致）
2. 设置 → 主题与外观 → 界面风格 → 玻璃态：设置页本身立即变玻璃（recreate），列表滚动时顶栏/卡片/底栏有真模糊效果，背景可见主题色氛围光
3. 抽屉打开：抽屉面板半透明且背后内容被模糊
4. 任意对话框（如主题色选择）：半透明"轻玻璃"
5. 深色/浅色开关切换：玻璃透明度与描边随深浅变化（浅色更实、描边更亮）
6. AMOLED + 玻璃态：显示玻璃态而非纯黑
7. 切回经典：一切恢复 v1.1.55 原样
8. 视频播放页：无玻璃效果（播放器 UI 不变）
9. Android 11 及以下设备（如有）：玻璃降级为半透明无模糊，无崩溃
10. 10 个主题色 × 玻璃态各抽查 2 个：氛围光颜色跟随主题色变化
