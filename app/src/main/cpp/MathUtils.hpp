#ifndef MATH_UTILS_HPP
#define MATH_UTILS_HPP

#include <cmath>
#include <vector>

namespace math {

struct mat4 {
    float m[16];
    mat4() {
        for(int i=0; i<16; ++i) m[i] = 0.0f;
    }
    static mat4 identity() {
        mat4 res;
        res.m[0] = res.m[5] = res.m[10] = res.m[15] = 1.0f;
        return res;
    }
};

inline mat4 ortho(float left, float right, float bottom, float top, float near, float far) {
    mat4 res = mat4::identity();
    res.m[0] = 2.0f / (right - left);
    res.m[5] = 2.0f / (top - bottom);
    res.m[10] = -2.0f / (far - near);
    res.m[12] = -(right + left) / (right - left);
    res.m[13] = -(top + bottom) / (top - bottom);
    res.m[14] = -(far + near) / (far - near);
    return res;
}

inline mat4 translate(const mat4& m, float x, float y, float z) {
    mat4 res = m;
    res.m[12] = m.m[0] * x + m.m[4] * y + m.m[8] * z + m.m[12];
    res.m[13] = m.m[1] * x + m.m[5] * y + m.m[9] * z + m.m[13];
    res.m[14] = m.m[2] * x + m.m[6] * y + m.m[10] * z + m.m[14];
    res.m[15] = m.m[3] * x + m.m[7] * y + m.m[11] * z + m.m[15];
    return res;
}

} // namespace math

#endif
