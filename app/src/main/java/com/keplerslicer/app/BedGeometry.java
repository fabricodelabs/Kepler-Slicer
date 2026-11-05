package com.keplerslicer.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exact planar outline from the engine's resolved printable_area, in millimetres. */
final class BedGeometry {
    private static final double EPS = 1e-8;
    private static final class P { final double x,y; P(double x,double y){this.x=x;this.y=y;} }
    final double height, centerX, centerY, width, depth;
    private final List<P> polygon;
    private final List<P> triangles = new ArrayList<>(), grid = new ArrayList<>();

    /** Native payload is [height, x0, y0, ...], millimetres. */
    BedGeometry(double[] data) {
        if (data == null || data.length < 7 || data.length % 2 != 1 || data.length > 1025 ||
                !Double.isFinite(data[0]) || data[0] <= 0 || data[0] > 1_000_000)
            throw new IllegalArgumentException(L.t("Некорректная область печати профиля."));
        height = data[0]; List<P> pts = new ArrayList<>();
        for (int i=1;i<data.length;i+=2) {
            double x=data[i], y=data[i+1];
            if (!Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x)>1_000_000 || Math.abs(y)>1_000_000)
                throw new IllegalArgumentException(L.t("Некорректные координаты стола."));
            P v=new P(x,y); if(pts.isEmpty() || !same(pts.get(pts.size()-1),v)) pts.add(v);
        }
        if(pts.size()>1 && same(pts.get(0),pts.get(pts.size()-1))) pts.remove(pts.size()-1);
        boolean changed=true;
        while(changed && pts.size()>=3) {
            changed=false;
            for(int i=0;i<pts.size();i++) {
                P a=pts.get((i+pts.size()-1)%pts.size()), b=pts.get(i), c=pts.get((i+1)%pts.size());
                if(Math.abs(cross(a,b,c))<EPS && between(a,b,c)) { pts.remove(i); changed=true; break; }
            }
        }
        if(pts.size()<3 || !simple(pts)) throw new IllegalArgumentException(L.t("Контур стола повреждён или пересекает себя."));
        double area=area(pts); if(Math.abs(area)<EPS) throw new IllegalArgumentException(L.t("Контур стола не имеет площади."));
        if(area<0) Collections.reverse(pts);
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(P p:pts) { minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y); }
        width=maxX-minX; depth=maxY-minY; centerX=(minX+maxX)/2; centerY=(minY+maxY)/2;
        if(width<EPS || depth<EPS) throw new IllegalArgumentException(L.t("Стол не имеет размеров."));
        polygon=new ArrayList<>(); for(P p:pts) polygon.add(new P(p.x-centerX,p.y-centerY));
        triangulate();
        // 20 divisions per bounding axis. Clip every grid segment to the real polygon.
        for(int i=1;i<20;i++) {
            addScan(-depth/2 + depth*i/20, false);
            addScan(-width/2 + width*i/20, true);
        }
        for(int i=0;i<polygon.size();i++) {grid.add(polygon.get(i));grid.add(polygon.get((i+1)%polygon.size()));}
    }
    private static boolean same(P a,P b){return Math.abs(a.x-b.x)<EPS && Math.abs(a.y-b.y)<EPS;}
    private static double cross(P a,P b,P c){return (b.x-a.x)*(c.y-a.y)-(b.y-a.y)*(c.x-a.x);}
    private static boolean between(P a,P b,P c){return b.x>=Math.min(a.x,c.x)-EPS&&b.x<=Math.max(a.x,c.x)+EPS&&b.y>=Math.min(a.y,c.y)-EPS&&b.y<=Math.max(a.y,c.y)+EPS;}
    private static double area(List<P> p){double a=0;for(int i=0;i<p.size();i++){P x=p.get(i),y=p.get((i+1)%p.size());a+=x.x*y.y-y.x*x.y;}return a/2;}
    private static boolean hit(P a,P b,P c,P d) {
        double abC=cross(a,b,c),abD=cross(a,b,d),cdA=cross(c,d,a),cdB=cross(c,d,b);
        if(((abC>EPS && abD< -EPS)||(abC< -EPS && abD>EPS)) && ((cdA>EPS && cdB< -EPS)||(cdA< -EPS && cdB>EPS))) return true;
        return (Math.abs(abC)<EPS && between(a,c,b)) || (Math.abs(abD)<EPS && between(a,d,b)) ||
                (Math.abs(cdA)<EPS && between(c,a,d)) || (Math.abs(cdB)<EPS && between(c,b,d));
    }
    private static boolean simple(List<P> p) {
        int n=p.size(); for(int i=0;i<n;i++) for(int j=i+1;j<n;j++) {
            if(j==i+1 || (i==0 && j==n-1)) continue;
            if(hit(p.get(i),p.get((i+1)%n),p.get(j),p.get((j+1)%n))) return false;
        } return true;
    }
    private static boolean inTriangle(P p,P a,P b,P c){return cross(a,b,p)>=-EPS&&cross(b,c,p)>=-EPS&&cross(c,a,p)>=-EPS;}
    private void triangulate() {
        List<P> rest=new ArrayList<>(polygon);
        long checks = 0;
        while(rest.size()>3) {
            boolean found=false;
            for(int i=0;i<rest.size();i++) {
                P a=rest.get((i+rest.size()-1)%rest.size()), b=rest.get(i), c=rest.get((i+1)%rest.size());
                if(cross(a,b,c)<=EPS) continue;
                boolean blocked=false;
                for(int j=0;j<rest.size();j++) {
                    if (++checks > 3_000_000) throw new IllegalArgumentException(L.t("Контур стола слишком сложен для предпросмотра."));
                    P q=rest.get(j); if(q!=a&&q!=b&&q!=c&&inTriangle(q,a,b,c)){blocked=true;break;}
                }
                if(!blocked){triangles.add(a);triangles.add(b);triangles.add(c);rest.remove(i);found=true;break;}
            }
            if(!found) throw new IllegalArgumentException(L.t("Не удалось построить контур стола."));
        }
        triangles.addAll(rest);
    }
    private void addScan(double scan,boolean vertical) {
        List<Double> intersections=new ArrayList<>();
        for(int i=0;i<polygon.size();i++) {
            P a=polygon.get(i),b=polygon.get((i+1)%polygon.size());
            double a0=vertical?a.x:a.y,b0=vertical?b.x:b.y;
            if((a0<=scan && b0>scan)||(b0<=scan&&a0>scan)) {
                double t=(scan-a0)/(b0-a0); intersections.add((vertical?a.y:a.x)+t*((vertical?b.y:b.x)-(vertical?a.y:a.x)));
            }
        }
        Collections.sort(intersections);
        if(intersections.size()%2!=0) throw new IllegalArgumentException(L.t("Не удалось построить сетку стола."));
        for(int i=0;i<intersections.size();i+=2) {
            double a=intersections.get(i),b=intersections.get(i+1);
            grid.add(vertical?new P(scan,a):new P(a,scan)); grid.add(vertical?new P(scan,b):new P(b,scan));
        }
    }
    float[] floor(double scale,float z){return make(triangles,scale,z);}
    float[] lines(double scale,float z){return make(grid,scale,z);}
    private static float[] make(List<P> points,double scale,float z) {
        if(!Double.isFinite(scale)||scale<=0) throw new IllegalArgumentException(L.t("Некорректный масштаб сцены."));
        float[] result=new float[points.size()*3]; int i=0;
        for(P p:points){result[i++]=(float)(p.x*scale);result[i++]=(float)(p.y*scale);result[i++]=z;}
        return result;
    }
}
