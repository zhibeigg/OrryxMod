#version 120

varying vec2 textureCoords;

uniform sampler2D buffer_a;
uniform sampler2D buffer_b;
uniform float intensive;
uniform float base;
uniform float threshold_up;
uniform float threshold_down;
uniform vec4 bloom_color;

void main(void){
    vec3 bloom = texture2D(buffer_b, textureCoords).rgb * intensive;
    vec3 background = texture2D(buffer_a, textureCoords).rgb;

    // 应用光晕颜色
    vec3 tintedBloom = bloom * bloom_color.rgb * bloom_color.a;

    float max = max(background.b, max(background.r, background.g));
    float min = min(background.b, min(background.r, background.g));
    gl_FragColor = vec4(background + tintedBloom * ((1. - (max + min) / 2.) * (threshold_up - threshold_down) + threshold_down + base), 1.);
}
