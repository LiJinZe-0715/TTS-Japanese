#!/usr/bin/env node
// Downloads the VOICEVOX Android binaries and voice data this engine needs, then
// arranges them the way Gradle expects. Re-running is cheap: finished steps are skipped.
//
//   node scripts/fetch-assets.mjs          # download everything
//   node scripts/fetch-assets.mjs --check  # only report what is already in place
//
// None of the five artifacts it produces are in git (see .gitignore): they are ~102 MB
// together. Run this once after cloning, before the first build. Downloads are cached in
// .cache/, so re-running after a partial checkout only fetches what is missing.
//
// What it restores:
//   jniLibs/arm64-v8a/*.so   VOICEVOX core + onnxruntime (the inference engine)
//   cpp/include/*.h          the matching C header
//   assets/vvm/0.vvm         the bundled voice model (4 characters)
//   assets/open_jtalk_dict.zip  the Japanese dictionary, repacked flat for AssetManager
//
// The VVM and the dictionary are shipped inside the APK on purpose, so the engine speaks
// offline the moment it is installed; every other voice model is downloaded on demand.

import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";
import {createHash} from "node:crypto";
import {execFileSync} from "node:child_process";
import {fileURLToPath} from "node:url";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const MAIN = path.join(ROOT, "app/src/main");
const JNI_LIBS = path.join(MAIN, "jniLibs/arm64-v8a");
const ASSETS = path.join(MAIN, "assets");
const INCLUDE = path.join(MAIN, "cpp/include");
const CACHE = path.join(ROOT, ".cache");

// `sha256` is the digest GitHub publishes for the release asset
// (/repos/{owner}/{repo}/releases/tags/{tag} → assets[].digest). It is the only thing that
// proves the bytes are what upstream built, so a mismatch is a hard failure rather than a
// warning: a silently corrupted .so here becomes an UnsatisfiedLinkError on the phone, and a
// corrupted .vvm becomes a voice pack that can never load.
//
// `null` means upstream published no digest for that asset. It is written out explicitly so
// the gap is visible in review instead of being mistaken for a checked download.
const CORE = {
    url: "https://github.com/VOICEVOX/voicevox_core/releases/download/0.17.1/voicevox_core-android-arm64-0.17.1.zip",
    file: "voicevox_core-android-arm64-0.17.1.zip",
    sha256: "069f29018ea27d939e3198ef34592a489181ba3d0287819f80c518963c639d37",
};
const ORT = {
    url: "https://github.com/VOICEVOX/onnxruntime-builder/releases/download/voicevox_onnxruntime-1.17.3/voicevox_onnxruntime-android-arm64-1.17.3.tgz",
    file: "voicevox_onnxruntime-android-arm64-1.17.3.tgz",
    // Published before GitHub started recording asset digests; the API returns digest: null.
    sha256: null,
};
const DICT = {
    // jaist 镜像已失效，downloads.sourceforge.net 会自动重定向到可用节点。
    url: "https://downloads.sourceforge.net/project/open-jtalk/Dictionary/open_jtalk_dic-1.11/open_jtalk_dic_utf_8-1.11.tar.gz",
    file: "open_jtalk_dic_utf_8-1.11.tar.gz",
    // SourceForge publishes no stable digest for this file.
    sha256: null,
};
const VVM = {
    url: "https://github.com/VOICEVOX/voicevox_vvm/releases/download/0.17.0/0.vvm",
    file: "0.vvm",
    sha256: "ecd35374d4182cd883cba5040376f7f888cc6ba248b1c2f4cea07cdb34bb1318",
};

const targets = {
    core: path.join(JNI_LIBS, "libvoicevox_core.so"),
    ort: path.join(JNI_LIBS, "libvoicevox_onnxruntime.so"),
    header: path.join(INCLUDE, "voicevox_core.h"),
    dict: path.join(ASSETS, "open_jtalk_dict.zip"),
    vvm: path.join(ASSETS, "vvm/0.vvm"),
};

const checkOnly = process.argv.includes("--check");

function ensureDirs() {
    for (const dir of [JNI_LIBS, ASSETS, path.join(ASSETS, "vvm"), INCLUDE, CACHE]) {
        fs.mkdirSync(dir, {recursive: true});
    }
}

function mb(bytes) {
    return (bytes / 1048576).toFixed(1) + " MB";
}

