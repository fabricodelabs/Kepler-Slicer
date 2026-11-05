package com.keplerslicer.app;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Identity of the immutable model, exact profile triple and typed process overrides. */
final class ResultFingerprint {
    static String of(String modelKey,String profileFingerprint,String overrides) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(String value:new String[]{"kepler-result-v1",modelKey,profileFingerprint,overrides}) {
                if(value==null)throw new IllegalArgumentException(L.t("Не указан снимок задания."));
                byte[] bytes=value.getBytes(StandardCharsets.UTF_8);digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);
            }
            StringBuilder result=new StringBuilder();for(byte b:digest.digest())result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return result.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
