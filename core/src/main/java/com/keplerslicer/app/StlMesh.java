package com.keplerslicer.app;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/** Streaming STL reader. Geometry is complete on disk; only the view is sampled. */
final class StlMesh {
    private static final int PREVIEW_TRIANGLES = 200_000;
    final float[] vertices;
    final double[] size;
    final long triangleCount;
    final int previewTriangleCount;
    final boolean previewSampled;

    private StlMesh(float[] vertices,double[] size,long count) {
        this.vertices=vertices; this.size=size; triangleCount=count; previewTriangleCount=vertices.length/9; previewSampled=count>previewTriangleCount;
    }
    /** Converts world-space native preview coordinates using the bounds of the complete selected model. */
    static StlMesh fromNative(float[] xyz, double[] low, double[] high, long count) throws IOException {
        if (xyz == null || xyz.length % 9 != 0 || low == null || high == null || low.length != 3 || high.length != 3 || count < xyz.length / 9)
            throw new IOException(L.t("Некорректные данные предпросмотра модели."));
        if(xyz.length==0) {if(count!=0)throw new IOException(L.t("Некорректное число треугольников модели."));return new StlMesh(xyz,new double[]{0,0,0},0);}
        double[] size=new double[3]; double max=0;
        for(int a=0;a<3;a++) { if(!Double.isFinite(low[a]) || !Double.isFinite(high[a]) || high[a] < low[a]) throw new IOException(L.t("Некорректные границы модели.")); size[a]=high[a]-low[a]; max=Math.max(max,size[a]); }
        if (!(max>0) || !Double.isFinite(max)) throw new IOException(L.t("Модель не имеет размеров."));
        for(int i=0;i<xyz.length;i++) {if(!Float.isFinite(xyz[i])) throw new IOException(L.t("Некорректные координаты модели.")); int a=i%3;xyz[i]=(float)((xyz[i]-(low[a]+high[a])*.5)*1.6/max);}
        return new StlMesh(xyz,size,count);
    }
    static StlMesh read(InputStream input) throws IOException {
        long length;
        if(input instanceof FileInputStream) { FileInputStream file=(FileInputStream)input; length=file.getChannel().size()-file.getChannel().position(); }
        else if(input instanceof ByteArrayInputStream) length=((ByteArrayInputStream)input).available();
        else throw new IOException(L.t("Для чтения STL нужен известный размер файла."));
        return read(input,length);
    }
    static StlMesh read(InputStream input,long length) throws IOException {
        if(input==null||length<0) throw new IOException(L.t("Не удалось прочитать STL."));
        if(length<15) throw new IOException(L.t("Файл STL пуст или повреждён."));
        PushbackInputStream in=new PushbackInputStream(new BufferedInputStream(input,65536),84);
        byte[] head=new byte[84]; int got=0,n;
        while(got<head.length&&(n=in.read(head,got,head.length-got))!=-1) {if(n>0)got+=n;}
        in.unread(head,0,got);
        long count=got==84?u32(head,80):-1;
        Accumulator acc=new Accumulator();
        if(count>=0 && 84L+count*50L==length) readBinary(in,count,acc);
        else readAscii(in,acc);
        return acc.finish();
    }
    private static void readBinary(InputStream input,long count,Accumulator acc)throws IOException {
        if(count==0) throw new IOException(L.t("STL не содержит треугольников."));
        byte[] header=new byte[84], record=new byte[50]; fully(input,header);
        float[] triangle=new float[9];
        for(long i=0;i<count;i++) {
            if(Thread.currentThread().isInterrupted()) throw new IOException(L.t("Чтение STL отменено."));
            fully(input,record);
            for(int k=0;k<9;k++) { triangle[k]=Float.intBitsToFloat((int)u32(record,12+4*k)); if(!Float.isFinite(triangle[k])) throw new IOException(L.t("Некорректные координаты STL.")); }
            acc.add(triangle);
        }
        if(input.read()!=-1) throw new IOException(L.t("Размер бинарного STL не соответствует заголовку."));
    }
    private static long u32(byte[] b,int p){return (b[p]&255L)|((b[p+1]&255L)<<8)|((b[p+2]&255L)<<16)|((b[p+3]&255L)<<24);}
    private static void fully(InputStream input,byte[] target)throws IOException {int pos=0;while(pos<target.length){int n=input.read(target,pos,target.length-pos);if(n<0)throw new IOException(L.t("Бинарный STL обрезан."));if(n>0)pos+=n;}}
    private static void readAscii(InputStream in,Accumulator acc)throws IOException {
        Tokens t=new Tokens(in); String first=t.next();
        if(first==null||!first.equals("solid")) throw new IOException(L.t("Не удалось распознать STL или файл повреждён. Команды ASCII STL должны быть записаны строчными буквами."));
        t.skipLine(); float[] tri=new float[9]; boolean ended=false;
        for(String token;(token=t.next())!=null;) {
            if(Thread.currentThread().isInterrupted()) throw new IOException(L.t("Чтение STL отменено."));
            if(token.equals("endsolid")){ended=true;break;}
            expect(token,"facet");expect(t.next(),"normal");
            for(int i=0;i<3;i++)if(t.next()==null)throw new IOException(L.t("Повреждён нормальный вектор ASCII STL."));
            expect(t.next(),"outer");expect(t.next(),"loop");
            for(int v=0;v<3;v++) { expect(t.next(),"vertex"); for(int k=0;k<3;k++){String s=t.next();try{if(s==null)throw new NumberFormatException();float x=Float.parseFloat(s);if(!Float.isFinite(x))throw new NumberFormatException();tri[v*3+k]=x;}catch(NumberFormatException e){throw new IOException(L.t("Некорректные координаты ASCII STL."),e);}} }
            expect(t.next(),"endloop");t.skipLine();expect(t.next(),"endfacet");t.skipLine();acc.add(tri);
        }
        if(!ended)throw new IOException(L.t("ASCII STL обрезан: отсутствует endsolid."));
    }
    private static void expect(String actual,String expected)throws IOException {if(actual==null||!actual.equals(expected))throw new IOException(L.t("Некорректная структура ASCII STL: ожидалось ")+expected+L.t(" (строчными буквами)."));}
    private static final class Tokens {
        private final InputStream input; private int last=-1;
        Tokens(InputStream input){this.input=input;}
        String next()throws IOException {
            int c;do{c=input.read();}while(c!=-1&&c<=32);
            if(c==-1){last=-1;return null;}
            StringBuilder b=new StringBuilder();
            do {if(c>=128||b.length()>=1024)throw new IOException(L.t("Некорректный текст ASCII STL."));b.append((char)c);c=input.read();}while(c!=-1&&c>32);
            last=c;return b.toString();
        }
        void skipLine()throws IOException {int c=last;if(c=='\n'||c==-1)return;while((c=input.read())!=-1&&c!='\n'){}last=c;}
    }
    private static final class Accumulator {
        private final float[] samples=new float[(PREVIEW_TRIANGLES-6)*9];
        private final float[][] extremes=new float[6][9]; private final long[] extremeIndex={-1,-1,-1,-1,-1,-1};
        private final double[] low={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY}, high={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        private int kept; private long stride=1,count;
        void add(float[] tri)throws IOException {
            for(int k=0;k<9;k++) {int axis=k%3;double v=tri[k];if(!Double.isFinite(v))throw new IOException(L.t("Некорректные координаты STL."));
                if(v<low[axis]){low[axis]=v;System.arraycopy(tri,0,extremes[axis*2],0,9);extremeIndex[axis*2]=count;}
                if(v>high[axis]){high[axis]=v;System.arraycopy(tri,0,extremes[axis*2+1],0,9);extremeIndex[axis*2+1]=count;}}
            if(kept==PREVIEW_TRIANGLES-6) {
                int next=0;for(int i=0;i<kept;i+=2){if(next!=i)System.arraycopy(samples,i*9,samples,next*9,9);next++;}kept=next;
                if(stride>Long.MAX_VALUE/2)throw new IOException(L.t("Слишком много данных в STL."));stride*=2;
            }
            if(count%stride==0){System.arraycopy(tri,0,samples,kept*9,9);kept++;}
            if(count==Long.MAX_VALUE)throw new IOException(L.t("Слишком много данных в STL."));count++;
        }
        StlMesh finish()throws IOException {
            if(count==0)throw new IOException(L.t("STL не содержит треугольников."));
            double[] size={high[0]-low[0],high[1]-low[1],high[2]-low[2]};double max=Math.max(size[0],Math.max(size[1],size[2]));
            if(!(max>0)||!Double.isFinite(max))throw new IOException(L.t("Модель не имеет размеров."));
            float[] out=Arrays.copyOf(samples,(kept+6)*9);int extra=0;
            for(int i=0;i<6;i++){long index=extremeIndex[i];if(index<0||index%stride==0)continue;boolean duplicate=false;for(int j=0;j<i;j++)if(extremeIndex[j]==index){duplicate=true;break;}if(!duplicate){System.arraycopy(extremes[i],0,out,(kept+extra)*9,9);extra++;}}
            out=Arrays.copyOf(out,(kept+extra)*9);
            for(int i=0;i<out.length;i++){int a=i%3;out[i]=(float)((out[i]-(low[a]+high[a])*.5)*1.6/max);}
            return new StlMesh(out,size,count);
        }
    }
}
