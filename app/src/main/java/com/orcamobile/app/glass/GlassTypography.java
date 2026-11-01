package com.orcamobile.app.glass;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.Log;
import android.widget.TextView;
import com.orcamobile.app.R;

/** Manrope weights from packaged Android font resources. */
public final class GlassTypography {
    private GlassTypography(){}
    public static void apply(TextView view,int weight){if(view==null)throw new IllegalArgumentException("Text view is required");view.setTypeface(typeface(view.getContext(),weight));}
    public static Typeface typeface(Context context,int weight){
        final int id;
        switch(weight){case 400:id=R.font.manrope_regular;break;case 500:id=R.font.manrope_medium;break;case 600:id=R.font.manrope_semibold;break;case 700:id=R.font.manrope_bold;break;default:throw new IllegalArgumentException("Manrope weight must be 400, 500, 600, or 700");}
        Resources res=context.getResources();
        try{return res.getFont(id);}catch(Resources.NotFoundException error){Log.w("KeplerGlass","Manrope font unavailable for weight "+weight,error);}
        return Typeface.create("sans-serif",weight>=600?Typeface.BOLD:Typeface.NORMAL);
    }
}
