"""Build app/src/main/assets/kepler_printer_catalog.json from the pinned upstream checkout.
Every instantiated machine profile listed in a vendor manifest (machine_list), including the ones in
sub-folders such as Elegoo/machine/EN4SERIES. Then run build_printer_covers.py for models and covers.
Usage: python tools/build_printer_catalog.py <upstream-checkout> <project-root>
"""
import json, pathlib, subprocess, sys

checkout = pathlib.Path(sys.argv[1]).resolve()
project = pathlib.Path(sys.argv[2]).resolve()
root = checkout / "resources/profiles"
revision = subprocess.check_output(["git", "-C", str(checkout), "rev-parse", "HEAD"], text=True).strip()
profiles = []
for manifest in sorted(root.glob("*.json")):
    vendor = manifest.stem
    try:
        data = json.loads(manifest.read_text(encoding="utf-8"))
    except (ValueError, UnicodeDecodeError):
        continue
    for item in data.get("machine_list", []):
        sub = item.get("sub_path", "")
        path = root / vendor / sub
        if not path.is_file():
            continue
        machine = json.loads(path.read_text(encoding="utf-8"))
        if str(machine.get("instantiation", "")).lower() != "true":
            continue
        profiles.append({"name": machine.get("name", item.get("name")), "vendor": vendor, "path": vendor + "/" + sub,
                         "model": machine.get("printer_model", ""), "variant": machine.get("printer_variant", "")})
profiles.sort(key=lambda p: (p["vendor"], p["name"]))
out = project / "app/src/main/assets/kepler_printer_catalog.json"
out.write_text(json.dumps({"source": "https://github.com/OrcaSlicer/OrcaSlicer", "revision": revision, "profiles": profiles},
                          ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
print(f"{len(profiles)} machine profiles, {len({(p['vendor'], p['model']) for p in profiles})} models")
