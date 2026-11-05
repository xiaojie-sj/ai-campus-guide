# 地图2 · CLI-Anything + Blender 校园建模成果

> 严格依据 `docs/assets/maps/地图2.png` 的标注、建筑轮廓与相对位置构建；未标注的院落式体量按宿舍/配套用房处理。
> 底图没有标注层数、立面做法与内部庭院细节，相关部分是合理推断，不宣称与实际校园完全一致。

## 1. 管线

| 环节 | 位置 / 命令 |
| --- | --- |
| CLI-Anything 仓库 | `%USERPROFILE%\.codex\vendor\CLI-Anything` |
| Codex Skill | `%USERPROFILE%\.codex\skills\cli-anything` |
| Blender Harness | `%USERPROFILE%\.codex\vendor\CLI-Anything\blender\agent-harness`（`pip install -e` 可编辑安装） |
| Blender | `blender.exe`（5.2.4 LTS RC 或兼容版本，需在 PATH 中） |
| CLI 入口 | `cli-anything-blender --project tools\campus_map2\campus-scene.blend-cli.json <command>` |

Blender 侧由 `build.py` 以 bpy API 生成几何、材质、灯光与相机，保存 `.blend` 并导出 GLB；
CLI-Anything 负责场景工程状态、对象/材质/相机/渲染指令与预览校验（`scene info`、`render execute`、`preview`）。

## 2. 资产

| 类型 | 路径 | 说明 |
| --- | --- | --- |
| GLB（前端使用） | `frontend\public\models\campus-buildings.glb` | 约 24 MB，68 mesh / 44 材质 / 503,350 三角面 |
| Blender 工程 | `docs\models\campus-buildings.blend` | 可直接编辑 |
| 布局真源 | `tools\campus_map2\layout.py` | 所有建筑/道路/广场的像素坐标 |
| 生成器 | `tools\campus_map2\build.py` | 建模、材质、相机、导出 |
| 后端迁移 | `backend\src\main\resources\db\migration\postgresql\V8__map2_campus_alignment.sql` | 点位/建筑轮廓/路网/相机预设 |
| 布局清单 | `docs\03-modeling\campus-layout.json` | 点位与建筑 block 坐标 |

## 3. 坐标约定

```
PX_PER_UNIT = 33
ORIGIN_X = 853, ORIGIN_Y = 660
world_x = (px - 853) / 33
world_z = (py - 660) / 33
Blender Y = -world_z     # Blender 中 Z 轴向上
```

模型范围约 `X: -34..34`、`Z: -26..22`、`Y: 0..3.0`（单位：world，约等于 33 m/格）。

## 4. 内容清单

- **北区宿舍**：14、13、12、11、10、9、8、7、6、5、4、1、2、3 栋，含阳台、栏板、空调机位/太阳能热水器、屋面楼梯间与水箱。
- **教学科研**：生物楼（回字形院落 + 南侧凹入入口）、科技楼（中部塔楼 + 两翼退台）、化学楼（低层连廊）。
- **行政服务**：综合楼（U 形围合）、招生就业工作处（L 形）、中通快递（长条服务楼 + 南侧雨棚）。
- **出入口**：北门、东门门楼 + 门岗 + 校名牌 + 道闸。
- **道路**：学院路、宝成路、航空路及校内环路/纵路，含路缘石、人行道、中心虚线、斑马线。
- **景观**：同大广场（铺装分格 + 中央圆形铺装）、北门/东门广场、生物楼前广场与圆形景观广场、运动场草坪及围网看台、停车场、行道树、路灯、长椅、车辆。
- **南区**：未标注的成排院落按宿舍处理（含 `s13a`）。

## 5. 复现命令

```powershell
# 1) 重新生成模型（blend + glb + 6 张验证渲染）
blender.exe --background --factory-startup `
  --python tools\campus_map2\build.py

# 2) 重新生成 V8 SQL 与布局清单
python tools\campus_map2\export_backend.py

# 3) 前端构建与测试
cd frontend; npm run build; npm test

# 4) 网页端截图（需先 npm run dev）
node tools\campus_map2\scripts\web_screenshot.js `
  "http://127.0.0.1:5173/campus-viewer.html" `
  "docs\03-modeling\renders\07_web_preview.png"
```

## 6. 验证

- `renders/02_overview_top.png` 与 `docs/assets/maps/地图2.png` 同尺寸（1706×1320）正交顶视对齐，见 `00_map_vs_top.png`。
- GLB 解析：68 nodes / 68 meshes / 44 materials / 503,350 triangles。
- 网页端实际加载：`范围 68.0 × 52.0 · 高度 3.0 · 约 503k 三角面`，见 `renders/07_web_preview.png`。
- 前端 `vue-tsc + vite build` 通过；Vitest 2 个文件 5 个用例全部通过。
- 旧版渲染与 smoke test 属于一次性开发产物，不进入 Git 仓库。

## 7. 已知不确定性

1. 底图无层数标注，层高与层数是按体量与用途的合理推断。
2. 未标注的小体量按宿舍/配套房处理，未逐一对应真实楼名。
3. 生物楼、综合楼的内院与入口细节由外轮廓推断。
4. 运动场底图未画跑道，因此只做草坪 + 围网 + 看台，不臆造跑道线。
