#version 120

varying vec2 textureCoords;

void main(void){
    gl_Position = gl_Vertex;
    textureCoords = gl_Vertex.xy * 0.5 + 0.5;
}
