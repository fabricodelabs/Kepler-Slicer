package com.keplerslicer.app.glass;
import android.content.Context;
import android.view.View;
import android.widget.*;
public final class GlassUi {
 public static final class Tokens {public int textPrimary;public float cardRadius;}
 public final Tokens tokens=new Tokens();
 public GlassUi(Context c,View v,boolean b){}
 public LinearLayout vertical(){return null;}public void pad(View v,int a,int b,int c,int d){}public TextView heading(String s){return null;}public void space(LinearLayout v,int h){}
 public TextView text(String s,int f,int w,int c){return null;}public EditText edit(String s,int t,boolean m){return null;}public View stackedRow(String s,View v){return null;}public View row(String s,View v,Runnable r){return null;}public View action(String s,boolean b,Runnable r){return null;}public View surface(View v,float r){return null;}
}
