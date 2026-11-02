package com.orcamobile.app;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Real triangle-mesh preview, rendered with OpenGL ES. Drag to rotate; pinch to zoom. */
final class StlView extends GLSurfaceView {
    private final MeshRenderer renderer;
    private final StlMesh sourceMesh;
    private final double[] projectBounds;
    private final float orbitSpeed;
    private final float moveSpeed, density;
    private final boolean reverseZoom;
    private final boolean zoomToGesture,freeCamera,swapGestures,touchpad;
    private final java.util.concurrent.atomic.AtomicInteger transformRevision = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger pathRevision = new java.util.concurrent.atomic.AtomicInteger();
    private volatile BedGeometry bed;
    private volatile boolean volumeReady;
    private volatile ModelTransform currentTransform = ModelTransform.identity();
    private float lastX, lastY, lastDistance,lastMidX,lastMidY,lastHoverX,lastHoverY;
    private int mouseAction,hoverAction;
    private boolean moveMode, dragging, pinchGesture;
    private volatile boolean toolpathMode;
    private double dragStartX, dragStartY;
    private ModelTransform dragBase;
    private MoveCallback moveCallback;
    interface MoveCallback { void completed(ModelTransform transform); }
    void setMoveMode(boolean enabled, MoveCallback callback) { cancelDrag(); moveMode=enabled&&!toolpathMode; moveCallback=callback; setContentDescription(cameraDescription(moveMode,toolpathMode)); }
    void discardPendingMove() { cancelDrag(); renderer.dragX=renderer.dragY=0; requestRender(); }
    @Override public void onPause() { cancelDrag(); super.onPause(); }
    private void cancelDrag() { if(dragging) { dragging=false; renderer.dragX=renderer.dragY=0; requestRender(); } }

