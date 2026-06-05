# Remaining Tasks for Terraria Live Wallpaper

### 1. Environmental Effects 
- **Liquid Rendering:**
  - Parse liquid types (Water, Lava, Honey, Shimmer) accurately.
  - Implement a specialized shader or texture animation for flowing liquids.
- **Parallax Backgrounds:**
  - Load background layers from the texture pack.
  - Implement scrolling logic in `native-lib.cpp` that responds to camera movement.

### 2. Performance Optimization
- **Chunking:**
  - Currently, we draw every tile individually using a `for` loop, which is heavy on draw calls.
  - **Plan:** Divide the world into 16x16 or 32x32 tile chunks and upload them as static VBOs.
- **Instancing:** 
  - Use `glDrawArraysInstanced` for repetitive tiles to reduce CPU overhead.

### 3. Polish & Logic
- **Advanced Framing:** 
  - Expand the `getStandardFrame` logic to support more complex Terraria connections (slopes, half-bricks, and 47-frame tile sets).
- **Time of Day:**
  - Link the sky color to the actual device time or a custom game-time loop.
