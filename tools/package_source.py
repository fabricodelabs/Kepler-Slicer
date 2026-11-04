#!/usr/bin/env python3
"""Package the Phase 2 Android source tree without local state or APKs.

The archive is built beside the project, checked against the source files and
atomically replaced only after every check succeeds. This does not build the app.
"""
from __future__ import annotations
import argparse
import hashlib
import os
from pathlib import Path
import tempfile
import zipfile
import shutil
import subprocess

PROJECT = Path(__file__).resolve().parents[1]
DIRECTORIES = ('app', 'docs', 'engine', 'gradle', 'tools')
ROOT_FILES = ('.gitignore', 'build.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat',
              'LICENSE-ORCASLICER.txt', 'NETWORK_SUPPORT.md', 'README.md', 'settings.gradle')
EXCLUDED_DIRS = {'.git', '.gradle', '.idea', 'build', '__pycache__'}
EXCLUDED_FILES = {'local.properties', '.env', '.DS_Store', 'Thumbs.db'}
EXCLUDED_SUFFIXES = {'.apk', '.aab', '.pyc', '.keystore', '.jks', '.pem', '.key'}
ALREADY_COMPRESSED = {'.zip', '.jar', '.png', '.jpg', '.jpeg', '.webp', '.gz'}
PREFIX = 'KeplerSlicer/'
UPSTREAM_PREFIX = PREFIX + 'third_party/OrcaSlicer/'
UPSTREAM_REVISION = '6d2869c430284714f0a22570c38d3b359965a802'
UPSTREAM_BINARY_SUFFIXES = {'.exe', '.dll', '.so', '.dylib', '.a', '.lib', '.o', '.pdb',
                            '.class', '.pyd', '.node', '.apk', '.aab', '.pyc'}


def collect() -> list[Path]:
    result = []
    for name in ROOT_FILES:
        file = PROJECT / name
        if file.is_symlink() or not file.is_file():
            raise ValueError('Missing or symlinked required root file: ' + name)
        result.append(file)
    for name in DIRECTORIES:
        base = PROJECT / name
        if base.is_symlink() or not base.is_dir():
            raise ValueError('Missing or symlinked required directory: ' + name)
        for directory, dirs, files in os.walk(base, followlinks=False):
            parent = Path(directory)
            for item in dirs:
                if (parent / item).is_symlink():
                    raise ValueError('Symlinked directory rejected: ' + str(parent / item))
            dirs[:] = sorted(item for item in dirs if item.lower() not in EXCLUDED_DIRS)
            for item in sorted(files):
                file = parent / item
                if file.is_symlink():
                    raise ValueError('Symlinked file rejected: ' + str(file))
                if item.lower() in {x.lower() for x in EXCLUDED_FILES} or file.suffix.lower() in EXCLUDED_SUFFIXES:
                    continue
                if not file.is_file():
                    raise ValueError('Unexpected filesystem entry: ' + str(file))
                result.append(file)
    result.sort(key=lambda p: p.relative_to(PROJECT).as_posix())
    names = [p.relative_to(PROJECT).as_posix() for p in result]
    if len(names) != len(set(name.casefold() for name in names)):
        raise ValueError('Case-insensitive archive name collision')
    return result


def digest(stream) -> str:
    result = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b''):
        result.update(block)
    return result.hexdigest()

def upstream_included(name: str) -> bool:
    path=Path(name)
    parts=name.replace('\\','/').split('/')
    if not name or name.startswith('/') or '\\' in name or any(p in ('','.', '..') for p in parts):
        raise ValueError('Unexpected upstream archive path: '+name)
    if any(p.lower() in EXCLUDED_DIRS for p in parts):return False
    if path.name.lower() in {x.lower() for x in EXCLUDED_FILES}:return False
    return path.suffix.lower() not in UPSTREAM_BINARY_SUFFIXES

