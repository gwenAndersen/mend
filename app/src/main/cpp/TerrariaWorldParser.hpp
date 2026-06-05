#ifndef MEND_NATIVE_TERRARIAWORLDPARSER_HPP
#define MEND_NATIVE_TERRARIAWORLDPARSER_HPP

#include <string>
#include <vector>
#include <fstream>
#include <android/asset_manager.h>
#include <cstdint>

// Helper function to check if a specific bit in a byte is set
inline bool isBitSet(uint8_t byte, int bitPosition) {
    return (byte >> bitPosition) & 1;
}

// Represents a single tile in the world
struct Tile {
    uint16_t type = 0;
    uint16_t wall = 0;
    int16_t frameX = -1;
    int16_t frameY = -1;
    uint8_t liquidAmount = 0;
    uint8_t liquidType = 0;
    uint16_t flags = 0;
    uint8_t tileColor = 0;
    uint8_t wallColor = 0;

    // Flag bits
    static constexpr uint16_t ACTIVE = 1 << 0;
    static constexpr uint16_t WALL_ACTIVE = 1 << 1;
    static constexpr uint16_t WIRE_RED = 1 << 2;
    static constexpr uint16_t WIRE_GREEN = 1 << 3;
    static constexpr uint16_t WIRE_BLUE = 1 << 4;
    static constexpr uint16_t WIRE_YELLOW = 1 << 5;
    static constexpr uint16_t HALF_BRICK = 1 << 6;
    static constexpr uint16_t ACTUATOR = 1 << 7;
    static constexpr uint16_t INACTIVE = 1 << 8;
    static constexpr uint16_t LAVA = 1 << 9;
    static constexpr uint16_t HONEY = 1 << 10;
    static constexpr uint16_t SHIMMER = 1 << 11;

    bool isActive() const { return flags & ACTIVE; }
    void setActive(bool v) { if (v) flags |= ACTIVE; else flags &= ~ACTIVE; }
    bool isWallActive() const { return flags & WALL_ACTIVE; }
    void setWallActive(bool v) { if (v) flags |= WALL_ACTIVE; else flags &= ~WALL_ACTIVE; }
};

class TerrariaWorldParser {
public:
    std::string worldName;
    int32_t worldWidth = 0;
    int32_t worldHeight = 0;
    int32_t spawnX = 0;
    int32_t spawnY = 0;
    std::vector<Tile> tiles;

    TerrariaWorldParser(AAssetManager* assetManager, const std::string& filePath);
    bool parse();

private:
    AAssetManager* assetManager;
    std::string filePath;
    AAsset* asset;
    std::vector<int32_t> sectionPointers;
    std::vector<bool> tileFrameImportant;

    // Helper to read from the AAsset
    template<typename T>
    bool read(T& value);

    // Helper to read a Pascal-style string
    std::string readString();

    // Specific parser for Terraria's 7-bit encoded integer
    int32_t read7BitEncodedInt();

    // Section-specific parsers
    bool parseHeader();
    bool parseWorldInfo();
    bool parseTileData();
};

#endif //MEND_NATIVE_TERRARIAWORLDPARSER_HPP
