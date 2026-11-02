package com.orcamobile.app;
public final class CameraGestureMathTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void near(double a,double b,double tolerance,String message){check(Math.abs(a-b)<tolerance,message+": "+a+" vs "+b);}
    public static void main(String[] args){
        near(CameraGestureMath.zoom(1f,2f),2,1e-6,"pinch magnifies");
        near(CameraGestureMath.zoom(1f,.5f),.5,1e-6,"reverse pinch magnifies oppositely");
        near(CameraGestureMath.zoom(4f,2f),5,1e-6,"zoom upper bound");
        near(CameraGestureMath.zoom(.4f,.1f),.35,1e-6,"zoom lower bound");
        near(CameraGestureMath.pitch(80f,30f,false),89,1e-6,"constrained orbit stops");
        near(CameraGestureMath.pitch(-80f,-30f,false),-89,1e-6,"constrained orbit stops other side");
        near(CameraGestureMath.pitch(80f,30f,true),110,1e-6,"free camera crosses pole");
        near(CameraGestureMath.yaw(350f,20f),10,1e-6,"yaw wraps without overflow");
        double depth=9.5,height=1000,displacement=125;
        double shift=CameraGestureMath.pan(0,(float)displacement,(int)height,depth);
        double recoveredPixels=shift*height/(2*depth*Math.tan(Math.toRadians(20)));
        near(recoveredPixels,displacement,.0001,"screen-space pan scales with projection depth");
        near(CameraGestureMath.pan(2f,50f,0,depth),2,1e-6,"invalid viewport leaves pan unchanged");
        check(Float.isFinite(CameraGestureMath.zoom(Float.NaN,1f)),"invalid zoom stays finite");
        System.out.println("Camera gesture math passed: "+checks+" checks");
    }
}
