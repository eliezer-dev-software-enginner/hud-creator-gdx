#!/usr/bin/env python3
import os
import stat
from config import *

gradlew = ROOT / "gradlew"
if not os.access(gradlew, os.X_OK):
    gradlew.chmod(gradlew.stat().st_mode | stat.S_IXUSR)

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

print("[4/5] 🎁 Generating .deb package...")
run_jpackage(temp_dir, "deb", [
    "--linux-shortcut",
    "--linux-menu-group", "Office",
    "--linux-package-name", APP_NAME.lower().replace(" ", "-"),
])

print("[5/5] 📝 Renaming package...")
final = rename_output("deb")
print(f"\n✅ .deb package created: {final}")
print(f"   To install: sudo dpkg -i \"{final}\"")
open_dist_folder()
