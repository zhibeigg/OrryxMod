#version 120

varying vec2 textureCoords;

uniform sampler2D colourTexture;
uniform vec3 tintColor;

void main(void){
    vec4 texColor = texture2D(colourTexture, textureCoords);
    gl_FragColor = vec4(texColor.rgb * tintColor, texColor.a);
}