function sha256Of(file) {
    return createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

/** Throws unless `file` hashes to `expected`. A `null` expectation is reported, not assumed. */
function verify(file, expected, label) {
    if (!expected) {
        console.log(`  WARNING: no published checksum for ${label}; bytes were not verified`);
        return;
    }
    const actual = sha256Of(file);
    if (actual !== expected) {
        fs.rmSync(file, {force: true});
        throw new Error(
            `checksum mismatch for ${label}\n  expected ${expected}\n  actual   ${actual}\n` +
            "  (deleted; re-run to download again)");
    }
    console.log(`  verified ${label} (sha256 ${expected.slice(0, 12)}…)`);
}

async function download({url, file, sha256}) {
    const target = path.join(CACHE, file);
    // A cached file is re-verified rather than trusted on size: the point of the cache is to
    // make re-runs cheap, not to let a half-written file from a killed run become the build.
    if (fs.existsSync(target) && fs.statSync(target).size > 0) {
        verify(target, sha256, file);
        console.log(`  cached ${file} (${mb(fs.statSync(target).size)})`);
        return target;
    }
    console.log(`  GET ${url}`);
    const response = await fetch(url, {redirect: "follow"});
    if (!response.ok) throw new Error(`HTTP ${response.status} for ${url}`);
    const buffer = Buffer.from(await response.arrayBuffer());
    fs.writeFileSync(target, buffer);
    verify(target, sha256, file);
    console.log(`  saved ${file} (${mb(buffer.length)})`);
    return target;
}

/**
 * Windows 10+ ships bsdtar as System32\tar.exe, and bsdtar reads zip as well as tar.gz. It is
 * used by full path so that a GNU tar from Git Bash or MSYS earlier on PATH cannot shadow it:
 * that one cannot read zip, and a plain cmd/PowerShell session has no `unzip` at all.
 */
function windowsTar() {
    if (process.platform !== "win32") return null;
    const tar = path.join(process.env.SystemRoot || "C:\\Windows", "System32", "tar.exe");
    return fs.existsSync(tar) ? tar : null;
}

function extract(archive, dest) {
    fs.mkdirSync(dest, {recursive: true});
    // Run from the archive's directory with relative paths: GNU tar reads "C:\..."
    // as a remote host named "C", and msys tar cannot read zip at all.
    const cwd = path.dirname(archive);
    const relative = path.relative(cwd, dest) || ".";
    const name = path.basename(archive);
    const bsdtar = windowsTar();
    if (bsdtar) {
        execFileSync(bsdtar, ["-xf", name, "-C", relative], {stdio: "inherit", cwd});
        return;
    }
    const args = name.endsWith(".zip")
        ? ["-o", "-q", name, "-d", relative]
        : ["-xzf", name, "-C", relative];
    execFileSync(name.endsWith(".zip") ? "unzip" : "tar", args, {stdio: "inherit", cwd});
}

function findFile(dir, name) {
    for (const entry of fs.readdirSync(dir, {withFileTypes: true})) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            const found = findFile(full, name);
            if (found) return found;
        } else if (entry.name === name) {
            return full;
        }
    }
    return null;
}

const CRC_TABLE = (() => {
    const table = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
        let c = n;
        for (let k = 0; k < 8; k++) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
        table[n] = c;
    }
    return table;
})();

function crc32(buffer) {
    let crc = -1;
    for (let i = 0; i < buffer.length; i++) crc = CRC_TABLE[(crc ^ buffer[i]) & 0xFF] ^ (crc >>> 8);
    return (crc ^ -1) >>> 0;
}

/** Minimal deflate zip writer: enough for a flat dictionary, no external deps. */
function writeZip(entries, target) {
    const locals = [];
    const central = [];
    let offset = 0;
    for (const {name, data} of entries) {
        const nameBytes = Buffer.from(name, "utf8");
        const deflated = zlib.deflateRawSync(data, {level: 9});
        const crc = crc32(data);
        const local = Buffer.alloc(30);
        local.writeUInt32LE(0x04034b50, 0);
        local.writeUInt16LE(20, 4);
        local.writeUInt16LE(0x0800, 6);
        local.writeUInt16LE(8, 8);
        local.writeUInt32LE(crc, 14);
        local.writeUInt32LE(deflated.length, 18);
        local.writeUInt32LE(data.length, 22);
        local.writeUInt16LE(nameBytes.length, 26);
        locals.push(local, nameBytes, deflated);

        const head = Buffer.alloc(46);
        head.writeUInt32LE(0x02014b50, 0);
        head.writeUInt16LE(20, 4);
        head.writeUInt16LE(20, 6);
        head.writeUInt16LE(0x0800, 8);
        head.writeUInt16LE(8, 10);
        head.writeUInt32LE(crc, 16);
        head.writeUInt32LE(deflated.length, 20);
        head.writeUInt32LE(data.length, 24);
        head.writeUInt16LE(nameBytes.length, 28);
        head.writeUInt32LE(offset, 42);
        central.push(head, nameBytes);

        offset += 30 + nameBytes.length + deflated.length;
    }
    const centralBuffer = Buffer.concat(central);
    const end = Buffer.alloc(22);
    end.writeUInt32LE(0x06054b50, 0);
    end.writeUInt16LE(entries.length, 8);
    end.writeUInt16LE(entries.length, 10);
    end.writeUInt32LE(centralBuffer.length, 12);
    end.writeUInt32LE(offset, 16);
    fs.writeFileSync(target, Buffer.concat([...locals, centralBuffer, end]));
}

