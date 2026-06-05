# Future Implementation: Figma-to-Wallpaper Workflow

## Overview
This document outlines the strategy for integrating custom-designed "arts" from Figma into the **mend** live wallpaper engine. The goal is to move beyond tile-based worlds into conceptual, layered, and animated scenes.

## 1. Scene Architecture: Layered Parallax
The wallpaper will be treated as a "depth-aware" scene rather than a flat image.
- **Background Layer:** Static or slow-moving distant elements (e.g., sky, far mountains).
- **Midground Layer:** Primary scene elements that respond moderately to movement.
- **Foreground Layer:** Close-up objects with high parallax sensitivity.
- **Interactive Layer:** Objects that react to touch, swipes, or gyroscope data.

## 2. Figma Export Strategy
To implement these designs, elements should be exported as follows:
- **Transparent Slices:** Individual objects or layers exported as optimized PNG or WebP files.
- **Vector Paths:** Complex shapes that need to remain sharp at any scale can be exported as SVGs for conversion to `VectorDrawable`.
- **Z-Index Mapping:** A specification of depth for each exported slice to determine the parallax intensity.
- **Animation Specs:** Descriptions of movement (e.g., "floating at 0.5Hz", "pulse opacity every 2s").

## 3. Technical Integration in `mend`
- **Asset Pipeline:** Implement an automated or semi-automated way to load these slices into a Texture Atlas.
- **Parallax Shader:** A dedicated OpenGL shader to handle the X/Y offsets of layers based on user interaction (gyro/swipe).
- **Animation Controller:** A Kotlin-based system to drive "alive" properties (rotation, scaling, alpha) of specific Figma components.
- **Dynamic Layout:** Use Figma constraints to ensure the art scales correctly across different Android screen aspect ratios.

## 4. Concepts to Explore
- **Environmental Particles:** Adding dust, rain, or light rays as programmatically generated layers on top of Figma assets.
- **Adaptive Lighting:** Changing the tint of Figma layers based on the time of day or device battery status.
- **Interactive Triggers:** Tapping a specific "Figma object" triggers a unique animation or opens the wallpaper settings.
