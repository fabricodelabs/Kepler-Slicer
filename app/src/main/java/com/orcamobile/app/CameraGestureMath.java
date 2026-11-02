package com.orcamobile.app;

/** Screen-space camera math; does not modify model geometry or slicing transforms. */
final class CameraGestureMath {
    private static final double TAN_HALF_FOV=Math.tan(Math.toRadians(20));
    private CameraGestureMath() {}
    static float zoom(float current,float ratio) {
        if(!Float.isFinite(current)||!Float.isFinite(ratio)||current<=0||ratio<=0)return 1f;
        return Math.max(.35f,Math.min(5f,current*ratio));
    }
    static float pitch(float current,float delta,boolean free) {
        if(!Float.isFinite(current)||!Float.isFinite(delta))return 25f;
        float value=current+delta;
        return free?value%360f:Math.max(-89f,Math.min(89f,value));
    }
    static float yaw(float current,float delta) {
        if(!Float.isFinite(current)||!Float.isFinite(delta))return -35f;
        return (current+delta)%360f;
    }
    static float pan(float current,float pixels,int height,double depth) {
        if(!Float.isFinite(current)||!Float.isFinite(pixels)||height<=0||!Double.isFinite(depth)||depth<=0)return current;
        double value=current+pixels*(2*depth*TAN_HALF_FOV/height);
        return (float)Math.max(-50,Math.min(50,value));
    }
}
