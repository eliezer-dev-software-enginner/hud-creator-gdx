#!/usr/bin/env python3
"""
Builds Plics SW as an app-image (jpackage, same jlink runtime the .deb uses) and
assembles a local Flatpak install via flatpak-builder --user --install, just to
test. Doesn't publish anything to Flathub — that still requires opening a manual
Pull Request at github.com/flathub/flathub, their review, etc.

Requires: flatpak and flatpak-builder installed, and the runtimes
org.freedesktop.Platform//24.08 and org.freedesktop.Sdk//24.08 (the script doesn't
install those two for you — if you don't have them, run
`flatpak install flathub org.freedesktop.Platform//24.08
org.freedesktop.Sdk//24.08` first).
"""
import os
import shutil
import stat
import subprocess
from config import *

APP_ID = "io.github.eliezerdevsoftwareenginner.PlicsSW"
FLATPAK_DIR = ROOT / "flatpak"

gradlew = ROOT / "gradlew"
if not os.access(gradlew, os.X_OK):
    gradlew.chmod(gradlew.stat().st_mode | stat.S_IXUSR)

for tool in ("flatpak", "flatpak-builder"):
    if shutil.which(tool) is None:
        raise EnvironmentError(f"'{tool}' not found on PATH. Install it before running this script.")

temp_dir = prepare_temp()

print("[1/5] 📦 Generating fat JAR...")
run_gradle("clean", "shadowJar")
jar_file = find_jar()
shutil.copy(jar_file, temp_dir / "app.jar")

print("[2/5] 📚 Copying JavaFX modules...")
copy_javafx(temp_dir)

print("[3/5] ⚙️  Generating runtime with jlink...")
run_jlink(temp_dir)
copy_natives(temp_dir)

smoke_test(temp_dir)

print("[4/5] 🧩 Generating app-image (jpackage)...")
run_jpackage(temp_dir, "app-image")

app_image_dir = ROOT / "dist" / APP_NAME
if not app_image_dir.exists():
    raise FileNotFoundError(f"app-image not found at {app_image_dir}")

build_input_dir = FLATPAK_DIR / "app-image"
shutil.rmtree(build_input_dir, ignore_errors=True)
shutil.copytree(app_image_dir, build_input_dir)

print("[5/5] 🏗️  flatpak-builder (build + install --user)...")
subprocess.run(
    [
        "flatpak-builder", "--user", "--install", "--force-clean",
        str(FLATPAK_DIR / "_build"), str(FLATPAK_DIR / f"{APP_ID}.yml"),
    ],
    cwd=FLATPAK_DIR, check=True,
)

print(f"\n✅ Instalado localmente. Teste com: flatpak run {APP_ID}")
print(f"   Pra desinstalar: flatpak uninstall {APP_ID}")