async function main() {
    ensureDirs();
    console.log("VOICEVOX TTS engine assets");

    if (checkOnly) {
        for (const [name, file] of Object.entries(targets)) {
            const state = fs.existsSync(file) ? mb(fs.statSync(file).size) : "MISSING";
            console.log(`  ${name.padEnd(7)} ${state.padEnd(12)} ${path.relative(ROOT, file)}`);
        }
        // Which downloads the script can actually verify, so a missing checksum is noticed
        // here rather than after a corrupt asset has been unpacked into the APK.
        const verified = [["core", CORE], ["onnx", ORT], ["dict", DICT], ["vvm", VVM]]
            .filter(([, spec]) => spec.sha256)
            .map(([name]) => name);
        const unverified = [["core", CORE], ["onnx", ORT], ["dict", DICT], ["vvm", VVM]]
            .filter(([, spec]) => !spec.sha256)
            .map(([name]) => name);
        console.log(`  checksum verified:   ${verified.join(", ") || "(none)"}`);
        console.log(`  checksum unavailable:${unverified.length ? " " + unverified.join(", ") : " (none)"}`);
        return;
    }

    if (!fs.existsSync(targets.core) || !fs.existsSync(targets.header)) {
        const archive = await download(CORE);
        const staging = path.join(CACHE, "core");
        fs.rmSync(staging, {recursive: true, force: true});
        extract(archive, staging);
        const lib = findFile(staging, "libvoicevox_core.so");
        const header = findFile(staging, "voicevox_core.h");
        if (!lib || !header) throw new Error("core archive layout changed");
        fs.copyFileSync(lib, targets.core);
        fs.copyFileSync(header, targets.header);
        console.log(`  -> ${path.relative(ROOT, targets.core)} (${mb(fs.statSync(targets.core).size)})`);
    } else {
        console.log("  core already in place");
    }

    if (!fs.existsSync(targets.ort)) {
        const archive = await download(ORT);
        const staging = path.join(CACHE, "ort");
        fs.rmSync(staging, {recursive: true, force: true});
        extract(archive, staging);
        const lib = findFile(staging, "libvoicevox_onnxruntime.so");
        if (!lib) throw new Error("onnxruntime archive layout changed");
        fs.copyFileSync(lib, targets.ort);
        console.log(`  -> ${path.relative(ROOT, targets.ort)} (${mb(fs.statSync(targets.ort).size)})`);
    } else {
        console.log("  onnxruntime already in place");
    }

    if (!fs.existsSync(targets.dict)) {
        const archive = await download(DICT);
        const staging = path.join(CACHE, "dict");
        fs.rmSync(staging, {recursive: true, force: true});
        extract(archive, staging);
        const root = path.join(staging, "open_jtalk_dic_utf_8-1.11");
        const source = fs.existsSync(root) ? root : staging;
        const entries = fs.readdirSync(source, {withFileTypes: true})
            .filter((entry) => entry.isFile())
            .map((entry) => ({name: entry.name, data: fs.readFileSync(path.join(source, entry.name))}));
        if (!entries.some((entry) => entry.name === "sys.dic")) throw new Error("dictionary layout changed");
        writeZip(entries, targets.dict);
        console.log(`  -> ${path.relative(ROOT, targets.dict)} (${entries.length} files, ${mb(fs.statSync(targets.dict).size)})`);
    } else {
        console.log("  dictionary already in place");
    }

    if (!fs.existsSync(targets.vvm)) {
        const archive = await download(VVM);
        fs.copyFileSync(archive, targets.vvm);
        console.log(`  -> ${path.relative(ROOT, targets.vvm)} (${mb(fs.statSync(targets.vvm).size)})`);
    } else {
        console.log("  voice model already in place");
    }

    console.log("done");
}

main().catch((error) => {
    console.error(error.message);
    process.exit(1);
});
