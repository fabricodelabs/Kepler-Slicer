"""Extract exact upstream printer covers for the indexed OrcaSlicer revision.
Usage: python tools/build_printer_covers.py <orca-checkout> <project-root>
"""
import hashlib, json, pathlib, shutil, subprocess, sys

checkout = pathlib.Path(sys.argv[1]).resolve()
project = pathlib.Path(sys.argv[2]).resolve()
assets = project / "app/src/main/assets"
catalog = json.loads((assets / "orca_machine_catalog.json").read_text(encoding="utf-8"))
revision = subprocess.check_output(["git", "-C", str(checkout), "rev-parse", "HEAD"], text=True).strip()
if revision != catalog["revision"]:
    raise SystemExit("Orca checkout and machine catalog revisions do not match")
root = checkout / "resources/profiles"
dest = assets / "printer_covers"
dest.mkdir(exist_ok=True)
groups = sorted({(p["vendor"], p.get("model", "")) for p in catalog["profiles"] if p.get("model")})
models = []
for vendor, model in groups:
    path = root / vendor / (model + "_cover.png")
    cover = ""
    if path.is_file():
        # Names are opaque so Android assets are portable even for Unicode vendors/models.
        cover = hashlib.sha256((vendor + "/" + model).encode("utf-8")).hexdigest() + ".png"
        shutil.copyfile(path, dest / cover)
    models.append({"vendor": vendor, "model": model, "cover": cover})
(assets / "orca_machine_models.json").write_text(json.dumps({"revision": revision, "models": models}, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
print(f"{len(models)} models, {sum(bool(m['cover']) for m in models)} exact upstream covers")
