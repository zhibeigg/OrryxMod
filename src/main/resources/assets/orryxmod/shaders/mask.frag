#version 120

uniform vec4 u_color;
uniform sampler2D u_texture;
uniform float u_alphaThreshold;

varying vec2 v_texCoord;

void main(void){
    // 采样纹理获取 alpha 值
    float alpha = texture2D(u_texture, v_texCoord).a;

    // 丢弃透明像素
    if (alpha < u_alphaThreshold) {
        discard;
    }

    gl_FragColor = u_color;
}
