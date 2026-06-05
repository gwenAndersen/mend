#include "TerrariaWorldParser.hpp"
#include <android/log.h>

#define LOG_TAG "TerrariaWorldParser"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

TerrariaWorldParser::TerrariaWorldParser(AAssetManager* manager, const std::string& path)
    : assetManager(manager), filePath(path), asset(nullptr) {}

// --- Generic and Helper Read Functions ---

template<typename T>
bool TerrariaWorldParser::read(T& value) {
    return AAsset_read(asset, &value, sizeof(T)) == sizeof(T);
}

std::string TerrariaWorldParser::readString() {
    int32_t length = read7BitEncodedInt();
    if (length < 0) return "";
    std::vector<char> buffer(length);
    if (AAsset_read(asset, buffer.data(), length) != length) {
        LOGE("Failed to read string data.");
        return "";
    }
    return std::string(buffer.data(), length);
}

int32_t TerrariaWorldParser::read7BitEncodedInt() {
    int32_t count = 0;
    int shift = 0;
    uint8_t b;
    do {
        if (shift == 5 * 7) {
            LOGE("Invalid 7-bit encoded integer format.");
            return -1;
        }
        if (AAsset_read(asset, &b, 1) != 1) {
            LOGE("Failed to read byte for 7-bit encoded int.");
            return -1;
        }
        count |= (static_cast<int32_t>(b & 0x7F)) << shift;
        shift += 7;
    } while ((b & 0x80) != 0);
    return count;
}


// --- Section-specific Parsers ---

bool TerrariaWorldParser::parseHeader() {
    LOGI("Parsing file format header...");
    int32_t version;
    if (!read(version)) { LOGE("Failed to read version."); return false; }
    LOGI("World version: %d", version);

    // Read "relogic" magic word (7 bytes)
    std::vector<char> magicWordBuffer(7);
    if (AAsset_read(asset, magicWordBuffer.data(), 7) != 7) {
        LOGE("Failed to read magic word.");
        return false;
    }
    // std::string magicWord(magicWordBuffer.data(), 7); // For debugging
    // LOGI("Magic word: %s", magicWord.c_str());

    // Read file type (1 byte)
    uint8_t fileType;
    if (!read(fileType)) { LOGE("Failed to read fileType."); return false; }
    // LOGI("File type: %d", fileType);

    // Read revision number (uint32)
    uint32_t revision_num;
    if (!read(revision_num)) { LOGE("Failed to read revision_num."); return false; }
    // LOGI("Revision Number: %u", revision_num);

    // Read favorite flag (uint64) - Boolean mask (fav/cloud save status)
    uint64_t favorite_flag;
    if (!read(favorite_flag)) { LOGE("Failed to read favorite_flag."); return false; }
    // LOGI("Favorite Flag: %llu", favorite_flag);

    // Read section count (int16)
    int16_t numSections;
    if (!read(numSections)) { LOGE("Failed to read numSections."); return false; }
    // LOGI("Found %d sections.", numSections);

    // Read section pointers (int32[])
    sectionPointers.resize(numSections);
    if (AAsset_read(asset, sectionPointers.data(), numSections * sizeof(int32_t)) != numSections * sizeof(int32_t)) {
        LOGE("Failed to read section pointers.");
        return false;
    }

    // Read TileFrameImportant bit array
    int16_t tileTypeCount;
    if (!read(tileTypeCount)) {
        LOGE("Failed to read tileTypeCount.");
        return false;
    }

    tileFrameImportant.resize(tileTypeCount);
    int byteCount = (tileTypeCount + 7) / 8;
    std::vector<uint8_t> bitBuffer(byteCount);
    if (AAsset_read(asset, bitBuffer.data(), byteCount) != byteCount) {
        LOGE("Failed to read tileFrameImportant bits.");
        return false;
    }

    for (int i = 0; i < tileTypeCount; ++i) {
        tileFrameImportant[i] = (bitBuffer[i / 8] >> (i % 8)) & 1;
    }

    LOGI("Successfully parsed file format header. Found %d sections and %d tile types.", numSections, tileTypeCount);
    return true;
}

