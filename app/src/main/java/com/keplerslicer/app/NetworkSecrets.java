package com.keplerslicer.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Network credentials are private AES-GCM records; the key stays in Android Keystore. */
final class NetworkSecrets {
    private static final Object LOCK=new Object();
    private static final String ALIAS="kepler.network.v1";
    static String get(Context context,String key)throws IOException {
        synchronized(LOCK){try{
            String id=id(key), encoded=context.getSharedPreferences("network_secrets",0).getString(id,"");if(encoded==null||encoded.isEmpty())return "";
            if(encoded.length()>6*1024*1024)throw new IOException("Сохранённая запись подключения повреждена.");
            byte[] packet=Base64.decode(encoded,Base64.NO_WRAP);if(packet.length<29||packet[0]!=1)throw new IOException("Неверная версия хранилища подключений.");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,packet,1,12));cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(packet,13,packet.length-13),StandardCharsets.UTF_8);
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Не удалось расшифровать сохранённое подключение.",e);}}
    }
    static void put(Context context,String key,String value)throws IOException {
        if(value==null||value.getBytes(StandardCharsets.UTF_8).length>4*1024*1024)throw new IOException("Слишком большая запись подключения.");
        synchronized(LOCK){try{
            String id=id(key);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),iv=cipher.getIV();if(iv.length!=12)throw new IOException("Неверный размер параметра шифрования.");
            byte[] packet=new byte[13+encrypted.length];packet[0]=1;System.arraycopy(iv,0,packet,1,12);System.arraycopy(encrypted,0,packet,13,encrypted.length);
            SharedPreferences preferences=context.getSharedPreferences("network_secrets",0);String previous=preferences.getString(id,null);
            if(!preferences.edit().putString(id,Base64.encodeToString(packet,Base64.NO_WRAP)).commit()){
                if(previous==null)preferences.edit().remove(id).apply();else preferences.edit().putString(id,previous).apply();
                throw new IOException("Не удалось сохранить подключение на устройстве.");
            }
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Не удалось зашифровать подключение.",e);}}
    }
    static void remove(Context context,String key)throws IOException {synchronized(LOCK){try{String id=id(key);SharedPreferences p=context.getSharedPreferences("network_secrets",0);String previous=p.getString(id,null);if(!p.edit().remove(id).commit()){if(previous!=null)p.edit().putString(id,previous).apply();throw new IOException("Не удалось удалить запись подключения.");}}catch(IOException e){throw e;}catch(Exception e){throw new IOException("Не удалось изменить хранилище подключений.",e);}}}
    private static String id(String key)throws Exception{if(key==null||key.isEmpty()||key.length()>512)throw new IOException("Неверное имя записи подключения.");StringBuilder s=new StringBuilder("v1-");for(byte b:MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)))s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    private static SecretKey key()throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());return generator.generateKey();}
}
