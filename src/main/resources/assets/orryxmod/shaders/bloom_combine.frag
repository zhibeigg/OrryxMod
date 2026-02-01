#version 120

varying vec2 textureCoords;

uniform sampler2D buffer_a;      // 背景
uniform sampler2D buffer_b;      // 模糊后的泛光
uniform float intensive;
uniform float base;
uniform float threshold_up;
uniform float threshold_down;
uniform vec3 tint_color;
uniform float use_tint;

void main(void){
    vec3 bloom = texture2D(buffer_b, textureCoords).rgb * intensive;
    vec3 background = texture2D(buffer_a, textureCoords).rgb;

    // 应用颜色着色
    vec3 tintedBloom = bloom;
    if (use_tint > 0.5) {
        tintedBloom = bloom * tint_color;
    }

    // 计算背景亮度用于权重
    float maxC = max(background.b, max(background.r, background.g));
    float minC = min(background.b, min(background.r, background.g));
    float weight = (1.0 - (maxC + minC) / 2.0) * (threshold_up - threshold_down) + threshold_down + base;

    // 泛光遮罩：只在泛光强度 > 0.02 时混合
    // 这可以过滤掉扩散到云彩等区域的微弱泛光
    float bloomMask = step(0.02, length(tintedBloom));

    gl_FragColor = vec4(background + tintedBloom * weight * bloomMask, 1.0);
}
