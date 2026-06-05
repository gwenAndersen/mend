# Implementation Plan: Terraria Live Wallpaper

## Phase 1: Data Ingestion (Done)
- [x] C++ .wld Parser: Supports version 279 (1.4.4).
- [x] Optimized 8-byte `Tile` struct for memory efficiency.
- [x] RLE decompression for tile data.
- [x] Spawn point detection for camera centering.

## Phase 2: Basic Rendering (Done)
- [x] GLES 3.0 Renderer with ortho projection.
- [x] Camera system with pinch-to-zoom and drag-to-pan.
- [x] Diagnostic colors (Magenta/Red) to verify GPU communication.

## Phase 3: Texture Atlas & Auto-Tiling (Done)
- [x] Integration of `stb_image` for native PNG loading.
- [x] Extraction and mapping of `Tiles_*.png` and `Wall_*.png` textures.
- [x] Two-pass rendering (Pass 1: Walls, Pass 2: Tiles) for proper transparency.
- [x] Auto-tiling algorithm for standard blocks (Dirt, Stone, etc.).
- [x] Correct 32x32 UV mapping for walls with frame variety.

## Phase 4: Environmental Effects (Next)
- [ ] **Liquid Rendering:** Animated water, lava, and honey using the parsed liquid data.
- [ ] **Parallax Backgrounds:** Layered forest/mountain backgrounds that scroll at different speeds.
- [ ] **Day/Night Cycle:** Dynamic sky colors and global light level adjustments.

## Phase 5: Optimization & Polish (Upcoming)
- [ ] **Instanced Rendering:** Optimize the draw loop to handle millions of tiles more efficiently.
- [ ] **Vertex Buffer Objects (VBO) for Chunks:** Move from per-tile draws to chunk-based draws to reduce draw calls.
- [ ] **UI Polish:** Add settings for world selection and performance modes.
