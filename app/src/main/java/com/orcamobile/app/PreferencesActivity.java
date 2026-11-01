package com.orcamobile.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Native Android preferences grouped like the supplied Orca desktop-to-mobile references. */
public final class PreferencesActivity extends Activity {
    private MdUi ui; private LinearLayout page; private String section=""; private SharedPreferences prefs;
    @Override public void onCreate(Bundle state){MdUi.prepare(this); super.onCreate(state);prefs=getSharedPreferences("app_preferences",0);if(state!=null)section=state.getString("section","");render();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("section",section);super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){if(section.isEmpty())super.onBackPressed();else{section="";render();}}
    private void render(){MdUi.prepare(this);ui=new MdUi(this);String title=section.isEmpty()?"Настройки приложения":section;page=ui.page(this,title,()->onBackPressed());ui.navigation(this,3);
        if(section.isEmpty()){
            row("Основные","Оформление и история моделей","◐",()->open("Основные"));
            row("Управление","Вращение и масштаб 3D-стола","⌖",()->open("Управление"));
            row("Сеть","Подключения и синхронизация","⌁",()->open("Сеть"));
            row("Разработчику","Версия движка и сведения о сборке","⌘",()->open("Разработчику"));
            ui.gap(page,24);row("О Kepler Slicer","Android · OrcaSlicer · лицензия","K",()->open("О Kepler Slicer"));
        }else if(section.equals("Основные")){
            row("Оформление",ui.dark?"Тёмное · монохром":"Светлое · монохром","◐",this::chooseTheme);
            row("Недавние файлы",prefs.getInt("recent_count",12)+" моделей","▱",this::chooseRecentCount);
            info("Единицы","Миллиметры. Профили и координаты движка Orca используют миллиметры.");
            info("Сохранение проекта","Открытая STL-модель, её преобразование и выбор профилей сохраняются на устройстве автоматически.");
        }else if(section.equals("Управление")){
            row("Скорость вращения",String.format(java.util.Locale.ROOT,"%.2f×",prefs.getFloat("orbit_speed",.75f)),"⌖",this::chooseOrbit);
            row("Чувствительность перемещения",String.format(java.util.Locale.ROOT,"%.2f×",prefs.getFloat("move_speed",.6f)),"⌖",this::chooseMove);
            toggle("Обратное масштабирование","Инвертирует жест сведения и разведения пальцев", "reverse_zoom");
            info("Жесты","Один палец — вращение камеры. Два пальца — масштаб. Кнопка вида на рабочем столе возвращает камеру в исходное положение.");
            info("Модель и камера","Вращение камеры не изменяет модель. Размер, поворот и смещение модели задаются отдельным инструментом рабочего стола.");
        }else if(section.equals("Сеть")){
            row("Подключения по Wi-Fi","Поиск, адреса, проверка связи и передача файлов","⌁",()->startActivity(new android.content.Intent(this,NetworkActivity.class)));
            row("Orca Cloud","Вход, синхронизация и конфликты профилей","☁",()->startActivity(new android.content.Intent(this,CloudSyncActivity.class)));
            info("Локальная работа","Импорт STL, выбор встроенных профилей, нарезка и сохранение G-code работают локально.");
        }else if(section.equals("Разработчику")){
            info("Исходный движок","OrcaSlicer\n"+OrcaProfileCatalog.UPSTREAM_REVISION);
            info("Платформа", "Android "+android.os.Build.VERSION.RELEASE+" · API "+android.os.Build.VERSION.SDK_INT+"\nABI движка: arm64-v8a");
            info("Состояние переноса","Экспериментальная Android-сборка. Полный перенос функций настольной Orca ещё не завершён.");
            info("Проверка G-code","Результаты проверяются в эмуляторе. Сравнение всех режимов с настольной версией и проверка на физическом принтере ещё не выполнены.");
        }else{
            TextView brand=ui.text("Kepler Slicer",30,true);page.addView(brand);ui.gap(page,8);TextView sub=ui.text("Мобильное рабочее пространство для 3D-печати",14,false);sub.setTextColor(ui.muted);page.addView(sub);ui.gap(page,28);
            info("Движок и материалы","Официальный исходный код OrcaSlicer, профили принтеров, процессов, материалов и изображения принтеров.");
            info("Лицензия","GNU Affero General Public License v3. Исходники сборки и сведения о сторонних компонентах поставляются вместе с проектом.");
            info("Интерфейс","Монохромное оформление по принципам Material Design 3. Компоновка адаптирована под Android по предоставленным вами референсам.");
        }
    }
    private void open(String name){section=name;render();}
    private void row(String title,String subtitle,String icon,Runnable action){page.addView(ui.row(title,subtitle,icon,action));ui.gap(page,10);}
    private void info(String title,String description){page.addView(ui.row(title,description,null,null));ui.gap(page,10);}
    private void toggle(String title,String description,String key){LinearLayout card=ui.horizontal();ui.pad(card,16,16,16,16);card.setBackground(ui.shape(ui.surface,22));LinearLayout labels=ui.vertical();labels.addView(ui.text(title,15,true));ui.gap(labels,6);TextView detail=ui.text(description,12,false);detail.setTextColor(ui.muted);labels.addView(detail);card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));Switch s=new Switch(this);s.setContentDescription(title);s.setChecked(prefs.getBoolean(key,false));s.setThumbTintList(android.content.res.ColorStateList.valueOf(ui.ink));s.setTrackTintList(android.content.res.ColorStateList.valueOf(ui.line));s.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean(key,checked).apply());card.addView(s);page.addView(card);ui.gap(page,10);}
    private void chooseTheme(){MdUi.alert(this).setTitle("Оформление").setSingleChoiceItems(new String[]{"Тёмное","Светлое"},ui.dark?0:1,(d,n)->{prefs.edit().putString("theme",n==0?"dark":"light").apply();d.dismiss();render();}).setNegativeButton("Отмена",null).show();}
    private void chooseRecentCount(){int[] counts={6,12,18,30};String[] labels={"6 моделей","12 моделей","18 моделей","30 моделей"};int current=prefs.getInt("recent_count",12),selected=1;for(int i=0;i<counts.length;i++)if(counts[i]==current)selected=i;MdUi.alert(this).setTitle("Недавние файлы").setSingleChoiceItems(labels,selected,(d,n)->{prefs.edit().putInt("recent_count",counts[n]).apply();d.dismiss();render();}).setNegativeButton("Отмена",null).show();}
    private void chooseOrbit(){float[] values={.25f,.5f,.75f,1f,1.5f,2f};int selected=1;float current=prefs.getFloat("orbit_speed",.75f);for(int i=0;i<values.length;i++)if(values[i]==current)selected=i;MdUi.alert(this).setTitle("Скорость вращения").setSingleChoiceItems(new String[]{"0.25×","0.5×","0.75×","1×","1.5×","2×"},selected,(d,n)->{prefs.edit().putFloat("orbit_speed",values[n]).apply();d.dismiss();render();}).setNegativeButton("Отмена",null).show();}    private void chooseMove(){float[] values={.25f,.5f,.6f,.75f,1f};float current=prefs.getFloat("move_speed",.6f);int selected=2;for(int i=0;i<values.length;i++)if(values[i]==current)selected=i;MdUi.alert(this).setTitle("Чувствительность перемещения").setSingleChoiceItems(new String[]{"0.25×","0.5×","0.6×","0.75×","1×"},selected,(d,n)->{prefs.edit().putFloat("move_speed",values[n]).apply();d.dismiss();render();}).setNegativeButton("Отмена",null).show();}

}
