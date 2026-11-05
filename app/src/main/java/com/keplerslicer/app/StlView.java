package com.keplerslicer.app;

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
    void setMoveMode(boolean enabled, MoveCallback callback) { cancelDrag(); moveMode=enabled&&!toolpathMode; moveCallback=callback; setContentDescription(cameraDescription(moveMode,toolpathMode)); renderer.gizmo=moveMode; requestRender(); }
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
    /** Plate name drawn on the bed (upper case, 10sp/600 at 60% white like the Figma Workspace). */
    void setPlateLabel(String label) {
        if (label == null || label.trim().isEmpty()) { queueEvent(() -> renderer.setLabel(null)); requestRender(); return; }
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG); p.setTypeface(com.keplerslicer.app.glass.GlassTypography.typeface(getContext(), 600)); p.setTextSize(48); p.setColor(0x99E0E0E0); p.setLetterSpacing(.04f);
        String text = label.trim().toUpperCase(java.util.Locale.getDefault()); float w = p.measureText(text); android.graphics.Paint.FontMetrics m = p.getFontMetrics();
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(Math.max(8, (int) Math.ceil(w) + 8), (int) Math.ceil(m.descent - m.ascent) + 8, android.graphics.Bitmap.Config.ARGB_8888);
        new android.graphics.Canvas(b).drawText(text, 4, 4 - m.ascent, p);
        queueEvent(() -> renderer.setLabel(b)); requestRender();
    }
    private android.graphics.drawable.Drawable backdrop; private android.view.View backdropAnchor;
    private final int[] backdropMine = new int[2], backdropTheirs = new int[2], backdropLast = {Integer.MIN_VALUE, 0, 0, 0};
    /**
     * Kepler v3: the GL surface sits behind the window, so the glass backdrop is drawn into the scene
     * as a positioned copy of the anchor's background instead of showing through.
     */
    void setBackdrop(android.graphics.drawable.Drawable drawable, android.view.View anchor) {
        backdrop = drawable; backdropAnchor = anchor; backdropLast[0] = Integer.MIN_VALUE;
        getViewTreeObserver().addOnGlobalLayoutListener(this::updateBackdrop);
    }
    private void updateBackdrop() {
        if (backdrop == null || backdropAnchor == null || getWidth() <= 0 || getHeight() <= 0) return;
        getLocationInWindow(backdropMine); backdropAnchor.getLocationInWindow(backdropTheirs);
        int dx = backdropTheirs[0] - backdropMine[0], dy = backdropTheirs[1] - backdropMine[1];
        if (backdropLast[0] == dx && backdropLast[1] == dy && backdropLast[2] == getWidth() && backdropLast[3] == getHeight()) return;
        backdropLast[0] = dx; backdropLast[1] = dy; backdropLast[2] = getWidth(); backdropLast[3] = getHeight();
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(getWidth(), getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap); canvas.translate(dx, dy);
        backdrop.setBounds(0, 0, Math.max(1, backdropAnchor.getWidth()), Math.max(1, backdropAnchor.getHeight())); backdrop.draw(canvas);
        queueEvent(() -> renderer.setBackdrop(bitmap)); requestRender();
    }
    /** Material slot colours per preview triangle (null restores the default model colour). */
    void setTriangleColors(int[] argb) { queueEvent(() -> { renderer.triangleColors = argb; renderer.rebuildTints(); requestRender(); }); }
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
        if(geometry==null||visibleLayers<1||visibleLayers>geometry.layerCount)throw new IllegalArgumentException(L.t("Некорректный слой предпросмотра."));
        cancelDrag();moveMode=false;toolpathMode=true;final int revision=pathRevision.incrementAndGet();
        setContentDescription(cameraDescription(false,true));
        queueEvent(()->{
            if(revision!=pathRevision.get())return;
            try {
                if(bed==null||!volumeReady||renderer.sceneScale<=0)throw new IllegalStateException(L.t("Область печати ещё не готова."));
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
        if (transform == null || sourceMesh == null) throw new IllegalArgumentException(L.t("Требуется модель и её преобразование."));
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
        if(projectBounds.length!=6)throw new IllegalArgumentException(L.t("Не удалось определить координаты проекта 3MF."));
        for(double value:projectBounds)if(!Double.isFinite(value))throw new IllegalArgumentException(L.t("Некорректные координаты проекта 3MF."));
        boolean identity=transform.rx==0&&transform.ry==0&&transform.rz==0&&transform.sx==1&&transform.sy==1&&transform.sz==1&&transform.offsetX==0&&transform.offsetY==0;
        if(identity)return sourceMesh.vertices;
        // The project moves as one group (engine: apply_group_transform): rotate/scale around its centre, move by the
        // offset from where it was, then stand on the bed again.
        float[] moved=transform.previewVertices(sourceMesh.vertices,sourceMesh.size);
        double unit=Math.max(sourceMesh.size[0],Math.max(sourceMesh.size[1],sourceMesh.size[2]))/1.6,cz=(projectBounds[2]+projectBounds[5])/2;float minZ=Float.MAX_VALUE;
        for(int i=2;i<moved.length;i+=3)minZ=Math.min(minZ,moved[i]);
        float dz=(float)((projectBounds[2]-cz)/unit-minZ);for(int i=2;i<moved.length;i+=3)moved[i]+=dz;
        return moved;
    }
    /** Converts normalized native-preview points to the bed-centred scene, retaining world Z. */
    static float[] projectVertices(float[] vertices,double[] size,double[] bounds,double bedCenterX,double bedCenterY,double scale) {
        if(bounds==null||bounds.length!=6||size==null||size.length!=3||!(scale>0)||!Double.isFinite(scale))throw new IllegalArgumentException(L.t("Некорректная геометрия проекта."));
        double unit=Math.max(size[0],Math.max(size[1],size[2]))/1.6,cx=bounds[0]/2+bounds[3]/2,cy=bounds[1]/2+bounds[4]/2,cz=bounds[2]/2+bounds[5]/2;
        if(!(unit>0)||!Double.isFinite(unit)||!Double.isFinite(cx)||!Double.isFinite(cy)||!Double.isFinite(cz))throw new IllegalArgumentException(L.t("Некорректные размеры проекта."));
        float[] result=new float[vertices.length];for(int i=0;i<vertices.length;i+=3){double x=(vertices[i]*unit+cx-bedCenterX)*scale,y=(vertices[i+1]*unit+cy-bedCenterY)*scale,z=(vertices[i+2]*unit+cz)*scale;if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>Float.MAX_VALUE||Math.abs(y)>Float.MAX_VALUE||Math.abs(z)>Float.MAX_VALUE)throw new IllegalArgumentException(L.t("Координаты проекта вне диапазона предпросмотра."));result[i]=(float)x;result[i+1]=(float)y;result[i+2]=(float)z;}return result;
    }
    // ---- Paint brush: one finger paints the triangles under a sphere brush, two fingers keep moving the camera ----
    interface PaintCallback { void painted(int[] previewTriangles); }
    private volatile PaintCallback paintCallback; private volatile float paintRadiusMm = 3f;
    void setPaintMode(PaintCallback callback, float radiusMm) { paintCallback = callback; paintRadiusMm = Math.max(0.2f, radiusMm); }
    boolean paintMode() { return paintCallback != null; }
    private void paintAt(float x, float y) {
        final int w = getWidth(), h = getHeight(); final PaintCallback cb = paintCallback; if (cb == null || w <= 0 || h <= 0) return;
        float[] p = new float[16], camera = new float[16], mvp = new float[16], inverse = new float[16];
        Matrix.perspectiveM(p, 0, 40, (float) w / h, .1f, 20f); cameraMatrix(camera); Matrix.multiplyMM(mvp, 0, p, 0, camera, 0);
        if (!Matrix.invertM(inverse, 0, mvp, 0)) return;
        float nx = 2f * x / w - 1, ny = 1 - 2f * y / h; float[] a = new float[4], b = new float[4];
        Matrix.multiplyMV(a, 0, inverse, 0, new float[]{nx, ny, -1, 1}, 0); Matrix.multiplyMV(b, 0, inverse, 0, new float[]{nx, ny, 1, 1}, 0);
        if (a[3] == 0 || b[3] == 0) return;
        final float ox = a[0] / a[3], oy = a[1] / a[3], oz = a[2] / a[3], dx = b[0] / b[3] - ox, dy = b[1] / b[3] - oy, dz = b[2] / b[3] - oz;
        final double scale = renderer.sceneScale; final float radius = (float) (paintRadiusMm * (scale > 0 ? scale : 0.01));
        queueEvent(() -> {
            float[] v = renderer.sceneVertices; int best = -1; float bestT = Float.MAX_VALUE;
            for (int i = 0; i + 8 < v.length; i += 9) { // Moller-Trumbore
                float e1x = v[i+3]-v[i], e1y = v[i+4]-v[i+1], e1z = v[i+5]-v[i+2], e2x = v[i+6]-v[i], e2y = v[i+7]-v[i+1], e2z = v[i+8]-v[i+2];
                float px = dy*e2z-dz*e2y, py = dz*e2x-dx*e2z, pz = dx*e2y-dy*e2x, det = e1x*px+e1y*py+e1z*pz; if (Math.abs(det) < 1e-12f) continue;
                float inv = 1/det, tx = ox-v[i], ty = oy-v[i+1], tz = oz-v[i+2], u = (tx*px+ty*py+tz*pz)*inv; if (u < 0 || u > 1) continue;
                float qx = ty*e1z-tz*e1y, qy = tz*e1x-tx*e1z, qz = tx*e1y-ty*e1x, vv = (dx*qx+dy*qy+dz*qz)*inv; if (vv < 0 || u+vv > 1) continue;
                float t = (e2x*qx+e2y*qy+e2z*qz)*inv; if (t > 0 && t < bestT) { bestT = t; best = i / 9; }
            }
            if (best < 0) return;
            float hx = ox+dx*bestT, hy = oy+dy*bestT, hz = oz+dz*bestT, r2 = radius*radius; int[] hit = new int[64]; int n = 0;
            for (int i = 0; i + 8 < v.length; i += 9) {
                float cx = (v[i]+v[i+3]+v[i+6])/3-hx, cy = (v[i+1]+v[i+4]+v[i+7])/3-hy, cz = (v[i+2]+v[i+5]+v[i+8])/3-hz;
                if (cx*cx+cy*cy+cz*cz <= r2 || i / 9 == best) { if (n == hit.length) hit = java.util.Arrays.copyOf(hit, n * 2); hit[n++] = i / 9; }
            }
            final int[] result = java.util.Arrays.copyOf(hit, n);
            post(() -> { PaintCallback current = paintCallback; if (current != null) current.painted(result); });
        });
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        int action=e.getActionMasked();
        if (paintCallback != null && e.getPointerCount() == 1 && (action == MotionEvent.ACTION_DOWN || (action == MotionEvent.ACTION_MOVE && !pinchGesture))) {
            if (action == MotionEvent.ACTION_DOWN) { getParent().requestDisallowInterceptTouchEvent(true); lastX = e.getX(); lastY = e.getY(); pinchGesture = false; lastDistance = 0; }
            paintAt(e.getX(), e.getY()); return true;
        }
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
        String subject=paths?L.t("Траектории печати на трёхмерном столе. "):L.t("Трёхмерный стол. ");
        if(moving)return subject+(swapGestures?L.t("Одним пальцем перемещайте модель, двумя вращайте или изменяйте масштаб вида."):L.t("Одним пальцем перемещайте модель, двумя панорамируйте или изменяйте масштаб вида."));
        return subject+(swapGestures?L.t("Одним пальцем панорамируйте; двумя вращайте или изменяйте масштаб вида."):L.t("Одним пальцем вращайте; двумя панорамируйте или изменяйте масштаб вида."));
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
        volatile float[] sceneVertices = new float[0];
        FloatBuffer positions, normals, gridPositions, bedPositions, tints; int count, gridCount, bedCount; int tint = -1;
        /** ARGB per preview triangle (material slot colours), or null for the default model colour. */
        int[] triangleColors;
        void rebuildTints() {
            int[] c = triangleColors; if (c == null || count == 0 || c.length != count / 3) { tints = null; return; }
            float[] t = new float[count * 4];
            for (int tri = 0; tri < c.length; tri++) { float r = ((c[tri] >> 16) & 255) / 255f, g = ((c[tri] >> 8) & 255) / 255f, b = (c[tri] & 255) / 255f;
                for (int v = 0; v < 3; v++) { int at = (tri * 3 + v) * 4; t[at] = r; t[at + 1] = g; t[at + 2] = b; t[at + 3] = 1; } }
            tints = buffer(t);
        }
        ToolpathGeometry pathGeometry; FloatBuffer pathPositions; int visiblePathLayers; double bedCenterX,bedCenterY;
        volatile float yaw = -35, pitch = 25, zoom = 1,panX,panY;
        volatile float dragX, dragY; volatile double sceneScale;
        volatile boolean dark = true;
        final float[] projection = new float[16], model = new float[16], mvp = new float[16];
        int program, pos, norm, matrix, modelMatrix, gridProgram, gridPos, gridMatrix, gridColor;
        int colorProgram, colorPos, colorAttr, colorMatrix;
        volatile boolean gizmo; FloatBuffer gizmoPositions, gizmoColors; int gizmoCount;
        /** Plate label (Figma "TEXTURED PEI PLATE", 10/600, 60%): a text texture lying on the bed along its front edge. */
        int labelProgram, labelPos, labelUv, labelMatrix, labelSampler, labelTexture; volatile android.graphics.Bitmap labelBitmap; boolean labelDirty; FloatBuffer labelQuad; float labelAspect = 6;
        volatile boolean volumeLabel = true;
        void setLabel(android.graphics.Bitmap b) { labelBitmap = b; labelDirty = true; if (b != null) labelAspect = b.getWidth() / (float) Math.max(1, b.getHeight()); if (lastBedW > 0) buildLabelQuad(lastBedW, lastBedD); }
        float lastBedW, lastBedD;
        void buildLabelQuad(float w, float d) { lastBedW = w; lastBedD = d; float h = Math.min(w, d) * .045f, len = h * labelAspect, x0 = -w / 2 + h * .8f, y0 = -d / 2 + h * .5f, z = -.005f;
            labelQuad = buffer(new float[]{x0, y0, z, 0, 1, x0 + len, y0, z, 1, 1, x0, y0 + h, z, 0, 0, x0 + len, y0 + h, z, 1, 0}); }
        void drawLabel(float[] mvpMatrix) {
            if (labelBitmap == null || labelQuad == null) return;
            if (labelDirty || labelTexture == 0) { if (labelTexture == 0) { int[] t = new int[1]; GLES20.glGenTextures(1, t, 0); labelTexture = t[0]; } GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, labelTexture); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE); GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE); android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, labelBitmap, 0); labelDirty = false; }
            GLES20.glUseProgram(labelProgram); GLES20.glUniformMatrix4fv(labelMatrix, 1, false, mvpMatrix, 0); GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, labelTexture); GLES20.glUniform1i(labelSampler, 0);
            labelQuad.position(0); GLES20.glEnableVertexAttribArray(labelPos); GLES20.glVertexAttribPointer(labelPos, 3, GLES20.GL_FLOAT, false, 20, labelQuad); labelQuad.position(3); GLES20.glEnableVertexAttribArray(labelUv); GLES20.glVertexAttribPointer(labelUv, 2, GLES20.GL_FLOAT, false, 20, labelQuad);
            GLES20.glDepthMask(false); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4); GLES20.glDepthMask(true); GLES20.glDisableVertexAttribArray(labelPos); GLES20.glDisableVertexAttribArray(labelUv);
        }
        /** Tools move gizmo (Figma 3:176): dashed #FFB454 bounding box and X/Y/Z arrows (#FF8A80, #8BE28B, #8AB4FF) from the base centre. */
        void buildGizmo(float[] scene) {
            if (scene.length < 9) { gizmoCount = 0; return; }
            float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
            for (int i = 0; i < scene.length; i += 3) { x0 = Math.min(x0, scene[i]); x1 = Math.max(x1, scene[i]); y0 = Math.min(y0, scene[i+1]); y1 = Math.max(y1, scene[i+1]); z0 = Math.min(z0, scene[i+2]); z1 = Math.max(z1, scene[i+2]); }
            java.util.ArrayList<float[]> lines = new java.util.ArrayList<>(); float pad = .04f * Math.max(x1 - x0, Math.max(y1 - y0, z1 - z0)) + .02f;
            x0 -= pad; y0 -= pad; x1 += pad; y1 += pad; z1 += pad;
            float[][] c = {{x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}}; int[][] e = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
            float[] box = {1f, .706f, .329f};
            for (int[] edge : e) { float[] a = c[edge[0]], b = c[edge[1]]; int dashes = 8; for (int d = 0; d < dashes; d++) { float t0 = d / (float) dashes, t1 = (d + .55f) / dashes; lines.add(new float[]{a[0]+(b[0]-a[0])*t0, a[1]+(b[1]-a[1])*t0, a[2]+(b[2]-a[2])*t0, a[0]+(b[0]-a[0])*t1, a[1]+(b[1]-a[1])*t1, a[2]+(b[2]-a[2])*t1, box[0], box[1], box[2]}); } }
            float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, len = .55f * Math.max(.4f, Math.max(x1 - x0, y1 - y0)), up = .7f * Math.max(.4f, z1 - z0) + (z1 - z0);
            float[][] axes = {{len,0,0, 1f,.541f,.502f},{0,len,0, .545f,.886f,.545f},{0,0,up, .541f,.706f,1f}};
            for (float[] a : axes) { float ex = cx + a[0], ey = cy + a[1], ez = z0 + a[2]; lines.add(new float[]{cx, cy, z0, ex, ey, ez, a[3], a[4], a[5]});
                float hx = a[0] * .15f, hy = a[1] * .15f, hz = a[2] * .15f, sx = a[2] != 0 ? .08f * up : 0, sy = a[0] != 0 ? .08f * len : a[1] != 0 ? 0 : 0, sz = a[2] == 0 ? .08f * len : 0;
                lines.add(new float[]{ex, ey, ez, ex - hx + sx, ey - hy + sy, ez - hz + sz, a[3], a[4], a[5]}); lines.add(new float[]{ex, ey, ez, ex - hx - sx, ey - hy - sy, ez - hz - sz, a[3], a[4], a[5]}); }
            float[] p = new float[lines.size() * 6], col = new float[lines.size() * 6]; int k = 0;
            for (float[] l : lines) { System.arraycopy(l, 0, p, k, 6); col[k] = col[k+3] = l[6]; col[k+1] = col[k+4] = l[7]; col[k+2] = col[k+5] = l[8]; k += 6; }
            gizmoPositions = buffer(p); gizmoColors = buffer(col); gizmoCount = p.length / 3;
        }
        int quadProgram, quadPos, quadSampler, backdropTexture; android.graphics.Bitmap backdropBitmap; boolean backdropDirty;
        final FloatBuffer quad = buffer(new float[]{-1,-1, 1,-1, -1,1, 1,1});
        void setBackdrop(android.graphics.Bitmap bitmap) { backdropBitmap = bitmap; backdropDirty = true; }
        private void drawBackdrop() {
            if (backdropBitmap == null) return;
            if (backdropDirty || backdropTexture == 0) {
                if (backdropTexture == 0) { int[] t = new int[1]; GLES20.glGenTextures(1, t, 0); backdropTexture = t[0]; }
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backdropTexture);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
                android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, backdropBitmap, 0); backdropDirty = false;
            }
            GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glUseProgram(quadProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backdropTexture); GLES20.glUniform1i(quadSampler, 0);
            quad.position(0); GLES20.glEnableVertexAttribArray(quadPos); GLES20.glVertexAttribPointer(quadPos, 2, GLES20.GL_FLOAT, false, 0, quad);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4); GLES20.glDisableVertexAttribArray(quadPos); GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        }
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
                if (!Double.isFinite(sceneSpan) || sceneSpan <= 0) throw new IllegalArgumentException(L.t("Не удалось рассчитать масштаб стола."));
                if(worldBounds!=null && vertices.length>0){if(worldBounds.length!=6)throw new IllegalArgumentException(L.t("Не удалось определить координаты проекта 3MF."));for(double value:worldBounds)if(!Double.isFinite(value))throw new IllegalArgumentException(L.t("Некорректные координаты проекта 3MF."));
                    sceneSpan=Math.max(sceneSpan,2*Math.max(Math.max(Math.abs(worldBounds[0]-bed.centerX),Math.abs(worldBounds[3]-bed.centerX)),Math.max(Math.abs(worldBounds[1]-bed.centerY),Math.abs(worldBounds[4]-bed.centerY))));sceneSpan=Math.max(sceneSpan,2*Math.max(Math.abs(worldBounds[2]),Math.abs(worldBounds[5])));
                }
                if(!Double.isFinite(sceneSpan)||sceneSpan<=0)throw new IllegalArgumentException(L.t("Недопустимые границы проекта 3MF."));
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
            count = scene.length / 3; buildGizmo(scene);
            positions = buffer(scene); sceneVertices = scene;
            float[] n = new float[scene.length];
            for (int i=0; i<scene.length; i+=9) {
                float ax=scene[i+3]-scene[i], ay=scene[i+4]-scene[i+1], az=scene[i+5]-scene[i+2];
                float bx=scene[i+6]-scene[i], by=scene[i+7]-scene[i+1], bz=scene[i+8]-scene[i+2];
                float nx=ay*bz-az*by, ny=az*bx-ax*bz, nz=ax*by-ay*bx;
                float len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz); if (len > 1e-12f) { nx/=len; ny/=len; nz/=len; } else { nx=0; ny=0; nz=1; }
                for(int j=0;j<3;j++){ n[i+j*3]=nx; n[i+j*3+1]=ny; n[i+j*3+2]=nz; }
            }
            normals = buffer(n); rebuildTints();
            float minZ = Float.POSITIVE_INFINITY;
            for (int i=2; i<scene.length; i+=3) minZ = Math.min(minZ, scene[i]);
            if (!Float.isFinite(minZ)) minZ = 0;
            if (bed != null) {
                float[] floor = bed.floor(bedScale, -.02f), lines = bed.lines(bedScale, -.01f);
                bedPositions = buffer(floor); bedCount = floor.length / 3; buildLabelQuad((float) (bed.width * bedScale), (float) (bed.depth * bedScale));
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
            // Kepler v3 model faces: top #8CC8FF, lit side #3D8FE0, shaded side #2A6FBE (Workspace viewport, node 3:73).
            String vs="attribute vec3 aPosition; attribute vec3 aNormal; attribute vec4 aTint; uniform mat4 uMvp; uniform mat4 uModel; varying vec3 shade; void main(){ vec3 o=normalize(aNormal); vec3 n=normalize(mat3(uModel)*aNormal); float side=clamp(0.5-0.5*n.x/max(0.2,length(n.xy)),0.0,1.0); float top=smoothstep(0.55,0.9,abs(o.z)); vec3 sideColor=mix(vec3(0.165,0.435,0.745),vec3(0.239,0.561,0.878),side); shade=mix(sideColor,vec3(0.549,0.784,1.0),top); if(aTint.a>0.5){ shade=aTint.rgb*mix(mix(0.62,0.85,side),1.0,top)+vec3(0.04); } gl_Position=uMvp*vec4(aPosition,1.0); }";
            String fs="precision mediump float; varying vec3 shade; void main(){ gl_FragColor=vec4(shade,1.0); }";
            String qvs="attribute vec2 aPosition; varying vec2 uv; void main(){ uv=vec2(aPosition.x*0.5+0.5,0.5-aPosition.y*0.5); gl_Position=vec4(aPosition,0.0,1.0); }";
            String qfs="precision mediump float; varying vec2 uv; uniform sampler2D uTexture; void main(){ gl_FragColor=texture2D(uTexture,uv); }";
            quadProgram=GLES20.glCreateProgram(); GLES20.glAttachShader(quadProgram,shader(GLES20.GL_VERTEX_SHADER,qvs)); GLES20.glAttachShader(quadProgram,shader(GLES20.GL_FRAGMENT_SHADER,qfs)); GLES20.glLinkProgram(quadProgram);
            String cvs="attribute vec3 aPosition; attribute vec3 aColor; uniform mat4 uMvp; varying vec3 vColor; void main(){vColor=aColor;gl_Position=uMvp*vec4(aPosition,1.0);}";
            String cfs="precision mediump float; varying vec3 vColor; void main(){gl_FragColor=vec4(vColor,1.0);}";
            colorProgram=GLES20.glCreateProgram(); GLES20.glAttachShader(colorProgram,shader(GLES20.GL_VERTEX_SHADER,cvs)); GLES20.glAttachShader(colorProgram,shader(GLES20.GL_FRAGMENT_SHADER,cfs)); GLES20.glLinkProgram(colorProgram);
            colorPos=GLES20.glGetAttribLocation(colorProgram,"aPosition"); colorAttr=GLES20.glGetAttribLocation(colorProgram,"aColor"); colorMatrix=GLES20.glGetUniformLocation(colorProgram,"uMvp");
            String lvs="attribute vec3 aPosition; attribute vec2 aUv; uniform mat4 uMvp; varying vec2 uv; void main(){uv=aUv;gl_Position=uMvp*vec4(aPosition,1.0);}";
            String lfs="precision mediump float; varying vec2 uv; uniform sampler2D uTexture; void main(){gl_FragColor=texture2D(uTexture,uv);}";
            labelProgram=GLES20.glCreateProgram(); GLES20.glAttachShader(labelProgram,shader(GLES20.GL_VERTEX_SHADER,lvs)); GLES20.glAttachShader(labelProgram,shader(GLES20.GL_FRAGMENT_SHADER,lfs)); GLES20.glLinkProgram(labelProgram);
            labelPos=GLES20.glGetAttribLocation(labelProgram,"aPosition"); labelUv=GLES20.glGetAttribLocation(labelProgram,"aUv"); labelMatrix=GLES20.glGetUniformLocation(labelProgram,"uMvp"); labelSampler=GLES20.glGetUniformLocation(labelProgram,"uTexture"); labelTexture=0;
            quadPos=GLES20.glGetAttribLocation(quadProgram,"aPosition"); quadSampler=GLES20.glGetUniformLocation(quadProgram,"uTexture"); backdropTexture=0;
            program=GLES20.glCreateProgram(); GLES20.glAttachShader(program,shader(GLES20.GL_VERTEX_SHADER,vs)); GLES20.glAttachShader(program,shader(GLES20.GL_FRAGMENT_SHADER,fs)); GLES20.glLinkProgram(program);
            int[] ok=new int[1]; GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0); if(ok[0]==0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
            pos=GLES20.glGetAttribLocation(program,"aPosition"); norm=GLES20.glGetAttribLocation(program,"aNormal"); tint=GLES20.glGetAttribLocation(program,"aTint"); matrix=GLES20.glGetUniformLocation(program,"uMvp"); modelMatrix=GLES20.glGetUniformLocation(program,"uModel");
            String gvs="attribute vec3 aPosition; uniform mat4 uMvp; void main(){gl_Position=uMvp*vec4(aPosition,1.0);}";
            String gfs="precision mediump float; uniform vec4 uColor; void main(){gl_FragColor=uColor;}";
            gridProgram=GLES20.glCreateProgram(); GLES20.glAttachShader(gridProgram,shader(GLES20.GL_VERTEX_SHADER,gvs)); GLES20.glAttachShader(gridProgram,shader(GLES20.GL_FRAGMENT_SHADER,gfs)); GLES20.glLinkProgram(gridProgram);
            GLES20.glGetProgramiv(gridProgram,GLES20.GL_LINK_STATUS,ok,0); if(ok[0]==0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(gridProgram));
            gridPos=GLES20.glGetAttribLocation(gridProgram,"aPosition"); gridMatrix=GLES20.glGetUniformLocation(gridProgram,"uMvp"); gridColor=GLES20.glGetUniformLocation(gridProgram,"uColor");
        }
        public void onSurfaceChanged(GL10 gl, int width, int height) { GLES20.glViewport(0,0,width,height); float aspect=(float)width/Math.max(height,1); Matrix.perspectiveM(projection,0,40,aspect,.1f,20f); }
        public void onDrawFrame(GL10 gl) {
            if (dark) GLES20.glClearColor(.220f,.239f,.275f,1); else GLES20.glClearColor(.980f,.980f,.969f,1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT); drawBackdrop();
            GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA); Matrix.setIdentityM(model,0); Matrix.translateM(model,0,panX,panY,-9.5f); Matrix.scaleM(model,0,zoom,zoom,zoom); Matrix.rotateM(model,0,pitch,1,0,0); Matrix.rotateM(model,0,yaw,0,1,0);
            // STL and the bed use Z-up. Convert to OpenGL's Y-up camera after
            // camera orbit, without changing the printable object transform.
            Matrix.rotateM(model,0,-90,1,0,0); Matrix.multiplyMM(mvp,0,projection,0,model,0);
            GLES20.glUseProgram(gridProgram); GLES20.glUniformMatrix4fv(gridMatrix,1,false,mvp,0);
            GLES20.glEnableVertexAttribArray(gridPos);
            // Plate #14161A at 55%, grid white at 16% (node 3:73).
            if (dark) GLES20.glUniform4f(gridColor,.078f,.086f,.102f,.55f); else GLES20.glUniform4f(gridColor,.80f,.81f,.79f,1);
            bedPositions.position(0); GLES20.glVertexAttribPointer(gridPos,3,GLES20.GL_FLOAT,false,0,bedPositions); GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,bedCount);
            if (dark) GLES20.glUniform4f(gridColor,1f,1f,1f,.16f); else GLES20.glUniform4f(gridColor,.62f,.63f,.61f,1);
            gridPositions.position(0); GLES20.glVertexAttribPointer(gridPos,3,GLES20.GL_FLOAT,false,0,gridPositions); GLES20.glDrawArrays(GLES20.GL_LINES,0,gridCount); GLES20.glDisableVertexAttribArray(gridPos); if(dark&&volumeLabel)drawLabel(mvp);
            if(pathGeometry!=null && pathPositions!=null) {
                int layers=Math.max(0,Math.min(visiblePathLayers,pathGeometry.layerCount)),vertices=pathGeometry.layerEnds[layers];
                if(vertices>0 && sceneScale>0) {
                    float[] placement=ToolpathGeometry.sceneTransform(bedCenterX,bedCenterY,sceneScale),pathModel=model.clone(),pathMvp=new float[16];
                    Matrix.translateM(pathModel,0,placement[0],placement[1],0);
                    Matrix.scaleM(pathModel,0,placement[2],placement[2],placement[2]);Matrix.multiplyMM(pathMvp,0,projection,0,pathModel,0);
                    FloatBuffer pathColors=pathGeometry.colors;
                    if(pathColors!=null){GLES20.glUseProgram(colorProgram);GLES20.glUniformMatrix4fv(colorMatrix,1,false,pathMvp,0);GLES20.glEnableVertexAttribArray(colorPos);GLES20.glEnableVertexAttribArray(colorAttr);pathPositions.position(0);pathColors.position(0);GLES20.glVertexAttribPointer(colorPos,3,GLES20.GL_FLOAT,false,0,pathPositions);GLES20.glVertexAttribPointer(colorAttr,3,GLES20.GL_FLOAT,false,0,pathColors);GLES20.glLineWidth(2f);GLES20.glDrawArrays(GLES20.GL_LINES,0,vertices);GLES20.glDisableVertexAttribArray(colorPos);GLES20.glDisableVertexAttribArray(colorAttr);}
                    else{GLES20.glUseProgram(gridProgram);GLES20.glUniformMatrix4fv(gridMatrix,1,false,pathMvp,0);
                    if(dark)GLES20.glUniform4f(gridColor,1f,.624f,.263f,1);else GLES20.glUniform4f(gridColor,.12f,.12f,.12f,1);
                    GLES20.glEnableVertexAttribArray(gridPos);pathPositions.position(0);GLES20.glVertexAttribPointer(gridPos,3,GLES20.GL_FLOAT,false,0,pathPositions);GLES20.glLineWidth(1.5f);GLES20.glDrawArrays(GLES20.GL_LINES,0,vertices);GLES20.glDisableVertexAttribArray(gridPos);}
                }
                return;
            }
            if (count == 0) return;
            float[] objectModel=model.clone(),objectMvp=new float[16];Matrix.translateM(objectModel,0,dragX,dragY,0);Matrix.multiplyMM(objectMvp,0,projection,0,objectModel,0);
            GLES20.glUseProgram(program); GLES20.glUniformMatrix4fv(matrix,1,false,objectMvp,0); GLES20.glUniformMatrix4fv(modelMatrix,1,false,objectModel,0);
            positions.position(0); normals.position(0); GLES20.glEnableVertexAttribArray(pos); GLES20.glEnableVertexAttribArray(norm);
            GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,0,positions); GLES20.glVertexAttribPointer(norm,3,GLES20.GL_FLOAT,false,0,normals);
            if (tint >= 0) { if (tints != null) { tints.position(0); GLES20.glEnableVertexAttribArray(tint); GLES20.glVertexAttribPointer(tint,4,GLES20.GL_FLOAT,false,0,tints); } else { GLES20.glDisableVertexAttribArray(tint); GLES20.glVertexAttrib4f(tint,0,0,0,0); } }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,count); if (tint >= 0 && tints != null) GLES20.glDisableVertexAttribArray(tint);
            GLES20.glDisableVertexAttribArray(pos); GLES20.glDisableVertexAttribArray(norm);
            if (gizmo && gizmoCount > 0) { GLES20.glDisable(GLES20.GL_DEPTH_TEST); GLES20.glUseProgram(colorProgram); GLES20.glUniformMatrix4fv(colorMatrix,1,false,objectMvp,0); GLES20.glEnableVertexAttribArray(colorPos); GLES20.glEnableVertexAttribArray(colorAttr); gizmoPositions.position(0); gizmoColors.position(0); GLES20.glVertexAttribPointer(colorPos,3,GLES20.GL_FLOAT,false,0,gizmoPositions); GLES20.glVertexAttribPointer(colorAttr,3,GLES20.GL_FLOAT,false,0,gizmoColors); GLES20.glLineWidth(3f); GLES20.glDrawArrays(GLES20.GL_LINES,0,gizmoCount); GLES20.glDisableVertexAttribArray(colorPos); GLES20.glDisableVertexAttribArray(colorAttr); GLES20.glEnable(GLES20.GL_DEPTH_TEST); }
        }
    }
}