bool TerrariaWorldParser::parseWorldInfo() {
    LOGI("Parsing world info...");
    if (sectionPointers.empty()) {
        LOGE("Section pointers not available for world info.");
        return false;
    }
    if (sectionPointers.size() < 1) { // sectionPointers[0] is for World Header
        LOGE("Section pointer for World Header (index 0) not available.");
        return false;
    }
    // Seek to the World Header section (first pointer in sectionPointers)
    AAsset_seek(asset, sectionPointers[0], SEEK_SET);

    worldName = readString();
    LOGI("World Name: %s", worldName.c_str());

    readString(); // Skip Seed

    int64_t generatorVersion;
    if (!read(generatorVersion)) { LOGE("Failed to read GeneratorVersion."); return false; }

    std::vector<uint8_t> uuid_bytes(16);
    if (AAsset_read(asset, uuid_bytes.data(), 16) != 16) { LOGE("Failed to read UUID."); return false; }

    int32_t worldId;
    if (!read(worldId)) { LOGE("Failed to read WorldID."); return false; }

    // Read world bounds (pixels)
    int32_t dummy_left, dummy_right, dummy_top, dummy_bottom;
    if (!read(dummy_left)) { LOGE("Failed to read LeftWorld."); return false; }
    if (!read(dummy_right)) { LOGE("Failed to read RightWorld."); return false; }
    if (!read(dummy_top)) { LOGE("Failed to read TopWorld."); return false; }
    if (!read(dummy_bottom)) { LOGE("Failed to read BottomWorld."); return false; }

    if (!read(worldHeight)) { LOGE("Failed to read MaxTilesY (worldHeight)."); return false; } // MaxTilesY
    if (!read(worldWidth)) { LOGE("Failed to read MaxTilesX (worldWidth)."); return false; }  // MaxTilesX

    // Skip to Spawn Tile X/Y
    // 1.4.4 Section 0 structure after MaxTilesX:
    // GameMode (4), SecretSeeds (8), CreationTime (8), MoonType (1), TreeX (3*4), TreeStyle (4*4), 
    // CaveBackX (3*4), CaveBackStyle (4*4), BackStyles (3*4)
    AAsset_seek(asset, 4 + 8 + 8 + 1 + 12 + 16 + 12 + 16 + 12, SEEK_CUR);

    if (!read(spawnX)) { LOGE("Failed to read SpawnX."); return false; }
    if (!read(spawnY)) { LOGE("Failed to read SpawnY."); return false; }

    LOGI("World Dimensions: %d x %d, Spawn: %d, %d", worldWidth, worldHeight, spawnX, spawnY);
    return true;
}

