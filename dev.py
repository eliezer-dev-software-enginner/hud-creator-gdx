import hashlib
import subprocess
import sys
import threading
import time
from pathlib import Path

from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler

ROOT = Path(__file__).resolve().parent
WATCH_DIRS = [ROOT / "src/main/java", ROOT / "src/main/resources"]
GRADLE_RUN = ["./gradlew", "run"] if sys.platform != "win32" else ["gradlew.bat", "run"]
DEBOUNCE_SECONDS = 1.5

# Editores (IntelliJ com "safe write", vim swap, etc.) criam/tocam arquivos sem que
# o conteúdo do seu código tenha de fato mudado — não conta como mudança real.
IGNORED_NAME_MARKERS = ("___jb_tmp___", "___jb_old___", ".swp", ".swx", "~")

process = None
process_lock = threading.Lock()


def is_noise(path_str: str) -> bool:
    name = Path(path_str).name
    if name.startswith("."):
        return True
    return any(marker in name for marker in IGNORED_NAME_MARKERS)


def content_hash(path_str: str):
    try:
        with open(path_str, "rb") as f:
            return hashlib.sha256(f.read()).hexdigest()
    except OSError:
        return None


class ChangeHandler(FileSystemEventHandler):
    """
    Só reinicia quando o CONTEÚDO de um arquivo realmente muda — não a qualquer
    evento de sistema de arquivos. O IntelliJ salva (ou "re-salva" sem mudança
    nenhuma) todos os arquivos abertos ao perder foco da janela ("Save on frame
    deactivation"), via write-num-temp + rename-por-cima (evento "moved" pro
    watchdog). Cada um desses disparava um restart mesmo sem nada ter mudado.

    O restart em si é debounced de verdade: uma rajada de eventos (ex.: "Save
    All" salvando 10 arquivos de uma vez) reinicia UMA vez só, depois que os
    eventos pararem de chegar por DEBOUNCE_SECONDS — em vez de só ignorar
    tudo que cair dentro da janela e potencialmente nunca reiniciar.
    """

    def __init__(self, on_settled):
        self.known_hashes = {}
        self.on_settled = on_settled
        self._timer = None
        self._timer_lock = threading.Lock()

    def on_any_event(self, event):
        if event.is_directory:
            return

        target_path = getattr(event, "dest_path", None) or event.src_path
        if is_noise(target_path):
            return

        if event.event_type == "deleted":
            changed = self.known_hashes.pop(event.src_path, None) is not None
        else:
            if event.event_type == "moved":
                # o caminho antigo (temp file do safe-write) some — só o dest importa
                self.known_hashes.pop(event.src_path, None)

            new_hash = content_hash(target_path)
            if new_hash is None:
                return  # arquivo já não existe mais / não deu pra ler — ignora

            changed = self.known_hashes.get(target_path) != new_hash
            self.known_hashes[target_path] = new_hash

        if changed:
            self._schedule_restart()

    def _schedule_restart(self):
        with self._timer_lock:
            if self._timer is not None:
                self._timer.cancel()
            self._timer = threading.Timer(DEBOUNCE_SECONDS, self.on_settled)
            self._timer.daemon = True
            self._timer.start()

    def cancel_pending(self):
        with self._timer_lock:
            if self._timer is not None:
                self._timer.cancel()
                self._timer = None


def start():
    global process
    print("[dev] Iniciando aplicação...")
    process = subprocess.Popen(GRADLE_RUN, cwd=ROOT)


def kill_process():
    global process
    if process is None or process.poll() is not None:
        process = None
        return

    if sys.platform == "win32":
        subprocess.run(
            ["taskkill", "/F", "/T", "/PID", str(process.pid)],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
        process.wait()
    else:
        process.terminate()
        process.wait()

    process = None


def restart():
    # o timer do debounce dispara numa thread separada da do observer,
    # então travamos pra garantir que só um restart roda por vez.
    with process_lock:
        print("[dev] Mudança detectada — reiniciando...")
        kill_process()
        start()


if __name__ == "__main__":
    # O .desktop de desenvolvimento (pro ícone aparecer na dock/taskbar no Linux) é
    # criado pelo próprio app agora — megalodonte.application.LinuxDesktopEntry,
    # chamado a partir de Main.java — e não mais daqui, pra valer também rodando
    # direto pela IDE, sem passar por este script.
    start()

    observer = Observer()
    handler = ChangeHandler(on_settled=restart)

    watched = []
    for d in WATCH_DIRS:
        if not d.exists():
            print(f"[dev] Aviso: diretório não encontrado, ignorando: {d}")
            continue
        observer.schedule(handler, str(d), recursive=True)
        watched.append(str(d))

    observer.start()
    print(f"[dev] Monitorando: {watched} (Ctrl+C para sair)")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("[dev] Encerrando...")
        handler.cancel_pending()
        with process_lock:
            kill_process()
        observer.stop()
    observer.join()