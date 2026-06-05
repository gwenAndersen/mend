#include <jni.h>
#include <string>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <GLES3/gl3.h>
#include "TerrariaWorldParser.hpp"
#include "MathUtils.hpp"
#include <algorithm>
#include <map>
#include <vector>

#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

#define LOG_TAG "NativeLib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct TextureInfo {
    GLuint id = 0;
    int width = 0;
    int height = 0;
};

// Global state
std::unique_ptr<TerrariaWorldParser> worldParser;
GLuint shaderProgram;
GLuint vbo;
GLint mvpLocation, colorLocation, texLocation, useTexLocation, uvOffsetLocation, uvScaleLocation;
std::map<uint16_t, TextureInfo> tileTextures;
std::map<uint16_t, TextureInfo> wallTextures;
AAssetManager* globalAssetManager = nullptr;

struct Camera {
    float x = 0.0f;
    float y = 0.0f;
    float zoom = 1.0f;
    int screenWidth = 0;
    int screenHeight = 0;
} camera;

const char* vertexShaderSource = R"glsl(#version 300 es
    layout (location = 0) in vec2 aPos;
    layout (location = 1) in vec2 aTexCoord;
    uniform mat4 uMVP;
    uniform vec2 uUVOffset;
    uniform vec2 uUVScale;
    out vec2 vTexCoord;
    void main() {
        gl_Position = uMVP * vec4(aPos, 0.0, 1.0);
        vTexCoord = aTexCoord * uUVScale + uUVOffset;
    }
)glsl";

const char* fragmentShaderSource = R"glsl(#version 300 es
    precision mediump float;
    uniform vec4 uColor;
    uniform sampler2D uTexture;
    uniform bool uUseTexture;
    in vec2 vTexCoord;
    out vec4 FragColor;
    void main() {
        if (uUseTexture) {
            FragColor = texture(uTexture, vTexCoord) * uColor;
        } else {
            FragColor = uColor;
        }
        if (FragColor.a < 0.1) discard;
    }
)glsl";

TextureInfo loadTexture(const std::string& path) {
    TextureInfo info;
    if (!globalAssetManager) return info;
    
    AAsset* asset = AAssetManager_open(globalAssetManager, path.c_str(), AASSET_MODE_BUFFER);
    if (!asset) return info;

    off_t length = AAsset_getLength(asset);
    std::vector<unsigned char> buffer(length);
    AAsset_read(asset, buffer.data(), length);
    AAsset_close(asset);

    int width, height, channels;
    unsigned char* data = stbi_load_from_memory(buffer.data(), (int)length, &width, &height, &channels, 4);
    if (!data) {
        LOGE("Failed to load texture: %s", path.c_str());
        return info;
    }

    GLuint textureID;
    glGenTextures(1, &textureID);
    glBindTexture(GL_TEXTURE_2D, textureID);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

    stbi_image_free(data);
    LOGI("Loaded texture: %s (%dx%d)", path.c_str(), width, height);
    
    info.id = textureID;
    info.width = width;
    info.height = height;
    return info;
}

TextureInfo getTileTexture(uint16_t type) {
    if (tileTextures.find(type) != tileTextures.end()) return tileTextures[type];
    std::string path = "Textures/Tiles_" + std::to_string(type) + ".png";
    TextureInfo info = loadTexture(path);
    tileTextures[type] = info;
    return info;
}

TextureInfo getWallTexture(uint16_t type) {
    if (wallTextures.find(type) != wallTextures.end()) return wallTextures[type];
    std::string path = "Textures/Wall_" + std::to_string(type) + ".png";
    TextureInfo info = loadTexture(path);
    wallTextures[type] = info;
    return info;
}

