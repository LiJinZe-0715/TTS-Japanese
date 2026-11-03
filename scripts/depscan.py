"""Package dependency matrix, cycle detection, and the layering rules the compiler cannot see.

Run with `py scripts/depscan.py` (or `python3` elsewhere). Exits non-zero when a rule is
broken, so it can be wired into a build step; the matrix is printed either way.

The module split already makes the compiler enforce `app -> application -> domain`: the two
inner modules are plain Java libraries with no Android SDK on their classpath. What it cannot
see is anything inside the `app` module, so these are checked here:

  * the settings presenter and its contracts (SettingsPresenter, SettingsScreen, ScreenHost,
    Tone, Format) must not use `android.*`. That is what lets the presenter tests run on a
    plain JVM.
  * only `di` uses `infrastructure`. Everything else talks to the ports.
  * no dependency cycles between packages, across all three modules.

"Use" means any reference in code: an import, a static import, or a fully qualified name such
as `android.util.Log.e(...)`, which needs no import at all. Comments and string literals are
ignored, so a javadoc link or an Intent action string does not count.
"""

import collections
import os
import re
import sys

SOURCE_ROOTS = [
    "domain/src/main/java",
    "application/src/main/java",
    "app/src/main/java",
]
PREFIX = "app.voicevox.tts."

# Files in the app module that promise to be free of Android.
PURE_FILES = {
    "app.voicevox.tts.presentation.settings": {
        "SettingsPresenter.java", "SettingsScreen.java", "ScreenHost.java",
        "Tone.java", "Format.java",
    },
}

INFRASTRUCTURE = PREFIX + "infrastructure"
COMPOSITION_ROOT = PREFIX + "di"

ANDROID_USE = re.compile(r"(?<![\w.])androidx?\.[a-z]\w*")
# Plain, static and wildcard imports of this app's own packages.
LOCAL_IMPORT = re.compile(
    r"^import\s+(?:static\s+)?(app\.voicevox\.tts[\w.]*?)(?:\.\*)?\s*;", re.M)
COMMENTS_AND_STRINGS = re.compile(
    r'/\*.*?\*/|//[^\n]*|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'', re.S)


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def code_only(source):
    """The source with comments and string/char literals blanked out."""
    return COMMENTS_AND_STRINGS.sub(" ", source)


def in_package(pkg, root):
    return pkg == root or pkg.startswith(root + ".")


def short(pkg):
    return pkg.replace(PREFIX, "")


def main():
    missing = [root for root in SOURCE_ROOTS if not os.path.isdir(root)]
    if missing:
        print(f"not found: {', '.join(missing)} (run from the repo root)", file=sys.stderr)
        return 2

    imports = {}
    android_leaks = []
    for root in SOURCE_ROOTS:
        for dirpath, _, files in os.walk(root):
            sources = [name for name in files if name.endswith(".java")]
            if not sources:
                continue
            pkg = os.path.relpath(dirpath, root).replace(os.sep, ".")
            imports.setdefault(pkg, set())
            for name in sources:
                path = os.path.join(dirpath, name)
                source = read(path)
                imports[pkg].update(LOCAL_IMPORT.findall(source))
                if name in PURE_FILES.get(pkg, ()) and ANDROID_USE.search(code_only(source)):
                    android_leaks.append(os.path.relpath(path).replace(os.sep, "/"))

    packages = set(imports)

    def owner(symbol):
        parts = symbol.split(".")
        for i in range(len(parts), 0, -1):
            candidate = ".".join(parts[:i])
            if candidate in packages:
                return candidate
        return None

    edges = collections.defaultdict(set)
    for pkg, symbols in imports.items():
        for symbol in symbols:
            target = owner(symbol)
            if target and target != pkg:
                edges[pkg].add(target)

    names = sorted(packages)
    width = max(len(short(n)) for n in names) if names else 0
    print("package dependencies:")
    for pkg in names:
        deps = sorted(edges.get(pkg, ()))
        shown = ", ".join(short(d) for d in deps) or "(none)"
        print(" ", short(pkg).ljust(width), "->", shown)

    cycles = []
    state = {}

    def visit(node, stack):
        state[node] = 1
        for nxt in sorted(edges.get(node, ())):
            if state.get(nxt) == 1:
                cycles.append(" -> ".join(short(p) for p in stack + [node, nxt]))
                return True
            if state.get(nxt) is None and visit(nxt, stack + [node]):
                return True
        state[node] = 2
        return False

    for node in names:
        if state.get(node) is None:
            visit(node, [])

    infrastructure_users = []
    for pkg in names:
        if in_package(pkg, INFRASTRUCTURE) or pkg == COMPOSITION_ROOT:
            continue
        used = sorted(short(d) for d in edges.get(pkg, ()) if in_package(d, INFRASTRUCTURE))
        if used:
            infrastructure_users.append(f"{short(pkg)} -> {', '.join(used)}")

    print()
    print("cycles:", cycles if cycles else "none")
    print("android leaks:", android_leaks if android_leaks else "none")
    print("infrastructure outside di:", infrastructure_users if infrastructure_users else "none")

    failed = bool(cycles or android_leaks or infrastructure_users)
    if failed:
        print()
        print("LAYERING BROKEN", file=sys.stderr)
        for cycle in cycles:
            print(f"  cycle: {cycle}", file=sys.stderr)
        for leak in android_leaks:
            print(f"  android reference in a pure file: {leak}", file=sys.stderr)
        for line in infrastructure_users:
            print(f"  infrastructure used outside di: {line}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
