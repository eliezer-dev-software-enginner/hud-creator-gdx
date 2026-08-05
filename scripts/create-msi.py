from config import *

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

print("[4/5] 🎁 Generating MSI package...")
run_jpackage(temp_dir, "msi", [
    "--win-menu",
    "--win-shortcut",
    "--win-per-user-install",
])

print("[5/5] 📝 Renaming package...")
final = rename_output("msi")
print(f"\n✅ MSI created: {final}")
open_dist_folder()