    StlView(Context context, StlMesh mesh) { this(context,mesh,null); }
    /** 3MF bounds are world-space minXYZ/maxXYZ in millimetres; null retains STL/OBJ behavior. */
    StlView(Context context, StlMesh mesh,double[] bounds) {
        super(context); sourceMesh = mesh; projectBounds=bounds==null?null:bounds.clone(); setEGLContextClientVersion(2);
        android.content.SharedPreferences prefs = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE);
        float speed = prefs.getFloat("orbit_speed", .75f); orbitSpeed = Float.isFinite(speed) ? Math.max(.2f, Math.min(3f, speed)) : .75f;
        float move = prefs.getFloat("move_speed", .6f); moveSpeed = Float.isFinite(move) ? Math.max(.1f, Math.min(2f, move)) : .6f;
        density=Math.max(.1f,context.getResources().getDisplayMetrics().density);
        reverseZoom = prefs.getBoolean("reverse_zoom", false);
        zoomToGesture=ControlPreferences.zoomToGesture(context);freeCamera=ControlPreferences.freeCamera(context);
        swapGestures=ControlPreferences.swapGestures(context);touchpad=ControlPreferences.touchpad(context);
        renderer = new MeshRenderer(mesh); setRenderer(renderer); setRenderMode(RENDERMODE_WHEN_DIRTY);
        setContentDescription(cameraDescription(false,false));
    }
    void resetCamera() { renderer.yaw = -35; renderer.pitch = 25; renderer.zoom = 1;renderer.panX=renderer.panY=0; requestRender(); }
    void setDarkMode(boolean dark) { renderer.dark = dark; requestRender(); }
    /** Update only the view; raw printable dimensions remain owned by the selected profile. */
    void setBuildVolume(BedGeometry volume, TransformCallback callback) {
        cancelDrag(); volumeReady=false; bed = volume;
        final int revision = transformRevision.get();
        queueEvent(() -> {
            if (revision != transformRevision.get()) return;
            try {
                float[] vertices = previewGeometry(currentTransform);
                renderer.setGeometry(vertices, volume, sourceMesh == null ? null : sourceMesh.size,projectBounds);
                volumeReady=volume!=null;
                requestRender();
                if (callback != null) post(() -> { if (revision == transformRevision.get()) callback.completed(null); });
            } catch (RuntimeException error) {
                if (callback != null) post(() -> { if (revision == transformRevision.get()) callback.completed(error); });
            }
        });
    }
    interface TransformCallback { void completed(Exception error); }
    /** Geometry is built off-thread by ToolpathGeometry.build; completion runs on UI thread. */
    void setToolpaths(ToolpathGeometry geometry,int visibleLayers,TransformCallback callback) {
        if(geometry==null||visibleLayers<1||visibleLayers>geometry.layerCount)throw new IllegalArgumentException("Некорректный слой предпросмотра.");
        cancelDrag();moveMode=false;toolpathMode=true;final int revision=pathRevision.incrementAndGet();
        setContentDescription(cameraDescription(false,true));
        queueEvent(()->{
            if(revision!=pathRevision.get())return;
            try {
                if(bed==null||!volumeReady||renderer.sceneScale<=0)throw new IllegalStateException("Область печати ещё не готова.");
                renderer.setToolpaths(geometry,visibleLayers);requestRender();
                if(callback!=null)post(()->{if(revision==pathRevision.get())callback.completed(null);});
            } catch(RuntimeException error) {
                toolpathMode=false;renderer.clearToolpaths();requestRender();
                if(callback!=null)post(()->{if(revision==pathRevision.get())callback.completed(error);});
            }
        });
    }
    void setVisibleToolpathLayers(int visibleLayers) {
        final int revision=pathRevision.get();queueEvent(()->{if(revision==pathRevision.get()&&renderer.pathGeometry!=null){renderer.visiblePathLayers=Math.max(1,Math.min(visibleLayers,renderer.pathGeometry.layerCount));requestRender();}});
    }
    void clearToolpaths() {
        toolpathMode=false;final int revision=pathRevision.incrementAndGet();
        setContentDescription(cameraDescription(false,false));
        queueEvent(()->{if(revision==pathRevision.get()){renderer.clearToolpaths();requestRender();}});
    }
    /** Object editing is separate from camera gestures. Completion runs on the UI thread. */
    void setModelTransform(ModelTransform transform, TransformCallback callback) {
        if (transform == null || sourceMesh == null) throw new IllegalArgumentException("Требуется модель и её преобразование.");
        final int revision = transformRevision.incrementAndGet();
        queueEvent(() -> {
            if (revision != transformRevision.get()) return;
            try {
                float[] vertices = previewGeometry(transform);
                if (revision != transformRevision.get()) return;
                renderer.setGeometry(vertices, bed, sourceMesh.size,projectBounds);
                renderer.dragX = renderer.dragY = 0;
                currentTransform = transform;
                volumeReady=bed!=null;
                requestRender();
                if (callback != null) post(() -> { if (revision == transformRevision.get()) callback.completed(null); });
            } catch (RuntimeException error) {
                if (callback != null) post(() -> { if (revision == transformRevision.get()) callback.completed(error); });
            }
        });
    }
    private float[] previewGeometry(ModelTransform transform) {
        if(sourceMesh==null||sourceMesh.vertices.length==0)return new float[0];
        if(projectBounds==null)return transform.previewVertices(sourceMesh.vertices,sourceMesh.size);
        if(projectBounds.length!=6)throw new IllegalArgumentException("Не удалось определить координаты проекта 3MF.");
        if(transform.rx!=0||transform.ry!=0||transform.rz!=0||transform.sx!=1||transform.sy!=1||transform.sz!=1||transform.offsetX!=0||transform.offsetY!=0)throw new IllegalArgumentException("Преобразование проекта 3MF пока не поддерживается.");
        for(double value:projectBounds)if(!Double.isFinite(value))throw new IllegalArgumentException("Некорректные координаты проекта 3MF.");
        return sourceMesh.vertices;
    }
    /** Converts normalized native-preview points to the bed-centred scene, retaining world Z. */
    static float[] projectVertices(float[] vertices,double[] size,double[] bounds,double bedCenterX,double bedCenterY,double scale) {
        if(bounds==null||bounds.length!=6||size==null||size.length!=3||!(scale>0)||!Double.isFinite(scale))throw new IllegalArgumentException("Некорректная геометрия проекта.");
        double unit=Math.max(size[0],Math.max(size[1],size[2]))/1.6,cx=bounds[0]/2+bounds[3]/2,cy=bounds[1]/2+bounds[4]/2,cz=bounds[2]/2+bounds[5]/2;
        if(!(unit>0)||!Double.isFinite(unit)||!Double.isFinite(cx)||!Double.isFinite(cy)||!Double.isFinite(cz))throw new IllegalArgumentException("Некорректные размеры проекта.");
        float[] result=new float[vertices.length];for(int i=0;i<vertices.length;i+=3){double x=(vertices[i]*unit+cx-bedCenterX)*scale,y=(vertices[i+1]*unit+cy-bedCenterY)*scale,z=(vertices[i+2]*unit+cz)*scale;if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>Float.MAX_VALUE||Math.abs(y)>Float.MAX_VALUE||Math.abs(z)>Float.MAX_VALUE)throw new IllegalArgumentException("Координаты проекта вне диапазона предпросмотра.");result[i]=(float)x;result[i+1]=(float)y;result[i+2]=(float)z;}return result;
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN) { getParent().requestDisallowInterceptTouchEvent(true);lastX=e.getX();lastY=e.getY();lastDistance=0;pinchGesture=false;mouseAction=mouseAction(e);
            if(moveMode&&mouseAction==0&&sourceMesh!=null&&volumeReady&&renderer.sceneScale>0&&moveCallback!=null){double[] p=bedPoint(lastX,lastY);if(p!=null){dragging=true;dragStartX=p[0];dragStartY=p[1];dragBase=currentTransform;}} return true; }
        if(action==MotionEvent.ACTION_POINTER_DOWN&&e.getPointerCount()>=2){cancelDrag();pinchGesture=true;lastDistance=distance(e);lastMidX=(e.getX(0)+e.getX(1))/2;lastMidY=(e.getY(0)+e.getY(1))/2;return true;}
        if(action==MotionEvent.ACTION_MOVE){
            if(e.getPointerCount()>=2){float d=distance(e),mx=(e.getX(0)+e.getX(1))/2,my=(e.getY(0)+e.getY(1))/2;
                if(lastDistance>0&&d>0)zoomAt(mx,my,reverseZoom?lastDistance/d:d/lastDistance);
                if(swapGestures)orbit(mx-lastMidX,my-lastMidY);else pan(mx-lastMidX,my-lastMidY);
                lastDistance=d;lastMidX=mx;lastMidY=my;}
            else if(dragging){double[] p=bedPoint(e.getX(),e.getY());if(p!=null){double dx=(p[0]-dragStartX)*moveSpeed,dy=(p[1]-dragStartY)*moveSpeed,scale=renderer.sceneScale,ox=dragBase.offsetX+dx/scale,oy=dragBase.offsetY+dy/scale;if(Double.isFinite(ox)&&Double.isFinite(oy)&&Math.abs(ox)<=10000&&Math.abs(oy)<=10000){renderer.dragX=(float)dx;renderer.dragY=(float)dy;}}}
            else if(mouseAction!=0&&!pinchGesture){if(mouseAction==2)pan(e.getX()-lastX,e.getY()-lastY);else orbit(e.getX()-lastX,e.getY()-lastY);}
            else if(!moveMode&&!pinchGesture&&!isMouse(e)){if(swapGestures)pan(e.getX()-lastX,e.getY()-lastY);else orbit(e.getX()-lastX,e.getY()-lastY);}
            lastX=e.getX();lastY=e.getY();requestRender();return true; }
        if(action==MotionEvent.ACTION_POINTER_UP){lastDistance=0;int n=e.getActionIndex()==0?1:0;lastX=e.getX(n);lastY=e.getY(n);return true;}
        if(action==MotionEvent.ACTION_CANCEL){cancelDrag();getParent().requestDisallowInterceptTouchEvent(false);return true;}
        if(action==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(false);if(dragging){dragging=false;double scale=renderer.sceneScale;float dx=renderer.dragX,dy=renderer.dragY;if(dx==0&&dy==0){renderer.dragX=renderer.dragY=0;}else try{moveCallback.completed(new ModelTransform(dragBase.rx,dragBase.ry,dragBase.rz,dragBase.sx,dragBase.sy,dragBase.sz,dragBase.offsetX+dx/scale,dragBase.offsetY+dy/scale));}catch(RuntimeException ex){renderer.dragX=renderer.dragY=0;requestRender();}}return true;}
        return true;
    }
    private static boolean isMouse(MotionEvent e){return (e.getSource()&android.view.InputDevice.SOURCE_MOUSE)==android.view.InputDevice.SOURCE_MOUSE;}
    private String cameraDescription(boolean moving,boolean paths){
        String subject=paths?"Траектории печати на трёхмерном столе. ":"Трёхмерный стол. ";
        if(moving)return subject+(swapGestures?"Одним пальцем перемещайте модель, двумя вращайте или изменяйте масштаб вида.":"Одним пальцем перемещайте модель, двумя панорамируйте или изменяйте масштаб вида.");
        return subject+(swapGestures?"Одним пальцем панорамируйте; двумя вращайте или изменяйте масштаб вида.":"Одним пальцем вращайте; двумя панорамируйте или изменяйте масштаб вида.");
    }
    private int mouseAction(MotionEvent e) {
        if(!isMouse(e))return 0;
        boolean alt=(e.getMetaState()&android.view.KeyEvent.META_ALT_ON)!=0,shift=(e.getMetaState()&android.view.KeyEvent.META_SHIFT_ON)!=0;
        if(touchpad){if(alt&&!shift)return 1;if(shift&&!alt)return 2;return 0;}
        int buttons=e.getButtonState();
        if((buttons&(MotionEvent.BUTTON_SECONDARY|MotionEvent.BUTTON_TERTIARY))!=0)return 2;
        return moveMode?0:1;
    }
    private void orbit(float dx,float dy) {
        renderer.yaw=CameraGestureMath.yaw(renderer.yaw,dx*.22f*orbitSpeed/density);
        renderer.pitch=CameraGestureMath.pitch(renderer.pitch,dy*.22f*orbitSpeed/density,freeCamera);
    }
    private void pan(float dx,float dy) {
        renderer.panX=CameraGestureMath.pan(renderer.panX,dx,getHeight(),9.5);
        renderer.panY=CameraGestureMath.pan(renderer.panY,-dy,getHeight(),9.5);
    }
    private void zoomAt(float x,float y,float ratio) {
        double[] anchor=zoomToGesture?bedPoint(x,y):null;
        renderer.zoom=CameraGestureMath.zoom(renderer.zoom,ratio);
        if(anchor==null)return;
        int w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
        float[] projection=new float[16],camera=new float[16],mvp=new float[16],point={(float)anchor[0],(float)anchor[1],0,1},eye=new float[4],clip=new float[4];
        Matrix.perspectiveM(projection,0,40,(float)w/h,.1f,20f);cameraMatrix(camera);Matrix.multiplyMV(eye,0,camera,0,point,0);
        Matrix.multiplyMM(mvp,0,projection,0,camera,0);Matrix.multiplyMV(clip,0,mvp,0,point,0);
        if(!Float.isFinite(clip[3])||clip[3]<=.00001f||!Float.isFinite(eye[2])||eye[2]>=-.1f)return;
        float sx=(clip[0]/clip[3]+1)*w/2,sy=(1-clip[1]/clip[3])*h/2;
        if(!Float.isFinite(sx)||!Float.isFinite(sy))return;
        renderer.panX=CameraGestureMath.pan(renderer.panX,x-sx,h,-eye[2]);
        renderer.panY=CameraGestureMath.pan(renderer.panY,sy-y,h,-eye[2]);
    }
    @Override public boolean onGenericMotionEvent(MotionEvent e) {
        if((e.getSource()&android.view.InputDevice.SOURCE_MOUSE)==android.view.InputDevice.SOURCE_MOUSE) {
            if(e.getActionMasked()==MotionEvent.ACTION_SCROLL) {
                float delta=e.getAxisValue(MotionEvent.AXIS_VSCROLL);
                if(Float.isFinite(delta)&&delta!=0){float ratio=(float)Math.pow(1.15,Math.max(-20,Math.min(20,reverseZoom?-delta:delta)));zoomAt(e.getX(),e.getY(),ratio);requestRender();return true;}
            }
        }
        return super.onGenericMotionEvent(e);
    }
    @Override public boolean onHoverEvent(MotionEvent e) {
        if(touchpad&&isMouse(e)&&e.getActionMasked()==MotionEvent.ACTION_HOVER_ENTER){lastHoverX=e.getX();lastHoverY=e.getY();hoverAction=0;return true;}
        if(touchpad&&isMouse(e)&&e.getActionMasked()==MotionEvent.ACTION_HOVER_MOVE) {
            boolean alt=(e.getMetaState()&android.view.KeyEvent.META_ALT_ON)!=0,shift=(e.getMetaState()&android.view.KeyEvent.META_SHIFT_ON)!=0;
            int action=alt&&!shift?1:shift&&!alt?2:0;
            if(action!=0&&hoverAction==action){if(action==1)orbit(e.getX()-lastHoverX,e.getY()-lastHoverY);else pan(e.getX()-lastHoverX,e.getY()-lastHoverY);requestRender();}
            lastHoverX=e.getX();lastHoverY=e.getY();hoverAction=action;return true;
        } else if(touchpad&&isMouse(e)&&e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT){hoverAction=0;return true;}
        return super.onHoverEvent(e);
    }
    private void cameraMatrix(float[] camera) {
        Matrix.setIdentityM(camera,0);Matrix.translateM(camera,0,renderer.panX,renderer.panY,-9.5f);
        Matrix.scaleM(camera,0,renderer.zoom,renderer.zoom,renderer.zoom);Matrix.rotateM(camera,0,renderer.pitch,1,0,0);
        Matrix.rotateM(camera,0,renderer.yaw,0,1,0);Matrix.rotateM(camera,0,-90,1,0,0);
    }
    private double[] bedPoint(float x,float y){int w=getWidth(),h=getHeight();if(w<=0||h<=0)return null;float[] p=new float[16],camera=new float[16],mvp=new float[16],inverse=new float[16];
        Matrix.perspectiveM(p,0,40,(float)w/h,.1f,20f);cameraMatrix(camera);Matrix.multiplyMM(mvp,0,p,0,camera,0);if(!Matrix.invertM(inverse,0,mvp,0))return null;return intersectBed(inverse,x,y,w,h);}
    static double[] intersectBed(float[] inverse,float x,float y,int width,int height){float nx=2*x/width-1,ny=1-2*y/height;float[] near={nx,ny,-1,1},far={nx,ny,1,1},a=new float[4],b=new float[4];Matrix.multiplyMV(a,0,inverse,0,near,0);Matrix.multiplyMV(b,0,inverse,0,far,0);
        if(Math.abs(a[3])<1e-8||Math.abs(b[3])<1e-8)return null;double ax=a[0]/a[3],ay=a[1]/a[3],az=a[2]/a[3],bx=b[0]/b[3],by=b[1]/b[3],bz=b[2]/b[3],dz=bz-az;if(Math.abs(dz)<1e-7)return null;double t=-az/dz;if(t<0||t>1)return null;double px=ax+t*(bx-ax),py=ay+t*(by-ay);return Double.isFinite(px)&&Double.isFinite(py)?new double[]{px,py}:null;}
    private static float distance(MotionEvent e) { return (float)Math.hypot(e.getX(0)-e.getX(1), e.getY(0)-e.getY(1)); }

    private static final class MeshRenderer implements Renderer {
        FloatBuffer positions, normals, gridPositions, bedPositions; int count, gridCount, bedCount;
        ToolpathGeometry pathGeometry; FloatBuffer pathPositions; int visiblePathLayers; double bedCenterX,bedCenterY;
        volatile float yaw = -35, pitch = 25, zoom = 1,panX,panY;
        volatile float dragX, dragY; volatile double sceneScale;
        volatile boolean dark = true;
        final float[] projection = new float[16], model = new float[16], mvp = new float[16];
        int program, pos, norm, matrix, modelMatrix, gridProgram, gridPos, gridMatrix, gridColor;
        MeshRenderer(StlMesh mesh) { setGeometry(mesh == null ? new float[0] : mesh.vertices, null, mesh == null ? null : mesh.size,null); }
        void setToolpaths(ToolpathGeometry geometry,int layers){pathGeometry=geometry;pathPositions=geometry.vertices.duplicate();pathPositions.position(0);visiblePathLayers=layers;}
        void clearToolpaths(){pathGeometry=null;pathPositions=null;visiblePathLayers=0;}
        void setGeometry(float[] vertices, BedGeometry bed, double[] sourceSize,double[] worldBounds) {
            float[] scene = vertices;
            double bedScale = 1;
            if (bed != null) {
                double maxModel = sourceSize == null ? 1 : Math.max(sourceSize[0], Math.max(sourceSize[1], sourceSize[2]));
                double mmPerUnit = maxModel / 1.6, sceneSpan = Math.max(bed.width, bed.depth), minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
                double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
                for (int i=0; i<vertices.length; i+=3) {
                    minX=Math.min(minX,vertices[i]*mmPerUnit);maxX=Math.max(maxX,vertices[i]*mmPerUnit);minY=Math.min(minY,vertices[i+1]*mmPerUnit);maxY=Math.max(maxY,vertices[i+1]*mmPerUnit);
                    minZ = Math.min(minZ, vertices[i+2] * mmPerUnit); maxZ = Math.max(maxZ, vertices[i+2] * mmPerUnit);
                }
                if (vertices.length > 0) sceneSpan = Math.max(sceneSpan,Math.max(maxZ-minZ,Math.max(maxX-minX,maxY-minY)));
                if (!Double.isFinite(sceneSpan) || sceneSpan <= 0) throw new IllegalArgumentException("Не удалось рассчитать масштаб стола.");
                if(worldBounds!=null && vertices.length>0){if(worldBounds.length!=6)throw new IllegalArgumentException("Не удалось определить координаты проекта 3MF.");for(double value:worldBounds)if(!Double.isFinite(value))throw new IllegalArgumentException("Некорректные координаты проекта 3MF.");
                    sceneSpan=Math.max(sceneSpan,2*Math.max(Math.max(Math.abs(worldBounds[0]-bed.centerX),Math.abs(worldBounds[3]-bed.centerX)),Math.max(Math.abs(worldBounds[1]-bed.centerY),Math.abs(worldBounds[4]-bed.centerY))));sceneSpan=Math.max(sceneSpan,2*Math.max(Math.abs(worldBounds[2]),Math.abs(worldBounds[5])));
                }
                if(!Double.isFinite(sceneSpan)||sceneSpan<=0)throw new IllegalArgumentException("Недопустимые границы проекта 3MF.");
                bedScale = 3.2 / sceneSpan;
                if(worldBounds!=null && vertices.length>0)scene=projectVertices(vertices,sourceSize,worldBounds,bed.centerX,bed.centerY,bedScale);
                else {scene = new float[vertices.length];for (int i=0; i<vertices.length; i+=3) {
                    scene[i] = (float)(vertices[i] * mmPerUnit * bedScale);
                    scene[i+1] = (float)(vertices[i+1] * mmPerUnit * bedScale);
                    scene[i+2] = (float)((vertices[i+2] * mmPerUnit - minZ) * bedScale);
                }}
            }
            sceneScale = bed == null ? 0 : bedScale;
            bedCenterX=bed==null?0:bed.centerX;bedCenterY=bed==null?0:bed.centerY;
            count = scene.length / 3;
            positions = buffer(scene);
            float[] n = new float[scene.length];
            for (int i=0; i<scene.length; i+=9) {
                float ax=scene[i+3]-scene[i], ay=scene[i+4]-scene[i+1], az=scene[i+5]-scene[i+2];
                float bx=scene[i+6]-scene[i], by=scene[i+7]-scene[i+1], bz=scene[i+8]-scene[i+2];
                float nx=ay*bz-az*by, ny=az*bx-ax*bz, nz=ax*by-ay*bx;
                float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz); if (len > 1e-12f) { nx/=len; ny/=len; nz/=len; } else { nx=0; ny=0; nz=1; }
                for(int j=0;j<3;j++){ n[i+j*3]=nx; n[i+j*3+1]=ny; n[i+j*3+2]=nz; }
            }
            normals = buffer(n);
            float minZ = Float.POSITIVE_INFINITY;
            for (int i=2; i<scene.length; i+=3) minZ = Math.min(minZ, scene[i]);
            if (!Float.isFinite(minZ)) minZ = 0;
            if (bed != null) {
                float[] floor = bed.floor(bedScale, -.02f), lines = bed.lines(bedScale, -.01f);
                bedPositions = buffer(floor); bedCount = floor.length / 3;
                gridPositions = buffer(lines); gridCount = lines.length / 3;
                return;
            }
            float[] grid = new float[11 * 4 * 3]; int at = 0;
            for (int i=-5; i<=5; i++) {
                float p = i * .4f, z = minZ - .01f;
                grid[at++] = p; grid[at++] = -2; grid[at++] = z;
                grid[at++] = p; grid[at++] = 2; grid[at++] = z;
                grid[at++] = -2; grid[at++] = p; grid[at++] = z;
                grid[at++] = 2; grid[at++] = p; grid[at++] = z;
            }
            gridPositions = buffer(grid); gridCount = grid.length / 3;
            float z = minZ - .02f;
            bedPositions = buffer(new float[]{-2,-2,z, 2,-2,z, 2,2,z, -2,-2,z, 2,2,z, -2,2,z}); bedCount = 6;
        }
        static FloatBuffer buffer(float[] a) { FloatBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer(); b.put(a).position(0); return b; }
        static int shader(int type, String source) { int s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,source); GLES20.glCompileShader(s); int[] ok=new int[1]; GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0); if(ok[0]==0) throw new IllegalStateException(GLES20.glGetShaderInfoLog(s)); return s; }
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            String vs="attribute vec3 aPosition; attribute vec3 aNormal; uniform mat4 uMvp; uniform mat4 uModel; varying float light; void main(){ vec3 n=normalize(mat3(uModel)*aNormal); light=0.32+0.68*abs(dot(n,normalize(vec3(0.4,0.6,1.0)))); gl_Position=uMvp*vec4(aPosition,1.0); }";
            String fs="precision mediump float; varying float light; void main(){ gl_FragColor=vec4(vec3(0.82)*light,1.0); }";
            program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,shader(GLES20.GL_VERTEX_SHADER,vs)); GLES20.glAttachShader(program,shader(GLES20.GL_FRAGMENT_SHADER,fs)); GLES20.glLinkProgram(program);
            int[] ok=new int[1]; GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0); if(ok[0]==0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
            pos=GLES20.glGetAttribLocation(program,"aPosition"); norm=GLES20.glGetAttribLocation(program,"aNormal"); matrix=GLES20.glGetUniformLocation(program,"uMvp"); modelMatrix=GLES20.glGetUniformLocation(program,"uModel");
            String gvs="attribute vec3 aPosition; uniform mat4 uMvp; void main(){gl_Position=uMvp*vec4(aPosition,1.0);}";
            String gfs="precision mediump float; uniform vec4 uColor; void main(){gl_FragColor=uColor;}";
            gridProgram=GLES20.glCreateProgram(); GLES20.glAttachShader(gridProgram,shader(GLES20.GL_VERTEX_SHADER,gvs)); GLES20.glAttachShader(gridProgram,shader(GLES20.GL_FRAGMENT_SHADER,gfs)); GLES20.glLinkProgram(gridProgram);
            GLES20.glGetProgramiv(gridProgram,GLES20.GL_LINK_STATUS,ok,0); if(ok[0]==0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(gridProgram));
            gridPos=GLES20.glGetAttribLocation(gridProgram,"aPosition"); gridMatrix=GLES20.glGetUniformLocation(gridProgram,"uMvp"); gridColor=GLES20.glGetUniformLocation(gridProgram,"uColor");
        }
        public void onSurfaceChanged(GL10 gl, int width, int height) { GLES20.glViewport(0,0,width,height); float aspect=(float)width/Math.max(height,1); Matrix.perspectiveM(projection,0,40,aspect,.1f,20f); }
        public void onDrawFrame(GL10 gl) {
            if (dark) GLES20.glClearColor(.067f,.075f,.067f,1); else GLES20.glClearColor(.980f,.980f,.969f,1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT); Matrix.setIdentityM(model,0); Matrix.translateM(model,0,panX,panY,-9.5f); Matrix.scaleM(model,0,zoom,zoom,zoom); Matrix.rotateM(model,0,pitch,1,0,0); Matrix.rotateM(model,0,yaw,0,1,0);
            // STL and the bed use Z-up. Convert to OpenGL's Y-up camera after
            // camera orbit, without changing the printable object transform.
            Matrix.rotateM(model,0,-90,1,0,0); Matrix.multiplyMM(mvp,0,projection,0,model,0);
            GLES20.glUseProgram(gridProgram); GLES20.glUniformMatrix4fv(gridMatrix,1,false,mvp,0);
            GLES20.glEnableVertexAttribArray(gridPos);
            if (dark) GLES20.glUniform4f(gridColor,.19f,.20f,.21f,1); else GLES20.glUniform4f(gridColor,.80f,.81f,.79f,1);
            bedPositions.position(0); GLES20.glVertexAttribPointer(gridPos,3,GLES20.GL_FLOAT,false,0,bedPositions); GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,bedCount);
            if (dark) GLES20.glUniform4f(gridColor,.34f,.35f,.36f,1); else GLES20.glUniform4f(gridColor,.62f,.63f,.61f,1);
            gridPositions.position(0); GLES20.glVertexAttribPointer(gridPos,3,GLES20.GL_FLOAT,false,0,gridPositions); GLES20.glDrawArrays(GLES20.GL_LINES,0,gridCount); GLES20.glDisableVertexAttribArray(gridPos);
            if(pathGeometry!=null && pathPositions!=null) {
                int layers=Math.max(0,Math.min(visiblePathLayers,pathGeometry.layerCount)),vertices=pathGeometry.layerEnds[layers];
                if(vertices>0 && sceneScale>0) {
                    float[] placement=ToolpathGeometry.sceneTransform(bedCenterX,bedCenterY,sceneScale),pathModel=model.clone(),pathMvp=new float[16];
                    Matrix.translateM(pathModel,0,placement[0],placement[1],0);
                    Matrix.scaleM(pathModel,0,placement[2],placement[2],placement[2]);Matrix.multiplyMM(pathMvp,0,projection,0,pathModel,0);
                    GLES20.glUseProgram(gridProgram);GLES20.glUniformMatrix4fv(gridMatrix,1,false,pathMvp,0);
                    if(dark)GLES20.glUniform4f(gridColor,.92f,.92f,.92f,1);else GLES20.glUniform4f(gridColor,.12f,.12f,.12f,1);
                    GLES20.glEnableVertexAttribArray(gridPos);pathPositions.position(0);GLES20.glVertexAttribPointer(gridPos,3,GLES20.GL_FLOAT,false,0,pathPositions);GLES20.glLineWidth(1.5f);GLES20.glDrawArrays(GLES20.GL_LINES,0,vertices);GLES20.glDisableVertexAttribArray(gridPos);
                }
                return;
            }
            if (count == 0) return;
            float[] objectModel=model.clone(),objectMvp=new float[16];Matrix.translateM(objectModel,0,dragX,dragY,0);Matrix.multiplyMM(objectMvp,0,projection,0,objectModel,0);
            GLES20.glUseProgram(program); GLES20.glUniformMatrix4fv(matrix,1,false,objectMvp,0); GLES20.glUniformMatrix4fv(modelMatrix,1,false,objectModel,0);
            positions.position(0); normals.position(0); GLES20.glEnableVertexAttribArray(pos); GLES20.glEnableVertexAttribArray(norm);
            GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,0,positions); GLES20.glVertexAttribPointer(norm,3,GLES20.GL_FLOAT,false,0,normals); GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,count);
            GLES20.glDisableVertexAttribArray(pos); GLES20.glDisableVertexAttribArray(norm);
        }
    }
}