GLuint compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint success;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &success);
    if (!success) {
        char infoLog[512];
        glGetShaderInfoLog(shader, 512, nullptr, infoLog);
        LOGE("Shader Compilation Error: %s", infoLog);
    }
    return shader;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mend_Live3dWallpaperService_00024Live3dWallpaperEngine_initEngine(
        JNIEnv* env,
        jobject /* this */,
        jobject assetManager,
        jstring filePath) {

    globalAssetManager = AAssetManager_fromJava(env, assetManager);
    const char* nativeFilePath = env->GetStringUTFChars(filePath, 0);

    LOGI("Initializing native engine with file: %s", nativeFilePath);

    worldParser = std::make_unique<TerrariaWorldParser>(globalAssetManager, nativeFilePath);
    if (!worldParser->parse()) {
        LOGE("Failed to parse the world file.");
    } else {
        camera.x = (float)worldParser->spawnX;
        camera.y = (float)worldParser->spawnY;
        camera.zoom = 50.0f;
        LOGI("Camera initialized at spawn: %.2f, %.2f", camera.x, camera.y);
    }

    env->ReleaseStringUTFChars(filePath, nativeFilePath);
}

void setupGL() {
    GLuint vertexShader = compileShader(GL_VERTEX_SHADER, vertexShaderSource);
    GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentShaderSource);
    shaderProgram = glCreateProgram();
    glAttachShader(shaderProgram, vertexShader);
    glAttachShader(shaderProgram, fragmentShader);
    glLinkProgram(shaderProgram);
    
    GLint linked;
    glGetProgramiv(shaderProgram, GL_LINK_STATUS, &linked);
    if (!linked) {
        char infoLog[512];
        glGetProgramInfoLog(shaderProgram, 512, nullptr, infoLog);
        LOGE("Shader Linking Error: %s", infoLog);
    } else {
        LOGI("Shader Program linked successfully.");
    }
    
    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);

    mvpLocation = glGetUniformLocation(shaderProgram, "uMVP");
    colorLocation = glGetUniformLocation(shaderProgram, "uColor");
    texLocation = glGetUniformLocation(shaderProgram, "uTexture");
    useTexLocation = glGetUniformLocation(shaderProgram, "uUseTexture");
    uvOffsetLocation = glGetUniformLocation(shaderProgram, "uUVOffset");
    uvScaleLocation = glGetUniformLocation(shaderProgram, "uUVScale");

    float vertices[] = {
        // Pos      // Tex
        0.0f, 0.0f, 0.0f, 0.0f,
        1.0f, 0.0f, 1.0f, 0.0f,
        0.0f, 1.0f, 0.0f, 1.0f,
        1.0f, 1.0f, 1.0f, 1.0f
    };

    glGenBuffers(1, &vbo);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mend_Live3dWallpaperService_00024Live3dWallpaperEngine_onSurfaceCreated(
        JNIEnv* env,
        jobject /* this */) {
    LOGI("Surface created.");
    setupGL();
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mend_Live3dWallpaperService_00024Live3dWallpaperEngine_onSurfaceChanged(
        JNIEnv* env,
        jobject /* this */,
        jint width,
        jint height) {
    LOGI("Surface changed: %dx%d", width, height);
    glViewport(0, 0, width, height);
    camera.screenWidth = width;
    camera.screenHeight = height;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mend_Live3dWallpaperService_00024Live3dWallpaperEngine_updateCamera(
        JNIEnv* env,
        jobject /* this */,
        jfloat dx,
        jfloat dy,
        jfloat dz) {
    camera.x += dx * (camera.zoom / camera.screenHeight);
    camera.y += dy * (camera.zoom / camera.screenHeight);
    camera.zoom = std::max(1.0f, camera.zoom * dz);

    if (worldParser) {
        camera.x = std::clamp(camera.x, 0.0f, (float)worldParser->worldWidth);
        camera.y = std::clamp(camera.y, 0.0f, (float)worldParser->worldHeight);
    }
}

// Helper to get connected frame for standard tiles (Dirt, Stone, etc.)
void getStandardFrame(int x, int y, uint16_t type, int16_t& outX, int16_t& outY) {
    if (!worldParser) return;

    bool up = (y > 0 && worldParser->tiles[(y-1) * worldParser->worldWidth + x].isActive() && worldParser->tiles[(y-1) * worldParser->worldWidth + x].type == type);
    bool down = (y < worldParser->worldHeight-1 && worldParser->tiles[(y+1) * worldParser->worldWidth + x].isActive() && worldParser->tiles[(y+1) * worldParser->worldWidth + x].type == type);
    bool left = (x > 0 && worldParser->tiles[y * worldParser->worldWidth + (x-1)].isActive() && worldParser->tiles[y * worldParser->worldWidth + (x-1)].type == type);
    bool right = (x < worldParser->worldWidth-1 && worldParser->tiles[y * worldParser->worldWidth + (x+1)].isActive() && worldParser->tiles[y * worldParser->worldWidth + (x+1)].type == type);

    int mask = (up ? 1 : 0) | (right ? 2 : 0) | (down ? 4 : 0) | (left ? 8 : 0);

    // Standard 16-frame cardinal lookup table (18px offsets)
    static const int16_t lookupX[] = {54, 54, 72, 0, 54, 72, 0, 0, 72, 36, 90, 18, 36, 36, 18, 18};
    static const int16_t lookupY[] = {0, 36, 0, 36, 18, 36, 0, 18, 18, 36, 0, 36, 0, 18, 0, 18};

    outX = lookupX[mask];
    outY = lookupY[mask];

    // For the center tile (mask 15), add some variety if it's a large mass
    if (mask == 15) {
        int variation = (x % 3 + (y % 3) * 3) % 3;
        if (variation > 0) {
            outX = 18 + (variation * 18);
            outY = 54; // Standard location for extra center variations
        }
    }
}

void getWallFrame(int x, int y, uint16_t type, int16_t& outX, int16_t& outY) {
    if (!worldParser) return;

    // Walls usually blend with any other wall type in Terraria
    bool up = (y > 0 && worldParser->tiles[(y-1) * worldParser->worldWidth + x].isWallActive());
    bool down = (y < worldParser->worldHeight-1 && worldParser->tiles[(y+1) * worldParser->worldWidth + x].isWallActive());
    bool left = (x > 0 && worldParser->tiles[y * worldParser->worldWidth + (x-1)].isWallActive());
    bool right = (x < worldParser->worldWidth-1 && worldParser->tiles[y * worldParser->worldWidth + (x+1)].isWallActive());

    int mask = (up ? 1 : 0) | (right ? 2 : 0) | (down ? 4 : 0) | (left ? 8 : 0);
    
    int row = 4; // Default to Center
    switch(mask) {
        case 6:  row = 0; break; // R+D (Top Left)
        case 14: row = 1; break; // L+R+D (Top Mid)
        case 12: row = 2; break; // L+D (Top Right)
        case 7:  row = 3; break; // U+R+D (Mid Left)
        case 15: row = 4; break; // U+L+R+D (Center)
        case 13: row = 5; break; // U+L+D (Mid Right)
        case 3:  row = 6; break; // U+R (Bot Left)
        case 11: row = 7; break; // U+L+R (Bot Mid)
        case 9:  row = 8; break; // U+L (Bot Right)
    }

    int variation = (x + y) % 3;
    outX = variation * 36;
    outY = row * 36;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mend_Live3dWallpaperService_00024Live3dWallpaperEngine_onDrawFrame(
        JNIEnv* env,
        jobject /* this */) {

    glClearColor(0.53f, 0.81f, 0.92f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    if (!worldParser || worldParser->tiles.empty()) return;

    glUseProgram(shaderProgram);
    glBindBuffer(GL_ARRAY_BUFFER, vbo);
    
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)(2 * sizeof(float)));

    float aspect = (float)camera.screenWidth / (float)camera.screenHeight;
    float halfHeight = camera.zoom / 2.0f;
    float halfWidth = halfHeight * aspect;

    math::mat4 projection = math::ortho(
        camera.x - halfWidth, camera.x + halfWidth,
        camera.y + halfHeight, camera.y - halfHeight,
        -1.0f, 1.0f
    );

    int startX = std::max(0, (int)(camera.x - halfWidth - 1));
    int endX = std::min(worldParser->worldWidth - 1, (int)(camera.x + halfWidth + 1));
    int startY = std::max(0, (int)(camera.y - halfHeight - 1));
    int endY = std::min(worldParser->worldHeight - 1, (int)(camera.y + halfHeight + 1));

    for (int x = startX; x <= endX; ++x) {
        for (int y = startY; y <= endY; ++y) {
            const Tile& tile = worldParser->tiles[y * worldParser->worldWidth + x];
            
            // Pass 1: Draw Wall
            if (tile.isWallActive()) {
                TextureInfo texInfo = getWallTexture(tile.wall);
                if (texInfo.id != 0) {
                    math::mat4 mvp = projection;
                    mvp.m[12] = projection.m[0] * x + projection.m[12];
                    mvp.m[13] = projection.m[5] * y + projection.m[13];
                    glUniformMatrix4fv(mvpLocation, 1, GL_FALSE, mvp.m);

                    glActiveTexture(GL_TEXTURE0);
                    glBindTexture(GL_TEXTURE_2D, texInfo.id);
                    glUniform1i(texLocation, 0);
                    glUniform1i(useTexLocation, 1);
                    
                    int16_t fx, fy;
                    getWallFrame(x, y, tile.wall, fx, fy);

                    glUniform2f(uvOffsetLocation, (float)fx / (float)texInfo.width, (float)fy / (float)texInfo.height);
                    glUniform2f(uvScaleLocation, 32.0f / (float)texInfo.width, 32.0f / (float)texInfo.height);
                    glUniform4f(colorLocation, 0.6f, 0.6f, 0.6f, 1.0f); // Walls are slightly darker
                    
                    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
                }
            }

            // Pass 2: Draw Tile
            if (tile.isActive()) {
                TextureInfo texInfo = getTileTexture(tile.type);
                math::mat4 mvp = projection;
                mvp.m[12] = projection.m[0] * x + projection.m[12];
                mvp.m[13] = projection.m[5] * y + projection.m[13];
                glUniformMatrix4fv(mvpLocation, 1, GL_FALSE, mvp.m);

                int16_t fx = 0, fy = 0;
                if (tile.frameX != -1) {
                    fx = tile.frameX;
                    fy = tile.frameY;
                } else {
                    getStandardFrame(x, y, tile.type, fx, fy);
                }

                if (texInfo.id != 0) {
                    glActiveTexture(GL_TEXTURE0);
                    glBindTexture(GL_TEXTURE_2D, texInfo.id);
                    glUniform1i(texLocation, 0);
                    glUniform1i(useTexLocation, 1);
                    
                    glUniform2f(uvOffsetLocation, (float)fx / (float)texInfo.width, (float)fy / (float)texInfo.height);
                    glUniform2f(uvScaleLocation, 16.0f / (float)texInfo.width, 16.0f / (float)texInfo.height);
                } else {
                    glUniform1i(useTexLocation, 0);
                }

                glUniform4f(colorLocation, 1.0f, 1.0f, 1.0f, 1.0f);
                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
            }
        }
    }
    
    glDisableVertexAttribArray(0);
    glDisableVertexAttribArray(1);

    static int frameCount = 0;
    if (frameCount++ % 60 == 0) {
        LOGI("Camera: %.2f, %.2f Zoom: %.2f | Textures Cached: %zu", 
             camera.x, camera.y, camera.zoom, tileTextures.size() + wallTextures.size());
    }
}