def git(checkout: Path, *args: str) -> bytes:
    return subprocess.run(['git','-C',str(checkout),*args],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE).stdout


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path,
                        default=PROJECT.parent / 'Kepler-Slicer-Glass-Phase2-source.zip')
    parser.add_argument('--upstream', type=Path, default=PROJECT.parent.parent / 'work' / 'OrcaSlicer',
                        help='Clean local upstream Git checkout at the pinned revision')
    args = parser.parse_args()
    target = args.output.resolve()
    if target == PROJECT or PROJECT in target.parents:
        raise ValueError('Archive must not be written inside the project being packaged')
    if target.suffix.lower() != '.zip':
        raise ValueError('Output must have a .zip extension')
    target.parent.mkdir(parents=True, exist_ok=True)
    files = collect()
    signatures = {f: (f.stat().st_size, f.stat().st_mtime_ns) for f in files}
    checkout=args.upstream.resolve()
    if git(checkout,'rev-parse','HEAD').decode('ascii').strip()!=UPSTREAM_REVISION:
        raise ValueError('upstream checkout is not at the pinned revision')
    if git(checkout,'status','--porcelain=v1','--untracked-files=all').strip():
        raise ValueError('upstream checkout is not clean; refusing to omit local changes')
    fd, temp_name = tempfile.mkstemp(prefix='.kepler-source-', suffix='.zip', dir=target.parent)
    os.close(fd)
    temporary = Path(temp_name)
    fd, upstream_name = tempfile.mkstemp(prefix='.kepler-upstream-', suffix='.zip', dir=target.parent)
    os.close(fd)
    upstream_temporary = Path(upstream_name)
    try:
        git(checkout,'archive','--format=zip','--output',str(upstream_temporary),UPSTREAM_REVISION)
        with zipfile.ZipFile(upstream_temporary,'r') as upstream:
            upstream_entries=[item for item in upstream.infolist() if not item.is_dir() and upstream_included(item.filename)]
            upstream_names=[UPSTREAM_PREFIX+item.filename for item in upstream_entries]
            if len(upstream_names)!=len(set(name.casefold() for name in upstream_names)):
                raise ValueError('Case-insensitive upstream archive name collision')
            if UPSTREAM_PREFIX+'revision.txt' in upstream_names:
                raise ValueError('Upstream revision.txt would collide with provenance file')
            revision_text=('Repository: https://github.com/OrcaSlicer/OrcaSlicer\n'
                           'Commit: '+UPSTREAM_REVISION+'\n'
                           'Git-tracked tree exported without .git, build/cache directories, local.properties, '
                           'APK/AAB and compiled executable/library files. OBJ test models are retained.\n'
                           'The .agents entry is a Git symlink to .claude and is preserved as such in the ZIP.\n')
            manifest=('Kepler Slicer Glass Phase 2 Android source snapshot\n'
                      'Project: app, engine bridge, resources, tests and documentation.\n'
                      'Upstream: third_party/OrcaSlicer at '+UPSTREAM_REVISION+'; see its revision.txt and LICENSE.txt.\n'
                      'No Git metadata, build/cache directories, local.properties, APK/AAB or signing secrets.\n'
                      'Upstream compiled executable and library files are excluded; test OBJ models are retained.\n'
                      'Prebuilt Android ARM64 JNI library and Gradle wrapper JAR are included.\n'
                      'Rebuilding native requires separately obtained Android NDK, compiler and cross-built dependencies.\n'
                      'Android/Gradle rebuilding requires installed SDK, Gradle distribution and dependencies; '
                      'the wrapper may download them unless already cached. No fully offline rebuild is claimed.\n')
            generated={UPSTREAM_PREFIX+'revision.txt':revision_text.encode('utf8'),
                       PREFIX+'SOURCE_ARCHIVE_MANIFEST.txt':manifest.encode('utf8')}
            with zipfile.ZipFile(temporary, 'w', allowZip64=True, compresslevel=6) as archive:
                for file in files:
                    relative = file.relative_to(PROJECT).as_posix()
                    method = zipfile.ZIP_STORED if file.suffix.lower() in ALREADY_COMPRESSED else zipfile.ZIP_DEFLATED
                    archive.write(file, PREFIX + relative, compress_type=method)
                for info in upstream_entries:
                    output=zipfile.ZipInfo(UPSTREAM_PREFIX+info.filename,info.date_time)
                    output.create_system=info.create_system
                    output.external_attr=info.external_attr
                    output.compress_type=zipfile.ZIP_STORED if Path(info.filename).suffix.lower() in ALREADY_COMPRESSED else zipfile.ZIP_DEFLATED
                    with upstream.open(info) as source, archive.open(output,'w',force_zip64=True) as dest:
                        shutil.copyfileobj(source,dest,1024*1024)
                for name,data in generated.items():archive.writestr(name,data)
            with zipfile.ZipFile(temporary, 'r') as archive:
                expected = ({PREFIX + f.relative_to(PROJECT).as_posix() for f in files}
                            | set(upstream_names) | set(generated))
                if set(archive.namelist()) != expected or len(archive.namelist()) != len(expected):
                    raise ValueError('Archive contents do not match the allowlist')
                for file in files:
                    with file.open('rb') as source, archive.open(PREFIX + file.relative_to(PROJECT).as_posix()) as saved:
                        if digest(source) != digest(saved):
                            raise ValueError('File changed or failed verification: ' + str(file.relative_to(PROJECT)))
                for info in upstream_entries:
                    with upstream.open(info) as source, archive.open(UPSTREAM_PREFIX+info.filename) as saved:
                        if digest(source)!=digest(saved):raise ValueError('Upstream file failed verification: '+info.filename)
                for name,data in generated.items():
                    if archive.read(name)!=data:raise ValueError('Generated manifest failed verification: '+name)
        for file, old in signatures.items():
            if not file.exists() or (file.stat().st_size, file.stat().st_mtime_ns) != old:
                raise ValueError('Source changed during packaging: ' + str(file.relative_to(PROJECT)))
        if collect() != files:
            raise ValueError('Source file list changed during packaging')
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)
        upstream_temporary.unlink(missing_ok=True)
    with target.open('rb') as stream:
        checksum = digest(stream)
    print('Archive:', target)
    print('Project files:', len(files), 'Pinned upstream files:', len(upstream_entries))
    print('Size:', target.stat().st_size, 'bytes')
    print('SHA-256:', checksum)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
