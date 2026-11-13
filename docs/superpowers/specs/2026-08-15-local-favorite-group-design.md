# 本地收藏分组 设计文档

日期：2026-08-15
版本：v1.1.23

## 概述

为本地收藏（离线收藏）增加分组管理能力：收藏视频时可选择分组，收藏页按分组 Tab 筛选，支持新建/重命名/删除分组与批量移动。

## 数据模型

- 新增 `FavoriteGroupEntity`（表 `FavoriteGroupEntity`）：`id`(Int PK autoGenerate)、`name`(String，允许重名)、`createdAt`(Long)
- `FavoriteEntity` 新增字段 `groupId: Int? = null`（null = 未分组）
- HistoryDatabase version 5→6，Migration5To6：
  - `CREATE TABLE FavoriteGroupEntity`
  - `ALTER TABLE FavoriteEntity ADD COLUMN groupId INTEGER`（旧收藏自动归为未分组）

## DAO

- 新增 `FavoriteGroupDao`：loadAll（按 createdAt 排序 Flow）、getAll、findBy、insert（返回 Long）、update、delete、deleteAll、insertAll、clearGroup（`UPDATE FavoriteEntity SET groupId = NULL WHERE groupId = :groupId`）、`@Transaction deleteWithFavorites(group)`（先 clearGroup 再 delete，保证原子性）
- `FavoriteDao` 新增 `updateGroupIds(ids: List<Int>, groupId: Int?)`：批量移动

## Repository（DatabaseRepo）

- `object Favorite` 新增：`update(favorite)`、`moveToGroup(ids, groupId)`
- 新增 `object FavoriteGroup`：`loadAll()`、`getAll()`、`insert(name)`、`rename(group, name)`、`deleteWithFavorites(group)`

## 交互

### 收藏视频（VideoRouteActions.toggleLocalFavorite）

- 已收藏 → 直接取消收藏（不变）
- 未收藏 → 弹分组选择 AlertDialog（列表：「未分组」+ 各分组 + 「＋ 新建分组」）：
  - 选「未分组」→ 以 groupId=null 收藏
  - 选分组 → 以该 groupId 收藏
  - 选「新建分组」→ 弹输入框对话框 → 创建分组并以新 groupId 收藏
  - 取消对话框 → 不收藏
- 无分组时仍弹（列表仅「未分组」+「新建分组」）

### 收藏页（LocalFavoriteScreen）

- 顶部横滑 Tab 行（LazyRow + FilterChip）：`全部 | 未分组 | 各分组…`，尾部「＋」按钮打开新建分组对话框
- 选中 Tab 过滤显示；搜索框对当前 Tab 生效
- 长按分组 Chip → 分组管理对话框（重命名 / 删除）；删除确认提示"组内视频将移至未分组"
- 多选批量操作栏新增「移动到分组」按钮 → 弹分组选择对话框（未分组 + 各分组 + 新建）
- Tab 模型：`ALL_TAB = -1`（全部）、`UNGROUPED_TAB = -2`（未分组）、`>=0` = 分组 id
- 空状态：全部为空 → 现有空提示；分组为空 → "该组暂无收藏"

### 对话框（参照 DownloadDialogs.kt 模式）

- `CreateFavoriteGroupDialog`：新建分组（输入名称）
- `FavoriteGroupManageDialog`：长按分组弹出（重命名输入 + 删除）
- `MoveFavoriteGroupDialog`：批量移动选择

## 备份兼容

- BackupData 新增 `favoriteGroups: List<FavoriteGroupEntity>? = null`；BACKUP_VERSION 1→2
- 导出：`favoriteGroups = HistoryDatabase.instance.favoriteGroup.getAll()`
- 导入：先恢复 groups，再恢复 favorites；favorites 中 groupId 不在恢复的 groups 中 → 置 null（防脏引用）；旧备份（无 groups 字段）→ favorites 的 groupId 默认 null，全部归未分组

## 涉及文件

- 新增：`logic/entity/FavoriteGroupEntity.kt`、`logic/dao/FavoriteGroupDao.kt`、`ui/screen/home/local_favorite/` 下分组对话框（可并入 LocalFavoriteScreen.kt）
- 修改：`FavoriteEntity.kt`、`HistoryDatabase.kt`、`FavoriteDao.kt`、`DatabaseRepo.kt`、`BackupManager.kt`、`VideoRouteActions.kt`、`VideoTabScreens.kt`、`LocalFavoriteScreen.kt`、`LocalFavoriteRoute.kt`、`strings.xml`（values / values-en / values-ja / values-zh-rCN）、`app/build.gradle.kts`

## 验证

- 数据库迁移：旧版本升级后收藏数据保留且全部归未分组
- 收藏弹窗：选组/新建/取消三种路径
- 收藏页：Tab 过滤、搜索、批量移动、分组 CRUD、删除分组后视频回未分组
- 备份：新备份含分组，恢复后分组与归属正确；旧备份可恢复（全部未分组）