bool TerrariaWorldParser::parseTileData() {
    LOGI("Attempting to parse tile data for world %s with dimensions: %d x %d. Total tiles expected: %lld.",
         worldName.c_str(), worldWidth, worldHeight, static_cast<long long>(worldWidth) * worldHeight);

    if (worldWidth <= 0 || worldHeight <= 0) {
        LOGE("Invalid world dimensions (%d x %d) for tile parsing.", worldWidth, worldHeight);
        return false;
    }
    // Seek to the Tile Data section (second pointer in sectionPointers)
    if (sectionPointers.size() < 2) {
        LOGE("Tile data section pointer not available.");
        return false;
    }
    AAsset_seek(asset, sectionPointers[1], SEEK_SET);

    try {
        tiles.assign(static_cast<size_t>(worldWidth) * worldHeight, Tile());
    } catch (const std::exception& e) {
        LOGE("Failed to allocate tiles vector: %s", e.what());
        return false;
    }

    int totalParsedTiles = 0;

    for (int x = 0; x < worldWidth; ++x) {
        for (int y = 0; y < worldHeight; ) {
            uint8_t flags1, flags2 = 0, flags3 = 0;
            if (!read(flags1)) return false;

            if (flags1 & 1) { // Bit 0: Has flags2
                if (!read(flags2)) return false;
                if (flags2 & 1) { // Bit 0 of flags2: Has flags3
                    if (!read(flags3)) return false;
                }
            }

            Tile tile;
            // Block (Tile)
            if (flags1 & 2) { // Bit 1: Has tile
                tile.setActive(true);
                uint8_t low;
                if (!read(low)) return false;
                uint16_t tileType = low;
                if (flags1 & 32) { // Bit 5: Has high byte
                    uint8_t high;
                    if (!read(high)) return false;
                    tileType |= (static_cast<uint16_t>(high) << 8);
                }
                tile.type = tileType;

                if (tileType < tileFrameImportant.size() && tileFrameImportant[tileType]) {
                    if (!read(tile.frameX) || !read(tile.frameY)) return false;
                }

                if (flags3 & 8) { // Bit 3 of flags3: Tile color
                    if (!read(tile.tileColor)) return false;
                }
            }

            // Wall
            if (flags1 & 4) { // Bit 2: Has wall
                tile.setWallActive(true);
                uint8_t wallLow;
                if (!read(wallLow)) return false;
                uint16_t wallType = wallLow;
                if (flags3 & 64) { // Bit 6 of flags3: Has high byte for wall
                    uint8_t wallHigh;
                    if (!read(wallHigh)) return false;
                    wallType |= (static_cast<uint16_t>(wallHigh) << 8);
                }
                tile.wall = wallType;

                if (flags3 & 16) { // Bit 4 of flags3: Wall color
                    if (!read(tile.wallColor)) return false;
                }
            }

            // Liquid
            uint8_t liquidType = (flags1 & 24) >> 3; // Bits 3-4
            if (liquidType != 0) {
                if (!read(tile.liquidAmount)) return false;
                tile.liquidType = liquidType;
                if (liquidType == 2) tile.flags |= Tile::LAVA;
                if (liquidType == 3) tile.flags |= Tile::HONEY;
                if (flags3 & 128) tile.flags |= Tile::SHIMMER;
            }

            // Flags from flags2
            if (flags2 > 1) {
                if (flags2 & 2) tile.flags |= Tile::WIRE_RED;
                if (flags2 & 4) tile.flags |= Tile::WIRE_BLUE;
                if (flags2 & 8) tile.flags |= Tile::WIRE_GREEN;
                // Bits 4-6: Slope/Half-brick (skipped for now)
                if (flags2 & 128) tile.flags |= Tile::ACTUATOR;
            }

            // Flags from flags3
            if (flags3 > 0) {
                if (flags3 & 2) tile.flags |= Tile::WIRE_YELLOW;
                if (flags3 & 4) tile.flags |= Tile::INACTIVE;
            }

            // RLE
            int16_t rle = 0;
            uint8_t rleMode = (flags1 & 192) >> 6; // Bits 6-7
            if (rleMode == 1) { // 01 (decimal 64)
                uint8_t rle8;
                if (!read(rle8)) return false;
                rle = rle8;
            } else if (rleMode == 2) { // 10 (decimal 128)
                if (!read(rle)) return false;
            }

            for (int i = 0; i <= rle; ++i) {
                if (y < worldHeight) {
                    tiles[y * worldWidth + x] = tile;
                    y++;
                    totalParsedTiles++;
                } else {
                    LOGE("RLE overflow at %d,%d", x, y);
                    break;
                }
            }
        }
    }
    LOGI("Successfully parsed %d tiles.", totalParsedTiles);
    return true;
}


// --- Main Parse Function ---

bool TerrariaWorldParser::parse() {
    asset = AAssetManager_open(assetManager, filePath.c_str(), AASSET_MODE_BUFFER);
    if (!asset) {
        LOGE("Failed to open world file: %s", filePath.c_str());
        return false;
    }

    if (!parseHeader()) {
        LOGE("Failed to parse file header.");
        AAsset_close(asset);
        return false;
    }

    if (!parseWorldInfo()) {
        LOGE("Failed to parse world info.");
        AAsset_close(asset);
        return false;
    }

    if (!parseTileData()) {
        LOGE("Failed to parse tile data.");
        AAsset_close(asset);
        return false;
    }

    AAsset_close(asset);
    LOGI("Finished parsing world file: %s (%dx%d)", worldName.c_str(), worldWidth, worldHeight);
    return true;
}
