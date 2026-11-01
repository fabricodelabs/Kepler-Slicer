import com.orcamobile.app.settings.SettingsSchema;
import org.json.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

public class SettingsSchemaTest {
    static void check(boolean value, String what) { if (!value) throw new AssertionError(what); }
    static SettingsSchema.Option find(SettingsSchema schema, String kind, String key) {
        return schema.options(kind).stream().filter(v -> v.key.equals(key)).findFirst().orElseThrow();
    }
    static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("accepted invalid input"); }
        catch (IllegalArgumentException expected) { }
    }
    public static void main(String[] args) throws Exception {
        JSONObject source = new JSONObject(Files.readString(Path.of(args[0])));
        Constructor<SettingsSchema> constructor = SettingsSchema.class.getDeclaredConstructor(JSONObject.class);
        constructor.setAccessible(true);
        SettingsSchema schema = constructor.newInstance(source);
        check(schema.options("process").size()==431 && schema.options("filament").size()==168 && schema.options("printer").size()==230,"counts");
        check(schema.uiInventory().getJSONObject("sections").getJSONArray("process").length()==431,"inventory");
        SettingsSchema.Option layer = find(schema,"process","layer_height");
        check(layer.mode.equals("comSimple") && layer.modeKnown && layer.parseInput("0,25", "0.2").equals("0.25"),"layer");
        rejects(() -> layer.parseInput("-0.1", "0.2"));
        rejects(() -> layer.parseInput("NaN", "0.2"));
        SettingsSchema.Option bool = find(schema,"process","enable_support");
        check(bool.isBoolean() && bool.parseInput("true", "0").equals("1") && !bool.booleanValue("0"),"boolean");
        rejects(() -> bool.booleanValue(null));
        SettingsSchema.Option seam = find(schema,"process","seam_position");
        check(seam.displayValue("nearest").equals("Nearest"),"enum label");
        rejects(() -> seam.parseInput("definitely_not_enum", "nearest"));
        SettingsSchema.Option vector = find(schema,"filament","filament_diameter");
        check(vector.isVector() && ((JSONArray) vector.parseInput("[\"1.75\", \"nil\"]",null)).length()==2,"array");
        rejects(() -> vector.parseInput("[1,]",null));
        rejects(() -> vector.parseInput("[NaN]",null));
        rejects(() -> vector.parseInput("[1] garbage",null));
        rejects(() -> vector.parseInput("{unquoted:1}",null));
        try { schema.options("process").clear(); throw new AssertionError("mutable options"); }
        catch (UnsupportedOperationException expected) {}
        System.out.println("Settings schema host checks passed");
    }
}
